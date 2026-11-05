package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ShinigamiDownloadStatusTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        ShinigamiDownloadStatus.reset();
        context.getSharedPreferences(ShinigamiStore.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    @Test
    public void readingProgressAndDownloadProgressDoNotShareAKey() {
        ShinigamiStore store = new ShinigamiStore(context);
        store.setProgress("chapter-a", 12);
        ShinigamiDownloadStatus.set("chapter-a",
                ShinigamiDownloadStatus.DOWNLOADING, 48, null);
        assertEquals(12, store.progress("chapter-a"));
        assertEquals(48, ShinigamiDownloadStatus.percent("chapter-a"));
    }

    @Test
    public void runningDownloadCarriesAPercentage() {
        ShinigamiDownloader local = new ShinigamiDownloader(context);
        assertEquals("Belum diunduh", local.storageLabel("chapter-a", 3));
        ShinigamiDownloadStatus.set("chapter-a",
                ShinigamiDownloadStatus.DOWNLOADING, 48, null);
        assertEquals("Mengunduh 48%", local.storageLabel("chapter-a", 3));
        ShinigamiDownloadStatus.set("chapter-a",
                ShinigamiDownloadStatus.FAILED, 48, "koneksi putus");
        assertEquals("Unduhan gagal", local.storageLabel("chapter-a", 3));
    }

    @Test
    public void deletingADownloadNeverResetsReadingProgress() {
        ShinigamiStore store = new ShinigamiStore(context);
        store.setProgress("chapter-a", 12);
        store.setPage("chapter-a", 2);
        ShinigamiDownloader local = new ShinigamiDownloader(context);
        local.deleteChapter("chapter-a");
        ShinigamiDownloadStatus.clear("chapter-a");
        assertEquals(12, store.progress("chapter-a"));
        assertEquals(2, store.page("chapter-a"));
    }

    @Test
    public void readLabelAndDownloadLabelAreDifferentStrings() {
        assertEquals("Dibaca 12%", ShinigamiChapterProgress.readLabel(12));
        assertEquals("Selesai", ShinigamiChapterProgress.readLabel(100));
        assertEquals("", ShinigamiChapterProgress.readLabel(0));
    }

    @Test
    public void failedDownloadKeepsValidationRuleForSavedLabel() {
        assertFalse(new ShinigamiDownloader(context).isDownloaded("missing", 0));
        assertEquals("Belum diunduh",
                new ShinigamiDownloader(context).storageLabel("missing", 0));
    }

    @Test
    public void queueSkipsChaptersThatAreAlreadyComplete() throws Exception {
        ShinigamiStore store = new ShinigamiStore(context);
        store.setProgress("chapter-a", 12);
        store.setChapterPages("chapter-done",
                java.util.Collections.singletonList("https://assets.shngm.id/chapter/0.png"));
        ShinigamiDownloader local = new ShinigamiDownloader(context);
        local.chapterDir("chapter-done").mkdirs();
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(
                new java.io.File(local.chapterDir("chapter-done"), "0.img"))) {
            android.graphics.Bitmap.createBitmap(50, 50, android.graphics.Bitmap.Config.ARGB_8888)
                    .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
        }
        java.util.List<String> missing = ShinigamiDownloadQueue.missing(context,
                java.util.Arrays.asList("chapter-a", "chapter-done"));
        assertEquals(java.util.Collections.singletonList("chapter-a"), missing);
        assertEquals(12, store.progress("chapter-a"));
        local.deleteChapter("chapter-done");
    }

    @Test
    public void queueStaysLockedWhileSourcePermissionIsUnverified() {
        ShinigamiDownloadPolicy.unlockForTesting(false);
        assertFalse(ShinigamiDownloadQueue.enqueueChapter(context, "chapter-locked"));
        assertEquals(ShinigamiDownloadStatus.NOT_DOWNLOADED,
                ShinigamiDownloadStatus.state("chapter-locked"));
        assertEquals("Belum diunduh",
                new ShinigamiDownloader(context).storageLabel("chapter-locked", 3));
        assertTrue(ShinigamiDownloadPolicy.blockedMessage().contains("izin"));
    }

    @Test
    public void workRequestCarriesEverySelectedChapter() {
        java.util.List<String> ids = java.util.Arrays.asList("c-1", "c-2", "c-3");
        androidx.work.Data data = ShinigamiDownloadQueue.input("m-1", "Judul", ids);
        assertEquals("c-1\nc-2\nc-3", data.getString(ShinigamiDownloadQueue.KEY_CHAPTERS));
        assertEquals("m-1", data.getString(ShinigamiDownloadQueue.KEY_MANGA_ID));
    }

    @Test
    public void downloadActionLabelFollowsStatus() {
        assertEquals("Unduh", ShinigamiDetailActivity.downloadActionLabel("Belum diunduh"));
        assertEquals("Batal", ShinigamiDetailActivity.downloadActionLabel("Mengunduh 48%"));
        assertEquals("Hapus", ShinigamiDetailActivity.downloadActionLabel("Tersimpan offline"));
        assertEquals("Ulangi", ShinigamiDetailActivity.downloadActionLabel("Unduhan gagal"));
    }
}
