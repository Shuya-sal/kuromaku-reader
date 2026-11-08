package id.shuyasal.kuromaku.offline;

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

import id.shuyasal.kuromaku.anime.AnimeDownloadStatus;

/**
 * Pengaman crash-loop unduhan.
 *
 * <p>Yang dijaga: pekerjaan yang pernah membunuh proses tidak dijalankan otomatis
 * lagi, dan episode lain tidak ikut tersentuh.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class DownloadCrashGuardTest {

    private Context context;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        AnimeDownloadStatus.reset();
        DownloadCrashGuard.reset(context);
        DownloadStateFile.install(context);
    }

    @Test public void pekerjaanYangSelesaiTidakDicurigai() {
        DownloadCrashGuard.begin(context, "ep1");
        DownloadCrashGuard.end(context);

        assertEquals("tidak ada penanda tertinggal", "", DownloadCrashGuard.recover(context));
        assertFalse(DownloadCrashGuard.isSuspect(context, "ep1"));
    }

    @Test public void prosesMatiSaatMengunduhMembuatEpisodeDicurigai() {
        // begin tanpa end = proses mati di tengah unduhan.
        DownloadCrashGuard.begin(context, "ep1");

        assertEquals("episode yang terhenti dikenali", "ep1",
                DownloadCrashGuard.recover(context));
        assertTrue(DownloadCrashGuard.isSuspect(context, "ep1"));
        assertEquals("status menjadi gagal, bukan tersimpan", AnimeDownloadStatus.FAILED,
                AnimeDownloadStatus.state("ep1"));
        assertEquals("penanda dibersihkan", "", DownloadCrashGuard.recover(context));
    }

    @Test public void episodeLainTidakIkutDicurigai() {
        DownloadCrashGuard.begin(context, "ep1");
        DownloadCrashGuard.recover(context);

        assertFalse("episode lain aman", DownloadCrashGuard.isSuspect(context, "ep2"));
        assertEquals(AnimeDownloadStatus.NOT_DOWNLOADED,
                AnimeDownloadStatus.state("ep2"));
    }

    @Test public void penandaBertahanDiPreferensi() {
        DownloadCrashGuard.begin(context, "ep9");
        // "Proses baru": tidak ada memori yang diwarisi, tetapi penanda ada di disk.
        assertFalse("belum dicurigai sebelum pulih",
                DownloadCrashGuard.isSuspect(context, "ep9"));
        DownloadCrashGuard.recover(context);
        assertTrue("setelah pulih, episode dicurigai",
                DownloadCrashGuard.isSuspect(context, "ep9"));
    }

    @Test public void karantinaSetelahPembaruanMenahanPekerjaanLama() {
        assertTrue("percobaan pertama setelah update ditahan",
                DownloadQuarantine.judge(false, 1, true).quarantine);
        assertFalse("pekerjaan baru tetap boleh jalan",
                DownloadQuarantine.judge(false, 0, true).quarantine);
        assertFalse("tanpa update, percobaan pertama tetap jalan",
                DownloadQuarantine.judge(false, 1, false).quarantine);
    }
}
