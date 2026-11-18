package id.shuyasal.kuromaku.shinigami;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Menggambar beranda katalog memakai respons ASLI dari sumber baru
 * (api.shinigami.io, 24 judul) supaya terlihat apa yang benar-benar tampil di
 * layar: status bar, tab, dan grid judul. Bukan asersi gaya, hanya bukti
 * render yang bisa diperiksa.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 33, qualifiers = "w540dp-h960dp-xhdpi")
public class ShinigamiNewApiRenderTest {

    static final List<String> REQUESTS = new ArrayList<>();

    public static class Catalog extends ShinigamiActivity {
        @Override protected ShinigamiSource createSource() {
            return new ShinigamiSource(this, url -> {
                REQUESTS.add(url);
                if (url.contains("/taxonomy/")) return fixture("newapi_genres.json");
                return fixture("live_new_api_list.json");
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

    @Test public void berandaSumberBaruTergambar() throws Exception {
        try (ActivityController<Catalog> controller =
                     Robolectric.buildActivity(Catalog.class).setup()) {
            Catalog host = controller.get();
            for (int attempt = 0; attempt < 4; attempt++) {
                java.lang.reflect.Field field = ShinigamiActivity.class.getDeclaredField("worker");
                field.setAccessible(true);
                ((java.util.concurrent.ExecutorService) field.get(host))
                        .submit(() -> { }).get(20, java.util.concurrent.TimeUnit.SECONDS);
                Shadows.shadowOf(Looper.getMainLooper()).idle();
            }

            android.widget.TextView status = host.getWindow().getDecorView()
                    .findViewWithTag("shinigami_status");
            String statusText = status == null ? "(tidak ada)" : status.getText().toString();
            System.out.println("STATUS: " + statusText);
            for (String request : REQUESTS) System.out.println("MINTA: " + request);

            View decor = host.getWindow().getDecorView();
            String path = shoot(decor, 1080, 1920, "newapi-home.png");
            System.out.println("GAMBAR: " + path);

            androidx.recyclerview.widget.RecyclerView grid = host.getWindow().getDecorView()
                    .findViewWithTag("shinigami_grid");
            if (grid != null) {
                System.out.println("KARTU: " + grid.getAdapter().getItemCount());
            }
        }
    }

    private String shoot(View decor, int width, int height, String name) throws Exception {
        for (int pass = 0; pass < 2; pass++) {
            decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            decor.layout(0, 0, width, height);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        decor.draw(new Canvas(bitmap));
        File out = new File(System.getProperty("java.io.tmpdir"), name);
        try (FileOutputStream stream = new FileOutputStream(out)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        }
        return out.getAbsolutePath() + " " + out.length();
    }
}
