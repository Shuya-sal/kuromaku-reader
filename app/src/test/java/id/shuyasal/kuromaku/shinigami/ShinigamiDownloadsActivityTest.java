package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Halaman Unduhan: hanya berkas nyata, dan hapus tidak menyentuh progres baca. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w360dp-h740dp")
public class ShinigamiDownloadsActivityTest {

    private static final String CHAPTER = "chapter-dl";
    private Context context;
    private ShinigamiStore store;
    private ShinigamiDownloader downloader;

    @Before public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(ShinigamiStore.FILE_NAME, 0).edit().clear().commit();
        ShinigamiDownloadPolicy.acknowledge(context);
        store = new ShinigamiStore(context);
        downloader = new ShinigamiDownloader(context);
        ShinigamiDownloadStatus.clear(CHAPTER);
        writePage(CHAPTER, 0);
        writePage(CHAPTER, 1);
        store.setChapterPages(CHAPTER, java.util.Arrays.asList(
                "https://assets.shngm.id/chapter/0.png", "https://assets.shngm.id/chapter/1.png"));
        store.setChapterOwner(CHAPTER, "manga-dl", "Judul Unduhan", "", 9d, "Chapter 9");
        store.setProgress(CHAPTER, 42);
    }

    @After public void tearDown() {
        downloader.deleteChapter(CHAPTER);
        ShinigamiDownloadStatus.clear(CHAPTER);
    }

    private void writePage(String chapterId, int index) throws Exception {
        File dir = downloader.chapterDir(chapterId);
        assertTrue(dir.mkdirs() || dir.isDirectory());
        Bitmap bitmap = Bitmap.createBitmap(30, 40, Bitmap.Config.ARGB_8888);
        try (FileOutputStream out = new FileOutputStream(new File(dir, index + ".img"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        } finally {
            bitmap.recycle();
        }
    }

    private ShinigamiDownloadsActivity launch() throws Exception {
        ShinigamiDownloadsActivity activity = Robolectric
                .buildActivity(ShinigamiDownloadsActivity.class).setup().get();
        Field field = ShinigamiDownloadsActivity.class.getDeclaredField("worker");
        field.setAccessible(true);
        ((ExecutorService) field.get(activity)).submit(() -> {
        }).get(5, TimeUnit.SECONDS);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        return activity;
    }

    private View tagged(ShinigamiDownloadsActivity activity, String tag) {
        return activity.getWindow().getDecorView().findViewWithTag(tag);
    }

    @Test public void showsMangaWithValidatedOfflineChapters() throws Exception {
        ShinigamiDownloadsActivity activity = launch();
        assertNotNull(tagged(activity, "downloads_manga_manga-dl"));
        assertEquals("Judul Unduhan",
                ((TextView) tagged(activity, "downloads_title_manga-dl")).getText().toString());
        String count = ((TextView) tagged(activity, "downloads_count_manga-dl")).getText().toString();
        assertTrue(count, count.contains("1 chapter offline"));
        assertNotNull(tagged(activity, "downloads_continue_manga-dl"));
        assertNotNull(tagged(activity, "downloads_chapter_" + CHAPTER));
        assertNotNull(tagged(activity, "downloads_delete_" + CHAPTER));
        assertEquals(View.GONE, tagged(activity, "downloads_empty").getVisibility());
    }

    @Test public void emptyStateWhenNoFilesExist() throws Exception {
        downloader.deleteChapter(CHAPTER);
        ShinigamiDownloadsActivity activity = launch();
        assertEquals(View.VISIBLE, tagged(activity, "downloads_empty").getVisibility());
        assertNull(tagged(activity, "downloads_manga_manga-dl"));
        assertEquals(context.getString(id.shuyasal.kuromaku.R.string.shinigami_downloads_empty_title),
                ((TextView) tagged(activity, "downloads_summary")).getText().toString());
    }

    @Test public void deleteAsksForConfirmationAndKeepsReadingProgress() throws Exception {
        ShinigamiDownloadsActivity activity = launch();
        tagged(activity, "downloads_delete_" + CHAPTER).performClick();

        androidx.appcompat.app.AlertDialog dialog = (androidx.appcompat.app.AlertDialog)
                org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertNotNull("dialog konfirmasi harus muncul", dialog);
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertNull("berkas harus hilang", downloader.pageFile(CHAPTER, 0));
        assertNull("chapter harus hilang dari daftar",
                tagged(activity, "downloads_chapter_" + CHAPTER));
        assertEquals("progres baca tidak boleh berubah", 42, store.progress(CHAPTER));
    }

    @Test public void continueReadingOpensReaderForSavedChapter() throws Exception {
        ShinigamiDownloadsActivity activity = launch();
        tagged(activity, "downloads_continue_manga-dl").performClick();
        android.content.Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(ShinigamiReaderActivity.class.getName(), started.getComponent().getClassName());
        assertEquals(CHAPTER, started.getStringExtra(ShinigamiReaderActivity.EXTRA_CHAPTER_ID));
        assertEquals("membuka reader tidak boleh mengubah progres", 42, store.progress(CHAPTER));
    }
}
