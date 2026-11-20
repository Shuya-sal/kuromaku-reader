package id.shuyasal.kuromaku.anime.kompi;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import id.shuyasal.kuromaku.anime.AnimeDownloadPolicy;
import id.shuyasal.kuromaku.anime.AnimeDownloadTask;
import id.shuyasal.kuromaku.ui.Prefs;

/**
 * Unduhan Anime Server 2.
 *
 * <p>Keluhan yang dijaga: menekan "Unduh" tidak menghasilkan apa pun. Akarnya
 * antrean menolak setiap permintaan yang belum disetujui, sementara layar
 * Anime Server 2 tidak pernah meminta persetujuan — jadi tombolnya diam saja.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class KompiDownloadConsentTest {

    private final Context context = ApplicationProvider.getApplicationContext();

    @Before public void bersihkan() {
        context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        AnimeDownloadPolicy.revoke(context);
    }

    @Test public void unduhanTertutupSebelumDisetujui() {
        assertFalse("tanpa persetujuan unduhan harus ditolak",
                AnimeDownloadPolicy.allowed(context));
    }

    @Test public void persetujuanMembukaUnduhan() {
        AnimeDownloadPolicy.acknowledge(context);
        assertTrue(AnimeDownloadPolicy.allowed(context));
    }

    @Test public void persetujuanTersimpanAntarSesi() {
        AnimeDownloadPolicy.acknowledge(context);
        // Nilai dibaca dari SharedPreferences yang sama, bukan dari memori.
        assertTrue(context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE)
                .getBoolean("anime_offline_consent", false));
    }

    @Test public void pemberitahuanMenyebutBerkasDanTidakDisebarkan() {
        String notice = AnimeDownloadPolicy.notice();
        assertTrue("menyebut berkas media", notice.contains("alamat media"));
        assertTrue("menyebut tidak disebarkan", notice.contains("bukan untuk disebarkan"));
        assertTrue("menyebut server tertentu", notice.contains("berkas media"));
    }

    /**
     * Server "direct" dari Animekompi (Pixel HD) berakhir di pixeldrain, dan
     * alamatnya harus lolos aturan tugas unduhan supaya bisa diantrekan.
     */
    @Test public void berkasPixelDrainDianggapBolehDiunduh() {
        AnimeDownloadTask task = new AnimeDownloadTask(
                "hyoken-ii", "Hyoken", "",
                "Episode 7",
                "hyoken-no-majutsushi-ga-sekai-wo-suberu-ii-episode-7-subtitle-indonesia",
                "", "Pixel HD (Tanpa Iklan)",
                "https://pixeldrain.com/api/file/sN2nH8CH?download", "PROGRESSIVE");
        assertTrue(task.downloadable());
    }

    @Test public void halamanPemutarTidakBolehDiunduh() {
        AnimeDownloadTask task = new AnimeDownloadTask(
                "hyoken-ii", "Hyoken", "", "Episode 7", "slug-ep-7", "",
                "Server GDrive",
                "https://www.blogger.com/video.g?token=abc", "PROGRESSIVE");
        assertFalse("halaman pemutar bukan berkas", task.downloadable());
    }

    @Test public void hlsTidakDitawarkanSebagaiUnduhan() {
        AnimeDownloadTask task = new AnimeDownloadTask(
                "a", "A", "", "Episode 1", "ep-1", "", "Server",
                "https://contoh/video.mp4", "HLS");
        assertFalse("HLS tidak boleh dianggap berkas langsung", task.downloadable());
        assertTrue("alasannya harus jelas",
                task.reasonNotDownloadable().contains("HLS"));
    }
}
