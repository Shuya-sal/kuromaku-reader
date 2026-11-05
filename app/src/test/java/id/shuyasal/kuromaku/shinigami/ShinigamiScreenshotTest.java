package id.shuyasal.kuromaku.shinigami;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bukan asersi: menggambar beranda ke bitmap supaya hasil layout (rail, kartu,
 * jarak) bisa diperiksa sebagai gambar nyata, bukan hanya tag view.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 33, qualifiers = "w360dp-h640dp-xhdpi")
public class ShinigamiScreenshotTest {

    public static class Catalog extends ShinigamiActivity {
        @Override protected ShinigamiSource createSource() {
            return new ShinigamiSource(this, url -> url.contains("/genre/")
                    ? "{\"retcode\":0,\"data\":[]}"
                    : ShinigamiReaderTest.fixture("live_manga_list.json"));
        }
    }

    @Test
    public void renderHomeWithContinueRail() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(ShinigamiStore.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.getSharedPreferences(ShinigamiConfig.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        ShinigamiStore store = new ShinigamiStore(context);
        store.recordRead("manga-a", "Regressor Instruction Manual",
                "", "chapter-a", 210);
        store.setProgress("chapter-a", 68);
        store.recordRead("manga-b", "Nano Machine", "", "chapter-b", 212);
        store.setProgress("chapter-b", 24);
        store.recordRead("manga-c", "The Great Mage Returns After 4000 Years",
                "", "chapter-c", 8);
        store.setProgress("chapter-c", 91);

        ShinigamiActivity host = Robolectric.buildActivity(Catalog.class).setup().get();
        java.lang.reflect.Field field = ShinigamiActivity.class.getDeclaredField("worker");
        field.setAccessible(true);
        ((java.util.concurrent.ExecutorService) field.get(host)).submit(() -> {})
                .get(5, java.util.concurrent.TimeUnit.SECONDS);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        View decor = host.getWindow().getDecorView();
        int width = 1080;
        int height = 1920;
        decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, width, height);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        decor.draw(new Canvas(bitmap));
        File out = new File(System.getProperty("kuromaku.screenshot",
                System.getProperty("java.io.tmpdir") + "/shinigami-home.png"));
        try (FileOutputStream stream = new FileOutputStream(out)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        }
        View rail = decor.findViewWithTag("shinigami_continue_rail");
        float density = host.getResources().getDisplayMetrics().density;
        System.out.println("SCREENSHOT " + out.getAbsolutePath() + " " + out.length()
                + " railDp=" + Math.round(rail.getHeight() / density)
                + " cards=" + ((android.view.ViewGroup) decor
                        .findViewWithTag("shinigami_continue_cards")).getChildCount());
    }
}
