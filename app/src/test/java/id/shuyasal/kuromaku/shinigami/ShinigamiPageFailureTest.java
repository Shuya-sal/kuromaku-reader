package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pemuatan gambar Manga Server 2 saat ada halaman bermasalah.
 *
 * <p>Yang dijaga di sini: satu gambar yang gagal tidak boleh menghapus halaman
 * lain, dan gangguan sesaat dicoba ulang sebelum halaman ditandai gagal.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w320dp-h640dp-mdpi")
public class ShinigamiPageFailureTest {

    private static final String MANGA = "14423a1b-8d7e-41e2-af00-ba0ba655d5f9";
    private static final String CHAPTER = "8b01b9ec-876c-4908-b153-97e86bc9e12f";

    private static ShinigamiSource.Transport transport;

    public static class Reader extends ShinigamiReaderActivity {
        @Override protected ShinigamiSource createSource() {
            return new ShinigamiSource(this, transport);
        }
    }

    private Context context;

    @Before public void siapkan() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(ShinigamiStore.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.getSharedPreferences(ShinigamiConfig.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    private Intent intent() {
        return new Intent(context, Reader.class)
                .putExtra(ShinigamiReaderActivity.EXTRA_MANGA_ID, MANGA)
                .putExtra(ShinigamiReaderActivity.EXTRA_MANGA_TITLE, "Demonic Emperor")
                .putExtra(ShinigamiReaderActivity.EXTRA_CHAPTER_ID, CHAPTER)
                .putExtra(ShinigamiReaderActivity.EXTRA_CHAPTER_NUMBER, 1d);
    }

    /** Payload chapter dengan satu alamat yang tidak bisa dipakai. */
    private static String chapterJson() throws Exception {
        JSONArray files = new JSONArray();
        files.put("1-aaa.jpg");
        files.put("http://jahat.example/2.jpg");
        files.put("3-ccc.jpg");
        files.put("4-ddd.jpg");
        JSONObject inner = new JSONObject()
                .put("chapter_id", CHAPTER)
                .put("path", "chapter/2026")
                .put("data", files);
        JSONObject data = new JSONObject()
                .put("chapter_id", CHAPTER)
                .put("base_url", "https://cdn.shngm.id")
                .put("chapter", inner);
        return new JSONObject().put("retcode", 0).put("data", data).toString();
    }

    private void await(Reader reader) throws Exception {
        Field field = ShinigamiReaderActivity.class.getDeclaredField("worker");
        field.setAccessible(true);
        ((ExecutorService) field.get(reader)).submit(() -> {}).get(10, TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private View tagged(Reader reader, String tag) {
        View view = reader.getWindow().getDecorView().findViewWithTag(tag);
        assertNotNull(tag, view);
        return view;
    }

    @Test public void halamanRusakDilewatiDanSisanyaTetapDitampilkan() throws Exception {
        final String json = chapterJson();
        transport = url -> json;

        try (ActivityController<Reader> controller =
                     Robolectric.buildActivity(Reader.class, intent()).setup()) {
            Reader reader = controller.get();
            await(reader);
            RecyclerView list = (RecyclerView) tagged(reader, "shinigami_reader_list");
            assertEquals("tiga halaman sehat tetap tampil", 3,
                    list.getAdapter().getItemCount());

            // Halaman rusak tidak boleh membawa pesan galat seluruh chapter.
            View error = tagged(reader, "shinigami_reader_error");
            assertEquals(View.GONE, error.getVisibility());
        }
    }

    @Test public void pembacaMemberitahuAdaHalamanYangDilewati() throws Exception {
        final String json = chapterJson();
        transport = url -> json;

        try (ActivityController<Reader> controller =
                     Robolectric.buildActivity(Reader.class, intent()).setup()) {
            Reader reader = controller.get();
            await(reader);
            TextView notice = (TextView)
                    tagged(reader, "shinigami_reader_offline_notice");
            assertEquals(View.VISIBLE, notice.getVisibility());
            String text = notice.getText().toString();
            assertTrue(text, text.contains("tidak tersedia di sumber"));
            assertTrue(text, text.contains("Halaman lain tetap ditampilkan"));
        }
    }

    // ------------------------------------------------------- percobaan ulang

    @Test public void gambarDicobaUlangSebelumDitandaiGagal() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        File expected = new File(context.getCacheDir(), "halaman.img");
        File result = ShinigamiReaderActivity.fetchPage(2, 1L, () -> {
            if (calls.incrementAndGet() < 3) throw new IOException("jaringan putus");
            return expected;
        });
        assertEquals("berhasil pada percobaan ketiga", expected, result);
        assertEquals(3, calls.get());
    }

    @Test public void kegagalanTerakhirDilaporkanSetelahSemuaPercobaan() {
        AtomicInteger calls = new AtomicInteger();
        try {
            ShinigamiReaderActivity.fetchPage(2, 1L, () -> {
                calls.incrementAndGet();
                throw new IOException("server menolak");
            });
            fail("harus melaporkan kegagalan");
        } catch (IOException expected) {
            assertEquals("server menolak", expected.getMessage());
        }
        assertEquals("tiga percobaan: satu awal dan dua ulangan", 3, calls.get());
    }

    @Test public void tanpaPercobaanUlangHanyaSatuKaliCoba() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        File expected = new File(context.getCacheDir(), "sekali.img");
        File result = ShinigamiReaderActivity.fetchPage(0, 1L, () -> {
            calls.incrementAndGet();
            return expected;
        });
        assertEquals(expected, result);
        assertEquals(1, calls.get());
    }

    // ------------------------------------------------- kebijakan coba ulang

    @Test public void kesalahanSementaraDicobaLagi() {
        assertTrue(ShinigamiReaderActivity.worthRetrying(
                new IOException("timeout")));
        assertTrue(ShinigamiReaderActivity.worthRetrying(
                new IOException("Gambar gagal dimuat (HTTP 503).")));
        assertTrue(ShinigamiReaderActivity.worthRetrying(
                new IOException("Gambar gagal dimuat (HTTP 429).")));
    }

    @Test public void halamanYangMemangTidakAdaTidakDicobaLagi() {
        // 404/403 tidak akan berubah: mengulanginya hanya menahan halaman
        // berikutnya di antrean yang sama.
        assertFalse(ShinigamiReaderActivity.worthRetrying(
                new IOException("Gambar gagal dimuat (HTTP 404).")));
        assertFalse(ShinigamiReaderActivity.worthRetrying(
                new IOException("Gambar gagal dimuat (HTTP 403).")));
    }

    @Test public void halaman404TidakDiulangSaatDimuat() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try {
            ShinigamiReaderActivity.fetchPage(1, 1L, () -> {
                calls.incrementAndGet();
                throw new IOException("Gambar gagal dimuat (HTTP 404).");
            }, ShinigamiReaderActivity::worthRetrying);
            fail("harus melaporkan kegagalan");
        } catch (IOException expected) {
            assertEquals("Gambar gagal dimuat (HTTP 404).", expected.getMessage());
        }
        assertEquals("hanya satu percobaan", 1, calls.get());
    }
}
