package id.shuyasal.kuromaku.anime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.widget.Button;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Pemutar native: format waktu, penolakan sumber yang bukan media, dan perilaku
 * Activity ketika halaman episode tidak punya berkas video.
 *
 * <p>Video sungguhan tidak diputar di sini — Robolectric tidak punya codec.
 * Yang diuji adalah keputusan aplikasi: jangan menampilkan layar hitam, dan
 * selalu sediakan jalan ke halaman resmi.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w360dp-h640dp-mdpi")
public class AnimePlayerTest {

    @Test public void formatWaktuMudahDibaca() {
        assertEquals("00:00", AnimePlayerActivity.format(0));
        assertEquals("00:00", AnimePlayerActivity.format(-5));
        assertEquals("01:05", AnimePlayerActivity.format(65_000));
        assertEquals("59:59", AnimePlayerActivity.format(3_599_000));
        assertEquals("1:01:01", AnimePlayerActivity.format(3_661_000));
    }

    @Test public void halamanEpisodeTanpaMediaMenampilkanPesanDanTombolSumber() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                AnimePlayerActivity.class)
                .putExtra(AnimePlayerActivity.EXTRA_ANIME_ID, "fxskc")
                .putExtra(AnimePlayerActivity.EXTRA_ANIME_TITLE, "FX Senshi Kurumi-chan")
                .putExtra(AnimePlayerActivity.EXTRA_EPISODE_URL,
                        "https://otakudesu.blog/episode/fxskc-episode-1-sub-indo/")
                .putExtra(AnimePlayerActivity.EXTRA_EPISODE_LABEL, "Episode 1");

        AnimePlayerActivity activity = Robolectric.buildActivity(
                AnimePlayerActivity.class, intent).setup().get();

        TextView error = activity.getWindow().getDecorView()
                .findViewWithTag("anime_player_error");
        assertNotNull("Pesan status harus ada", error);

        // Resolusi berjalan di thread pekerja; tunggu sebentar lalu periksa hasil.
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        // Tunggu sampai kotak status benar-benar tampil dan berisi pesan.
        for (int i = 0; i < 80 && text(error).trim().isEmpty(); i++) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
        }

        assertTrue("Pesan harus terlihat, bukan layar hitam", isVisible((android.view.View) error.getParent()));
        assertFalse("Pesan tidak boleh kosong", text(error).trim().isEmpty());

        Button source = activity.getWindow().getDecorView()
                .findViewWithTag("anime_player_source");
        assertNotNull("Tombol Tonton di Sumber harus tersedia", source);
        assertEquals("Tonton di Sumber", source.getText().toString());
    }

    private static boolean isVisible(android.view.View view) {
        android.view.View current = view;
        while (current != null) {
            if (current.getVisibility() != android.view.View.VISIBLE) return false;
            if (!(current.getParent() instanceof android.view.View)) return true;
            current = (android.view.View) current.getParent();
        }
        return true;
    }

    private static String text(TextView view) {
        return view.getText() == null ? "" : view.getText().toString();
    }

    @Test public void episodeTanpaAlamatLangsungDitutupDenganPesan() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                AnimePlayerActivity.class)
                .putExtra(AnimePlayerActivity.EXTRA_ANIME_ID, "fxskc");
        AnimePlayerActivity activity = Robolectric.buildActivity(
                AnimePlayerActivity.class, intent).setup().get();

        TextView error = activity.getWindow().getDecorView()
                .findViewWithTag("anime_player_error");
        assertNotNull(error);
        assertTrue(text(error).contains("tidak memiliki alamat"));
    }
}