package id.shuyasal.kuromaku.anime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * Pesan "tidak bisa diputar" tidak boleh menutupi video yang sudah berjalan.
 *
 * <p>Keluhan pengguna: memilih server menampilkan layar kegagalan, padahal
 * gambarnya sudah berjalan; menekan "Coba Lagi" baru terasa normal. Penyebabnya
 * kotak pesan tidak pernah disembunyikan ketika pemutar mencapai STATE_READY.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w360dp-h640dp-mdpi")
public class AnimePlayerErrorOverlayTest {

    private static Intent intent() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                AnimePlayerActivity.class);
        intent.putExtra("anime_id", "mgexpl");
        intent.putExtra("title", "Magical★Explorer");
        intent.putExtra("episode_url",
                "https://otakudesu.blog/episode/mgexpl-episode-1-sub-indo/");
        intent.putExtra("episode_label", "Episode 1");
        return intent;
    }

    private static View errorBox(AnimePlayerActivity activity) {
        return findViewById(activity.getWindow().getDecorView(), "anime_player_error_box");
    }

    private static View findViewById(View root, String tag) {
        if (tag.equals(root.getTag())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findViewById(group.getChildAt(i), tag);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void invoke(Object target, String name, Object... args) throws Exception {
        for (java.lang.reflect.Method method : target.getClass().getDeclaredMethods()) {
            if (!method.getName().equals(name)) continue;
            if (method.getParameterTypes().length != args.length) continue;
            method.setAccessible(true);
            method.invoke(target, args);
            return;
        }
        throw new IllegalStateException("metode tidak ditemukan: " + name);
    }

    @Test public void pesanGagalHilangBegituVideoSiap() throws Exception {
        try (ActivityController<AnimePlayerActivity> controller =
                     Robolectric.buildActivity(AnimePlayerActivity.class, intent())
                             .setup().visible()) {
            AnimePlayerActivity activity = controller.get();
            invoke(activity, "showError", "Server vidhide 720p tidak dapat diputar aplikasi.");

            View box = errorBox(activity);
            assertNotNull("kotak pesan harus ada", box);
            assertEquals("pesan gagal tampil", View.VISIBLE, box.getVisibility());

            // Pemutar mencapai STATE_READY: gambar sudah berjalan.
            activity.onVideoReady();
            assertEquals("pesan gagal tidak boleh menutupi video",
                    View.GONE, box.getVisibility());
        }
    }

    @Test public void pesanTetapTampilBilaBelumAdaVideo() throws Exception {
        try (ActivityController<AnimePlayerActivity> controller =
                     Robolectric.buildActivity(AnimePlayerActivity.class, intent())
                             .setup().visible()) {
            AnimePlayerActivity activity = controller.get();
            invoke(activity, "showUnavailable", "Server ini hanya menyediakan pemutar penyedia.");
            View box = errorBox(activity);
            assertNotNull(box);
            assertEquals("tanpa gambar berjalan, penjelasan harus terlihat",
                    View.VISIBLE, box.getVisibility());
        }
    }

    @Test public void videoSiapBerulangTetapBersih() throws Exception {
        try (ActivityController<AnimePlayerActivity> controller =
                     Robolectric.buildActivity(AnimePlayerActivity.class, intent())
                             .setup().visible()) {
            AnimePlayerActivity activity = controller.get();
            activity.onVideoReady();
            activity.onVideoReady();
            assertEquals(View.GONE, errorBox(activity).getVisibility());
        }
    }

    @Test public void antreanPemeriksaanTidakMelebihiDuaSekaligus() throws Exception {
        try (ActivityController<AnimePlayerActivity> controller =
                     Robolectric.buildActivity(AnimePlayerActivity.class, intent())
                             .setup().visible()) {
            AnimePlayerActivity activity = controller.get();

            // Keadaan awal: belum ada mirror dan belum ada yang diperiksa.
            Object queue = readField(activity, "probeQueue");
            Object probing = readField(activity, "probingServers");
            assertNotNull(queue);
            assertNotNull(probing);
            assertTrue("antrean kosong saat belum ada mirror",
                    ((java.util.Collection<?>) queue).isEmpty());
            assertTrue("tidak ada pemeriksaan berjalan tanpa mirror",
                    ((java.util.Collection<?>) probing).isEmpty());
        }
    }

    private static Object readField(Object target, String name) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}