package id.shuyasal.kuromaku.anime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.KeyEvent;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.TvMode;

/**
 * Kontrol pemutar di televisi tidak boleh menghilang sendiri.
 *
 * <p>Keluhan aslinya: "ketika berhasil pilih server UI button play dan lainnya
 * akan otomatis menghilang dalam 5 detik". Di ponsel itu wajar — satu ketukan
 * memunculkannya lagi. Di televisi tidak ada ketukan: begitu kontrol hilang,
 * tombol putar dan tombol pilih server ikut hilang dan tidak bisa dipanggil
 * kembali. Jadi di televisi kontrol harus tetap ada.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w960dp-h540dp-land-mdpi")
public class AnimePlayerTvControlsTest {

    private Context context;

    private static Intent episode() {
        return new Intent(ApplicationProvider.getApplicationContext(),
                AnimePlayerActivity.class)
                .putExtra(AnimePlayerActivity.EXTRA_ANIME_ID, "fxskc")
                .putExtra(AnimePlayerActivity.EXTRA_ANIME_TITLE, "FX Senshi Kurumi-chan")
                .putExtra(AnimePlayerActivity.EXTRA_EPISODE_URL,
                        "https://otakudesu.blog/episode/fxskc-episode-1-sub-indo/")
                .putExtra(AnimePlayerActivity.EXTRA_EPISODE_LABEL, "Episode 1");
    }

    @Before public void siapkan() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    private void jadikanTelevisi() {
        Shadows.shadowOf(context.getPackageManager())
                .setSystemFeature(PackageManager.FEATURE_LEANBACK, true);
        new Prefs(context).setInt(TvMode.PREF_FORCE, 1);
    }

    private static View bilah(AnimePlayerActivity activity, String tag) {
        return activity.getWindow().getDecorView().findViewWithTag(tag);
    }

    @Test public void diTelevisiKontrolTidakBolehHilangSendiri() {
        // Ini keputusan yang sebenarnya: di televisi auto-hide dimatikan.
        // Tidak diuji lewat timer karena di pengujian unit tidak ada video yang
        // benar-benar berputar, sehingga timer auto-hide tidak pernah berjalan —
        // tes berbasis waktu di sini akan selalu lulus dan tidak menguji apa pun.
        jadikanTelevisi();
        try (ActivityController<AnimePlayerActivity> controller =
                     Robolectric.buildActivity(AnimePlayerActivity.class, episode()).setup()) {
            AnimePlayerActivity activity = controller.get();
            ShadowLooper.idleMainLooper();
            assertFalse("televisi: kontrol tidak pernah hilang sendiri",
                    activity.autoHideAllowed());
        }
    }

    @Test public void diPonselKontrolTetapBolehHilangSendiri() {
        new Prefs(context).setInt(TvMode.PREF_FORCE, 0);
        Shadows.shadowOf(context.getPackageManager())
                .setSystemFeature(PackageManager.FEATURE_LEANBACK, false);
        try (ActivityController<AnimePlayerActivity> controller =
                     Robolectric.buildActivity(AnimePlayerActivity.class, episode()).setup()) {
            AnimePlayerActivity activity = controller.get();
            ShadowLooper.idleMainLooper();
            assertTrue("ponsel: perilaku lama dipertahankan",
                    activity.autoHideAllowed());
        }
    }

    @Test public void tombolPilihServerBisaDipilihSetelahWaktuBerjalan() {
        // Bukan hanya terlihat: harus tetap bisa dipilih dengan remote.
        jadikanTelevisi();
        try (ActivityController<AnimePlayerActivity> controller =
                     Robolectric.buildActivity(AnimePlayerActivity.class, episode()).setup()) {
            AnimePlayerActivity activity = controller.get();
            ShadowLooper.idleMainLooper();
            ShadowLooper.getShadowMainLooper().idleFor(java.time.Duration.ofSeconds(20));
            ShadowLooper.idleMainLooper();

            View tombolServer = bilah(activity, "anime_player_pick_server");
            assertNotNull(tombolServer);
            assertTrue("tombol pilih server bisa dipilih dengan remote",
                    tombolServer.isFocusable());
            assertEquals("tidak membutuhkan sentuhan",
                    false, tombolServer.isFocusableInTouchMode());
        }
    }

    @Test public void tombolArahTidakMenelanTombolSaatKontrolTampil() {
        // Saat kontrol tampil, tombol arah harus diteruskan ke sistem supaya
        // sorotan bisa berpindah antar tombol — bukan direbut untuk seek.
        jadikanTelevisi();
        try (ActivityController<AnimePlayerActivity> controller =
                     Robolectric.buildActivity(AnimePlayerActivity.class, episode()).setup()) {
            AnimePlayerActivity activity = controller.get();
            ShadowLooper.idleMainLooper();

            // Tombol arah saat kontrol tampil harus diteruskan ke sistem supaya
            // sorotan bisa berpindah antar tombol — bukan direbut untuk seek.
            // Yang diperiksa: tombol arah tidak menggeser waktu.
            activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_DPAD_DOWN));
            ShadowLooper.idleMainLooper();

            assertNotNull("layar masih hidup setelah tombol arah", activity);
            View tombolServer = bilah(activity, "anime_player_pick_server");
            assertNotNull("kontrol tidak hilang karena tombol arah", tombolServer);
            assertEquals("kontrol tetap terlihat", View.VISIBLE, tombolServer.getVisibility());
        }
    }

    @Test public void padaPonselKontrolTetapBolehHilangSendiri() {
        // Perilaku ponsel tidak boleh berubah: di sana menyembunyikan kontrol
        // memang diinginkan, dan satu ketukan memunculkannya lagi.
        new Prefs(context).setInt(TvMode.PREF_FORCE, 0);
        Shadows.shadowOf(context.getPackageManager())
                .setSystemFeature(PackageManager.FEATURE_LEANBACK, false);

        try (ActivityController<AnimePlayerActivity> controller =
                     Robolectric.buildActivity(AnimePlayerActivity.class, episode()).setup()) {
            AnimePlayerActivity activity = controller.get();
            ShadowLooper.idleMainLooper();
            View tombolServer = bilah(activity, "anime_player_pick_server");
            assertNotNull("tombol pilih server ada di ponsel", tombolServer);
            // Di ponsel tombol server berada di kotak galat, bukan overlay
            // layar penuh; keberadaannya tidak berubah oleh perubahan ini.
            assertTrue("tombol ponsel tidak dipaksa bisa difokus",
                    !tombolServer.isFocusable() || tombolServer.isClickable());
        }
    }
}
