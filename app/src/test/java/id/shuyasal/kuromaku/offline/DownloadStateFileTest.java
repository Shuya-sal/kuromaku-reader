package id.shuyasal.kuromaku.offline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.Map;

import id.shuyasal.kuromaku.anime.AnimeDownloadStatus;
import id.shuyasal.kuromaku.shinigami.ShinigamiDownloadStatus;

/**
 * Status unduhan yang bertahan setelah proses mati.
 *
 * <p>Yang dijaga: keadaan terbaca lagi setelah aplikasi dibuka ulang, modul
 * tidak saling menimpa, dan status menggantung tidak berubah menjadi klaim
 * "tersimpan".
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = id.shuyasal.kuromaku.KuromakuApplication.class)
public class DownloadStateFileTest {

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

    @Test public void kualitasDanPersenTerbacaSetelahProsesBerakhir() {
        AnimeDownloadStatus.set("ep1", AnimeDownloadStatus.DOWNLOADING, 42, "42%", "720p");

        // Proses "mati": peta di memori dikosongkan, berkas tetap ada.
        AnimeDownloadStatus.forgetForTesting();
        assertTrue("berkas status harus ada di penyimpanan",
                new File(context.getFilesDir(), "download-state.json").isFile());

        assertEquals(AnimeDownloadStatus.DOWNLOADING, AnimeDownloadStatus.state("ep1"));
        assertEquals(42, AnimeDownloadStatus.percent("ep1"));
        assertEquals("720p", AnimeDownloadStatus.quality("ep1"));
    }

    @Test public void modulTidakSalingMenimpa() {
        AnimeDownloadStatus.set("sama", AnimeDownloadStatus.SAVED, 100, "Tersimpan", "480p");
        ShinigamiDownloadStatus.set("sama", ShinigamiDownloadStatus.DOWNLOADING, 10, "");

        AnimeDownloadStatus.forgetForTesting();
        ShinigamiDownloadStatus.forgetForTesting();

        assertEquals("status anime tetap", AnimeDownloadStatus.SAVED,
                AnimeDownloadStatus.state("sama"));
        assertEquals("status manga tidak tertukar", ShinigamiDownloadStatus.DOWNLOADING,
                ShinigamiDownloadStatus.state("sama"));
        assertEquals("480p", AnimeDownloadStatus.quality("sama"));
    }

    @Test public void menghapusStatusIkutBersihDiBerkas() {
        AnimeDownloadStatus.set("ep1", AnimeDownloadStatus.FAILED, 0, "Gagal");
        AnimeDownloadStatus.clear("ep1");
        AnimeDownloadStatus.forgetForTesting();
        assertEquals(AnimeDownloadStatus.NOT_DOWNLOADED, AnimeDownloadStatus.state("ep1"));
    }

    @Test public void berkasStatusRusakTidakMembuatAplikasiGagal() throws Exception {
        File file = new File(context.getFilesDir(), "download-state.json");
        file.getParentFile().mkdirs();
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
            out.write("{ini bukan json".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        DownloadStateFile.install(context);
        // Tidak melempar; keadaan dianggap kosong.
        assertEquals(AnimeDownloadStatus.NOT_DOWNLOADED, AnimeDownloadStatus.state("apa saja"));
        assertNotNull("berkas tetap bisa ditulis ulang",
                new File(context.getFilesDir(), "download-state.json"));
    }

    @Test public void penulisanMemakaiBerkasSementaraLaluRename() {
        AnimeDownloadStatus.set("ep1", AnimeDownloadStatus.QUEUED, 0, "Menunggu");
        File temp = new File(context.getFilesDir(), "download-state.json.tmp");
        assertEquals("berkas sementara tidak tertinggal", false, temp.exists());
    }

    @Test public void statusNovelIkutTersimpan() {
        DownloadStatusStore.set("novel1|https://x/ch1", DownloadStatusStore.SAVED, "");
        DownloadStatusStore.setBytes("novel1|https://x/ch1", 100, 4, 4);
        DownloadStatusStore.resetMemoryForTesting();

        assertEquals(DownloadStatusStore.SAVED,
                DownloadStatusStore.state("novel1|https://x/ch1"));
        assertNotNull("progres byte ikut pulih",
                DownloadStatusStore.bytes("novel1|https://x/ch1"));
    }

    @Test public void keadaanTerbacaDariBerkasNyata() {
        DownloadStateFile.put("anime|ep9", 4, 100, "Tersimpan", "720p");
        Map<String, DownloadStateFile.Row> all = DownloadStateFile.all();
        DownloadStateFile.Row row = all.get("anime|ep9");
        assertNotNull(row);
        assertEquals(4, row.state);
        assertEquals("Tersimpan", row.message);
        assertNull(all.get("anime|tidak-ada"));
    }
}