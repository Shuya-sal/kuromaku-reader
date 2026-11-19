package id.shuyasal.kuromaku.shinigami;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Beranda katalog Manga Server 2 dengan respons ASLI sumber baru
 * (api.shinigami.io) — 24 judul, enam di antaranya bertanda rekomendasi.
 *
 * <p>Yang dijaga tes ini adalah DATA yang sampai ke layar: status jumlah judul,
 * isi daftar, dan isi rail sorotan. Gambar tampilannya sendiri tidak bisa
 * dipercaya dari Robolectric — data, ukuran, dan teksnya benar tetapi lapisan
 * gambarnya kosong — jadi bentuk visual diperiksa lewat pratinjau peramban
 * memakai data yang sama, bukan lewat bitmap di sini.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w540dp-h960dp-xhdpi")
public class ShinigamiNewApiRenderTest {

    static final List<String> REQUESTS = new ArrayList<>();

    public static class Catalog extends ShinigamiActivity {
        @Override protected ShinigamiSource createSource() {
            return new ShinigamiSource(this, url -> {
                REQUESTS.add(url);
                if (url.contains("/taxonomy/")) return fixture("newapi_genres.json");
                // highlight_newest.json = halaman judul terbaru asli dengan
                // penanda is_recommended; highlight_list.json = halaman populer.
                if (url.contains("sort=new_manga")) return fixture("highlight_newest.json");
                return fixture("highlight_list.json");
            });
        }
    }

    static String fixture(String name) throws java.io.IOException {
        try (InputStream in = ShinigamiNewApiRenderTest.class.getResourceAsStream(
                "/shinigami/" + name)) {
            if (in == null) throw new java.io.IOException("fixture tidak ada: " + name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            return out.toString("UTF-8");
        }
    }

    @Before public void setup() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(ShinigamiStore.FILE_NAME, 0).edit().clear().commit();
        context.getSharedPreferences(ShinigamiConfig.FILE_NAME, 0).edit().clear().commit();
        REQUESTS.clear();
    }

    private void await(Catalog host) throws Exception {
        Field field = ShinigamiActivity.class.getDeclaredField("worker");
        field.setAccessible(true);
        ((ExecutorService) field.get(host)).submit(() -> { }).get(30, TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @SuppressWarnings("unchecked")
    private static List<ShinigamiManga> highlights(Catalog host) throws Exception {
        Field field = ShinigamiActivity.class.getDeclaredField("highlights");
        field.setAccessible(true);
        return (List<ShinigamiManga>) field.get(host);
    }

    @Test public void berandaSumberBaruTergambar() throws Exception {
        try (ActivityController<Catalog> controller =
                     Robolectric.buildActivity(Catalog.class).setup()) {
            Catalog host = controller.get();
            for (int attempt = 0; attempt < 4; attempt++) await(host);

            // Pindah ke Terpopuler supaya sorotan dan grid katalog sama-sama terisi.
            View popular = host.getWindow().getDecorView()
                    .findViewWithTag("shinigami_chip_Terpopuler");
            if (popular != null) {
                popular.performClick();
                for (int attempt = 0; attempt < 4; attempt++) await(host);
            }

            TextView status = host.getWindow().getDecorView()
                    .findViewWithTag("shinigami_status");
            String statusText = status == null ? "(tidak ada)" : status.getText().toString();
            System.out.println("STATUS: " + statusText);
            for (String request : REQUESTS) System.out.println("MINTA: " + request);
            assertTrue("status harus menyebut jumlah judul: " + statusText,
                    statusText.matches("\\d+ manga"));

            RecyclerView grid = host.getWindow().getDecorView()
                    .findViewWithTag("shinigami_grid");
            assertNotNull("daftar katalog harus ada", grid);
            assertTrue("katalog harus terisi", grid.getAdapter().getItemCount() > 0);
            System.out.println("KARTU: " + grid.getAdapter().getItemCount());

            List<ShinigamiManga> loaded = highlights(host);
            assertFalse("sorotan tidak boleh kosong pada data ini", loaded.isEmpty());
            for (ShinigamiManga manga : loaded) {
                assertTrue("banner hanya judul bertanda: " + manga.title, manga.recommended);
                assertFalse("sampul banner harus dari sumber: " + manga.title,
                        manga.wideCoverUrl.isEmpty());
                System.out.println("BANNER: " + manga.title + " | " + manga.wideCoverUrl);
            }
        }
    }
}
