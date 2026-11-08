package id.shuyasal.kuromaku.offline;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import id.shuyasal.kuromaku.anime.AnimeDownloadStatus;
import id.shuyasal.kuromaku.shinigami.ShinigamiDownloadStatus;

/**
 * Rekonsiliasi saat aplikasi dibuka ulang.
 *
 * <p>Status yang menggantung tidak boleh berputar selamanya, dan "tersimpan"
 * tidak boleh bertahan bila berkasnya sudah tidak ada.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class DownloadReconcilerTest {

    private Context context;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        AnimeDownloadStatus.reset();
        ShinigamiDownloadStatus.reset();
        DownloadStatusStore.reset();
        DownloadStateFile.install(context);
        DownloadStateFile.clearPrefix("");
    }

    @After public void tearDown() {
        AnimeDownloadStatus.reset();
        ShinigamiDownloadStatus.reset();
        DownloadStatusStore.reset();
        DownloadStateFile.clearPrefix("");
    }

    @Test public void statusMengunduhYangMenggantungMenjadiDijeda() {
        long lama = System.currentTimeMillis() - 5 * 60_000L;
        DownloadStateFile.putAged(AnimeDownloadStatus.PREFIX + "ep1",
                AnimeDownloadStatus.DOWNLOADING, 37, "37%", "720p", lama);

        DownloadReconciler.reconcile(context);

        assertEquals("tidak dibiarkan berputar", AnimeDownloadStatus.PAUSED,
                AnimeDownloadStatus.state("ep1"));
        assertEquals("persen terakhir tetap jujur", 37, AnimeDownloadStatus.percent("ep1"));
        assertEquals("kualitas tidak hilang", "720p", AnimeDownloadStatus.quality("ep1"));
    }

    @Test public void pekerjaanYangBaruSajaBerjalanTidakDiganggu() {
        DownloadStateFile.putAged(AnimeDownloadStatus.PREFIX + "ep1",
                AnimeDownloadStatus.DOWNLOADING, 10, "10%", "720p",
                System.currentTimeMillis());

        DownloadReconciler.reconcile(context);

        assertEquals("masih dianggap berjalan", AnimeDownloadStatus.DOWNLOADING,
                AnimeDownloadStatus.state("ep1"));
    }

    @Test public void klaimTersimpanTanpaBerkasDiturunkan() {
        DownloadStateFile.putAged(AnimeDownloadStatus.PREFIX + "ep-hilang",
                AnimeDownloadStatus.SAVED, 100, "Tersimpan", "720p",
                System.currentTimeMillis() - 120_000L);

        DownloadReconciler.reconcile(context);

        assertEquals("tidak boleh tetap tersimpan", AnimeDownloadStatus.FAILED,
                AnimeDownloadStatus.state("ep-hilang"));
    }

    @Test public void klaimBaruBelumDirekonsiliasi() {
        // Unduhan yang baru ditulis bisa masih berjalan di proses lain; rekonsiliasi
        // tidak boleh menurunkannya menjadi gagal hanya karena berkas belum terlihat.
        DownloadStateFile.putAged(AnimeDownloadStatus.PREFIX + "ep-baru",
                AnimeDownloadStatus.SAVED, 100, "Tersimpan", "720p",
                System.currentTimeMillis());

        DownloadReconciler.reconcile(context);

        assertEquals("klaim baru dibiarkan", AnimeDownloadStatus.SAVED,
                AnimeDownloadStatus.state("ep-baru"));
    }

    @Test public void statusNovelMenggantungMenjadiDijeda() {
        long lama = System.currentTimeMillis() - 5 * 60_000L;
        DownloadStateFile.putAged(DownloadStatusStore.PREFIX + "n1|https://x/ch1",
                DownloadStatusStore.DOWNLOADING, 20, "", "", lama);

        DownloadReconciler.reconcile(context);

        assertEquals("chapter tidak diklaim tersimpan", DownloadStatusStore.PARTIAL,
                DownloadStatusStore.state("n1|https://x/ch1"));
    }
}
