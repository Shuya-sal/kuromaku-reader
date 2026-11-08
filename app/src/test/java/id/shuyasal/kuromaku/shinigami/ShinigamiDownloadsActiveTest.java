package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.List;

/**
 * Unduhan yang sedang berjalan tampil di layar Unduhan Manga Server 2.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ShinigamiDownloadsActiveTest {

    private Context context;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        ShinigamiDownloadStatus.reset();
        id.shuyasal.kuromaku.offline.DownloadStateFile.install(context);
    }

    @After public void tearDown() {
        ShinigamiDownloadStatus.reset();
    }

    @Test public void chapterBerjalanMasukDaftarAktif() {
        ShinigamiDownloadStatus.describe("ch-1", "Judul Manga", "ch-1", "manga-1");
        ShinigamiDownloadStatus.set("ch-1", ShinigamiDownloadStatus.DOWNLOADING, 30, "");

        List<ShinigamiDownloadStatus.Active> aktif = ShinigamiDownloadStatus.active();
        assertEquals(1, aktif.size());
        assertEquals("Judul Manga", aktif.get(0).title);
        assertEquals("manga-1", aktif.get(0).owner);
        assertEquals(30, aktif.get(0).percent);
    }

    @Test public void tersimpanTidakMasukDaftarAktif() {
        ShinigamiDownloadStatus.set("ch-1", ShinigamiDownloadStatus.SAVED, 100, "");
        assertTrue(ShinigamiDownloadStatus.active().isEmpty());
    }

    @Test public void layarUnduhanMenampilkanBagianSedangDiunduh() {
        ShinigamiDownloadStatus.describe("ch-1", "Judul Manga", "ch-1", "manga-1");
        ShinigamiDownloadStatus.set("ch-1", ShinigamiDownloadStatus.DOWNLOADING, 30, "");

        try (ActivityController<ShinigamiDownloadsActivity> controller =
                     Robolectric.buildActivity(ShinigamiDownloadsActivity.class).setup()) {
            ShadowLooper.idleMainLooper();
            View root = controller.get().getWindow().getDecorView();

            View baris = root.findViewWithTag("downloads_manga_active_ch-1");
            assertNotNull("baris unduhan berjalan tampil", baris);
            assertNotNull("tombol batal tersedia",
                    baris.findViewWithTag("downloads_manga_cancel_ch-1"));
            assertNotNull("progress bar tersedia",
                    baris.findViewWithTag("downloads_manga_progress_ch-1"));
        }
    }
}
