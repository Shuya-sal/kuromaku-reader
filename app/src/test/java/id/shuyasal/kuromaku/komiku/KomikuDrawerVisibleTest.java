package id.shuyasal.kuromaku.komiku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Dialog;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/**
 * Menu samping harus benar-benar terlihat di layar setelah dibuka.
 *
 * <p>Dialog bisa saja "terbuka" menurut hitungan dialog, tetapi panelnya
 * tergeser keluar layar oleh animasi masuk yang tidak pernah selesai. Di kasus
 * itu pengguna melihat tidak ada apa-apa, dan dialog yang tidak terlihat tetap
 * menangkap sentuhan — persis keluhan "menu tidak bisa diklik".
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w411dp-h891dp-mdpi")
public class KomikuDrawerVisibleTest {

    @Before public void siapkan() {
        android.content.Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(KomikuStore.FILE_NAME, android.content.Context.MODE_PRIVATE)
                .edit().clear().commit();
        new KomikuCatalog(context).clear();
    }

    @Test public void panelMenuBeradaDiDalamLayarSetelahDibuka() {
        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            KomikuActivity activity = controller.get();
            Dialog drawer = KomikuNavigation.showDrawer(activity, activity.paletteUntukTes());
            assertNotNull("dialog menu dibuat", drawer);
            assertTrue("dialog tampil", drawer.isShowing());

            // Biarkan animasi masuk berjalan sampai selesai.
            ShadowLooper.idleMainLooper();
            for (int i = 0; i < 40; i++) {
                org.robolectric.shadows.ShadowSystemClock.advanceBy(
                        java.time.Duration.ofMillis(50));
                ShadowLooper.idleMainLooper();
            }

            View decor = drawer.getWindow() == null ? null : drawer.getWindow().getDecorView();
            assertNotNull("decor dialog ada", decor);
            assertEquals("panel tidak tergeser keluar layar",
                    0f, decor.getTranslationX(), 0.5f);
            assertTrue("panel punya lebar", decor.getWidth() > 0);

            View panel = deepestPanel(decor);
            assertNotNull("panel menu ada", panel);
            assertTrue("panel terlihat", panel.getVisibility() == View.VISIBLE);
            assertEquals("panel tidak tergeser", 0f, panel.getTranslationX(), 0.5f);
        }
    }

    @Test public void itemMenuBisaDiklikSetelahDibuka() {
        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            KomikuActivity activity = controller.get();
            Dialog drawer = KomikuNavigation.showDrawer(activity, activity.paletteUntukTes());
            ShadowLooper.idleMainLooper();

            View decor = drawer.getWindow().getDecorView();
            View home = byTag(decor, "drawer_home");
            assertNotNull("item Beranda ada", home);
            assertEquals("item Beranda bisa diklik", 0f, home.getTranslationX(), 0.5f);
            // Klik item harus menutup menu.
            home.performClick();
            ShadowLooper.idleMainLooper();
            assertTrue("menu tertutup setelah item dipilih", !drawer.isShowing());
        }
    }

    private static View deepestPanel(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View found = deepestPanel(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return view instanceof ViewGroup ? view : null;
    }

    private static View byTag(View root, String tag) {
        if (root == null) return null;
        if (tag.equals(root.getTag())) return root;
        if (!(root instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = byTag(group.getChildAt(i), tag);
            if (found != null) return found;
        }
        return null;
    }
}
