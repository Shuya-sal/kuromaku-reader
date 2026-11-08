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

import java.io.File;
import java.io.FileOutputStream;

/**
 * Perbaikan antrean WorkManager yang rusak.
 *
 * <p>Yang dijaga: hanya berkas basis data antrean yang dihapus, dan data
 * pengguna (berkas unduhan, status) tidak ikut tersentuh.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class WorkManagerRepairTest {

    private Context context;
    private File databases;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        databases = new File(context.getApplicationInfo().dataDir, "databases");
        //noinspection ResultOfMethodCallIgnored
        databases.mkdirs();
    }

    @Test public void hanyaBerkasAntreanYangDihapus() throws Exception {
        File workdb = new File(databases, "androidx.work.workdb");
        File wal = new File(databases, "androidx.work.workdb-wal");
        File lain = new File(databases, "pengguna.db");
        for (File file : new File[]{workdb, wal, lain}) {
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write("x".getBytes());
            }
        }

        int dihapus = WorkManagerRepair.deleteDatabase(context);

        assertEquals("dua berkas antrean dihapus", 2, dihapus);
        assertFalse("workdb hilang", workdb.exists());
        assertFalse("wal hilang", wal.exists());
        assertTrue("basis data lain tidak disentuh", lain.exists());
    }

    @Test public void berkasUnduhanTidakDisentuh() throws Exception {
        File unduhan = new File(context.getFilesDir(), "anime-downloads/ep1.mp4");
        //noinspection ResultOfMethodCallIgnored
        unduhan.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(unduhan)) {
            out.write("video".getBytes());
        }

        WorkManagerRepair.deleteDatabase(context);

        assertTrue("berkas unduhan tetap ada", unduhan.isFile());
        assertEquals("ukuran tidak berubah", 5, unduhan.length());
    }
}
