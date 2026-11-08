package id.shuyasal.kuromaku.offline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.content.Context;
import android.content.pm.ServiceInfo;

import androidx.test.core.app.ApplicationProvider;
import androidx.work.ForegroundInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Tipe foreground service pada notifikasi unduhan.
 *
 * <p>Android 16 menolak memulai foreground service bertipe {@code none} untuk
 * aplikasi {@code targetSdk >= 35}:
 *
 * <pre>
 * android.app.InvalidForegroundServiceTypeException:
 *   Starting FGS with type none ... has been prohibited
 * </pre>
 *
 * <p>Exception itu dilempar dari dalam service WorkManager, jadi tidak bisa
 * ditangkap dan langsung mematikan proses. Tes ini menjaga tipe tetap eksplisit
 * dan sama dengan yang dideklarasikan di manifest.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ForegroundTypeTest {

    @Test public void tipeBukanNone() {
        Context context = ApplicationProvider.getApplicationContext();
        ForegroundInfo info = ForegroundDownloads.info(context, 5100, "Mengunduh", "0%", 0, "tag");
        assertNotEquals("tipe none dilarang Android 16",
                ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE, info.getForegroundServiceType());
    }

    @Test public void tipeDataSyncSesuaiManifest() {
        Context context = ApplicationProvider.getApplicationContext();
        ForegroundInfo info = ForegroundDownloads.info(context, 5100, "Mengunduh", "0%", 0, "tag");
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                info.getForegroundServiceType());
        assertEquals("deklarasi manifest", ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                ForegroundDownloads.SERVICE_TYPE);
    }
}
