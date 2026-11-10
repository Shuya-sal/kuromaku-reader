package id.shuyasal.kuromaku.komiku;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Penyimpanan chapter Komiku untuk dibaca tanpa jaringan.
 *
 * <p>Halaman disimpan sebagai berkas di penyimpanan internal aplikasi dan
 * divalidasi sebelum dianggap ada: ukuran minimum, tipe gambar yang dikenali
 * dari bytes awal, dan kelengkapan wadah (JPEG/PNG/GIF/WebP). Berkas yang
 * terpotong tidak pernah dianggap "tersimpan offline", jadi tombol baca offline
 * tidak menjanjikan sesuatu yang tidak bisa dibuka.
 *
 * <p>Nama direktori memakai sidik jari alamat chapter, bukan judul — judul
 * berubah-ubah (mis. "Chapter 5" menjadi "Chapter 5 - Kelahiran") sedangkan
 * alamat chapter stabil.
 */
public final class KomikuDownloads {

    /** Berkas peta chapter → pemilik (judul manga dan label chapter). */
    static final String MAP_FILE = "komiku-downloads.json";

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();

    private static final String UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0 Mobile Safari/537.36 Kuromaku/1.0";

    private static final int MAX_IMAGE_BYTES = 32 * 1024 * 1024;

    private final Context context;

