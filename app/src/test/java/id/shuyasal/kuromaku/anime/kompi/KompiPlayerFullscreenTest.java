package id.shuyasal.kuromaku.anime.kompi;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

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
 * Tombol layar penuh pada pemutar Anime Server 2.
 *
 * <p>Pemutar Anime Streaming sudah punya; pemutar ini belum, sehingga status
 * bar dan bilah navigasi selalu memakan sebagian gambar. Yang diuji di sini
 * adalah tombol itu sendiri — diketuk lewat view yang sama yang dipakai
 * pembaca, bukan dengan memanggil metode activity secara langsung.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w411dp-h891dp-xhdpi")
public class KompiPlayerFullscreenTest {

    private Context context = ApplicationProvider.getApplicationContext();
    private ActivityController<KompiPlayerActivity> controller;

    @Before public void siapkan() {
        context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    /**
     * Layar pemutar dibuka dengan slug dan judul; daftar server diambil dari
     * jaringan, jadi tidak ada yang perlu dimuat untuk menguji tombolnya.
     */
    private KompiPlayerActivity buka() {
        Intent intent = new Intent(context, KompiPlayerActivity.class)
                .putExtra(KompiPlayerActivity.EXTRA_SLUG, "uji-slug")
                .putExtra(KompiPlayerActivity.EXTRA_TITLE, "Judul Uji")
                .putExtra(KompiPlayerActivity.EXTRA_EPISODE, "Episode 1");
        controller = Robolectric.buildActivity(KompiPlayerActivity.class, intent).setup();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        return controller.get();
    }

    private Button tombol(KompiPlayerActivity host, String teks) {
        View root = ((ViewGroup) host.getWindow().getDecorView()).getChildAt(0);
        return cari(root, teks);
    }

    private Button cari(View view, String teks) {
        if (view instanceof Button) {
            Button tombol = (Button) view;
            if (teks.contentEquals(tombol.getText())) return tombol;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button found = cari(group.getChildAt(i), teks);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test public void pemutarPunyaTombolLayarPenuh() {
        KompiPlayerActivity host = buka();
        assertNotNull("tombol Layar penuh harus ada di pemutar",
                tombol(host, "Layar penuh"));
        host.finish();
    }

    @Test public void mengetukTombolMasukDanKeluarLayarPenuh() {
        KompiPlayerActivity host = buka();
        assertFalse("awalnya bukan layar penuh", host.layarPenuhAktif());

        tombol(host, "Layar penuh").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue("setelah diketuk harus aktif", host.layarPenuhAktif());
        assertNotNull("label tombol berganti", tombol(host, "Keluar penuh"));

        tombol(host, "Keluar penuh").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertFalse("harus keluar dari layar penuh", host.layarPenuhAktif());
        assertNotNull("label kembali", tombol(host, "Layar penuh"));
        host.finish();
    }

    @Test public void tombolKembaliMenutupLayarPenuhLebihDulu() {
        KompiPlayerActivity host = buka();
        tombol(host, "Layar penuh").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(host.layarPenuhAktif());

        host.onKeyDown(android.view.KeyEvent.KEYCODE_BACK, new android.view.KeyEvent(
                android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK));
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertFalse("kembali pertama hanya menutup layar penuh", host.layarPenuhAktif());
        assertFalse("pemutar tidak boleh langsung ditutup", host.isFinishing());
        host.finish();
    }
}
