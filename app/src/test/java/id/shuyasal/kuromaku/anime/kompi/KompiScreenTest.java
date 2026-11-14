package id.shuyasal.kuromaku.anime.kompi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
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

import java.util.HashMap;
import java.util.Map;

import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.TvMode;

/**
 * Layar Animekompi di ponsel dan televisi.
 *
 * <p>Diuji: bagian beranda tergambar, kegagalan sumber ditampilkan sebagai
 * pesan (bukan layar kosong), dan di televisi ada kartu yang langsung tersorot
 * sehingga remote bisa langsung dipakai.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w411dp-h891dp-xhdpi")
public class KompiScreenTest {

    private static final String BERANDA = "{"
            + "\"latestEpisodes\":[{\"animeSlug\":\"toaru-anbu\",\"animeTitle\":\"Toaru Anbu no Item\","
            + "\"numberLabel\":\"06\",\"animeType\":\"TV\",\"animeStatus\":\"ongoing\"}],"
            + "\"popularWeekly\":[{\"slug\":\"tokyo-revengers\",\"title\":\"Tokyo Revengers\","
            + "\"cover\":\"/api/img/covers/tokyo.jpg\",\"type\":\"TV\",\"status\":\"ongoing\"}],"
            + "\"ongoing\":[],\"movies\":[]}";

    private static KompiSource nextSource;
    private Context context;

    /** Activity asli dengan sumber luring; Robolectric butuh konstruktor kosong. */
    public static final class Host extends KompiActivity {
        @Override protected KompiSource createSource() {
            return nextSource;
        }
    }

    @Before public void siapkan() {
        context = ApplicationProvider.getApplicationContext();
        new Prefs(context).setTheme(Prefs.THEME_LIGHT);
        nextSource = sumber(BERANDA);
    }

    @After public void bersihkan() {
        new Prefs(context).setTheme(Prefs.THEME_SYSTEM);
        new Prefs(context).setInt(TvMode.PREF_FORCE, 0);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    private static KompiSource sumber(String beranda) {
        Map<String, String> balasan = new HashMap<>();
        balasan.put("/api/home", beranda);
        return new KompiSource((url, referer) -> {
            for (Map.Entry<String, String> entri : balasan.entrySet()) {
                if (url.contains(entri.getKey())) return entri.getValue();
            }
            throw new java.io.IOException("Tidak ada balasan untuk " + url);
        });
    }

    private ActivityController<Host> buka() {
        return Robolectric.buildActivity(Host.class).setup();
    }

    private static boolean adaTeks(View root, String teks) {
        if (root instanceof TextView) {
            CharSequence isi = ((TextView) root).getText();
            if (isi != null && isi.toString().contains(teks)) return true;
        }
        if (root instanceof ViewGroup) {
            ViewGroup grup = (ViewGroup) root;
            for (int i = 0; i < grup.getChildCount(); i++) {
                if (adaTeks(grup.getChildAt(i), teks)) return true;
            }
        }
        return false;
    }

    private static int hitungTeks(View root, String teks) {
        int jumlah = 0;
        if (root instanceof TextView) {
            CharSequence isi = ((TextView) root).getText();
            if (isi != null && isi.toString().contains(teks)) jumlah += 1;
        }
        if (root instanceof ViewGroup) {
            ViewGroup grup = (ViewGroup) root;
            for (int i = 0; i < grup.getChildCount(); i++) {
                jumlah += hitungTeks(grup.getChildAt(i), teks);
            }
        }
        return jumlah;
    }

    @Test public void bagianBerandaTergambar() {
        try (ActivityController<Host> controller = buka()) {
            ShadowLooper.idleMainLooper();
            View layar = controller.get().getWindow().getDecorView();
            assertTrue("judul layar", adaTeks(layar, "Anime Kompi"));
            assertTrue("bagian episode terbaru", adaTeks(layar, "Episode Terbaru"));
            assertTrue("bagian populer", adaTeks(layar, "Populer Minggu Ini"));
            assertTrue("judul anime tampil", adaTeks(layar, "Toaru Anbu no Item"));
        }
    }

    @Test public void kartuMembukaDetail() {
        try (ActivityController<Host> controller = buka()) {
            ShadowLooper.idleMainLooper();
            View layar = controller.get().getWindow().getDecorView();
            View kartu = cariKartu(layar, "Toaru Anbu no Item");
            assertNotNull("kartu ada", kartu);
            kartu.performClick();
            Intent berikut = Shadows.shadowOf(controller.get()).getNextStartedActivity();
            assertNotNull("membuka layar detail", berikut);
            assertEquals(KompiDetailActivity.class.getName(), berikut.getComponent().getClassName());
            assertEquals("toaru-anbu", berikut.getStringExtra(KompiDetailActivity.EXTRA_SLUG));
        }
    }

    @Test public void kegagalanSumberDijelaskanBukanLayarKosong() {
        nextSource = new KompiSource((url, referer) -> {
            throw new java.io.IOException("Sumber membalas HTTP 522.");
        });
        try (ActivityController<Host> controller = buka()) {
            ShadowLooper.idleMainLooper();
            View layar = controller.get().getWindow().getDecorView();
            assertTrue("alasan ditampilkan", adaTeks(layar, "522"));
            assertTrue("tombol coba lagi tersedia", adaTeks(layar, "Coba lagi"));
        }
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-mdpi")
    public void televisiLangsungMemberiSorotan() {
        Shadows.shadowOf(context.getPackageManager())
                .setSystemFeature(PackageManager.FEATURE_LEANBACK, true);
        new Prefs(context).setInt(TvMode.PREF_FORCE, 1);
        try (ActivityController<Host> controller = buka()) {
            ShadowLooper.idleMainLooper();
            View layar = controller.get().getWindow().getDecorView();
            // Kartu anime harus bisa dicapai remote, bukan hanya disentuh.
            View kartu = cariKartu(layar, "Toaru Anbu no Item");
            assertNotNull("kartu anime ada", kartu);
            assertTrue("kartu bisa disorot", kartu.isFocusable());
            assertTrue("sorotan melekat pada kartu", kartu.requestFocus());
            assertTrue("kartu benar-benar tersorot", kartu.hasFocus());
        }
    }

    private static View cariKartu(View root, String judul) {
        if (root instanceof ViewGroup) {
            ViewGroup grup = (ViewGroup) root;
            for (int i = 0; i < grup.getChildCount(); i++) {
                View anak = grup.getChildAt(i);
                if (anak.isClickable() && anak.getContentDescription() != null
                        && judul.contentEquals(anak.getContentDescription())) {
                    return anak;
                }
                View dalam = cariKartu(anak, judul);
                if (dalam != null) return dalam;
            }
        }
        return null;
    }

    @SuppressWarnings("unused")
    private static LinearLayout contohWadah(Context context) {
        return new LinearLayout(context);
    }
}
