package id.shuyasal.kuromaku.shinigami;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Daftar unduhan yang benar-benar ada di penyimpanan aplikasi.
 *
 * <p>Sumber kebenaran adalah berkas: setiap halaman dicek dengan aturan yang
 * sama seperti saat membaca offline ({@link ShinigamiDownloader#pageFile}).
 * Status di memori ({@link ShinigamiDownloadStatus}) hanya dipakai untuk
 * chapter yang masih berjalan, tidak pernah untuk mengklaim unduhan selesai.
 *
 * <p>Pemilik chapter (manga) dibaca dari catatan yang ditulis saat mengantre.
 * Bila catatan itu tidak ada — misalnya unduhan dari versi sebelumnya — chapter
 * tetap ditampilkan lewat riwayat, bukan dibuang.
 */
public final class ShinigamiDownloads {

    public static final class Chapter {
        public final String chapterId;
        public final String label;
        public final double number;
        public final int pages;
        public final int expected;
        public final long bytes;
        public final boolean complete;

        Chapter(String chapterId, String label, double number, int pages, int expected,
                long bytes, boolean complete) {
            this.chapterId = chapterId == null ? "" : chapterId;
            this.label = label == null ? "" : label;
            this.number = number;
            this.pages = pages;
            this.expected = expected;
            this.bytes = bytes;
            this.complete = complete;
        }

        /** Persentase halaman lengkap; 0 bila tidak ada halaman valid. */
        public int percent() {
            if (expected <= 0) return complete ? 100 : 0;
            return Math.max(0, Math.min(100, pages * 100 / expected));
        }
    }

    public static final class Manga {
        public final String mangaId;
        public final String title;
        public final String coverUrl;
        public final List<Chapter> chapters = new ArrayList<>();

        Manga(String mangaId, String title, String coverUrl) {
            this.mangaId = mangaId == null ? "" : mangaId;
            this.title = title == null ? "" : title;
            this.coverUrl = coverUrl == null ? "" : coverUrl;
        }

        public int savedChapters() {
            int saved = 0;
            for (Chapter chapter : chapters) if (chapter.complete) saved++;
            return saved;
        }

        public long bytes() {
            long total = 0;
            for (Chapter chapter : chapters) total += chapter.bytes;
            return total;
        }

        /** Chapter lengkap terakhir yang dibaca, untuk tombol lanjut membaca. */
        public Chapter continueChapter(ShinigamiStore store) {
            for (Chapter chapter : chapters) {
                if (chapter.complete && chapter.chapterId.equals(store.lastChapter(mangaId))) {
                    return chapter;
                }
            }
            return completeFirst();
        }

        Chapter completeFirst() {
            for (Chapter chapter : chapters) if (chapter.complete) return chapter;
            return chapters.isEmpty() ? null : chapters.get(0);
        }
    }

    private ShinigamiDownloads() {
    }

    /** Pindai seluruh folder unduhan. Panggil dari thread latar. */
    public static List<Manga> scan(Context context) {
        Map<String, Manga> grouped = new LinkedHashMap<>();
        if (context == null) return new ArrayList<>();
        ShinigamiStore store = new ShinigamiStore(context);
        ShinigamiDownloader local = new ShinigamiDownloader(context);
        File root = new File(context.getFilesDir(), "shinigami");
        File[] folders = root.listFiles();
        if (folders == null) return new ArrayList<>();

        for (File folder : folders) {
            String chapterId = folder.getName();
            if (!folder.isDirectory() || !chapterId.matches("[A-Za-z0-9_-]{1,128}")) continue;
            int expected = Math.max(store.expectedPages(chapterId), countPages(folder));
            int valid = 0;
            long bytes = 0;
            for (int index = 0; index < expected; index++) {
                File page = local.pageFile(chapterId, index);
                if (page == null) continue;
                valid++;
                bytes += page.length();
            }
            if (valid == 0) continue;
            boolean complete = expected > 0 && valid >= expected;

            JSONObject owner = store.chapterOwner(chapterId);
            String mangaId = owner == null ? "" : owner.optString("manga_id", "");
            String title = owner == null ? "" : owner.optString("title", "");
            String cover = owner == null ? "" : owner.optString("cover", "");
            String label = owner == null ? "" : owner.optString("label", "");
            double number = owner == null ? 0d : owner.optDouble("number", 0d);

            if (mangaId.isEmpty()) {
                // Kompatibilitas: unduhan versi lama belum punya catatan pemilik.
                for (ShinigamiStore.HistoryEntry entry : store.history()) {
                    if (!chapterId.equals(entry.chapterId)) continue;
                    mangaId = entry.mangaId;
                    title = entry.title;
                    cover = entry.coverUrl;
                    number = entry.chapterNumber;
                    break;
                }
            }
            if (mangaId.isEmpty()) {
                mangaId = "unknown";
                if (title.isEmpty()) title = "Chapter tanpa judul manga";
            }
            if (title.isEmpty()) title = store.mangaTitle(mangaId);
            if (title.isEmpty()) title = "Manga tanpa judul";
            if (cover.isEmpty()) cover = store.mangaCover(mangaId);
            if (label.isEmpty()) label = ShinigamiManga.chapterLabel(number, "");

            Manga manga = grouped.get(mangaId);
            if (manga == null) {
                manga = new Manga(mangaId, title, cover);
                grouped.put(mangaId, manga);
            }
            manga.chapters.add(new Chapter(chapterId, label, number, valid,
                    Math.max(expected, valid), bytes, complete));
        }

        List<Manga> out = new ArrayList<>(grouped.values());
        for (Manga manga : out) {
            manga.chapters.sort((left, right) -> Double.compare(right.number, left.number));
        }
        out.sort((left, right) -> Integer.compare(right.savedChapters(), left.savedChapters()));
        return out;
    }

    private static int countPages(File folder) {
        File[] files = folder.listFiles();
        if (files == null) return 0;
        int count = 0;
        for (File file : files) {
            String name = file.getName();
            if (name.endsWith(".img") && file.length() >= 8) count++;
        }
        return count;
    }

    /** Unduhan satu judul, atau {@code null} bila tidak ada. */
    public static Manga forManga(Context context, String mangaId) {
        if (mangaId == null || mangaId.isEmpty()) return null;
        for (Manga manga : scan(context)) {
            if (mangaId.equals(manga.mangaId)) return manga;
        }
        return null;
    }

    /** Total byte seluruh chapter yang lolos validasi. */
    public static long totalBytes(List<Manga> mangas) {
        long total = 0;
        if (mangas == null) return 0;
        for (Manga manga : mangas) total += manga.bytes();
        return total;
    }

    /** Ukuran ringkas: "12,4 MB" / "845 KB". */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024d;
        if (kb < 1024) return String.format(java.util.Locale.ROOT, "%.0f KB", kb);
        double mb = kb / 1024d;
        if (mb < 1024) return String.format(java.util.Locale.ROOT, "%.1f MB", mb).replace('.', ',');
        return String.format(java.util.Locale.ROOT, "%.2f GB", mb / 1024d).replace('.', ',');
    }
}
