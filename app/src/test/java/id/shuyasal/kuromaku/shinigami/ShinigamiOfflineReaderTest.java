package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Uji alur offline: chapter lengkap dibaca dari file lokal tanpa memanggil
 * jaringan, chapter tanpa file menampilkan pesan jelas, dan menghapus unduhan
 * tidak menyentuh progres membaca.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w320dp-h640dp-mdpi")
public class ShinigamiOfflineReaderTest {
    private static final String MANGA = "14423a1b-8d7e-41e2-af00-ba0ba655d5f9";
    private static final String CHAPTER = "d6cd3f14-a528-4752-a546-0d14873e9a4a";
    private static final String PAGE_URL = "https://storage.shngm.id/page.webp";

    private static ShinigamiSource.Transport transport;
    private static final AtomicInteger CALLS = new AtomicInteger();

    public static class Reader extends ShinigamiReaderActivity {
        @Override protected ShinigamiSource createSource() {
            return new ShinigamiSource(this, transport);
        }
    }

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(ShinigamiStore.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        CALLS.set(0);
        transport = url -> {
            CALLS.incrementAndGet();
            throw new ShinigamiSource.SourceException("Koneksi terputus.",
                    new java.io.IOException("offline"));
        };
    }

    private Intent intent() {
        return new Intent(context, Reader.class)
                .putExtra(ShinigamiReaderActivity.EXTRA_MANGA_ID, MANGA)
                .putExtra(ShinigamiReaderActivity.EXTRA_MANGA_TITLE, "Regressor Instruction Manual")
                .putExtra(ShinigamiReaderActivity.EXTRA_CHAPTER_ID, CHAPTER)
                .putExtra(ShinigamiReaderActivity.EXTRA_CHAPTER_NUMBER, 210d);
    }

