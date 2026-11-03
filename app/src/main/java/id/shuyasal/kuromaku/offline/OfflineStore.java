package id.shuyasal.kuromaku.offline;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import id.shuyasal.kuromaku.data.Block;
import id.shuyasal.kuromaku.parser.ChapterContent;

/**
 * On-disk store for chapters that must be readable without a connection.
 *
 * <h3>Layout</h3>
 * <pre>
 * filesDir/offline/
 *   &lt;novelId&gt;/                      one directory per novel, never mixed
 *     index.json                     atomically written catalogue of saved chapters
 *     images/&lt;sha1(url)&gt;.jpg         one file per distinct illustration URL
 *     chapters/&lt;key&gt;/content.json    structured chapter with ordered blocks
 * </pre>
 *
 * <h3>Rules this class guarantees</h3>
 * <ul>
 *   <li><b>Stable identifier.</b> {@code key = sha1(novelId + "|" + chapterUrl)}. The URL
 *       is the chapter's identity on the source, so a re-download maps to the same
 *       directory instead of piling up copies.</li>
 *   <li><b>Atomic completion.</b> Content is written to {@code content.json.tmp}, synced,
 *       then renamed over the real file, and the index entry is added only afterwards.
 *       An interrupted download therefore leaves either the previous complete copy or
 *       nothing — never a half-written chapter the reader would treat as saved.</li>
 *   <li><b>No bare URLs.</b> Every illustration is stored as bytes; the JSON keeps the
 *       original URL for attribution plus the local file name that actually renders.</li>
 *   <li><b>No duplicates and no orphans.</b> Images are keyed by URL hash inside the novel,
 *       shared between chapters and reference counted; deleting a chapter removes only the
 *       files no other saved chapter still needs.</li>
 * </ul>
 */
public final class OfflineStore {

    private static final String TAG = "KuromakuOffline";

    static final int FORMAT_VERSION = 1;
    static final String ROOT = "offline";
    static final String INDEX_FILE = "index.json";
    static final String CONTENT_FILE = "content.json";
    static final String IMAGES_DIR = "images";
    static final String CHAPTERS_DIR = "chapters";

    private static volatile OfflineStore instance;

    private final File root;
    /** novelId -> (chapterUrl -> entry), loaded lazily from index.json. */
    private final Map<String, Map<String, Entry>> indexes = new LinkedHashMap<>();
    private final Map<String, Object> locks = new LinkedHashMap<>();

    private OfflineStore(File root) {
        this.root = root;
    }

    public static OfflineStore get(Context context) {
        OfflineStore local = instance;
        if (local != null) return local;
        synchronized (OfflineStore.class) {
            if (instance == null) {
                instance = new OfflineStore(new File(context.getApplicationContext().getFilesDir(), ROOT));
            }
            return instance;
        }
    }

    /** Replaces the singleton with one rooted at a temporary directory (tests). */
    public static synchronized void useRoot(File dir) {
        instance = new OfflineStore(dir);
    }

    public static synchronized void reset() {
        instance = null;
    }

    /** One saved chapter as recorded in the index. */
    public static final class Entry {
        public final String url;
        public final String key;
        public final String title;
        public final int volume;
        public final long bytes;
        public final long savedAt;
        public final int imageCount;
        public final List<String> imageFiles;

        Entry(String url, String key, String title, int volume, long bytes, long savedAt,
              int imageCount, List<String> imageFiles) {
            this.url = url == null ? "" : url;
            this.key = key == null ? "" : key;
            this.title = title == null ? "" : title;
            this.volume = volume;
            this.bytes = bytes;
            this.savedAt = savedAt;
            this.imageCount = imageCount;
            this.imageFiles = Collections.unmodifiableList(
                    imageFiles == null ? new ArrayList<String>() : imageFiles);
        }
    }

    public File novelDir(String novelId) {
        return new File(root, safe(novelId));
    }

    public File chapterDir(String novelId, String url) {
        return new File(new File(novelDir(novelId), CHAPTERS_DIR), keyFor(novelId, url));
    }

    public File imagesDir(String novelId) {
        return new File(novelDir(novelId), IMAGES_DIR);
    }

