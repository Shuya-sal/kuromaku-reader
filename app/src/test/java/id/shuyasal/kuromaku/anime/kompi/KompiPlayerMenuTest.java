package id.shuyasal.kuromaku.anime.kompi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
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

import id.shuyasal.kuromaku.ui.Prefs;

/**
 * Menu titik tiga pada pemutar Anime Server 2, padding status bar halaman
 * detail, dan rotasi layar.
 *
 * <p>Keluhan aslinya: tombol Server, layar penuh, dan rotasi diminta pindah ke
 * titik tiga di kanan atas; daftar server muncul terus dan tidak bisa ditutup;
 * dan halaman informasi masih terlalu tinggi sehingga menempel ke status bar.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w411dp-h891dp-xhdpi")
public class KompiPlayerMenuTest {

    private final Context context = ApplicationProvider.getApplicationContext();
    private ActivityController<KompiPlayerActivity> controller;

    @Before public void siapkan() {
        context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    private KompiPlayerActivity buka() {
        Intent intent = new Intent(context, KompiPlayerActivity.class)
                .putExtra(KompiPlayerActivity.EXTRA_SLUG, "uji-slug")
                .putExtra(KompiPlayerActivity.EXTRA_TITLE, "Judul Uji")
                .putExtra(KompiPlayerActivity.EXTRA_EPISODE, "Episode 1");
        controller = Robolectric.buildActivity(KompiPlayerActivity.class, intent).setup();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        return controller.get();
    }

    private View akar(KompiPlayerActivity host) {
        ViewGroup content = host.findViewById(android.R.id.content);
        return content.getChildAt(0);
    }

    private Button tag(KompiPlayerActivity host, String tag) {
        return akar(host).findViewWithTag(tag);
    }

    @Test public void titikTigaBerisiServerLayarPenuhDanRotasi() {
        KompiPlayerActivity host = buka();
        assertNotNull("tombol titik tiga ada", tag(host, "tombol_menu"));
        assertNotNull("Server di menu", tag(host, "tombol_server"));
        assertNotNull("Layar penuh di menu", tag(host, "tombol_layar_penuh"));
        assertNotNull("Rotasi di menu", tag(host, "tombol_rotasi"));
        assertEquals("⋮", tag(host, "tombol_menu").getText().toString());
        host.finish();
    }

    @Test public void menuTertutupSaatPemutarDibuka() {
        KompiPlayerActivity host = buka();
        View panel = akar(host).findViewWithTag("panel_menu");
        assertNotNull(panel);
        assertEquals("menu titik tiga mulai tertutup", View.GONE, panel.getVisibility());
        host.finish();
    }

    @Test public void titikTigaMembukaDanMenutupMenu() {
        KompiPlayerActivity host = buka();
        View panel = akar(host).findViewWithTag("panel_menu");

        tag(host, "tombol_menu").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(View.VISIBLE, panel.getVisibility());

        tag(host, "tombol_menu").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("ketukan kedua menutup menu", View.GONE, panel.getVisibility());
        host.finish();
    }

    /**
     * Keluhan: "list servernya muncul terus tidak bisa ditutup". Tombol Server
     * harus bekerja sebagai saklar, bukan hanya membuka.
     */
    @Test public void daftarServerBisaDitutupDenganTombolYangSama() {
        KompiPlayerActivity host = buka();
        LinearLayout panelServer = panelServer(host);
        assertNotNull(panelServer);
        assertEquals(View.GONE, panelServer.getVisibility());

        tag(host, "tombol_server").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("ketukan pertama membuka", View.VISIBLE, panelServer.getVisibility());

        tag(host, "tombol_server").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("ketukan kedua menutup", View.GONE, panelServer.getVisibility());
        host.finish();
    }

    @Test public void tombolRotasiMembalikOrientasi() {
        KompiPlayerActivity host = buka();
        tag(host, "tombol_rotasi").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        int pertama = ShadowOrientasi(host);
        assertTrue("orientasi berubah menjadi landscape",
                pertama == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        || pertama == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        tag(host, "tombol_rotasi").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        int kedua = ShadowOrientasi(host);
        assertTrue("ketukan kedua kembali potret",
                kedua == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        || kedua == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        host.finish();
    }

    private int ShadowOrientasi(KompiPlayerActivity host) {
        return host.getRequestedOrientation();
    }

    /** Panel server punya tag sendiri di UI. */
    private LinearLayout panelServer(KompiPlayerActivity host) {
        return akar(host).findViewWithTag("panel_server");
    }

    // ------------------------------------------------- halaman detail: header

    @Test public void detailMemakaiPaddingStatusBar() {
        Intent intent = new Intent(context, KompiDetailActivity.class)
                .putExtra(KompiDetailActivity.EXTRA_SLUG, "uji-slug")
                .putExtra(KompiDetailActivity.EXTRA_TITLE, "Judul Uji");
        ActivityController<KompiDetailActivity> detail =
                Robolectric.buildActivity(KompiDetailActivity.class, intent).setup();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        KompiDetailActivity host = detail.get();

        ViewGroup content = host.findViewById(android.R.id.content);
        View root = content.getChildAt(0);
        assertNotNull("isi layar detail", root);

        androidx.core.view.WindowInsetsCompat inset =
                new androidx.core.view.WindowInsetsCompat.Builder()
                        .setInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars(),
                                androidx.core.graphics.Insets.of(0, 72, 0, 48))
                        .build();
        androidx.core.view.ViewCompat.dispatchApplyWindowInsets(root, inset);

        assertTrue("padding atas harus memuat tinggi status bar, dapat "
                        + root.getPaddingTop(),
                root.getPaddingTop() >= 72);
        // Judul tidak boleh bertumpuk dengan waktu di status bar.
        TextView judul = cari(host.getWindow().getDecorView(), "Judul Uji");
        assertNotNull("judul halaman ada", judul);
        host.finish();
    }

    private TextView cari(View view, String teks) {
        if (view instanceof TextView) {
            CharSequence isi = ((TextView) view).getText();
            if (isi != null && isi.toString().contains(teks)) return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = cari(group.getChildAt(i), teks);
                if (found != null) return found;
            }
        }
        return null;
    }
}
