package id.shuyasal.kuromaku.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowPackageManager;

import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.TvFocus;
import id.shuyasal.kuromaku.ui.TvMode;
import id.shuyasal.kuromaku.ui.TvRemote;

/**
 * Dukungan televisi.
 *
 * <p>Di televisi tidak ada sentuhan: satu-satunya kendali adalah tombol arah.
 * Android hanya mengirim tombol itu ke view yang bisa menerima fokus, jadi view
 * yang hanya punya {@code setOnClickListener} tidak akan pernah bisa dipilih.
 * Tes ini mengunci dua hal yang membuat aplikasi benar-benar bisa dipakai di TV:
 * perangkat dikenali sebagai televisi, dan elemen yang bisa diketuk benar-benar
 * bisa dipilih dengan remote.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w960dp-h540dp-land-mdpi")
public class TvSupportTest {

    private Context context;

    @Before public void siapkan() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    /** Tandai perangkat uji sebagai televisi Android. */
    private void jadikanTelevisi() {
        Shadows.shadowOf(context.getPackageManager())
                .setSystemFeature(PackageManager.FEATURE_LEANBACK, true);
        new Prefs(context).setInt(TvMode.PREF_FORCE, 1);
    }

    // ------------------------------------------------------------ deteksi

    @Test public void perangkatBiasaBukanTelevisi() {
        new Prefs(context).setInt(TvMode.PREF_FORCE, 0);
        assertFalse(TvMode.isTelevision(context));
        assertEquals("Ponsel atau tablet (sentuh)", TvMode.describe(context));
    }

    @Test public void modeTelevisiBisaDipaksaUntukPengujian() {
        jadikanTelevisi();
        assertTrue(TvMode.isTelevision(context));
        assertTrue(TvMode.describe(context).startsWith("Televisi"));
    }

    @Test public void layarTanpaSentuhDianggapTelevisi() {
        // Box TV murah kadang tidak melaporkan dirinya sebagai televisi.
        // Yang membedakannya dari ponsel: tidak ada sentuhan sama sekali.
        ShadowPackageManager packages = Shadows.shadowOf(context.getPackageManager());
        packages.setSystemFeature(PackageManager.FEATURE_TOUCHSCREEN, false);
        packages.setSystemFeature(PackageManager.FEATURE_FAKETOUCH, false);
        new Prefs(context).setInt(TvMode.PREF_FORCE, -1);
        assertTrue("layar lebar tanpa sentuh = televisi", TvMode.detect(context));
    }

    @Test public void televisiMemakaiKartuLebihBesar() {
        // Yang menentukan keterbacaan dari sofa adalah lebar kartu, bukan
        // jumlah kolom: layar TV jauh lebih lebar, jadi jumlah kolom yang sama
        // justru berarti kartu lebih kecil.
        int lebarTv = TvMode.widthDp(context);
        jadikanTelevisi();
        int kolomTv = TvMode.gridColumns(context);
        int kartuTv = lebarTv / kolomTv;

        new Prefs(context).setInt(TvMode.PREF_FORCE, 0);
        int lebarPonsel = 411;
        int kolomPonsel = Math.max(2, Math.min(4, lebarPonsel / 150));
        int kartuPonsel = lebarPonsel / kolomPonsel;

        assertTrue("kartu TV (" + kartuTv + " dp) lebih lebar dari ponsel ("
                + kartuPonsel + " dp)", kartuTv > kartuPonsel);
        assertTrue("TV tetap beberapa kolom", kolomTv >= 3);
    }

    // --------------------------------------------------------------- fokus

    @Test public void tombolDanKartuBisaDipilihDenganRemote() {
        LinearLayout root = new LinearLayout(context);
        Button tombol = new Button(context);
        tombol.setText("Buka");
        tombol.setOnClickListener(v -> { });
        LinearLayout kartu = new LinearLayout(context);
        kartu.setOnClickListener(v -> { });
        root.addView(tombol);
        root.addView(kartu);

        TvFocus.prepare(root, Palette.of(Prefs.THEME_DARK));

        assertTrue("tombol bisa difokus", tombol.isFocusable());
        assertTrue("kartu bisa difokus", kartu.isFocusable());
    }

    @Test public void kartuTidakMembiarkanAnaknyaMencuriFokus() {
        jadikanTelevisi();
        // Satu kartu adalah satu tujuan. Kalau teks di dalamnya ikut bisa
        // difokus, pengguna harus menekan tombol arah berkali-kali hanya untuk
        // melewati satu kartu.
        LinearLayout kartu = new LinearLayout(context);
        kartu.setOnClickListener(v -> { });
        android.widget.TextView judul = new android.widget.TextView(context);
        judul.setText("Judul panjang");
        kartu.addView(judul);

        TvFocus.focusable(kartu, Palette.of(Prefs.THEME_DARK));

        assertEquals(ViewGroup_FOCUS_BLOCK, kartu.getDescendantFocusability());
    }

    /** Nilai {@code FOCUS_BLOCK_DESCENDANTS}, ditulis agar tidak bergantung import. */
    private static final int ViewGroup_FOCUS_BLOCK = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS;

    @Test public void viewTanpaAksiTidakIkutDipilih() {
        LinearLayout root = new LinearLayout(context);
        android.widget.TextView teks = new android.widget.TextView(context);
        teks.setText("Keterangan");
        root.addView(teks);

        TvFocus.prepare(root, Palette.of(Prefs.THEME_LIGHT));

        assertFalse("teks tanpa aksi tidak perlu fokus", teks.isFocusable());
    }

    @Test public void sorotanMunculSaatDipilih() {
        jadikanTelevisi();
        LinearLayout kartu = new LinearLayout(context);
        kartu.setOnClickListener(v -> { });
        TvFocus.focusable(kartu, Palette.of(Prefs.THEME_DARK));
        assertNotNull("latar sorotan dipasang", kartu.getBackground());
        // Latar harus berubah bentuk saat state fokus aktif: itu yang membuat
        // sorotan terlihat, bukan sekadar bisa difokus.
        android.graphics.drawable.Drawable latar = kartu.getBackground();
        assertTrue("latar mengikuti state fokus", latar.isStateful());
    }

    // ------------------------------------------------------------- remote

    @Test public void tombolRemoteDiterjemahkanMenjadiAksiBaca() {
        assertEquals(TvRemote.Action.SCROLL_DOWN, TvRemote.actionOf(KeyEvent.KEYCODE_DPAD_DOWN));
        assertEquals(TvRemote.Action.SCROLL_UP, TvRemote.actionOf(KeyEvent.KEYCODE_DPAD_UP));
        assertEquals(TvRemote.Action.NEXT, TvRemote.actionOf(KeyEvent.KEYCODE_DPAD_RIGHT));
        assertEquals(TvRemote.Action.PREVIOUS, TvRemote.actionOf(KeyEvent.KEYCODE_DPAD_LEFT));
        assertEquals(TvRemote.Action.NEXT, TvRemote.actionOf(KeyEvent.KEYCODE_MEDIA_NEXT));
        assertNull("tombol tengah bukan aksi gulir", TvRemote.actionOf(KeyEvent.KEYCODE_DPAD_CENTER));
    }

    @Test public void tombolBawahMenggulirIsiHalaman() {
        android.widget.ScrollView scroll = new android.widget.ScrollView(context);
        android.widget.LinearLayout isi = new android.widget.LinearLayout(context);
        isi.setOrientation(android.widget.LinearLayout.VERTICAL);
        for (int i = 0; i < 40; i++) {
            android.widget.TextView baris = new android.widget.TextView(context);
            baris.setText("Baris " + i);
            baris.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, 200));
            isi.addView(baris);
        }
        scroll.addView(isi);
        scroll.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY));
        scroll.layout(0, 0, 1080, 600);

        assertTrue("bisa digulir", TvRemote.canScroll(scroll, true));
        assertTrue("tombol bawah menggulir", TvRemote.scroll(scroll, true));
        assertTrue("posisi bergeser", scroll.getScrollY() > 0);

        int setelah = scroll.getScrollY();
        assertTrue("tombol atas menggulir balik", TvRemote.scroll(scroll, false));
        assertTrue("posisi kembali naik", scroll.getScrollY() < setelah);
    }

    @Test public void ujungHalamanTidakMenelanTombol() {
        // Bila sudah di ujung, pemanggil harus tahu supaya tombol itu bisa
        // dipakai untuk pindah chapter, bukan tertelan diam-diam.
        android.widget.ScrollView scroll = new android.widget.ScrollView(context);
        android.widget.TextView isi = new android.widget.TextView(context);
        isi.setText("Pendek");
        scroll.addView(isi);
        scroll.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY));
        scroll.layout(0, 0, 1080, 600);

        assertFalse("tidak bisa digulir", TvRemote.canScroll(scroll, true));
        assertFalse("tidak menggulir", TvRemote.scroll(scroll, true));
    }
}