    /**
     * Stable per-chapter identifier: SHA-1 over the novel id and the canonical
     * chapter URL. Two novels can share a chapter URL shape without ever
     * colliding, and the same chapter always maps to the same folder.
     */
    public static String keyFor(String novelId, String url) {
        String seed = safe(novelId) + "|" + (url == null ? "" : url.trim());
        return sha1(seed).substring(0, 32);
    }

    /** File name for one illustration, keyed only by its URL inside the novel. */
    public static String imageName(String url, String extension) {
        String ext = extension == null || extension.isEmpty() ? "img" : extension;
        return sha1(url == null ? "" : url.trim()).substring(0, 24) + "." + ext;
    }

    // ---------------------------------------------------------------- lookups

    public boolean isSaved(String novelId, String url) {
        Entry entry = entry(novelId, url);
        if (entry == null) return false;
        return new File(chapterDir(novelId, url), CONTENT_FILE).isFile();
    }

    public Entry entry(String novelId, String url) {
        if (url == null) return null;
        return index(novelId).get(url.trim());
    }

    public List<Entry> entries(String novelId) {
        return new ArrayList<>(index(novelId).values());
    }

    /** Saved chapters grouped by volume number, in index insertion order. */
    public Map<Integer, List<Entry>> byVolume(String novelId) {
        Map<Integer, List<Entry>> grouped = new LinkedHashMap<>();
        for (Entry entry : index(novelId).values()) {
            List<Entry> bucket = grouped.get(entry.volume);
            if (bucket == null) {
                bucket = new ArrayList<>();
                grouped.put(entry.volume, bucket);
            }
            bucket.add(entry);
        }
        return grouped;
    }

    public int savedCount(String novelId) {
        return index(novelId).size();
    }

    public int savedCountInVolume(String novelId, int volume) {
        int count = 0;
        for (Entry entry : index(novelId).values()) {
            if (entry.volume == volume) count++;
        }
        return count;
    }

    /** Total bytes used by one novel, measured on disk so deletes are accurate. */
    public long bytesOf(String novelId) {
        return sizeOf(novelDir(novelId));
    }

    public long totalBytes() {
        File[] novels = root.listFiles();
        long total = 0;
        if (novels == null) return 0;
        for (File novel : novels) {
            if (novel.isDirectory()) total += sizeOf(novel);
        }
        return total;
    }

    /** Novel ids that currently hold at least one saved chapter. */
    public List<String> novelsWithContent() {
        List<String> ids = new ArrayList<>();
        File[] novels = root.listFiles();
        if (novels == null) return ids;
        for (File novel : novels) {
            if (!novel.isDirectory()) continue;
            if (new File(novel, INDEX_FILE).isFile() || new File(novel, CHAPTERS_DIR).isDirectory()) {
                ids.add(novel.getName());
            }
        }
        return ids;
    }

    // ------------------------------------------------------------------- read

