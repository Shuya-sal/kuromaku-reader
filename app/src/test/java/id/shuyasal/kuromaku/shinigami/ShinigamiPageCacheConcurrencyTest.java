package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Prefetch dan halaman yang sedang terlihat bisa meminta alamat gambar yang
 * sama. Dulu keduanya menulis berkas sementara yang sama sehingga salah satu
 * gagal validasi dan halaman sempat kosong (berkedip). Sekarang permintaan
 * untuk URL yang sama dijalankan berurutan dan hanya satu yang mengunduh.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ShinigamiPageCacheConcurrencyTest {

    private static byte[] png() {
        Bitmap bitmap = Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        return out.toByteArray();
    }

    @Test
    public void twoConcurrentFetchesOfTheSameUrlDownloadOnce() throws Exception {
        final byte[] bytes = png();
        final AtomicInteger opens = new AtomicInteger();
        ShinigamiPageCache cache = new ShinigamiPageCache(
                androidx.test.core.app.ApplicationProvider.getApplicationContext(),
                (url, referer) -> {
                    opens.incrementAndGet();
                    // Tunda sedikit supaya kedua permintaan benar-benar bertumpuk.
                    try {
                        Thread.sleep(60);
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                    }
                    return new java.io.ByteArrayInputStream(bytes);
                });

        String url = "https://cdn.example.com/chapter/1/001.png";
        // Mulai dari keadaan bersih: hapus sisa berkas dari tes lain.
        File stale = new File(cache.dir(), ShinigamiPageCache.key(url) + ".img");
        //noinspection ResultOfMethodCallIgnored
        stale.delete();

        final File[] results = new File[2];
        final Throwable[] failures = new Throwable[2];
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(2);
        for (int i = 0; i < 2; i++) {
            final int slot = i;
            new Thread(() -> {
                try {
                    start.await(5, TimeUnit.SECONDS);
                    results[slot] = cache.fetch(url, "https://shinigami.id/");
                } catch (Throwable error) {
                    failures[slot] = error;
                } finally {
                    done.countDown();
                }
            }, "fetch-" + i).start();
        }
        start.countDown();
        assertTrue("kedua permintaan selesai", done.await(15, TimeUnit.SECONDS));

        assertNotNull("permintaan pertama berhasil", results[0]);
        assertNotNull("permintaan kedua berhasil", results[1]);
        assertEquals("hanya satu unduhan untuk URL yang sama", 1, opens.get());
        assertTrue(results[0].isFile());
        assertEquals(results[0].getAbsolutePath(), results[1].getAbsolutePath());
    }

    @Test
    public void fetchReusesValidatedFileWithoutAskingTheNetwork() throws Exception {
        final byte[] bytes = png();
        final AtomicInteger opens = new AtomicInteger();
        ShinigamiPageCache cache = new ShinigamiPageCache(
                androidx.test.core.app.ApplicationProvider.getApplicationContext(),
                (url, referer) -> {
                    opens.incrementAndGet();
                    return new java.io.ByteArrayInputStream(bytes);
                });
        String url = "https://cdn.example.com/chapter/2/004.png";
        new File(cache.dir(), ShinigamiPageCache.key(url) + ".img").delete();

        File first = cache.fetch(url, "https://shinigami.id/");
        File second = cache.fetch(url, "https://shinigami.id/");

        assertEquals(1, opens.get());
        assertNotNull(first);
        assertEquals(first.getAbsolutePath(), second.getAbsolutePath());
    }

    @Test
    public void brokenTransportDoesNotLeaveAFileBehind() throws Exception {
        ShinigamiPageCache cache = new ShinigamiPageCache(
                androidx.test.core.app.ApplicationProvider.getApplicationContext(),
                new ShinigamiPageCache.Transport() {
                    @Override public InputStream open(String url, String referer) throws IOException {
                        throw new IOException("koneksi putus");
                    }
                });
        String url = "https://cdn.example.com/chapter/3/009.png";
        File target = new File(cache.dir(), ShinigamiPageCache.key(url) + ".img");
        //noinspection ResultOfMethodCallIgnored
        target.delete();

        try {
            cache.fetch(url, "https://shinigami.id/");
            org.junit.Assert.fail("IOException diharapkan");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("koneksi"));
        }
        assertTrue("tidak ada berkas setengah jadi", !target.exists());
    }
}