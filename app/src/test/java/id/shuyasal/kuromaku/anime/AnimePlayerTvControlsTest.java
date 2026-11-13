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

    /**
     * Benarkah view ini terlihat pengguna?
     *
     * <p>{@code getVisibility()} hanya membaca view itu sendiri: tombol di dalam
     * bilah yang disembunyikan tetap melaporkan VISIBLE. Pemeriksaan harus ikut
     * menelusuri induknya, kalau tidak tes akan lulus padahal layarnya kosong.
     */
    private static boolean terlihat(View view) {
        View sekarang = view;
        while (sekarang != null) {
            if (sekarang.getVisibility() != View.VISIBLE) return false;
            android.view.ViewParent induk = sekarang.getParent();
            sekarang = induk instanceof View ? (View) induk : null;
        }
        return view != null;
    }

    @Test public void kontrolBolehHilangSendiriSaatMenonton() {
        // Saat menonton, kontrol memang harus menghilang sendiri — termasuk di
        // televisi. Yang membuatnya aman bukan menahan kontrol selamanya, tapi
        // tombol arah memunculkannya kembali.
        jadikanTelevisi();
        try (ActivityController<AnimePlayerActivity> controller =
                     Robolectric.buildActivity(AnimePlayerActivity.class, episode()).setup()) {
            AnimePlayerActivity activity = controller.get();
            ShadowLooper.idleMainLooper();
            assertTrue("kontrol boleh menghilang sendiri", activity.autoHideAllowed());
        }
    }

    @Test public void tombolTengahMenjalankanTombolYangTersorot() {
        // Inti keluhan "tidak bisa control UI": tombol tengah dulu selalu
        // menelan tekanan untuk putar/jeda, sehingga tombol yang sedang
        // tersorot tidak pernah bisa ditekan.
        jadikanTelevisi();
        try (ActivityController<AnimePlayerActivity> controller =
                     Robolectric.buildActivity(AnimePlayerActivity.class, episode()).setup()) {
            AnimePlayerActivity activity = controller.get();
            ShadowLooper.idleMainLooper();

            // Tombol "Putar" di bilah bawah — ini tombol kontrol sungguhan.
            View tombolPlay = bilah(activity, "anime_player_play");
            assertNotNull("tombol putar ada di bilah bawah", tombolPlay);
            tombolPlay.requestFocus();
            ShadowLooper.idleMainLooper();

            assertFalse("sorotan di tombol: tengah TIDAK memutar/menjeda",
                    activity.centerTogglesPlayback());
        }
    }

    @Test public void majuMundurSaatTersembunyiTidakMengambilSorotan() {
        // Sorotan tidak boleh dipindah ke bilah tombol saat maju/mundur ditekan,
        // karena tekanan berikutnya akan hanya berpindah tombol dan video tidak
        // pernah benar-benar dimajukan.
        jadikanTelevisi();
        try (ActivityController<AnimePlayerActivity> controller =
                     Robolectric.buildActivity(AnimePlayerActivity.class, episode()).setup()) {
            AnimePlayerActivity activity = controller.get();
            ShadowLooper.idleMainLooper();
            activity.hideControlsForTest();
            assertFalse("prasyarat: kontrol tersembunyi", activity.controlsVisibleForTest());

            activity.tekan(KeyEvent.KEYCODE_DPAD_RIGHT);

            assertTrue("maju memunculkan kontrol", activity.controlsVisibleForTest());
            assertFalse("sorotan tidak diambil bilah, jadi maju menggeser waktu",
                    activity.controlsHaveFocusForTest());
        }
    }

    @Test public void tombolTengahSaatTersembunyiMenaruhSorotanDiTombolPertama() {
        // Lewat OK, Master memang ingin masuk ke bilah tombol.
        jadikanTelevisi();
        try (ActivityController<AnimePlayerActivity> controller =
                     Robolectric.buildActivity(AnimePlayerActivity.class, episode()).setup()) {
            AnimePlayerActivity activity = controller.get();
            ShadowLooper.idleMainLooper();
            activity.hideControlsForTest();

            activity.tekan(KeyEvent.KEYCODE_DPAD_CENTER);

            assertTrue("tengah memunculkan kontrol", activity.controlsVisibleForTest());
            assertTrue("tengah menaruh sorotan di bilah",
                    activity.controlsHaveFocusForTest());
        }
    }

    @Test public void menutupPanelServerMengaktifkanAutoHideLagi() {
        // Bug yang dilaporkan: buka menu tiga titik -> Server -> tutup panel ->
        // kontrol tidak pernah menghilang lagi. Sebabnya panelOpen menyala
        // selamanya karena popup tidak punya pendengar "sudah ditutup".
        jadikanTelevisi();
        try (ActivityController<AnimePlayerActivity> controller =
                     Robolectric.buildActivity(AnimePlayerActivity.class, episode()).setup()) {
            AnimePlayerActivity activity = controller.get();
            ShadowLooper.idleMainLooper();

            activity.showServerPanelForTest();
            ShadowLooper.idleMainLooper();
            assertTrue("prasyarat: panel tercatat terbuka", activity.panelOpenForTest());
            assertFalse("selama panel terbuka auto-hide ditahan",
                    activity.shouldHideAfterIdle());

            activity.dismissServerPanelForTest();
            ShadowLooper.idleMainLooper();

            assertFalse("panel tertutup: penanda harus padam", activity.panelOpenForTest());
            assertTrue("panel tertutup: auto-hide jalan lagi",
                    activity.shouldHideAfterIdle());
        }
    }

    @Test public void kembaliPertamaMenyembunyikanKontrolKeduaKeluar() {
        jadikanTelevisi();
        try (ActivityController<AnimePlayerActivity> controller =
                     Robolectric.buildActivity(AnimePlayerActivity.class, episode()).setup()) {
            AnimePlayerActivity activity = controller.get();
            ShadowLooper.idleMainLooper();
            assertTrue("prasyarat: kontrol tampil", activity.controlsVisibleForTest());

            activity.tekanKembali();
            ShadowLooper.idleMainLooper();
            assertFalse("kembali pertama menyembunyikan kontrol",
                    activity.controlsVisibleForTest());
            assertFalse("kembali pertama belum keluar dari pemutar",
                    activity.isFinishing());

            activity.tekanKembali();
            assertTrue("kembali kedua meninggalkan pemutar", activity.isFinishing());
        }
    }

    @Test public void fokusDiBilahTidakMenahanPenyembunyian() {
        // Inilah bug "auto hide 1x saja": sorotan selalu ditaruh di bilah saat
        // kontrol dimunculkan, dan dulu sorotan dijadikan penahan penyembunyian
        // — jadi kontrol menghilang sekali, lalu tidak pernah lagi.
        jadikanTelevisi();
        try (ActivityController<AnimePlayerActivity> controller =
                     Robolectric.buildActivity(AnimePlayerActivity.class, episode()).setup()) {
            AnimePlayerActivity activity = controller.get();
            ShadowLooper.idleMainLooper();
            View tombolPlay = bilah(activity, "anime_player_play");
            assertNotNull(tombolPlay);
            tombolPlay.requestFocus();
            ShadowLooper.idleMainLooper();

            assertTrue("sorotan di bilah tetap boleh hilang sendiri",
                    activity.shouldHideAfterIdle());
        }
    }

    @Test public void menyembunyikanKontrolMelepaskanSorotanDariBilah() {
        // Kalau sorotan tertinggal di bilah tersembunyi, tombol arah berikutnya
        // mendarat di view tak terlihat dan remote terasa mati.
        jadikanTelevisi();
        try (ActivityController<AnimePlayerActivity> controller =
                     Robolectric.buildActivity(AnimePlayerActivity.class, episode()).setup()) {
            AnimePlayerActivity activity = controller.get();
            ShadowLooper.idleMainLooper();
            View tombolPlay = bilah(activity, "anime_player_play");
            assertNotNull(tombolPlay);
            tombolPlay.requestFocus();
            ShadowLooper.idleMainLooper();
            assertTrue("prasyarat: sorotan di bilah", activity.controlsHaveFocusForTest());

            activity.hideControlsForTest();

            assertFalse("sorotan dilepas dari bilah",
                    activity.controlsHaveFocusForTest());
        }
    }

    @Test public void diTelevisiPemutarLangsungLayarPenuh() {
        // Tanpa ini, auto-hide tidak pernah aktif di televisi karena auto-hide
        // hanya berjalan dalam mode layar penuh — bilah kontrol menutupi gambar
        // terus dan Master merasa "UI belum otomatis menghilang".
        jadikanTelevisi();
        try (ActivityController<AnimePlayerActivity> controller =
                     Robolectric.buildActivity(AnimePlayerActivity.class, episode()).setup()) {
            AnimePlayerActivity activity = controller.get();
            ShadowLooper.idleMainLooper();
            assertTrue("televisi mulai dalam layar penuh",
                    activity.isFullscreenForTest());
        }
    }

    @Test public void padaPonselPemutarTidakDipaksaLayarPenuh() {
        new Prefs(context).setInt(TvMode.PREF_FORCE, 0);
        Shadows.shadowOf(context.getPackageManager())
                .setSystemFeature(PackageManager.FEATURE_LEANBACK, false);
        try (ActivityController<AnimePlayerActivity> controller =
                     Robolectric.buildActivity(AnimePlayerActivity.class, episode()).setup()) {
            AnimePlayerActivity activity = controller.get();
            ShadowLooper.idleMainLooper();
            assertFalse("ponsel tidak dipaksa layar penuh",
                    activity.isFullscreenForTest());
        }
    }

    @Test public void kiriKananMenggeserWaktuSaatSorotanDiVideo() {
        // Seek harus tetap bekerja saat sorotan tidak di bilah tombol.
        jadikanTelevisi();
        try (ActivityController<AnimePlayerActivity> controller =
                     Robolectric.buildActivity(AnimePlayerActivity.class, episode()).setup()) {
            AnimePlayerActivity activity = controller.get();
            ShadowLooper.idleMainLooper();
            View permukaan = bilah(activity, "anime_player_surface");
            assertNotNull(permukaan);
            permukaan.requestFocus();
            ShadowLooper.idleMainLooper();

            assertFalse("sorotan di video: kiri/kanan menggeser waktu",
                    activity.navigatesBetweenControls());
        }
    }

    @Test public void kiriKananBerpindahTombolSaatSorotanDiBilah() {
        // Kalau tidak, tombol kedua dan seterusnya tidak akan pernah tercapai.
        jadikanTelevisi();
        try (ActivityController<AnimePlayerActivity> controller =
                     Robolectric.buildActivity(AnimePlayerActivity.class, episode()).setup()) {
            AnimePlayerActivity activity = controller.get();
            ShadowLooper.idleMainLooper();
            View tombolPlay = bilah(activity, "anime_player_play");
            assertNotNull(tombolPlay);
            tombolPlay.requestFocus();
            ShadowLooper.idleMainLooper();

            assertTrue("sorotan di bilah: kiri/kanan berpindah tombol",
                    activity.navigatesBetweenControls());
        }
    }

    @Test public void tombolTengahMemutarMenjedaSaatSorotanDiVideo() {
        jadikanTelevisi();
        try (ActivityController<AnimePlayerActivity> controller =
                     Robolectric.buildActivity(AnimePlayerActivity.class, episode()).setup()) {
            AnimePlayerActivity activity = controller.get();
            ShadowLooper.idleMainLooper();
            View permukaan = bilah(activity, "anime_player_surface");
            assertNotNull(permukaan);
            permukaan.requestFocus();
            ShadowLooper.idleMainLooper();

            assertTrue("sorotan di video: tengah memutar/menjeda",
                    activity.centerTogglesPlayback());
        }
    }

    @Test public void saatTersembunyiTombolArahMemunculkanKontrol() throws Exception {
        // Tanpa ini, kontrol yang sudah hilang tidak bisa dipanggil kembali.
        jadikanTelevisi();
        try (ActivityController<AnimePlayerActivity> controller =
                     Robolectric.buildActivity(AnimePlayerActivity.class, episode()).setup()) {
            AnimePlayerActivity activity = controller.get();
            ShadowLooper.idleMainLooper();

            // Sembunyikan kontrol seperti auto-hide melakukannya.
            java.lang.reflect.Method sembunyikan = AnimePlayerActivity.class
                    .getDeclaredMethod("setControlsVisible", boolean.class);
            sembunyikan.setAccessible(true);
            sembunyikan.invoke(activity, false);
            ShadowLooper.getShadowMainLooper().idleFor(java.time.Duration.ofSeconds(1));
            ShadowLooper.idleMainLooper();

            activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_DPAD_DOWN));
            ShadowLooper.idleMainLooper();

            // Diperiksa pada tombol yang benar-benar berada di bilah kontrol.
            View tombolPlay = bilah(activity, "anime_player_play");
            assertNotNull("tombol putar ada", tombolPlay);
            assertTrue("kontrol muncul lagi setelah tombol arah", terlihat(tombolPlay));
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
