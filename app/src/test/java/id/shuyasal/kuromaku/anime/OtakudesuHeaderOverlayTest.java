package id.shuyasal.kuromaku.anime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import id.shuyasal.kuromaku.ui.Prefs;

/**
 * Header beranda Anime Streaming sebagai lapisan di atas daftar.
 *
 * <p>Bukan animasi yang diuji, melainkan sifat yang membuat scroll stabil:
 * header tidak pernah dilepas dari hierarki, ukuran daftar tidak berubah saat
 * header disembunyikan, dan inset atas daftar mengikuti tinggi header.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class OtakudesuHeaderOverlayTest {

    private static OtakudesuSource nextSource;

    public static final class Host extends OtakudesuActivity {
        @Override protected OtakudesuSource createSource() {
            return nextSource;
        }
    }

    @Before public void setUp() {
        new Prefs(ApplicationProvider.getApplicationContext()).setTheme(Prefs.THEME_LIGHT);
        nextSource = new OtakudesuSource(url -> fixture("ongoing.html"));
    }

    @org.junit.After public void tearDown() {
        new Prefs(ApplicationProvider.getApplicationContext()).setTheme(Prefs.THEME_SYSTEM);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    private static String fixture(String name) {
        try (java.io.InputStream in = OtakudesuHeaderOverlayTest.class.getResourceAsStream(
                "/otakudesu/" + name)) {
            if (in == null) throw new IllegalStateException("fixture: " + name);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            return out.toString("UTF-8");
        } catch (java.io.IOException error) {
            throw new IllegalStateException(error);
        }
    }

    private static View findViewById(View root, String tag) {
        if (tag.equals(root.getTag())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findViewById(group.getChildAt(i), tag);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Tata layar seperti perangkat 1080x1920 supaya tinggi header nyata. */
    private static void layout(ActivityController<Host> controller) {
        View decor = controller.get().getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, 1080, 1920);
        ShadowLooper.idleMainLooper();
    }

    @Test public void headerAdalahLapisanDanDaftarTidakBerubahUkuran() {
        try (ActivityController<Host> controller = Robolectric.buildActivity(Host.class).setup()) {
            layout(controller);
            Host activity = controller.get();
            View header = findViewById(activity.getWindow().getDecorView(),
                    "otakudesu_top_section");
            View grid = findViewById(activity.getWindow().getDecorView(), "otakudesu_grid");
            assertNotNull("header harus ada", header);
            assertNotNull("daftar harus ada", grid);

            assertTrue("header tertata", header.getHeight() > 0);
            assertEquals("inset atas daftar mengikuti tinggi header",
                    header.getHeight(), grid.getPaddingTop());

            int listHeight = grid.getHeight();
            int headerHeight = header.getHeight();

            invoke(activity, "hideTopSection");
            ShadowLooper.idleMainLooper();
            layout(controller);

            assertEquals("header tidak boleh dilepas dari hierarki",
                    View.VISIBLE, header.getVisibility());
            assertEquals("header bergeser tepat setinggi dirinya",
                    -headerHeight, Math.round(header.getTranslationY()));
            assertEquals("alpha akhir nol", 0f, header.getAlpha(), 0.01f);
            assertEquals("ukuran daftar tidak berubah", listHeight, grid.getHeight());
            assertEquals("inset tetap, jadi tidak ada lompatan",
                    headerHeight, grid.getPaddingTop());
        }
    }

    @Test public void menampilkanLagiMengembalikanPosisiAwal() {
        try (ActivityController<Host> controller = Robolectric.buildActivity(Host.class).setup()) {
            layout(controller);
            Host activity = controller.get();
            View decor = activity.getWindow().getDecorView();
            View header = findViewById(decor, "otakudesu_top_section");

            invoke(activity, "hideTopSection");
            ShadowLooper.idleMainLooper();
            invoke(activity, "showTopSection");
            ShadowLooper.idleMainLooper();
            layout(controller);

            assertEquals(0f, header.getTranslationY(), 0.01f);
            assertEquals(1f, header.getAlpha(), 0.01f);
            assertEquals(View.VISIBLE, header.getVisibility());
        }
    }

    @Test public void riwayatKosongTidakMenyisakanRuangLanjutkanMenonton() {
        try (ActivityController<Host> controller = Robolectric.buildActivity(Host.class).setup()) {
            layout(controller);
            View decor = controller.get().getWindow().getDecorView();
            View rail = findViewById(decor, "otakudesu_continue_rail");
            assertNotNull("wadah Lanjutkan Menonton harus tetap ada", rail);
            assertEquals("tanpa riwayat, seluruh bagian hilang", View.GONE,
                    rail.getVisibility());
            ViewGroup.LayoutParams params = rail.getLayoutParams();
            assertTrue("tidak boleh menyisakan tinggi tetap",
                    params.height == ViewGroup.LayoutParams.WRAP_CONTENT
                            || params.height == 0);
            LinearLayout parent = (LinearLayout) rail.getParent();
            assertNotNull(parent);
        }
    }

    @Test public void headerTetapSatuWadahTanpaAnakGanda() {
        try (ActivityController<Host> controller = Robolectric.buildActivity(Host.class).setup()) {
            layout(controller);
            View decor = controller.get().getWindow().getDecorView();
            LinearLayout header = (LinearLayout) findViewById(decor, "otakudesu_top_section");
            assertNotNull(header);
            // Pencarian, Lanjutkan Menonton, dan baris filter — tidak lebih.
            assertEquals(3, header.getChildCount());
            View content = findViewById(decor, "otakudesu_content");
            assertNotNull("lapisan konten ada", content);
            assertEquals("header + daftar", 2, ((ViewGroup) content).getChildCount());
        }
    }

    private static void invoke(Object target, String name) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Method method = type.getDeclaredMethod(name);
                method.setAccessible(true);
                method.invoke(target);
                return;
            } catch (NoSuchMethodException missing) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException(error);
            }
        }
        throw new IllegalStateException("metode tidak ditemukan: " + name);
    }
}