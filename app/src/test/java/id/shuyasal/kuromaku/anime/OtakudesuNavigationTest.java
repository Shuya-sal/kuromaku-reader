package id.shuyasal.kuromaku.anime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * Navigasi anime: satu tombol Kembali di layar tingkat dalam, tidak ada di
 * beranda, dan tidak menumpuk Activity baru.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class OtakudesuNavigationTest {

    /**
     * Sumber offline untuk kedua layar.
     *
     * <p>Tanpa ini, tes navigasi membuka halaman sumber sungguhan: di mesin
     * pengembang kadang lolos, di CI bisa menggantung atau gagal karena jaringan.
     * Navigasi tidak perlu jaringan untuk dibuktikan.
     */
    private static OtakudesuSource offlineSource() {
        return new OtakudesuSource(url -> {
            if (url.contains("/episode/")) return fixture("episode_native_mp4.html");
            return fixture("detail.html");
        });
    }

    private static String fixture(String name) {
        try (java.io.InputStream in = OtakudesuNavigationTest.class.getResourceAsStream(
                "/otakudesu/" + name)) {
            if (in == null) throw new IllegalStateException("fixture: " + name);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException error) {
            throw new IllegalStateException(error);
        }
    }

    public static final class DetailHost extends OtakudesuDetailActivity {
        @Override protected OtakudesuSource createSource() {
            return offlineSource();
        }
    }

    public static final class PlayerHost extends AnimePlayerActivity {
        @Override protected OtakudesuSource createSource() {
            return offlineSource();
        }
    }

    /** Semua tombol dengan keterangan "Kembali" pada layar. */
    private static List<View> backButtons(View root) {
        List<View> found = new ArrayList<>();
        walk(root, found);
        return found;
    }

    private static void walk(View view, List<View> found) {
        if (view instanceof ImageButton) {
            CharSequence description = view.getContentDescription();
            if (description != null && "Kembali".contentEquals(description)) found.add(view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) walk(group.getChildAt(i), found);
        }
    }

    @Test public void detailPunyaTepatSatuTombolKembali() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                DetailHost.class)
                .putExtra(OtakudesuDetailActivity.EXTRA_ANIME_ID, "fxskc-sub-indo")
                .putExtra(OtakudesuDetailActivity.EXTRA_ANIME_TITLE, "FX Senshi Kurumi-chan");
        try (ActivityController<DetailHost> controller =
                     Robolectric.buildActivity(DetailHost.class, intent).setup()) {
            DetailHost activity = controller.get();
            List<View> backs = backButtons(activity.getWindow().getDecorView());
            assertEquals("hanya satu tombol Kembali", 1, backs.size());

            backs.get(0).performClick();
            assertTrue("kembali menutup layar detail", activity.isFinishing());
            assertNull("tidak membuka Activity baru",
                    Shadows.shadowOf(activity).getNextStartedActivity());
        }
    }

    @Test public void pemutarPunyaTepatSatuTombolKembali() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                PlayerHost.class)
                .putExtra(AnimePlayerActivity.EXTRA_ANIME_ID, "fxskc-sub-indo")
                .putExtra(AnimePlayerActivity.EXTRA_ANIME_TITLE, "FX Senshi Kurumi-chan")
                .putExtra(AnimePlayerActivity.EXTRA_EPISODE_URL,
                        "https://otakudesu.blog/episode/fxskc-episode-1-sub-indo/")
                .putExtra(AnimePlayerActivity.EXTRA_EPISODE_LABEL, "Episode 1");
        try (ActivityController<PlayerHost> controller =
                     Robolectric.buildActivity(PlayerHost.class, intent).setup()) {
            PlayerHost activity = controller.get();
            List<View> backs = backButtons(activity.getWindow().getDecorView());
            assertEquals("hanya satu tombol Kembali", 1, backs.size());

            View back = activity.getWindow().getDecorView()
                    .findViewWithTag("anime_player_back");
            assertNotNull(back);
            assertEquals(back, backs.get(0));

            back.performClick();
            assertTrue("kembali menutup pemutar", activity.isFinishing());
            assertNull("tidak menumpuk pemutar baru",
                    Shadows.shadowOf(activity).getNextStartedActivity());
        }
    }
}