    private void await(Reader reader) throws Exception {
        Field field = ShinigamiReaderActivity.class.getDeclaredField("worker");
        field.setAccessible(true);
        ((ExecutorService) field.get(reader)).submit(() -> {}).get(5, TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private View tagged(Reader reader, String tag) {
        View view = reader.getWindow().getDecorView().findViewWithTag(tag);
        assertNotNull(tag, view);
        return view;
    }

    /** Tulis N halaman PNG valid ke folder unduhan chapter. */
    private void storePages(int pages) throws Exception {
        ShinigamiStore store = new ShinigamiStore(context);
        List<String> urls = new ArrayList<>();
        for (int index = 0; index < pages; index++) urls.add(PAGE_URL + "?p=" + index);
        store.setChapterPages(CHAPTER, urls);
        ShinigamiDownloader downloader = new ShinigamiDownloader(context);
        File dir = downloader.chapterDir(CHAPTER);
        assertTrue(dir.isDirectory() || dir.mkdirs());
        for (int index = 0; index < pages; index++) {
            try (FileOutputStream out = new FileOutputStream(new File(dir, index + ".img"))) {
                Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888)
                        .compress(Bitmap.CompressFormat.PNG, 100, out);
            }
        }
    }

    @Test
    public void completeDownloadIsReadWithoutTouchingTheNetwork() throws Exception {
        storePages(3);
        ShinigamiStore store = new ShinigamiStore(context);
        store.setProgress(CHAPTER, 12);
        store.setPage(CHAPTER, 1);

        try (ActivityController<Reader> controller =
                     Robolectric.buildActivity(Reader.class, intent()).setup()) {
            Reader reader = controller.get();
            await(reader);
            RecyclerView list = (RecyclerView) tagged(reader, "shinigami_reader_list");
            assertEquals(3, list.getAdapter().getItemCount());
            assertEquals("tidak boleh ada permintaan jaringan", 0, CALLS.get());
            assertEquals(View.GONE, tagged(reader, "shinigami_reader_error").getVisibility());
            TextView notice = (TextView) tagged(reader, "shinigami_reader_offline_notice");
            assertEquals(View.VISIBLE, notice.getVisibility());
            assertTrue(notice.getText().toString(), notice.getText().toString().contains("offline"));
            assertTrue("progres baca tidak boleh nol", store.progress(CHAPTER) > 0);
            assertEquals(1, store.page(CHAPTER));
        }
    }

    @Test
    public void missingPagesOfflineShowsClearMessageAndKeepsProgress() throws Exception {
        ShinigamiStore store = new ShinigamiStore(context);
        store.setChapterPages(CHAPTER, java.util.Collections.singletonList(PAGE_URL));
        store.setProgress(CHAPTER, 12);
        store.setPage(CHAPTER, 1);

        try (ActivityController<Reader> controller =
                     Robolectric.buildActivity(Reader.class, intent()).setup()) {
            Reader reader = controller.get();
            await(reader);
            TextView notice = (TextView) tagged(reader, "shinigami_reader_offline_notice");
            assertEquals(View.VISIBLE, notice.getVisibility());
            assertTrue(notice.getText().toString(),
                    notice.getText().toString().contains("belum tersimpan lengkap"));
            assertEquals(12, store.progress(CHAPTER));
            assertEquals(1, store.page(CHAPTER));
        }
    }

    @Test
    public void deletingDownloadKeepsReadingProgressAndPagePosition() throws Exception {
        storePages(3);
        ShinigamiStore store = new ShinigamiStore(context);
        store.setProgress(CHAPTER, 67);
        store.setPage(CHAPTER, 2);

        assertTrue(ShinigamiDownloadQueue.deleteChapter(context, CHAPTER));

        assertEquals(67, store.progress(CHAPTER));
        assertEquals(2, store.page(CHAPTER));
        assertFalse(new ShinigamiDownloader(context).isDownloaded(CHAPTER, 3));
        assertEquals("Belum diunduh",
                new ShinigamiDownloader(context).storageLabel(CHAPTER, 3));
    }

    @Test
    public void partialDownloadIsNotReportedAsSavedOffline() throws Exception {
        storePages(3);
        ShinigamiDownloader downloader = new ShinigamiDownloader(context);
        assertTrue(new File(downloader.chapterDir(CHAPTER), "2.img").delete());

        assertFalse(downloader.isDownloaded(CHAPTER, 3));
        assertEquals("Belum diunduh", downloader.storageLabel(CHAPTER, 3));
    }

    @Test
    public void partialDownloadStillLoadsOnlineReaderWhenInternetWorks() throws Exception {
        storePages(3);
        ShinigamiDownloader probe = new ShinigamiDownloader(context);
        assertTrue(new File(probe.chapterDir(CHAPTER), "2.img").delete());
        assertFalse("dua halaman saja bukan unduhan lengkap",
                probe.isDownloaded(CHAPTER, new ShinigamiStore(context).expectedPages(CHAPTER)));
        CALLS.set(0);
        transport = url -> {
            CALLS.incrementAndGet();
            return "{\"retcode\":0,\"data\":{\"chapter_id\":\"" + CHAPTER
                    + "\",\"base_url\":\"https://assets.shngm.id\",\"chapter\":{"
                    + "\"path\":\"/chapter/manga/" + MANGA + "/chapter_" + CHAPTER + "/\","
                    + "\"data\":[\"0-a.webp\",\"1-b.webp\",\"2-c.webp\"]}}}";
        };

        try (ActivityController<Reader> controller =
                     Robolectric.buildActivity(Reader.class, intent()).setup()) {
            Reader reader = controller.get();
            await(reader);
            assertEquals(3, ((RecyclerView) tagged(reader, "shinigami_reader_list")).getAdapter().getItemCount());
            assertEquals("permintaan jaringan ke sumber", 1, CALLS.get());
            RecyclerView list = (RecyclerView) tagged(reader, "shinigami_reader_list");
            assertEquals(3, list.getAdapter().getItemCount());
            assertEquals(View.GONE, tagged(reader, "shinigami_reader_error").getVisibility());
        }
    }
}