    /**
     * Reads a saved chapter back into renderable blocks.
     *
     * @return the chapter, or {@code null} when it is absent or unreadable — a
     *         corrupt file is reported as "not saved" rather than shown broken.
     */
    public ChapterContent load(String novelId, String url) {
        File content = new File(chapterDir(novelId, url), CONTENT_FILE);
        if (!content.isFile()) return null;

        try {
            JSONObject json = new JSONObject(readText(content));
            JSONArray array = json.optJSONArray("blocks");
            if (array == null) return null;

            File images = imagesDir(novelId);
            List<Block> blocks = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                int type = item.optInt("t", Block.TYPE_PARAGRAPH);
                String text = item.optString("x", null);
                String imageUrl = item.optString("u", null);
                int level = item.optInt("l", 0);
                int width = item.optInt("w", 0);
                int height = item.optInt("h", 0);
                String file = item.optString("f", "");

                String absolute = null;
                if (!file.isEmpty()) {
                    File local = new File(images, file);
                    if (local.isFile()) absolute = local.getAbsolutePath();
                }

                Block block = Block.restored(type, text, imageUrl, level, width, height, absolute);
                // A picture whose file vanished must not be rendered as a
                // permanent empty box; drop it and let the text stand alone.
                if (type == Block.TYPE_IMAGE && absolute == null) continue;
                blocks.add(block);
            }
            if (blocks.isEmpty()) return null;
            return new ChapterContent(json.optString("title", ""), json.optString("url", url), blocks);
        } catch (Exception error) {
            Log.w(TAG, "chapter tersimpan tidak dapat dibaca: " + url, error);
            return null;
        }
    }

    // ------------------------------------------------------------------ write

    /** Result of one chapter write: what was stored and how much it weighed. */
    public static final class Saved {
        public final Entry entry;
        public final boolean reusedImages;

        Saved(Entry entry, boolean reusedImages) {
            this.entry = entry;
            this.reusedImages = reusedImages;
        }
    }

    /**
     * Stores one fully downloaded chapter.
     *
     * <p>The caller must have validated text and every image byte array already:
     * this method never records an index entry for content it could not write.
     */
    public Saved save(String novelId, String url, String title, int volume,
                      ChapterContent content, Map<String, byte[]> images) throws IOException {
        File dir = chapterDir(novelId, url);
        File imagesDir = imagesDir(novelId);
        if (!imagesDir.isDirectory() && !imagesDir.mkdirs()) {
            throw new IOException("tidak dapat membuat folder gambar");
        }
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("tidak dapat membuat folder chapter");
        }

        List<String> files = new ArrayList<>();
        long imageBytes = 0;
        boolean reused = false;

        if (images != null) {
            for (Map.Entry<String, byte[]> item : images.entrySet()) {
                String source = item.getKey();
                byte[] data = item.getValue();
                String extension = ImageValidator.extensionOf(data);
                if (extension == null) {
                    throw new IOException(ImageValidator.rejectionReason(data, source));
                }
                File target = new File(imagesDir, imageName(source, extension));
                if (target.isFile() && target.length() == data.length) {
                    // Same picture already stored by another chapter: reuse it
                    // instead of writing a second copy.
                    reused = true;
                } else {
                    writeAtomically(target, data);
                    imageBytes += data.length;
                }
                files.add(target.getName());
            }
        }

        JSONObject json = new JSONObject();
        try {
            json.put("version", FORMAT_VERSION);
            json.put("novel", novelId);
            json.put("url", url);
            json.put("title", title == null ? "" : title);
            json.put("volume", volume);
            json.put("savedAt", System.currentTimeMillis());

            JSONArray blocks = new JSONArray();
            if (content != null) {
                for (Block block : content.blocks()) {
                    JSONObject item = new JSONObject();
                    item.put("t", block.type);
                    if (block.text != null) item.put("x", block.text);
                    if (block.isImage()) {
                        item.put("u", block.imageUrl);
                        if (block.imageWidth > 0) item.put("w", block.imageWidth);
                        if (block.imageHeight > 0) item.put("h", block.imageHeight);
                        String name = fileNameOf(imagesDir, block.imageUrl);
                        if (name != null) item.put("f", name);
                    }
                    if (block.level > 0) item.put("l", block.level);
                    blocks.put(item);
                }
            }
            json.put("blocks", blocks);
        } catch (Exception error) {
            throw new IOException("tidak dapat menyusun isi chapter: " + error.getMessage());
        }

        File contentFile = new File(dir, CONTENT_FILE);
        writeAtomically(contentFile, json.toString().getBytes(StandardCharsets.UTF_8));

        long bytes = contentFile.length() + imageBytes;
        Entry entry = new Entry(url, keyFor(novelId, url), title, volume, bytes,
                System.currentTimeMillis(), files.size(), files);
        index(novelId).put(url.trim(), entry);
        writeIndex(novelId);
        return new Saved(entry, reused);
    }

    /** Records an entry that was already stored (used when rebuilding an index). */
    void putEntry(String novelId, Entry entry) {
        index(novelId).put(entry.url, entry);
    }

    // ----------------------------------------------------------------- delete

    /**
     * Deletes one chapter and any illustration no other saved chapter needs.
     *
     * <p>Reading progress and bookmarks live in {@code Prefs} under the chapter
     * URL and are deliberately untouched: removing a download must never look
     * like erasing where the reader stopped.
     */
    public void deleteChapter(String novelId, String url) {
        Map<String, Entry> index = index(novelId);
        Entry entry = index.remove(url == null ? null : url.trim());
        File dir = chapterDir(novelId, url);
        deleteTree(dir);

        Set<String> stillUsed = new LinkedHashSet<>();
        for (Entry other : index.values()) stillUsed.addAll(other.imageFiles);

        if (entry != null) {
            for (String file : entry.imageFiles) {
                if (stillUsed.contains(file)) continue;
                File image = new File(imagesDir(novelId), file);
                if (image.isFile() && !image.delete()) {
                    Log.w(TAG, "gambar tidak dapat dihapus: " + image);
                }
            }
        }
        writeIndex(novelId);
        pruneIfEmpty(novelId);
    }

    /**
     * Removes the novel folder once nothing is stored for it any more.
     *
     * <p>Without this an emptied library kept a 48-byte index and empty
     * {@code chapters}/{@code images} folders forever, so the reported storage
     * usage never returned to zero and "no unused files" was not true.
     */
    private void pruneIfEmpty(String novelId) {
        String id = safe(novelId);
        if (!index(id).isEmpty()) return;
        File chapters = new File(novelDir(id), CHAPTERS_DIR);
        File[] chapterDirs = chapters.listFiles();
        if (chapterDirs != null && chapterDirs.length > 0) return;
        File[] images = imagesDir(id).listFiles();
        if (images != null && images.length > 0) return;

        deleteTree(chapters);
        deleteTree(imagesDir(id));
        indexes.remove(id);
        //noinspection ResultOfMethodCallIgnored
        new File(novelDir(id), INDEX_FILE).delete();
        deleteTree(novelDir(id));
    }

    /** Deletes every saved chapter of one volume, keeping the rest untouched. */
    public int deleteVolume(String novelId, int volume) {
        List<Entry> targets = new ArrayList<>();
        for (Entry entry : index(novelId).values()) {
            if (entry.volume == volume) targets.add(entry);
        }
        for (Entry entry : targets) deleteChapter(novelId, entry.url);
        pruneIfEmpty(novelId);
        return targets.size();
    }

    /** Deletes everything stored for one novel. */
    public void deleteNovel(String novelId) {
        deleteTree(novelDir(novelId));
        indexes.remove(safe(novelId));
    }

    // ------------------------------------------------------------------ index

    private Map<String, Entry> index(String novelId) {
        String id = safe(novelId);
        synchronized (locks.computeIfAbsent(id, key -> new Object())) {
            Map<String, Entry> cached = indexes.get(id);
            if (cached != null) return cached;

            Map<String, Entry> loaded = new LinkedHashMap<>();
            File file = new File(novelDir(id), INDEX_FILE);
            if (file.isFile()) {
                try {
                    JSONObject json = new JSONObject(readText(file));
                    JSONArray array = json.optJSONArray("chapters");
                    if (array != null) {
                        for (int i = 0; i < array.length(); i++) {
                            Entry entry = fromJson(array.getJSONObject(i));
                            // Trust the disk, not the index: a chapter whose file
                            // was removed (or truncated) is not "saved".
                            if (entry != null
                                    && new File(chapterDir(id, entry.url), CONTENT_FILE).isFile()) {
                                loaded.put(entry.url, entry);
                            }
                        }
                    }
                } catch (Exception error) {
                    Log.w(TAG, "index unduhan rusak, memindai ulang", error);
                }
            }
            if (loaded.isEmpty()) loaded = scan(id);
            indexes.put(id, loaded);
            return loaded;
        }
    }

    /** Rebuilds the index from the chapter folders when index.json is missing. */
    private Map<String, Entry> scan(String novelId) {
        Map<String, Entry> found = new LinkedHashMap<>();
        File chapters = new File(novelDir(novelId), CHAPTERS_DIR);
        File[] dirs = chapters.listFiles();
        if (dirs == null) return found;

        for (File dir : dirs) {
            File content = new File(dir, CONTENT_FILE);
            if (!content.isFile()) continue;
            try {
                JSONObject json = new JSONObject(readText(content));
                String url = json.optString("url", "");
                if (url.isEmpty()) continue;
                found.put(url, new Entry(url, dir.getName(), json.optString("title", ""),
                        json.optInt("volume", 0), content.length(),
                        json.optLong("savedAt", content.lastModified()), 0,
                        new ArrayList<String>()));
            } catch (Exception ignored) {
                // Unreadable folders are simply not part of the catalogue.
            }
        }
        return found;
    }

    private void writeIndex(String novelId) {
        File index = new File(novelDir(novelId), INDEX_FILE);
        try {
            JSONObject json = new JSONObject();
            json.put("version", FORMAT_VERSION);
            json.put("novel", novelId);
            JSONArray array = new JSONArray();
            for (Entry entry : index(novelId).values()) {
                JSONObject item = new JSONObject();
                item.put("url", entry.url);
                item.put("key", entry.key);
                item.put("title", entry.title);
                item.put("volume", entry.volume);
                item.put("bytes", entry.bytes);
                item.put("savedAt", entry.savedAt);
                item.put("images", entry.imageCount);
                JSONArray files = new JSONArray();
                for (String file : entry.imageFiles) files.put(file);
                item.put("files", files);
                array.put(item);
            }
            json.put("chapters", array);
            writeAtomically(index, json.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            Log.w(TAG, "gagal menulis index unduhan", error);
        }
    }

    private Entry fromJson(JSONObject item) {
        try {
            JSONArray files = item.optJSONArray("files");
            List<String> list = new ArrayList<>();
            if (files != null) {
                for (int i = 0; i < files.length(); i++) list.add(files.getString(i));
            }
            return new Entry(item.optString("url", ""), item.optString("key", ""),
                    item.optString("title", ""), item.optInt("volume", 0),
                    item.optLong("bytes", 0), item.optLong("savedAt", 0),
                    item.optInt("images", list.size()), list);
        } catch (Exception error) {
            return null;
        }
    }

    // ------------------------------------------------------------------ utils

    /**
     * Writes {@code data} to {@code target} so a reader of that path sees either
     * the old file or the complete new one: bytes go to a sibling temp file, are
     * flushed to the device, then renamed over the target.
     */
    public static void writeAtomically(File target, byte[] data) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("tidak dapat membuat folder " + parent);
        }
        File temp = new File(parent, target.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(data);
            out.flush();
            out.getFD().sync();
        } catch (IOException error) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw error;
        }
        if (!temp.renameTo(target)) {
            // Some filesystems refuse to rename over an existing file.
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            if (!temp.renameTo(target)) {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
                throw new IOException("tidak dapat menyimpan " + target.getName());
            }
        }
    }

    static String readText(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) Math.min(Integer.MAX_VALUE, Math.max(1024, file.length()))];
            int read = 0;
            int step;
            while (read < buffer.length && (step = in.read(buffer, read, buffer.length - read)) > 0) {
                read += step;
            }
            return new String(buffer, 0, read, StandardCharsets.UTF_8);
        }
    }

    private static long sizeOf(File dir) {
        if (dir == null) return 0;
        if (dir.isFile()) return dir.length();
        File[] children = dir.listFiles();
        if (children == null) return 0;
        long total = 0;
        for (File child : children) total += sizeOf(child);
        return total;
    }

    private static void deleteTree(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) deleteTree(child);
        }
        //noinspection ResultOfMethodCallIgnored
        dir.delete();
    }

    private static String fileNameOf(File imagesDir, String url) {
        if (url == null) return null;
        File[] existing = imagesDir.listFiles();
        if (existing == null) return null;
        String prefix = sha1(url.trim()).substring(0, 24) + ".";
        for (File file : existing) {
            if (file.getName().startsWith(prefix)) return file.getName();
        }
        return null;
    }

    private static String safe(String value) {
        String raw = value == null ? "" : value.trim();
        if (raw.isEmpty()) return "unknown";
        return raw.replaceAll("[^A-Za-z0-9._-]", "_").toLowerCase(Locale.ROOT);
    }

    static String sha1(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) hex.append(String.format(Locale.ROOT, "%02x", b));
            return hex.toString();
        } catch (Exception error) {
            // SHA-1 is mandatory on every Android runtime; this is unreachable.
            return Integer.toHexString(value.hashCode());
        }
    }

    /** Human readable storage size, e.g. {@code "12,4 MB"}. */
    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }
}
