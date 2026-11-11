package id.shuyasal.kuromaku.tv;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;

import id.shuyasal.kuromaku.MainActivity;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.SectionDrawer;
import id.shuyasal.kuromaku.ui.TvMode;

/**
 * Menu samping harus bisa dipakai dengan remote.
 *
 * <p>Menu samping dibangun sendiri — bukan menu bawaan Android — sehingga baris
 * di dalamnya tidak otomatis bisa dipilih. Di ponsel itu tidak terasa karena
 * semua baris bisa diketuk. Di televisi, menu yang tidak bisa difokus berarti
 * seluruh isi menu mustahil dijangkau, termasuk berpindah antar sumber.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w960dp-h540dp-land-mdpi")
public class TvDrawerTest {

    private Context context;
    private ActivityController<MainActivity> controller;
    private Dialog drawer;

    @Before public void siapkan() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        Shadows.shadowOf(context.getPackageManager())
                .setSystemFeature(PackageManager.FEATURE_LEANBACK, true);
        new Prefs(context).setInt(TvMode.PREF_FORCE, 1);
    }

    @After public void bersihkan() {
        if (drawer != null && drawer.isShowing()) drawer.dismiss();
        if (controller != null) controller.close();
    }

    /** Buka menu samping persis seperti layar lain membukanya. */
    private void bukaMenu() {
        controller = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity = controller.get();
        drawer = SectionDrawer.show(activity, SectionDrawer.Section.NOVEL,
                Palette.of(Prefs.THEME_DARK), new SectionDrawer.Listener() {
                    @Override public void onNovel() { }

                    @Override public void onMangaVoratoon() { }

                    @Override public void onShinigami() { }

                    @Override public void onBackup() { }

                    @Override public void onRestore() { }
                });
        ShadowLooper.idleMainLooper();
        View root = drawer.getWindow().getDecorView();
        root.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 960, 540);
        ShadowLooper.idleMainLooper();
    }

    private static void kumpulkan(View view, List<TextView> hasil) {
        if (view == null) return;
        if (view instanceof TextView) hasil.add((TextView) view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                kumpulkan(group.getChildAt(index), hasil);
            }
        }
    }

    /** Baris menu dikenali dari tag yang dipasang SectionDrawer. */
    private static List<TextView> barisMenu(View root) {
        List<TextView> semua = new ArrayList<>();
        kumpulkan(root, semua);
        List<TextView> hasil = new ArrayList<>();
        for (TextView view : semua) {
            Object tag = view.getTag();
            if (tag instanceof String && ((String) tag).startsWith("drawer_")) hasil.add(view);
        }
        return hasil;
    }

    @Test public void semuaBarisMenuBisaDipilihDenganRemote() {
        bukaMenu();
        List<TextView> baris = barisMenu(drawer.getWindow().getDecorView());
        assertFalse("menu berisi baris", baris.isEmpty());
        for (TextView item : baris) {
            assertTrue("baris " + item.getTag() + " bisa dipilih dengan remote",
                    item.isFocusable());
            assertNotNull("baris " + item.getTag() + " punya sorotan",
                    item.getBackground());
        }
    }

    @Test public void barisMenuTidakButuhModeSentuh() {
        // Kalau fokus hanya didapat dari sentuhan, remote tidak bisa masuk.
        bukaMenu();
        for (TextView item : barisMenu(drawer.getWindow().getDecorView())) {
            assertFalse("baris " + item.getTag() + " tidak butuh sentuhan",
                    item.isFocusableInTouchMode());
        }
    }

    @Test public void sorotanAwalBeradaDiDalamMenu() {
        // Tanpa titik mulai, tombol arah tidak punya tempat berangkat.
        bukaMenu();
        View fokus = drawer.getWindow().getDecorView().findFocus();
        assertNotNull("ada baris menu yang langsung tersorot", fokus);
        Object tag = fokus.getTag();
        assertTrue("yang tersorot adalah baris menu, bukan wadah kosong",
                tag instanceof String && ((String) tag).startsWith("drawer_"));
    }

    @Test public void semuaPilihanMenuUtamaBisaDipilih() {
        bukaMenu();
        List<String> tag = new ArrayList<>();
        for (TextView item : barisMenu(drawer.getWindow().getDecorView())) {
            tag.add(String.valueOf(item.getTag()));
        }
        // Lima sumber + cadangan + pulihkan: semuanya harus bisa dijangkau.
        for (String wajib : new String[]{"drawer_novel", "drawer_manga_voratoon",
                "drawer_manga_shinigami", "drawer_manga_komiku", "drawer_anime_otakudesu",
                "drawer_backup", "drawer_restore"}) {
            assertTrue("menu " + wajib + " ada dan bisa dipilih", tag.contains(wajib));
        }
    }
}
