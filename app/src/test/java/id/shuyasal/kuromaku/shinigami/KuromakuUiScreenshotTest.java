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
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Menggambar layar nyata (bukan asersi visual) supaya hasil layout bisa
 * diperiksa: toolbar reader di layar kecil, rail ringkas di beranda, dan
 * kartu kaca di Source Hub.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 33, qualifiers = "w540dp-h960dp-xhdpi")
public class KuromakuUiScreenshotTest {
    private Context context;

    public static class Catalog extends ShinigamiActivity {
        @Override protected ShinigamiSource createSource() {
            return new ShinigamiSource(this, url -> url.contains("/genre/")
                    ? "{\"retcode\":0,\"data\":[]}"
                    : ShinigamiReaderTest.fixture("live_manga_list_big.json"));
        }
    }

    public static class Reader extends ShinigamiReaderActivity {
        @Override protected ShinigamiSource createSource() {
            return new ShinigamiSource(this, url -> url.contains("/list")
                    ? ShinigamiReaderTest.fixture("live_chapter_list.json")
                    : ShinigamiReaderTest.fixture("live_chapter_detail.json"));
        }
    }

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(ShinigamiStore.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.getSharedPreferences(ShinigamiConfig.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    private String shoot(View decor, int width, int height, String name) throws Exception {
        // Dua lintasan supaya penyesuaian ukuran latar kartu kaca ikut terpakai.
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

    @Test
    public void renderHome() throws Exception {
        ShinigamiStore store = new ShinigamiStore(context);
        store.recordRead("manga-a", "Regressor Instruction Manual", "", "chapter-a", 210);
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
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        View decor = host.getWindow().getDecorView();
        String info = shoot(decor, 1080, 1920, "kuromaku-home.png");
        View rail = decor.findViewWithTag("shinigami_continue_rail");
        float density = host.getResources().getDisplayMetrics().density;
        System.out.println("SHOT home " + info
                + " railDp=" + Math.round(rail.getHeight() / density)
                + " cards=" + ((android.view.ViewGroup) decor
                        .findViewWithTag("shinigami_continue_cards")).getChildCount());
    }

    @Test
    public void renderReaderToolbar() throws Exception {
        android.content.Intent intent = new android.content.Intent(
                ApplicationProvider.getApplicationContext(), Reader.class)
                .putExtra(ShinigamiReaderActivity.EXTRA_MANGA_ID, "manga-a")
                .putExtra(ShinigamiReaderActivity.EXTRA_MANGA_TITLE, "Regressor Instruction Manual")
                .putExtra(ShinigamiReaderActivity.EXTRA_CHAPTER_ID, "chapter-a")
                .putExtra(ShinigamiReaderActivity.EXTRA_CHAPTER_NUMBER, 399);
        ShinigamiReaderActivity host = Robolectric.buildActivity(Reader.class, intent)
                .setup().get();
        java.lang.reflect.Field field =
                ShinigamiReaderActivity.class.getDeclaredField("worker");
        field.setAccessible(true);
        ((java.util.concurrent.ExecutorService) field.get(host)).submit(() -> {})
                .get(5, java.util.concurrent.TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        View decor = host.getWindow().getDecorView();
        // Layar kecil: 320 dp.
        String info = shoot(decor, 640, 1280, "kuromaku-reader-toolbar.png");
        StringBuilder children = new StringBuilder();
        android.view.ViewGroup bar = null;
        View title = decor.findViewWithTag("shinigami_reader_title");
        if (title != null && title.getParent() instanceof android.view.ViewGroup) {
            bar = (android.view.ViewGroup) title.getParent();
        }
        if (bar != null) {
            for (int i = 0; i < bar.getChildCount(); i++) {
                View child = bar.getChildAt(i);
                children.append(child.getWidth()).append('x').append(child.getHeight()).append(' ');
            }
        }
        System.out.println("SHOT reader " + info + " barWidth=" + (bar == null ? -1 : bar.getWidth())
                + " children=" + children);
    }

    @Test
    public void renderSourceHub() throws Exception {
        id.shuyasal.kuromaku.SourceHubActivity host =
                Robolectric.buildActivity(id.shuyasal.kuromaku.SourceHubActivity.class)
                        .setup().get();
        View decor = host.getWindow().getDecorView();
        String info = shoot(decor, 1080, 1920, "kuromaku-source-hub.png");
        StringBuilder cards = new StringBuilder();
        for (String tag : new String[]{"source_hub_card_novel", "source_hub_card_voratoon",
                "source_hub_card_shinigami"}) {
            View card = decor.findViewWithTag(tag);
            int[] loc = new int[2];
            card.getLocationInWindow(loc);
            cards.append(tag).append('@').append(loc[0]).append(',').append(loc[1])
                    .append('+').append(card.getWidth()).append('x').append(card.getHeight())
                    .append(" tint=").append(Integer.toHexString(
                            ((android.graphics.drawable.ColorDrawable)
                                    ((id.shuyasal.kuromaku.ui.GlassCard) card).tintView()
                                            .getBackground()).getColor()))
                    .append(" blur=").append(((id.shuyasal.kuromaku.ui.GlassCard) card)
                            .hasBlurredBackdrop())
                    .append(" tx=").append(((id.shuyasal.kuromaku.ui.GlassCard) card)
                            .backdropView() == null ? -1
                            : ((id.shuyasal.kuromaku.ui.GlassCard) card)
                                    .backdropView().getTranslationY())
                    .append(" bw=").append(((id.shuyasal.kuromaku.ui.GlassCard) card)
                            .backdropView() == null ? -1
                            : ((id.shuyasal.kuromaku.ui.GlassCard) card)
                                    .backdropView().getWidth())
                    .append(" | ");
        }
        View bg = decor.findViewWithTag("source_hub_background");
        System.out.println("SHOT hub " + info + " bg=" + (bg == null ? -1 : bg.getHeight())
                + " cards=" + cards);
    }
}
