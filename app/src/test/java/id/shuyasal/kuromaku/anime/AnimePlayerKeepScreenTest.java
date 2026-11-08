package id.shuyasal.kuromaku.anime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.view.WindowManager;

import androidx.media3.common.Player;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * Layar tidak boleh meredup selama video ditonton.
 *
 * <p>Akar keluhan: aplikasi tidak pernah memakai {@code FLAG_KEEP_SCREEN_ON},
 * jadi Android menjalankan peredupan dan screen timeout normal begitu pengguna
 * berhenti menyentuh layar — persis saat kontrol auto-hide menghilang, sehingga
 * tampak seolah kontrol yang meredupkan video.
 *
 * <p>Yang dijaga di sini: flag menyala hanya saat video berjalan/menyiapkan
 * gambar di depan pengguna, dilepas saat jeda/keluar, dan kecerahan pilihan
 * pengguna tidak pernah ditimpa.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w360dp-h640dp-mdpi")
public class AnimePlayerKeepScreenTest {

    private static final int FLAG = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;

    private static Intent intent() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                AnimePlayerActivity.class);
        intent.putExtra("anime_id", "fxskc");
        intent.putExtra("title", "FX Senshi Kurumi-chan");
        intent.putExtra("episode_url",
                "https://otakudesu.blog/episode/fxskc-episode-1-sub-indo/");
        intent.putExtra("episode_label", "Episode 1");
        return intent;
    }

    private static boolean flagOn(AnimePlayerActivity activity) {
        return (activity.getWindow().getAttributes().flags & FLAG) != 0;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void invoke(Object target, String name) throws Exception {
        java.lang.reflect.Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }

    @Test public void keputusanMenahanLayarMengikutiStatusPemutaran() {
        // Tanpa player, atau saat Activity tidak terlihat: tidak menahan layar.
        assertFalse(AnimePlayerActivity.keepScreenOnNeeded(false, null));
    }

    @Test public void flagMenyalaSaatVideoBerjalanDanLepasSaatKeluar() throws Exception {
        try (ActivityController<AnimePlayerActivity> controller =
                     Robolectric.buildActivity(AnimePlayerActivity.class, intent())
                             .setup().visible()) {
            AnimePlayerActivity activity = controller.get();
            invoke(activity, "ensurePlayer");
            Player player = (Player) field(activity, "player");
            assertNotNull("pemutar harus sudah dibuat", player);

            // Belum memutar apa pun: layar mengikuti pengaturan sistem.
            assertFalse("tidak menahan layar tanpa pemutaran", flagOn(activity));

            set(activity, "playerForeground", true);
            player.setPlayWhenReady(true);
            invoke(activity, "applyKeepScreenOn");
            assertTrue("layar ditahan menyala selama video berjalan", flagOn(activity));

            // Kecerahan pengguna tidak boleh ditimpa aplikasi.
            assertEquals("kecerahan mengikuti sistem",
                    WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE,
                    activity.getWindow().getAttributes().screenBrightness, 0.001f);

            // Keluar dari layar: flag dilepas, tidak bocor ke halaman lain.
            set(activity, "playerForeground", false);
            invoke(activity, "applyKeepScreenOn");
            assertFalse("flag dilepas saat Activity tidak terlihat", flagOn(activity));
        }
    }

    @Test public void jedaMelepasFlag() throws Exception {
        try (ActivityController<AnimePlayerActivity> controller =
                     Robolectric.buildActivity(AnimePlayerActivity.class, intent())
                             .setup().visible()) {
            AnimePlayerActivity activity = controller.get();
            invoke(activity, "ensurePlayer");
            Player player = (Player) field(activity, "player");
            set(activity, "playerForeground", true);
            player.setPlayWhenReady(true);
            invoke(activity, "applyKeepScreenOn");
            assertTrue(flagOn(activity));

            player.setPlayWhenReady(false);
            invoke(activity, "applyKeepScreenOn");
            assertFalse("saat dijeda layar tidak perlu ditahan", flagOn(activity));
        }
    }

    @Test public void kontrolTersembunyiTidakMenyentuhKecerahan() throws Exception {
        try (ActivityController<AnimePlayerActivity> controller =
                     Robolectric.buildActivity(AnimePlayerActivity.class, intent())
                             .setup().visible()) {
            AnimePlayerActivity activity = controller.get();
            invoke(activity, "ensurePlayer");
            Player player = (Player) field(activity, "player");
            set(activity, "playerForeground", true);
            player.setPlayWhenReady(true);
            invoke(activity, "applyKeepScreenOn");

            float before = activity.getWindow().getAttributes().screenBrightness;
            invoke(activity, "hideControlsNow");
            org.robolectric.shadows.ShadowLooper.idleMainLooper();

            assertEquals("menyembunyikan kontrol tidak mengubah kecerahan",
                    before, activity.getWindow().getAttributes().screenBrightness, 0.001f);
            assertTrue("flag tetap menyala walau kontrol hilang", flagOn(activity));
        }
    }

    private static Object field(Object target, String name) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}