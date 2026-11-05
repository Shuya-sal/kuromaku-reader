package id.shuyasal.kuromaku.shinigami;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Jalur unduhan sungguhan: tulis berkas, validasi, resume, gagal bersih. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ShinigamiDownloaderTest {

    private static final String CHAPTER = "chapter-uji-download";
    private Context context;
    private byte[] page;

    @Before public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        ShinigamiDownloadPolicy.unlockForTesting(true);
        Bitmap bitmap = Bitmap.createBitmap(120, 400, Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        bitmap.recycle();
        page = out.toByteArray();
        cleanup();
    }

    @After public void tearDown() {
        cleanup();
        ShinigamiDownloadPolicy.resetForTesting();
    }

    private void cleanup() {
        new ShinigamiDownloader(context).deleteChapter(CHAPTER);
        ShinigamiDownloadStatus.clear(CHAPTER);
    }

    private static List<String> urls(int count) {
        List<String> out = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            out.add("https://assets.shngm.id/chapter/manga_x/chapter_y/" + index + "-abc.webp");
        }
        return out;
    }

    private ShinigamiDownloader.Transport transport(byte[] bytes, int[] calls) {
        return (url, referer) -> {
            calls[0]++;
            return new ShinigamiDownloader.Stream(200, "image/png", new ByteArrayInputStream(bytes));
        };
    }

    @Test public void downloadsEveryPageAndOnlyThenMarksSaved() throws Exception {
        int[] calls = {0};
        ShinigamiDownloader downloader = new ShinigamiDownloader(context, transport(page, calls));
        List<Integer> progress = new ArrayList<>();

        ShinigamiDownloader.Result result = downloader.download(CHAPTER, urls(3),
                (done, total) -> progress.add(done));

        assertTrue(result.complete);
        assertEquals(3, result.saved);
        assertEquals(3, result.total);
        assertEquals(3, calls[0]);
        for (int index = 0; index < 3; index++) {
            assertNotNull("halaman " + index + " harus ada", downloader.pageFile(CHAPTER, index));
        }
        assertTrue(downloader.isDownloaded(CHAPTER, 3));
        assertEquals(ShinigamiDownloader.LABEL_SAVED, downloader.storageLabel(CHAPTER, 3));
        assertEquals(ShinigamiDownloadStatus.SAVED, ShinigamiDownloadStatus.state(CHAPTER));
        // Progres berbasis jumlah halaman yang benar-benar tersimpan.
        assertEquals(Arrays.asList(1, 2, 3), progress);
    }

    @Test public void resumeSkipsPagesAlreadyOnDisk() throws Exception {
        int[] calls = {0};
        ShinigamiDownloader first = new ShinigamiDownloader(context, transport(page, calls));
        first.download(CHAPTER, urls(2), null);
        assertEquals(2, calls[0]);

        ShinigamiDownloader second = new ShinigamiDownloader(context, transport(page, calls));
        ShinigamiDownloader.Result result = second.download(CHAPTER, urls(2), null);

        assertTrue(result.complete);
        // Tidak ada unduhan ulang: halaman yang sudah valid dilewati.
        assertEquals(2, calls[0]);
    }

    @Test public void truncatedImageIsNeverCountedAsSaved() throws Exception {
        byte[] cut = Arrays.copyOf(page, page.length / 2);
        int[] calls = {0};
        ShinigamiDownloader downloader = new ShinigamiDownloader(context, (url, referer) -> {
            calls[0]++;
            // Halaman pertama utuh, halaman berikutnya terpotong seperti koneksi putus.
            byte[] body = calls[0] == 1 ? page : cut;
            return new ShinigamiDownloader.Stream(200, "image/png", new ByteArrayInputStream(body));
        });

        ShinigamiDownloader.Result result = downloader.download(CHAPTER, urls(2), null);

        assertFalse(result.complete);
        assertNotNull(downloader.pageFile(CHAPTER, 0));
        assertNull("berkas terpotong tidak boleh dianggap halaman valid",
                downloader.pageFile(CHAPTER, 1));
        assertEquals(ShinigamiDownloadStatus.FAILED, ShinigamiDownloadStatus.state(CHAPTER));
        assertEquals(ShinigamiDownloader.LABEL_FAILED, downloader.storageLabel(CHAPTER, 2));
        // Berkas sementara dibersihkan, tidak menumpuk di folder chapter.
        File[] files = downloader.chapterDir(CHAPTER).listFiles();
        if (files != null) {
            for (File file : files) {
                assertFalse("sisa berkas sementara: " + file.getName(),
                        file.getName().endsWith(".tmp"));
            }
        }
    }

    @Test public void accessDeniedFailsFastWithoutRetry() throws Exception {
        int[] calls = {0};
        ShinigamiDownloader downloader = new ShinigamiDownloader(context, (url, referer) -> {
            calls[0]++;
            throw new IOException("HTTP 403: akses gambar dibatasi.");
        });

        ShinigamiDownloader.Result result = downloader.download(CHAPTER, urls(1), null);

        assertFalse(result.complete);
        assertTrue(result.message.contains("403"));
        // 403 adalah pembatasan sumber, bukan gangguan jaringan: tidak diulang.
        assertEquals(1, calls[0]);
        assertEquals(ShinigamiDownloadStatus.FAILED, ShinigamiDownloadStatus.state(CHAPTER));
        assertFalse(downloader.isRunning(CHAPTER));
    }

    @Test public void networkFailureIsRetriedOnceBeforeReporting() throws Exception {
        int[] calls = {0};
        ShinigamiDownloader downloader = new ShinigamiDownloader(context, (url, referer) -> {
            calls[0]++;
            if (calls[0] == 1) throw new IOException("Koneksi terputus.");
            return new ShinigamiDownloader.Stream(200, "image/png", new ByteArrayInputStream(page));
        });

        ShinigamiDownloader.Result result = downloader.download(CHAPTER, urls(1), null);

        assertTrue(result.complete);
        assertEquals(2, calls[0]);
    }

    @Test public void downloadRequiresConsentAndKeepsNothingWithoutIt() throws Exception {
        ShinigamiDownloadPolicy.revoke(context);
        ShinigamiDownloadPolicy.resetForTesting();
        int[] calls = {0};
        ShinigamiDownloader downloader = new ShinigamiDownloader(context, transport(page, calls));

        try {
            downloader.download(CHAPTER, urls(1), null);
            fail("tanpa persetujuan unduhan harus ditolak");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("izin"));
        }
        assertEquals(0, calls[0]);
        assertNull(downloader.pageFile(CHAPTER, 0));
    }

    @Test public void deleteChapterRemovesFilesButNotReadingProgress() throws Exception {
        int[] calls = {0};
        ShinigamiDownloader downloader = new ShinigamiDownloader(context, transport(page, calls));
        downloader.download(CHAPTER, urls(2), null);

        ShinigamiStore store = new ShinigamiStore(context);
        store.setProgress(CHAPTER, 42);
        store.setPage(CHAPTER, 5);

        assertTrue(downloader.deleteChapter(CHAPTER));

        assertNull(downloader.pageFile(CHAPTER, 0));
        // Progres baca milik ShinigamiStore, bukan folder unduhan.
        assertEquals(42, store.progress(CHAPTER));
        assertEquals(5, store.page(CHAPTER));
        assertEquals(ShinigamiDownloader.LABEL_NOT_SAVED, downloader.storageLabel(CHAPTER, 2));
    }
}
