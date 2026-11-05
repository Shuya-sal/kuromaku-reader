package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Alur unduh ujung ke ujung pada tingkat penyimpanan: byte gambar diterima,
 * divalidasi, ditulis, lalu dibaca kembali sebagai unduhan nyata. Progres yang
 * dilaporkan berasal dari jumlah halaman tersimpan, bukan timer.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ShinigamiDownloadFlowTest {

    private static final String CHAPTER = "chapter-flow";
    private static final List<String> URLS = Arrays.asList(
            "https://assets.shngm.id/chapter/0.png",
            "https://assets.shngm.id/chapter/1.png",
            "https://assets.shngm.id/chapter/2.png");

    private Context context;
    private ShinigamiStore store;
    private byte[] png;

    @Before public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(ShinigamiStore.FILE_NAME, 0).edit().clear().commit();
        store = new ShinigamiStore(context);
        ShinigamiDownloadStatus.clear(CHAPTER);
        // Seperti pengguna menekan "Saya mengerti" sekali.
        ShinigamiDownloadPolicy.acknowledge(context);
        Bitmap bitmap = Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer));
        bitmap.recycle();
        png = buffer.toByteArray();
    }

    @After public void tearDown() {
        new ShinigamiDownloader(context).deleteChapter(CHAPTER);
        ShinigamiDownloadStatus.clear(CHAPTER);
    }

    private ShinigamiDownloader downloader(final int[] calls) {
        ShinigamiDownloader.Transport transport = (url, referer) -> {
            calls[0]++;
            return new ShinigamiDownloader.Stream(200, "image/png",
                    new ByteArrayInputStream(png));
        };
        return new ShinigamiDownloader(context, transport);
    }

    @Test public void downloadWritesEveryValidatedPageAndIsVisibleInDownloads() throws Exception {
        ShinigamiDownloader local = downloader(new int[1]);
        store.setChapterPages(CHAPTER, URLS);
        store.setChapterOwner(CHAPTER, "manga-flow", "Judul Uji", "https://assets.shngm.id/c.jpg",
                7d, "Chapter 7");

        final List<Integer> progress = new ArrayList<>();
        ShinigamiDownloader.Result result = local.download(CHAPTER, URLS,
                (done, total) -> progress.add(done * 100 / total));

        assertTrue(result.complete);
        assertEquals(3, result.saved);
        assertEquals(3, result.total);
        assertEquals(Arrays.asList(33, 66, 100), progress);
        for (int index = 0; index < 3; index++) {
            assertNotNull("halaman " + index, local.pageFile(CHAPTER, index));
        }

        List<ShinigamiDownloads.Manga> mangas = ShinigamiDownloads.scan(context);
        assertEquals(1, mangas.size());
        ShinigamiDownloads.Chapter saved = mangas.get(0).chapters.get(0);
        assertTrue(saved.complete);
        assertEquals(100, saved.percent());
        assertEquals("manga-flow", mangas.get(0).mangaId);
    }

    @Test public void progressIsIdempotentAndResumesWithoutRedownloading() throws Exception {
        int[] calls = new int[1];
        ShinigamiDownloader local = downloader(calls);
        store.setChapterPages(CHAPTER, URLS);
        assertTrue(local.download(CHAPTER, URLS, null).complete);
        assertEquals(3, calls[0]);

        ShinigamiDownloader again = downloader(calls);
        ShinigamiDownloader.Result second = again.download(CHAPTER, URLS, null);
        assertTrue(second.complete);
        assertEquals("tidak ada unduhan ulang", 3, calls[0]);
    }

    @Test public void partialFailureKeepsValidPagesAndReportsIncomplete() throws Exception {
        final int[] calls = new int[1];
        ShinigamiDownloader.Transport flaky = (url, referer) -> {
            calls[0]++;
            if (url.endsWith("1.png")) {
                throw new IOException("Koneksi terputus.");
            }
            return new ShinigamiDownloader.Stream(200, "image/png",
                    new ByteArrayInputStream(png));
        };
        ShinigamiDownloader local = new ShinigamiDownloader(context, flaky);
        ShinigamiDownloader.Result result = local.download(CHAPTER, URLS, null);

        assertFalse(result.complete);
        assertTrue(result.saved >= 1);
        assertNotNull(local.pageFile(CHAPTER, 0));
        assertTrue("halaman gagal tidak boleh ada", local.pageFile(CHAPTER, 1) == null);
    }

    @Test public void deletingDownloadKeepsReadingProgress() throws Exception {
        ShinigamiDownloader local = downloader(new int[1]);
        store.setChapterPages(CHAPTER, URLS);
        store.setProgress(CHAPTER, 42);
        assertTrue(local.download(CHAPTER, URLS, null).complete);

        local.deleteChapter(CHAPTER);

        assertTrue(ShinigamiDownloads.scan(context).isEmpty());
        assertEquals(42, store.progress(CHAPTER));
        assertEquals(new File(new File(context.getFilesDir(), "shinigami"), CHAPTER).exists(), false);
    }
}
