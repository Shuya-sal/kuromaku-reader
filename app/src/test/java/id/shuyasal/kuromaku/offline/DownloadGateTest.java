package id.shuyasal.kuromaku.offline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Batas global transfer bersamaan.
 *
 * <p>Yang dijaga: hanya satu transfer berjalan di seluruh aplikasi, dan slot
 * selalu dikembalikan sehingga antrean tidak macet.
 */
public class DownloadGateTest {

    @Test public void hanyaSatuSlot() {
        assertEquals(1, DownloadGate.SLOTS);
        assertTrue(DownloadGate.acquire());
        assertEquals("slot habis dipakai", 0, DownloadGate.available());
        DownloadGate.release();
        assertEquals("slot kembali", 1, DownloadGate.available());
    }

    @Test public void transferKeduaMenungguSampaiSlotDilepas() throws Exception {
        assertTrue(DownloadGate.acquire());
        CountDownLatch masuk = new CountDownLatch(1);
        AtomicBoolean selesai = new AtomicBoolean(false);
        Thread kedua = new Thread(() -> {
            if (DownloadGate.acquire()) {
                selesai.set(true);
                masuk.countDown();
                DownloadGate.release();
            }
        });
        kedua.setDaemon(true);
        kedua.start();

        Thread.sleep(120);
        assertEquals("belum boleh jalan bersamaan", false, selesai.get());
        DownloadGate.release();

        assertTrue("jalan setelah slot bebas", masuk.await(3, TimeUnit.SECONDS));
        kedua.join(2000);
    }
}
