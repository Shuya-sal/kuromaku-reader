package id.shuyasal.kuromaku.komiku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unduhan offline Manga Server 3.
 *
 * <p>Yang dijaga: hanya berkas yang benar-benar lengkap dianggap tersimpan,
 * berkas terpotong tidak pernah lolos, unduhan bisa dilanjutkan, dan menghapus
 * unduhan tidak menyentuh progres baca.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class KomikuDownloadTest {

    private Context context;
    private KomikuDownloads downloads;

    /** JPEG kecil yang benar-benar lengkap (SOI ... EOI). */
    private static byte[] jpeg(int padding) {
        byte[] bytes = new byte[64 + padding];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        bytes[3] = (byte) 0xE0;
        for (int i = 4; i < bytes.length - 2; i++) bytes[i] = (byte) (i % 251);
        bytes[bytes.length - 2] = (byte) 0xFF;
        bytes[bytes.length - 1] = (byte) 0xD9;
        return bytes;
    }

    /** PNG kecil yang lengkap. */
    private static byte[] png() {
        byte[] bytes = new byte[80];
        bytes[0] = (byte) 0x89;
        bytes[1] = 'P';
        bytes[2] = 'N';
        bytes[3] = 'G';
        bytes[bytes.length - 12] = 0;
        bytes[bytes.length - 11] = 0;
        bytes[bytes.length - 10] = 0;
        bytes[bytes.length - 9] = 0;
        bytes[bytes.length - 8] = 73;
        bytes[bytes.length - 7] = 69;
        bytes[bytes.length - 6] = 78;
        bytes[bytes.length - 5] = 68;
        bytes[bytes.length - 4] = (byte) 174;
        bytes[bytes.length - 3] = 66;
        bytes[bytes.length - 2] = 96;
        bytes[bytes.length - 1] = (byte) 130;
        return bytes;
    }

    private static void write(File file, byte[] bytes) throws Exception {
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
        }
    }

    @Before public void siapkan() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(KomikuStore.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        downloads = new KomikuDownloads(context);
        File root = new File(context.getFilesDir(), "komiku");
        deleteTree(root);
        File map = new File(context.getFilesDir(), KomikuDownloads.MAP_FILE);
        if (map.isFile()) assertTrue(map.delete());
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }

    private static final String CHAPTER = "https://komiku.org/black-clover-chapter-10/";

    @Test public void chapterLengkapDianggapTersimpan() throws Exception {
        for (int i = 0; i < 3; i++) {
            write(new File(downloads.chapterDir(CHAPTER), i + ".img"), jpeg(500));
        }
        assertTrue("tiga halaman lengkap", downloads.isDownloaded(CHAPTER, 3));
        assertEquals(3, downloads.savedPages(CHAPTER, 3));
        assertNotNull(downloads.pageFile(CHAPTER, 0));
    }

    @Test public void halamanTerpotongTidakDianggapTersimpan() throws Exception {
        for (int i = 0; i < 2; i++) {
            write(new File(downloads.chapterDir(CHAPTER), i + ".img"), jpeg(500));
        }
        // Halaman ketiga terpotong: tidak ada penanda akhir JPEG.
        byte[] broken = jpeg(500);
        byte[] truncated = new byte[broken.length - 2];
        System.arraycopy(broken, 0, truncated, 0, truncated.length);
        write(new File(downloads.chapterDir(CHAPTER), "2.img"), truncated);

        assertFalse("chapter belum lengkap", downloads.isDownloaded(CHAPTER, 3));
        assertEquals(2, downloads.savedPages(CHAPTER, 3));
        assertNull("halaman terpotong tidak dikembalikan",
                downloads.pageFile(CHAPTER, 2));
    }

    @Test public void berkasBukanGambarDitolak() throws Exception {
        write(new File(downloads.chapterDir(CHAPTER), "0.img"),
                "<html>bukan gambar</html>".getBytes(StandardCharsets.UTF_8));
        assertNull(downloads.pageFile(CHAPTER, 0));
        assertEquals(0, downloads.savedPages(CHAPTER, 1));
    }

    @Test public void pngLengkapDiterima() throws Exception {
        write(new File(downloads.chapterDir(CHAPTER), "0.img"), png());
        assertNotNull(downloads.pageFile(CHAPTER, 0));
    }

    @Test public void hapusUnduhanTidakMenghapusProgresBaca() throws Exception {
        write(new File(downloads.chapterDir(CHAPTER), "0.img"), jpeg(300));
        downloads.remember(CHAPTER, "black-clover", "Black Clover", "Chapter 10", 1);

        KomikuStore store = new KomikuStore(context);
        store.setProgress(CHAPTER, 0.62);
        store.recordChapter("black-clover", "Black Clover", "", CHAPTER, "Chapter 10", 10, 0.62);

        assertTrue(KomikuDownloadQueue.deleteChapter(context, CHAPTER));
        assertFalse(downloads.isDownloaded(CHAPTER, 1));
        assertEquals("progres baca tetap", 0.62, store.progress(CHAPTER), 0.001);
        assertTrue("riwayat tetap ada", store.history().size() >= 1);
    }

    @Test public void catatanPemilikDisimpanDanDibaca() {
        downloads.remember("https://komiku.org/one-piece-chapter-5/", "one-piece",
                "One Piece", "Chapter 5", 18);
        downloads.remember("https://komiku.org/one-piece-chapter-6/", "one-piece",
                "One Piece", "Chapter 6", 17);

        List<KomikuDownloads.Entry> entries = downloads.entriesFor("one-piece");
        assertEquals(2, entries.size());
        List<String> labels = new ArrayList<>();
        int pages = 0;
        for (KomikuDownloads.Entry entry : entries) {
            labels.add(entry.label);
            pages += entry.pages;
        }
        assertTrue("kedua label tercatat", labels.contains("Chapter 5")
                && labels.contains("Chapter 6"));
        assertEquals("jumlah halaman tercatat", 35, pages);
    }

    @Test public void namaFolderAmanDanStabil() {
        String first = KomikuDownloads.folderName("https://komiku.org/black-clover-chapter-10/");
        String second = KomikuDownloads.folderName("https://komiku.org/black-clover-chapter-10/");
        assertEquals("nama folder stabil", first, second);
        assertTrue("hanya huruf, angka, garis", first.matches("[A-Za-z0-9_-]+"));
        assertFalse("tidak memuat garis miring", first.contains("/"));
    }

    @Test public void statusAntreanTerbaca() {
        String key = "https://komiku.org/x-chapter-1/";
        KomikuDownloadStatus.clear("https://komiku.org/x-chapter-1/");
        assertEquals(KomikuDownloadStatus.NOT_DOWNLOADED,
                KomikuDownloadStatus.state("https://komiku.org/x-chapter-1/"));

        KomikuDownloadStatus.describe(key, "Judul X", "Chapter 1", "x");
        KomikuDownloadStatus.set(key, KomikuDownloadStatus.QUEUED, 0, "");
        assertEquals("Judul X", KomikuDownloadStatus.title(key));
        assertEquals("Chapter 1", KomikuDownloadStatus.subtitle(key));
        assertTrue("antrean terdaftar",
                KomikuDownloadStatus.active().stream()
                        .anyMatch(item -> item.chapterKey.equals(key)));

        KomikuDownloadStatus.set(key, KomikuDownloadStatus.DOWNLOADING, 42, "");
        assertEquals(42, KomikuDownloadStatus.percent(key));
        assertTrue("label menyebut persen",
                KomikuDownloadStatus.label(context, key, 0).contains("42"));
    }

    @Test public void antrekanChapterMengembalikanStatusYangJujur() throws Exception {
        String key = "https://komiku.org/w-chapter-1/";
        boolean queued = KomikuDownloadQueue.enqueueChapter(context, key, "w",
                "Judul W", "Chapter 1");
        // WorkManager bisa tersedia atau tidak di lingkungan tes; yang dijaga
        // adalah: bila dijadwalkan, statusnya benar-benar tercatat.
        if (queued) {
            int state = KomikuDownloadStatus.state(key);
            assertTrue("status tercatat saat dijadwalkan",
                    state == KomikuDownloadStatus.QUEUED
                            || state == KomikuDownloadStatus.DOWNLOADING
                            || state == KomikuDownloadStatus.FAILED);
        } else {
            assertEquals("tidak mengklaim antrean saat gagal",
                    KomikuDownloadStatus.NOT_DOWNLOADED, KomikuDownloadStatus.state(key));
        }
    }

    @Test public void labelStatusJujur() throws Exception {
        String key = "https://komiku.org/y-chapter-1/";
        KomikuDownloadStatus.clear(key);
        assertEquals("Belum diunduh",
                KomikuDownloadStatus.label(context, key, 0));

        write(new File(downloads.chapterDir(key), "0.img"), jpeg(200));
        downloads.remember(key, "y", "Judul Y", "Chapter 1", 2);
        assertTrue("sebagian diakui sebagian",
                KomikuDownloadStatus.label(context, key, 2).contains("Sebagian"));

        write(new File(downloads.chapterDir(key), "1.img"), jpeg(200));
        assertEquals("Tersimpan offline",
                KomikuDownloadStatus.label(context, key, 2));
    }

    @Test public void antreanMelewatiChapterYangSudahLengkap() throws Exception {
        String key = "https://komiku.org/z-chapter-1/";
        for (int i = 0; i < 2; i++) {
            write(new File(downloads.chapterDir(key), i + ".img"), jpeg(200));
        }
        downloads.remember(key, "z", "Judul Z", "Chapter 1", 2);

        Map<String, String> chapters = new LinkedHashMap<>();
        chapters.put(key, "Chapter 1");
        assertEquals("tidak ada yang diantrekan", 0,
                KomikuDownloadQueue.enqueue(context, "z", "Judul Z", chapters));
    }

    @Test public void ukuranTerhitung() throws Exception {
        for (int i = 0; i < 2; i++) {
            write(new File(downloads.chapterDir(CHAPTER), i + ".img"), jpeg(1000));
        }
        assertTrue("ukuran masuk akal", downloads.bytes(CHAPTER) > 1000);
    }
}
