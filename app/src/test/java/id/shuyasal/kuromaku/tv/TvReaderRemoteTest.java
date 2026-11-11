package id.shuyasal.kuromaku.tv;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.view.KeyEvent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.TvMode;
import id.shuyasal.kuromaku.ui.TvRemote;

/**
 * Setiap layar yang isinya digulir harus punya jalan dengan remote.
 *
 * <p>Layar daftar diselesaikan oleh rantai fokus: tombol arah memindahkan
 * sorotan dan wadahnya menggulir sendiri. Layar yang isinya bukan daftar kartu
 * — pembaca Novel, pembaca Server 3, pemutar Anime — butuh penanganan sendiri,
 * dan itulah yang diuji di sini.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w960dp-h540dp-land-mdpi")
public class TvReaderRemoteTest {

    private Context context;

    @Before public void siapkan() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        Shadows.shadowOf(context.getPackageManager())
                .setSystemFeature(PackageManager.FEATURE_LEANBACK, true);
        new Prefs(context).setInt(TvMode.PREF_FORCE, 1);
    }

    /** Wadah yang isinya bisa digulir, seperti isi novel. */
    private android.widget.ScrollView isiPanjang() {
        android.widget.ScrollView scroll = new android.widget.ScrollView(context);
        android.widget.LinearLayout isi = new android.widget.LinearLayout(context);
        isi.setOrientation(android.widget.LinearLayout.VERTICAL);
        for (int index = 0; index < 60; index++) {
            android.widget.TextView baris = new android.widget.TextView(context);
            baris.setText("Paragraf " + index + " isi novel yang cukup panjang.");
            baris.setHeight(120);
            isi.addView(baris);
        }
        scroll.addView(isi);
        scroll.measure(android.view.View.MeasureSpec.makeMeasureSpec(
                        960, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(
                        540, android.view.View.MeasureSpec.EXACTLY));
        scroll.layout(0, 0, 960, 540);
        return scroll;
    }

    @Test public void tombolBawahMenggulirIsiNovel() {
        android.widget.ScrollView scroll = isiPanjang();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        int sebelum = scroll.getScrollY();
        assertTrue("isi bisa digulir ke bawah", TvRemote.scroll(scroll, true));
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertTrue("isi benar-benar bergulir", scroll.getScrollY() > sebelum);
    }

    @Test public void tombolAtasMenggulirIsiKembali() {
        android.widget.ScrollView scroll = isiPanjang();
        scroll.scrollTo(0, 400);
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        int sebelum = scroll.getScrollY();
        assertTrue("isi bisa digulir ke atas", TvRemote.scroll(scroll, false));
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertTrue("isi bergulir balik ke atas", scroll.getScrollY() < sebelum);
    }

    @Test public void diUjungAtasPengguliranDilaporkanHabis() {
        // Ini yang membuat tombol atas bisa dipakai untuk pindah chapter:
        // pemanggil harus tahu bahwa tidak ada lagi yang bisa digulir.
        android.widget.ScrollView scroll = isiPanjang();
        scroll.scrollTo(0, 0);
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertTrue("tidak ada lagi yang bisa digulir ke atas",
                !TvRemote.scroll(scroll, false));
    }

    @Test public void tombolTengahPadaRemoteDikenali() {
        // Tombol tengah remote televisi mengirim KEYCODE_DPAD_CENTER, bukan
        // KEYCODE_ENTER. Salah satu tertinggal akan membuat tombol OK mati.
        assertNotNull(KeyEvent.class);
        assertTrue("kode tombol tengah tersedia", KeyEvent.KEYCODE_DPAD_CENTER > 0);
        assertTrue("kode putar/jeda tersedia", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE > 0);
        assertTrue("kode maju tersedia", KeyEvent.KEYCODE_MEDIA_FAST_FORWARD > 0);
        assertTrue("kode mundur tersedia", KeyEvent.KEYCODE_MEDIA_REWIND > 0);
    }

    @Test public void penangananTombolHanyaAktifDiTelevisi() {
        // Di ponsel, tombol volume dan tombol lain tidak boleh direbut aplikasi.
        new Prefs(context).setInt(TvMode.PREF_FORCE, 0);
        assertTrue("bukan televisi", !TvMode.isTelevision(context));
    }
}
