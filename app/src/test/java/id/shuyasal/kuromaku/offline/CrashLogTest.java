package id.shuyasal.kuromaku.offline;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Penangkap crash di dalam aplikasi.
 *
 * <p>Yang dijaga: exception yang mematikan proses benar-benar tercatat lengkap
 * dengan tipe dan lokasi, dan laporan bisa dibaca kembali dari aplikasi.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class CrashLogTest {

    private Context context;
    private Thread.UncaughtExceptionHandler asli;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        asli = Thread.getDefaultUncaughtExceptionHandler();
        CrashLog.resetForTests();
        CrashLog.clear(context);
    }

    @After public void tearDown() {
        CrashLog.resetForTests();
        Thread.setDefaultUncaughtExceptionHandler(asli);
        CrashLog.clear(context);
    }

    @Test public void crashTercatatLengkap() {
        CrashLog.install(context);
        Thread.UncaughtExceptionHandler handler =
                Thread.getDefaultUncaughtExceptionHandler();
        handler.uncaughtException(Thread.currentThread(),
                new IllegalStateException("antrean unduhan rusak",
                        new NullPointerException("berkas null")));

        String laporan = CrashLog.read(context);
        assertTrue("ada laporan", CrashLog.exists(context));
        assertTrue("menyebut FATAL EXCEPTION", laporan.contains("FATAL EXCEPTION"));
        assertTrue("menyebut tipe exception",
                laporan.contains("java.lang.IllegalStateException"));
        assertTrue("menyebut pesan", laporan.contains("antrean unduhan rusak"));
        assertTrue("menyebut penyebab", laporan.contains("Caused by"));
        assertTrue("menyebut NullPointerException",
                laporan.contains("java.lang.NullPointerException"));
        assertTrue("menyebut versi android", laporan.contains("android: API"));
    }

    @Test public void laporanBisaDihapus() {
        CrashLog.install(context);
        Thread.getDefaultUncaughtExceptionHandler()
                .uncaughtException(Thread.currentThread(), new RuntimeException("uji"));
        assertTrue(CrashLog.exists(context));
        CrashLog.clear(context);
        assertFalse(CrashLog.exists(context));
        assertTrue("isi kosong setelah dihapus", CrashLog.read(context).isEmpty());
    }

    @Test public void tanpaCrashTidakAdaLaporan() {
        assertFalse(CrashLog.exists(context));
        assertTrue(CrashLog.read(context).isEmpty());
    }
}
