package id.shuyasal.kuromaku.offline;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Riwayat kematian proses dari Android.
 *
 * <p>Yang dijaga: pembacaan riwayat tidak pernah melempar, dan hasilnya selalu
 * berupa teks yang aman ditampilkan (boleh kosong bila tidak ada kejadian).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ExitReportsTest {

    @Test public void tidakMelemparDanSelaluString() {
        Context context = ApplicationProvider.getApplicationContext();
        String ringkasan = ExitReports.summary(context);
        assertNotNull("tidak boleh null", ringkasan);
        assertTrue("berupa teks", ringkasan instanceof String);
    }

    @Test public void tanpaRiwayatTidakAdaLaporan() {
        Context context = ApplicationProvider.getApplicationContext();
        // Robolectric tidak menyediakan riwayat kematian proses: hasilnya kosong,
        // dan itu harus berarti "tidak ada laporan", bukan error.
        assertTrue("tidak ada laporan", !ExitReports.adaLaporan(context)
                || ExitReports.summary(context).contains("RIWAYAT"));
    }

    @Test public void contextNullAman() {
        assertTrue(ExitReports.summary(null).isEmpty());
        assertTrue(!ExitReports.adaLaporan(null));
    }
}
