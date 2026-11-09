package id.shuyasal.kuromaku.anime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Panel server harus memperbarui dirinya selagi popup terbuka.
 *
 * <p>Bug yang dijaga: {@code popupPanel()} dulu memanggil
 * {@code dismissServerPanel()} yang mengosongkan referensi panel, sehingga
 * setiap pembaruan keluar lebih awal. Akibatnya dialog tertahan di
 * "Memeriksa…" — badge di tombol sudah "2 siap" — sampai menu ditutup dan
 * dibuka lagi.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w360dp-h640dp-mdpi")
public class AnimeServerPanelRefreshTest {

    private static final List<OtakudesuPlayback.Mirror> MIRRORS = Arrays.asList(
            new OtakudesuPlayback.Mirror("720p", "odstream", "a", 0),
            new OtakudesuPlayback.Mirror("480p", "odcdn", "b", 1),
            new OtakudesuPlayback.Mirror("360p", "vidhide", "c", 2));

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

    private static Object field(Object target, String name) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static void invoke(Object target, String name, Class<?>[] types, Object... args)
            throws Exception {
        java.lang.reflect.Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        method.invoke(target, args);
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

    private static List<View> findAll(View root, String tag) {
        List<View> found = new ArrayList<>();
        if (tag.equals(root.getTag())) found.add(root);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                found.addAll(findAll(group.getChildAt(i), tag));
            }
        }
        return found;
    }

    private static List<String> texts(View root, String tag) {
        List<String> out = new ArrayList<>();
        for (View view : findAll(root, tag)) {
            if (view instanceof TextView) out.add(((TextView) view).getText().toString());
        }
        return out;
    }

    @Test public void hasilPemeriksaanLangsungTerlihatTanpaMenutupDialog() throws Exception {
        try (ActivityController<AnimePlayerActivity> controller =
                     Robolectric.buildActivity(AnimePlayerActivity.class, intent())
                             .setup().visible()) {
            AnimePlayerActivity activity = controller.get();

            // State: semua mirror sedang diperiksa, belum ada hasil.
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, OtakudesuPlayback.Source> probed =
                    (LinkedHashMap<String, OtakudesuPlayback.Source>) field(activity, "probedServers");
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, OtakudesuServerPicker.Probed> failed =
                    (LinkedHashMap<String, OtakudesuServerPicker.Probed>) field(activity, "failedServers");
            @SuppressWarnings("unchecked")
            List<String> probing = (List<String>) field(activity, "probingServers");
            @SuppressWarnings("unchecked")
            List<OtakudesuPlayback.Mirror> mirrorList =
                    (List<OtakudesuPlayback.Mirror>) field(activity, "mirrors");
            mirrorList.clear();
            mirrorList.addAll(MIRRORS);
            probed.clear();
            failed.clear();
            probing.clear();
            for (OtakudesuPlayback.Mirror mirror : MIRRORS) probing.add(mirror.key());

            id.shuyasal.kuromaku.ui.Palette colors =
                    (id.shuyasal.kuromaku.ui.Palette) field(activity, "palette");
            OtakudesuServerPanel panel = new OtakudesuServerPanel(activity,
                    new OtakudesuServerPanel.Palette(colors.surface, colors.text, colors.muted,
                            colors.accent, colors.ripple));
            OtakudesuServerPanel.Listener listener = new OtakudesuServerPanel.Listener() {
                @Override public void onPick(OtakudesuPlayback.Mirror mirror) { }
                @Override public void onRetryFailed() { }
                @Override public void onWatchSource() { }
            };
            Set<String> checking = new LinkedHashSet<>();
            for (OtakudesuPlayback.Mirror mirror : MIRRORS) checking.add(mirror.key());
            View view = panel.build(new ArrayList<>(MIRRORS), probed, failed, checking, listener);

            invoke(activity, "popupPanel", new Class<?>[]{
                    OtakudesuServerPanel.class, View.class}, panel, view);

            assertSame("panel yang tampil harus diingat untuk digambar ulang",
                    panel, field(activity, "serverPanelView"));
            assertEquals("awal: semua baris diperiksa", 3,
                    findAll(view, "anime_server_spinner").size());

            // Satu hasil masuk selagi dialog terbuka.
            probed.put(MIRRORS.get(1).key(), new OtakudesuPlayback.Source(
                    "https://cdn/odcdn.mp4", OtakudesuPlayback.Kind.PROGRESSIVE, "480p"));
            probing.remove(MIRRORS.get(1).key());
            invoke(activity, "refreshServerPanel", new Class<?>[0]);

            assertTrue("grup siap diputar muncul tanpa menutup dialog",
                    texts(view, "anime_server_section").contains("Siap diputar"));
            assertEquals("satu tombol Putar muncul", 1,
                    findAll(view, "anime_server_action").size());
            assertEquals("dua baris masih diperiksa", 2,
                    findAll(view, "anime_server_spinner").size());
            assertTrue("progres menyebut satu siap",
                    texts(view, "anime_server_progress").get(0).contains("1 siap"));

            // Hasil kedua: badge dan isi dialog sama-sama bergerak.
            probed.put(MIRRORS.get(0).key(), new OtakudesuPlayback.Source(
                    "https://cdn/odstream.mp4", OtakudesuPlayback.Kind.PROGRESSIVE, "720p"));
            probing.remove(MIRRORS.get(0).key());
            invoke(activity, "refreshServerPanel", new Class<?>[0]);

            assertEquals("dua tombol Putar", 2, findAll(view, "anime_server_action").size());
            assertTrue("progres menyebut dua siap",
                    texts(view, "anime_server_progress").get(0).contains("2 siap"));
            assertNotNull("badge memakai angka yang sama",
                    activity.findViewById(android.R.id.content));
        }
    }
}