    public KomikuDownloads(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Direktori satu chapter; dibuat bila belum ada. */
    public File chapterDir(String chapterKey) {
        File root = new File(context.getFilesDir(), "komiku");
        File dir = new File(root, folderName(chapterKey));
        if (!dir.isDirectory() && !dir.mkdirs()) {
            return dir;
        }
        return dir;
    }

    /**
     * Nama direktori dari alamat chapter.
     *
     * <p>Slug chapter dipakai bila bentuknya aman; selain itu sidik jari
     * sederhana. Tidak pernah memakai nama berkas mentah dari jaringan.
     */
    static String folderName(String chapterKey) {
        String slug = KomikuConfig.mangaId(chapterKey == null ? "" : chapterKey);
        if (slug == null) slug = "";
        slug = slug.replaceAll("[^A-Za-z0-9_-]", "-");
        if (slug.length() > 80) slug = slug.substring(0, 80);
        if (slug.isEmpty()) slug = "chapter";
        return slug + "-" + Integer.toHexString(
                (chapterKey == null ? "" : chapterKey).hashCode());
    }

    /** Berkas satu halaman, atau {@code null} bila belum ada / tidak valid. */
    public File pageFile(String chapterKey, int index) {
        if (index < 0) return null;
        File file = new File(chapterDir(chapterKey), index + ".img");
        return isValidImage(file) ? file : null;
    }

    /** Benar bila semua halaman yang diharapkan sudah valid. */
    public boolean isDownloaded(String chapterKey, int expectedPages) {
        if (expectedPages <= 0) return false;
        for (int index = 0; index < expectedPages; index++) {
            if (pageFile(chapterKey, index) == null) return false;
        }
        return true;
    }

    /** Jumlah halaman valid yang sudah ada. */
    public int savedPages(String chapterKey, int expectedPages) {
        int saved = 0;
        for (int index = 0; index < Math.max(0, expectedPages); index++) {
            if (pageFile(chapterKey, index) != null) saved++;
        }
        return saved;
    }

    /** Ukuran seluruh berkas chapter di penyimpanan. */
    public long bytes(String chapterKey) {
        File dir = chapterDir(chapterKey);
        File[] files = dir.listFiles();
        if (files == null) return 0;
        long total = 0;
        for (File file : files) total += file.length();
        return total;
    }

    /** Hapus seluruh berkas chapter. Progres baca tidak disentuh. */
    public boolean delete(String chapterKey) {
        File dir = chapterDir(chapterKey);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (!file.delete()) {
                    // Berkas terkunci: sisa berkas akan terhapus pada percobaan
                    // berikutnya, dan halaman yang tidak valid diabaikan.
                }
            }
        }
        boolean gone = dir.delete();
        forget(chapterKey);
        return gone;
    }

    // ------------------------------------------------------------- unduhan

    public static final class Result {
        public final boolean complete;
        public final int saved;
        public final int total;
        public final String message;

        Result(boolean complete, int saved, int total, String message) {
            this.complete = complete;
            this.saved = saved;
            this.total = total;
            this.message = message == null ? "" : message;
        }
    }

    public interface Progress {
        void onPage(int done, int total);
    }

    /**
     * Unduh seluruh halaman chapter.
     *
     * <p>Halaman yang sudah valid dilewati (resume). Setiap gambar divalidasi
     * sebelum ditulis permanen: berkas ditulis ke nama sementara lalu diganti
     * nama, jadi unduhan yang terputus tidak meninggalkan berkas setengah jadi
     * yang terlihat sah.
     */
    public Result download(String chapterKey, List<String> urls, Progress progress)
            throws IOException {
        if (urls == null || urls.isEmpty()) {
            throw new IOException("Daftar halaman chapter kosong.");
        }
        int total = urls.size();
        String referer = KomikuConfig.BASE + "/";
        int saved = 0;
        for (int index = 0; index < total; index++) {
            if (pageFile(chapterKey, index) != null) {
                saved++;
                if (progress != null) progress.onPage(saved, total);
                continue;
            }
            String url = urls.get(index);
            if (!KomikuConfig.isAllowed(url)) {
                throw new IOException("Alamat gambar ditolak: bukan HTTPS/host sumber.");
            }
            fetchToFile(url, referer, new File(chapterDir(chapterKey), index + ".img"));
            if (pageFile(chapterKey, index) == null) {
                throw new IOException("Halaman " + (index + 1) + " tidak lolos validasi.");
            }
            saved++;
            if (progress != null) progress.onPage(saved, total);
        }
        return new Result(saved == total, saved, total,
                saved == total ? "Tersimpan offline" : "Sebagian halaman tersimpan");
    }

    private void fetchToFile(String url, String referer, File target) throws IOException {
        Request request = new Request.Builder().url(url)
                .header("User-Agent", UA)
                .header("Referer", referer)
                .header("Accept", "image/*")
                .build();
        File temp = new File(target.getParentFile(), target.getName() + ".part");
        try (Response response = CLIENT.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (response.code() < 200 || response.code() >= 300) {
                throw new IOException("Sumber menolak gambar (HTTP " + response.code() + ").");
            }
            if (body == null) throw new IOException("Gambar kosong.");
            long declared = body.contentLength();
            if (declared > MAX_IMAGE_BYTES) throw new IOException("Gambar terlalu besar.");
            try (InputStream input = body.byteStream();
                 FileOutputStream output = new FileOutputStream(temp)) {
                byte[] buffer = new byte[16 * 1024];
                long total = 0;
                int read;
                while ((read = input.read(buffer)) > 0) {
                    total += read;
                    if (total > MAX_IMAGE_BYTES) {
                        throw new IOException("Gambar terlalu besar.");
                    }
                    output.write(buffer, 0, read);
                }
                output.flush();
            }
        } catch (IOException error) {
            if (temp.isFile() && !temp.delete()) {
                // Sisa berkas sementara diabaikan; nama .part tidak pernah
                // dibaca sebagai halaman.
            }
            throw error;
        }
        if (!isValidImage(temp)) {
            if (!temp.delete()) {
                // Berkas tidak valid tidak akan pernah dianggap halaman.
            }
            throw new IOException("Gambar tidak dikenali.");
        }
        if (target.isFile() && !target.delete()) {
            // Akan digantikan oleh rename di bawah bila sistem mengizinkan.
        }
        if (!temp.renameTo(target)) {
            throw new IOException("Halaman tidak dapat disimpan.");
        }
    }

    /** Validasi isi berkas gambar: tipe dikenal dan wadahnya lengkap. */
    static boolean isValidImage(File file) {
        if (file == null || !file.isFile() || file.length() < 64) return false;
        if (file.length() > MAX_IMAGE_BYTES) return false;
        byte[] head = new byte[16];
        int read;
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            read = in.read(head);
        } catch (IOException error) {
            return false;
        }
        if (read < 8) return false;
        try {
            return hasCompleteContainer(file, head);
        } catch (IOException | RuntimeException error) {
            return false;
        }
    }

    private static boolean hasCompleteContainer(File file, byte[] head) throws IOException {
        try (java.io.RandomAccessFile input = new java.io.RandomAccessFile(file, "r")) {
            long size = input.length();
            if (head[0] == 'R' && head[1] == 'I' && head[8] == 'W' && head[9] == 'E') {
                long declared = ((long) head[4] & 255) | (((long) head[5] & 255) << 8)
                        | (((long) head[6] & 255) << 16) | (((long) head[7] & 255) << 24);
                return declared + 8 <= size;
            }
            if ((head[0] & 255) == 137 && head[1] == 'P') {
                if (size < 12) return false;
                input.seek(size - 12);
                byte[] end = new byte[12];
                input.readFully(end);
                return Arrays.equals(end,
                        new byte[]{0, 0, 0, 0, 73, 69, 78, 68, (byte) 174, 66, 96, (byte) 130});
            }
            if (head[0] == 'G' && head[1] == 'I') {
                input.seek(size - 1);
                return input.read() == 59;
            }
            // JPEG (termasuk yang diawali APPn) atau WebP.
            if ((head[0] & 255) == 0xFF && (head[1] & 255) == 0xD8) {
                input.seek(size - 2);
                return input.read() == 0xFF && input.read() == 0xD9;
            }
            if (head[0] == 'R' && head[1] == 'I' && head[8] == 'W' && head[9] == 'E'
                    && head[12] == 'W' && head[13] == 'E') {
                return size > 16;
            }
            return false;
        }
    }

    // ----------------------------------------------------- peta pemilik

    /** Catat pemilik chapter supaya layar Unduhan bisa mengelompokkannya. */
    public void remember(String chapterKey, String mangaId, String mangaTitle,
                         String chapterLabel, int pages) {
        if (chapterKey == null || chapterKey.isEmpty()) return;
        try {
            JSONObject map = readMap();
            JSONObject item = new JSONObject();
            item.put("chapter", chapterKey);
            item.put("mangaId", mangaId == null ? "" : mangaId);
            item.put("mangaTitle", mangaTitle == null ? "" : mangaTitle);
            item.put("label", chapterLabel == null ? "" : chapterLabel);
            item.put("pages", pages);
            item.put("savedAt", System.currentTimeMillis());
            map.put(chapterKey, item);
            writeMap(map);
        } catch (Exception ignored) {
            // Catatan pemilik hanya untuk pengelompokan; unduhan tetap sah
            // walau catatan ini gagal ditulis.
        }
    }

    public void forget(String chapterKey) {
        if (chapterKey == null) return;
        try {
            JSONObject map = readMap();
            map.remove(chapterKey);
            writeMap(map);
        } catch (Exception ignored) {
            // Sama seperti di atas: bukan kegagalan yang perlu dilaporkan.
        }
    }

    /** Satu chapter yang tercatat punya berkas tersimpan. */
    public static final class Entry {
        public final String chapterKey;
        public final String mangaId;
        public final String mangaTitle;
        public final String label;
        public final int pages;
        public final long savedAt;

        Entry(JSONObject item) {
            this.chapterKey = item.optString("chapter", "");
            this.mangaId = item.optString("mangaId", "");
            this.mangaTitle = item.optString("mangaTitle", "");
            this.label = item.optString("label", "");
            this.pages = item.optInt("pages", 0);
            this.savedAt = item.optLong("savedAt", 0);
        }
    }

    /** Semua chapter yang tercatat, terbaru di atas. */
    public List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        try {
            JSONObject map = readMap();
            JSONArray names = map.names();
            if (names == null) return out;
            for (int i = 0; i < names.length(); i++) {
                JSONObject item = map.optJSONObject(names.getString(i));
                if (item == null) continue;
                Entry entry = new Entry(item);
                if (entry.chapterKey.isEmpty()) continue;
                out.add(entry);
            }
            out.sort((a, b) -> Long.compare(b.savedAt, a.savedAt));
        } catch (Exception ignored) {
            return out;
        }
        return out;
    }

    /** Chapter tersimpan untuk satu judul, terbaru di atas. */
    public List<Entry> entriesFor(String mangaId) {
        List<Entry> out = new ArrayList<>();
        if (mangaId == null) return out;
        for (Entry entry : entries()) {
            if (mangaId.equals(entry.mangaId)) out.add(entry);
        }
        return out;
    }

    /** Jumlah halaman valid untuk satu chapter menurut catatan. */
    public int savedFor(Entry entry) {
        if (entry == null) return 0;
        return savedPages(entry.chapterKey, entry.pages);
    }

    private JSONObject readMap() {
        File file = new File(context.getFilesDir(), MAP_FILE);
        if (!file.isFile()) return new JSONObject();
        try {
            String text = new String(java.nio.file.Files.readAllBytes(file.toPath()),
                    StandardCharsets.UTF_8);
            return new JSONObject(text);
        } catch (Exception broken) {
            // Peta rusak tidak boleh menghilangkan berkas yang sudah ada:
            // berkas halaman tetap valid dan tetap bisa dibaca.
            return new JSONObject();
        }
    }

    private void writeMap(JSONObject map) throws IOException {
        File file = new File(context.getFilesDir(), MAP_FILE);
        File temp = new File(context.getFilesDir(), MAP_FILE + ".tmp");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(temp),
                StandardCharsets.UTF_8)) {
            writer.write(map.toString());
        }
        if (file.isFile() && !file.delete()) {
            // Akan digantikan oleh rename di bawah.
        }
        if (!temp.renameTo(file)) throw new IOException("Peta unduhan tidak dapat disimpan.");
    }
}
