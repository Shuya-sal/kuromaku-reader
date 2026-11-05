package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Uji byte nyata: halaman hanya menjadi berkas final kalau header dan MIME
 * valid, berkas setengah jadi selalu dibersihkan, dan persentase unduhan
 * dihitung dari halaman yang benar-benar tersimpan.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ShinigamiPageStoreTest {
    private static final String CHAPTER = "chapter-pages";

    private Context context;
    private ShinigamiDownloader downloader;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(ShinigamiStore.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        downloader = new ShinigamiDownloader(context);
        downloader.deleteChapter(CHAPTER);
    }

    private byte[] png() {
        Bitmap bitmap = Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        return out.toByteArray();
    }

    @Test
    public void validPageBecomesAReadableLocalFile() throws Exception {
        File target = ShinigamiPageStore.fileFor(downloader, CHAPTER, 0);
        ShinigamiPageStore.save(new ByteArrayInputStream(png()), target, 200, "image/png");

        assertTrue(target.isFile());
        assertTrue(downloader.pageFile(CHAPTER, 0) != null);
        assertEquals(1, ShinigamiPageStore.savedCount(downloader, CHAPTER, 3));
        assertEquals(33, ShinigamiPageStore.percent(downloader, CHAPTER, 3));
    }

    @Test
    public void htmlErrorPageIsRejectedAndLeavesNoFile() throws Exception {
        File target = ShinigamiPageStore.fileFor(downloader, CHAPTER, 0);
        byte[] html = "<html><body>403 forbidden</body></html>".getBytes("UTF-8");
        try {
            ShinigamiPageStore.save(new ByteArrayInputStream(html), target, 200, "text/html");
            fail("HTML tidak boleh disimpan sebagai gambar");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("MIME"));
        }
        assertFalse(target.exists());
        assertNull(downloader.pageFile(CHAPTER, 0));
        assertEquals(0, ShinigamiPageStore.percent(downloader, CHAPTER, 3));
    }

    @Test
    public void truncatedImageIsNotCountedAsSaved() throws Exception {
        byte[] full = png();
        byte[] cut = java.util.Arrays.copyOf(full, full.length - 12);
        File target = ShinigamiPageStore.fileFor(downloader, CHAPTER, 1);
        ShinigamiPageStore.save(new ByteArrayInputStream(cut), target, 200, "image/png");

        assertNull("PNG terpotong tidak boleh divalidasi sebagai halaman", downloader.pageFile(CHAPTER, 1));
        assertEquals(0, ShinigamiPageStore.savedCount(downloader, CHAPTER, 2));
    }

    @Test
    public void deniedResponseNeverWritesAnything() throws Exception {
        File target = ShinigamiPageStore.fileFor(downloader, CHAPTER, 0);
        try {
            ShinigamiPageStore.save(new ByteArrayInputStream(png()), target, 403, "image/png");
            fail("HTTP 403 tidak boleh disimpan");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("403"));
        }
        assertFalse(target.exists());
    }

    @Test
    public void resumeOnlyCountsPagesThatAreAlreadyWhole() throws Exception {
        for (int index = 0; index < 2; index++) {
            ShinigamiPageStore.save(new ByteArrayInputStream(png()),
                    ShinigamiPageStore.fileFor(downloader, CHAPTER, index), 200, "image/png");
        }
        assertEquals(0, ShinigamiPageStore.percent(downloader, CHAPTER, 0));
        assertEquals(66, ShinigamiPageStore.percent(downloader, CHAPTER, 3));
        assertEquals(2, ShinigamiPageStore.savedCount(downloader, CHAPTER, 3));

        List<String> remaining = ShinigamiDownloadQueue.missing(context,
                java.util.Collections.singletonList(CHAPTER));
        assertEquals(1, remaining.size());
    }
}
