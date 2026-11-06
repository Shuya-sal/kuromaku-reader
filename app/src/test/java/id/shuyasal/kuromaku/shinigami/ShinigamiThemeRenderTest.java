package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;

/**
 * Membuktikan pergantian tema benar-benar mengubah warna yang digambar, bukan
 * hanya nilai SharedPreferences, dan tidak memicu permintaan katalog baru.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w320dp-h640dp-mdpi")
public class ShinigamiThemeRenderTest {

    static ShinigamiSource.Transport transport;
    final List<String> requests = java.util.Collections.synchronizedList(new ArrayList<>());

    public static class Screen extends ShinigamiActivity {
        @Override protected ShinigamiSource createSource() {
            return new ShinigamiSource(this, transport);
        }
    }

    @Before public void setup() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(Prefs.FILE_NAME, 0).edit().clear().commit();
        context.getSharedPreferences(ShinigamiStore.FILE_NAME, 0).edit().clear().commit();
        context.getSharedPreferences(ShinigamiConfig.FILE_NAME, 0).edit().clear().commit();
        requests.clear();
        transport = url -> {
            requests.add(url);
            if (url.contains("/genre/")) return "{\"retcode\":0,\"data\":[]}";
            return ShinigamiReaderTest.fixture("live_manga_list.json");
        };
    }

    void await(Screen host) throws Exception {
        Field field = ShinigamiActivity.class.getDeclaredField("worker");
        field.setAccessible(true);
        for (int i = 0; i < 4; i++) {
            ((ExecutorService) field.get(host)).submit(() -> { }).get(10, TimeUnit.SECONDS);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
    }

    int catalogRequests() {
        int count = 0;
        for (String url : requests) if (url.contains("/manga/list")) count++;
        return count;
    }

    static int backgroundColor(View view) {
        View target = view;
        while (target != null) {
            Drawable background = target.getBackground();
            if (background instanceof ColorDrawable) return ((ColorDrawable) background).getColor();
            if (!(target.getParent() instanceof View)) break;
            target = (View) target.getParent();
        }
        throw new AssertionError("tidak ada latar berwarna pada tampilan");
    }

    static View root(Screen host) {
        return host.getWindow().getDecorView().findViewById(android.R.id.content);
    }

    static android.widget.ListView firstList(View view) {
        if (view instanceof android.widget.ListView) return (android.widget.ListView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.widget.ListView found = firstList(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    static TextView titleView(Screen host) {
        ViewGroup content = host.getWindow().getDecorView().findViewById(android.R.id.content);
        // Beranda default Chapter Terbaru memakai kartu baris; grid memakai
        // tag lain. Keduanya diuji supaya warna teks tetap terbaca.
        TextView grid = content.findViewWithTag("shinigami_title");
        return grid != null ? grid : content.findViewWithTag("shinigami_row_title");
    }

    @Test public void memilihGelapMengubahWarnaYangDigambarTanpaMemuatUlangKatalog() throws Exception {
        try (ActivityController<Screen> controller = Robolectric.buildActivity(Screen.class).setup()) {
            Screen host = controller.get();
            await(host);

            assertEquals("awal harus terang",
                    Palette.of(Prefs.THEME_LIGHT).background, backgroundColor(root(host)));
            int before = catalogRequests();
            assertTrue("katalog harus dimuat sekali di awal", before >= 1);

            new Prefs(host).setTheme(Prefs.THEME_DARK);
            controller.recreate();
            await(controller.get());

            Screen dark = controller.get();
            assertEquals("latar gelap harus benar-benar digambar",
                    Palette.of(Prefs.THEME_DARK).background, backgroundColor(root(dark)));
            assertEquals("teks harus memakai warna tema gelap",
                    Palette.of(Prefs.THEME_DARK).text, titleView(dark).getCurrentTextColor());
            assertEquals("pergantian tema tidak boleh memuat ulang katalog: " + requests,
                    before, catalogRequests());

            new Prefs(dark).setTheme(Prefs.THEME_LIGHT);
            controller.recreate();
            await(controller.get());

            assertEquals("kembali ke terang harus benar-benar digambar",
                    Palette.of(Prefs.THEME_LIGHT).background,
                    backgroundColor(root(controller.get())));
            assertEquals(before, catalogRequests());
        }
    }

    @Test @Config(qualifiers = "w320dp-h640dp-night-mdpi")
    public void modeSystemMengikutiTemaPerangkatMalam() throws Exception {
        Context app = ApplicationProvider.getApplicationContext();
        new Prefs(app).setTheme(Prefs.THEME_SYSTEM);
        try (ActivityController<Screen> controller = Robolectric.buildActivity(Screen.class).setup()) {
            Screen host = controller.get();
            await(host);

            assertEquals("System + perangkat gelap harus menjadi gelap",
                    Palette.of(Prefs.THEME_DARK).background,
                    backgroundColor(root(controller.get())));
            assertEquals("nilai tersimpan tetap System",
                    Prefs.THEME_SYSTEM, new Prefs(controller.get()).theme());
        }
    }

    @Test @Config(qualifiers = "w320dp-h640dp-notnight-mdpi")
    public void modeSystemMengikutiTemaPerangkatSiang() throws Exception {
        Context app = ApplicationProvider.getApplicationContext();
        new Prefs(app).setTheme(Prefs.THEME_SYSTEM);
        try (ActivityController<Screen> controller = Robolectric.buildActivity(Screen.class).setup()) {
            Screen host = controller.get();
            await(host);

            assertEquals("System + perangkat terang harus menjadi terang",
                    Palette.of(Prefs.THEME_LIGHT).background,
                    backgroundColor(root(controller.get())));
        }
    }

    @Test public void dialogTemaMenampilkanPilihanSesuaiNilaiTersimpan() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        try (ActivityController<Screen> controller = Robolectric.buildActivity(Screen.class).setup()) {
            Screen host = controller.get();
            await(host);
            new Prefs(host).setTheme(Prefs.THEME_SEPIA);
            controller.recreate();
            await(controller.get());

            java.lang.reflect.Method dialog = ShinigamiActivity.class
                    .getDeclaredMethod("showThemeDialog");
            dialog.setAccessible(true);
            dialog.invoke(controller.get());

            android.app.Dialog shown = org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertNotNull("dialog tema harus tampil", shown);
            android.widget.ListView list = firstList(shown.getWindow().getDecorView());
            assertNotNull("dialog pilihan tema harus punya daftar", list);
            assertEquals("Sepia harus terpilih ketika tema tersimpan Sepia",
                    Prefs.THEME_SEPIA, list.getCheckedItemPosition());
            assertTrue("jumlah pilihan tema", list.getAdapter().getCount() >= 4);
        }
    }
}