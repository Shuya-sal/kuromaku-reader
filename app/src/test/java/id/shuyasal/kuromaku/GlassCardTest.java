package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.RippleDrawable;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import id.shuyasal.kuromaku.ui.GlassCard;
import id.shuyasal.kuromaku.ui.Prefs;

/**
 * Source Hub: kartu benar-benar tembus pandang (bukan putih solid), memakai
 * salinan ilustrasi yang diburamkan di Android 12+, dan tetap terbaca di
 * perangkat lama.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w360dp-h640dp-mdpi")
public class GlassCardTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    private SourceHubActivity launch() {
        SourceHubActivity activity = Robolectric.buildActivity(SourceHubActivity.class).setup().get();
        View decor = activity.getWindow().getDecorView();
        int width = activity.getResources().getDisplayMetrics().widthPixels;
        // Dua lintasan: lintasan pertama mengenali ukuran latar, lintasan kedua
        // menyesuaikan salinan ilustrasi ke ukuran itu (perilaku frame berikutnya).
        for (int pass = 0; pass < 2; pass++) {
            decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1400, View.MeasureSpec.EXACTLY));
            decor.layout(0, 0, width, 1400);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
        return activity;
    }

    private GlassCard card(SourceHubActivity activity, String tag) {
        View view = activity.getWindow().getDecorView().findViewWithTag(tag);
        assertNotNull(tag, view);
        assertTrue(tag + " harus kartu kaca", view instanceof GlassCard);
        return (GlassCard) view;
    }

    @Test
    public void cardsAreTranslucentNotSolidWhite() {
        SourceHubActivity activity = launch();
        for (String tag : new String[]{"source_hub_card_novel", "source_hub_card_voratoon",
                "source_hub_card_shinigami"}) {
            GlassCard card = card(activity, tag);
            View tint = card.tintView();
            android.graphics.drawable.ColorDrawable background =
                    (android.graphics.drawable.ColorDrawable) tint.getBackground();
            int alpha = Color.alpha(background.getColor());
            assertTrue(tag + " alpha " + alpha + " harus tembus pandang", alpha < 200);
            assertTrue(tag + " tidak boleh transparan total", alpha >= 60);
        }
    }

    @Test
    public void cardsBlurOnlyTheirOwnBackdrop() {
        SourceHubActivity activity = launch();
        GlassCard card = card(activity, "source_hub_card_novel");
        assertTrue("Android 12+ harus memakai backdrop blur", card.hasBlurredBackdrop());
        ImageView backdrop = card.backdropView();
        assertNotNull(backdrop);
        assertEquals("blur hanya pada latar kartu, teks tetap terpisah",
                View.IMPORTANT_FOR_ACCESSIBILITY_NO, backdrop.getImportantForAccessibility());
        // Salinan ilustrasi digeser tepat di belakang kartu.
        View background = activity.getWindow().getDecorView()
                .findViewWithTag("source_hub_background");
        int[] cardLocation = new int[2];
        int[] backgroundLocation = new int[2];
        card.getLocationInWindow(cardLocation);
        background.getLocationInWindow(backgroundLocation);
        assertEquals(backgroundLocation[1] - cardLocation[1],
                backdrop.getTranslationY(), 1f);
        assertEquals("salinan ilustrasi seukuran latar",
                background.getWidth(), backdrop.getWidth());
        assertEquals(background.getHeight(), backdrop.getHeight());
    }

    @Test
    @Config(sdk = 30, qualifiers = "w360dp-h640dp-mdpi")
    public void olderAndroidFallsBackToTranslucentSurface() {
        SourceHubActivity activity = launch();
        GlassCard card = card(activity, "source_hub_card_shinigami");
        assertFalse("di bawah Android 12 tidak ada blur", card.hasBlurredBackdrop());
        assertNotNull("isian semi-transparan tetap ada", card.tintView());
    }

    @Test
    public void cardsHaveRoundedCornersBorderShadowAndRipple() {
        SourceHubActivity activity = launch();
        GlassCard card = card(activity, "source_hub_card_voratoon");
        assertEquals(22, card.radiusDp());
        assertTrue("harus ada ripple saat ditekan",
                card.getForeground() instanceof RippleDrawable);
        assertTrue("harus ada bayangan lembut", card.getElevation() > 0);
        assertTrue("bayangan harus terpotong rapi", card.getClipToOutline());
        assertNotNull("border putih transparan", card.borderView());
        assertEquals("glass_border", card.borderView().getTag());
    }

    @Test
    public void darkThemeUsesDarkerGlass() {
        new Prefs(context).setTheme(Prefs.THEME_DARK);
        SourceHubActivity activity = launch();
        GlassCard dark = card(activity, "source_hub_card_novel");
        int darkColor = ((android.graphics.drawable.ColorDrawable)
                dark.tintView().getBackground()).getColor();

        context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        new Prefs(context).setTheme(Prefs.THEME_LIGHT);
        SourceHubActivity lightActivity = launch();
        GlassCard light = card(lightActivity, "source_hub_card_novel");
        int lightColor = ((android.graphics.drawable.ColorDrawable)
                light.tintView().getBackground()).getColor();

        assertFalse("opacity kaca harus berbeda antara terang dan gelap",
                darkColor == lightColor);
        int darkAlpha = Color.alpha(darkColor);
        int lightAlpha = Color.alpha(lightColor);
        assertTrue("kaca mode gelap lebih pekat (" + darkAlpha + " vs " + lightAlpha + ")",
                darkAlpha > lightAlpha);
        assertTrue("kaca harus tembus pandang, bukan solid", darkAlpha < 0xC0);
    }

    @Test
    public void tappingCardStartsTheMatchingSourceScreen() {
        SourceHubActivity activity = launch();
        String[][] expected = {
                {"source_hub_card_novel", MainActivity.class.getName()},
                {"source_hub_card_voratoon", MangaActivity.class.getName()},
                {"source_hub_card_shinigami",
                        "id.shuyasal.kuromaku.shinigami.ShinigamiActivity"},
        };
        for (String[] pair : expected) {
            card(activity, pair[0]).performClick();
            android.content.Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(pair[0] + " harus membuka halaman", started);
            assertEquals(pair[0], pair[1], started.getComponent().getClassName());
        }
    }
}
