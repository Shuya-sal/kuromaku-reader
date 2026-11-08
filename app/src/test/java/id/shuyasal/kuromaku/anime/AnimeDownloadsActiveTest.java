package id.shuyasal.kuromaku.anime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

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
 * Unduhan yang sedang berjalan tampil di layar Unduhan Anime.
 *
 * <p>Sebelumnya layar itu hanya menampilkan berkas yang sudah selesai, sehingga
 * unduhan yang sedang berjalan tidak terlihat di mana pun selain daftar episode.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class AnimeDownloadsActiveTest {

    private Context context;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        AnimeDownloadStatus.reset();
        id.shuyasal.kuromaku.offline.DownloadStateFile.install(context);
    }

    @After public void tearDown() {
        AnimeDownloadStatus.reset();
    }

    @Test public void unduhanBerjalanMasukDaftarAktif() {
        AnimeDownloadStatus.describe("ep1", "FX Senshi Kurumi-chan", "Episode 1 · 720p", "fxskc");
        AnimeDownloadStatus.set("ep1", AnimeDownloadStatus.DOWNLOADING, 42, "Mengunduh");

        List<AnimeDownloadStatus.Active> aktif = AnimeDownloadStatus.active();
        assertEquals("satu unduhan berjalan", 1, aktif.size());
        assertEquals("ep1", aktif.get(0).episodeKey);
        assertEquals("judul terbaca", "FX Senshi Kurumi-chan", aktif.get(0).title);
        assertEquals("keterangan terbaca", "Episode 1 · 720p", aktif.get(0).label());
        assertEquals("pemilik untuk pembatalan", "fxskc", aktif.get(0).owner);
        assertEquals(42, aktif.get(0).percent);
    }

    @Test public void yangSudahTersimpanTidakMasukDaftarAktif() {
        AnimeDownloadStatus.set("ep1", AnimeDownloadStatus.SAVED, 100, "Tersimpan");
        assertTrue("tidak ada yang berjalan", AnimeDownloadStatus.active().isEmpty());
    }

    @Test public void yangGagalTidakMenumpukDiLayarUnduhan() {
        AnimeDownloadStatus.set("ep1", AnimeDownloadStatus.FAILED, 0, "Gagal");
        AnimeDownloadStatus.set("ep2", AnimeDownloadStatus.PAUSED, 30, "Dijeda");
        assertEquals("hanya yang dijeda", 1, AnimeDownloadStatus.active().size());
        assertEquals("ep2", AnimeDownloadStatus.active().get(0).episodeKey);
    }

    @Test public void judulBertahanSaatProgresBerubah() {
        AnimeDownloadStatus.describe("ep1", "Judul", "Episode 1 · 480p", "fxskc");
        AnimeDownloadStatus.set("ep1", AnimeDownloadStatus.DOWNLOADING, 10, "");
        AnimeDownloadStatus.set("ep1", AnimeDownloadStatus.DOWNLOADING, 55, "");

        assertEquals("judul tidak hilang", "Judul", AnimeDownloadStatus.title("ep1"));
        assertEquals("keterangan tidak hilang", "Episode 1 · 480p",
                AnimeDownloadStatus.subtitle("ep1"));
    }

    @Test public void layarUnduhanMenampilkanBagianSedangDiunduh() {
        AnimeDownloadStatus.describe("ep1", "FX Senshi Kurumi-chan", "Episode 1 · 720p", "fxskc");
        AnimeDownloadStatus.set("ep1", AnimeDownloadStatus.DOWNLOADING, 42, "Mengunduh");

        try (ActivityController<AnimeDownloadsActivity> controller =
                     Robolectric.buildActivity(AnimeDownloadsActivity.class).setup()) {
            ShadowLooper.idleMainLooper();
            View root = controller.get().getWindow().getDecorView();

            View baris = root.findViewWithTag("anime_downloads_active_ep1");
            assertNotNull("baris unduhan berjalan tampil", baris);
            assertNotNull("tombol batal tersedia",
                    baris.findViewWithTag("anime_downloads_cancel_ep1"));
            assertNotNull("progress bar tersedia",
                    baris.findViewWithTag("anime_downloads_progress_ep1"));

            View empty = root.findViewWithTag("anime_downloads_empty");
            assertEquals("pesan kosong disembunyikan", View.GONE, empty.getVisibility());

            TextView summary = (TextView) root.findViewWithTag("anime_downloads_summary");
            assertTrue("ringkasan menyebut unduhan berjalan",
                    String.valueOf(summary.getText()).contains("berjalan"));
        }
    }

    @Test public void tanpaUnduhanBerjalanBagianTersembunyi() {
        try (ActivityController<AnimeDownloadsActivity> controller =
                     Robolectric.buildActivity(AnimeDownloadsActivity.class).setup()) {
            ShadowLooper.idleMainLooper();
            View root = controller.get().getWindow().getDecorView();
            assertNull("tidak ada baris aktif", root.findViewWithTag("anime_downloads_active_ep1"));
        }
    }
}
