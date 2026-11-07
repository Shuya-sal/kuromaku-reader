package id.shuyasal.kuromaku.anime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Halaman Perpustakaan Unduhan Anime.
 *
 * <p>Halaman ini harus benar tanpa internet: isinya dibangun dari berkas yang
 * benar-benar ada, dan bila belum ada unduhan ia menjelaskannya dengan jelas.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class AnimeDownloadsActivityTest {

    private Context context;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        clear();
    }

    @After public void tearDown() {
        clear();
        AnimeDownloadStatus.reset();
    }

    private void clear() {
        File root = AnimeDownloads.root(context);
        File[] dirs = root.listFiles();
        if (dirs != null) {
            for (File dir : dirs) {
                File[] files = dir.listFiles();
                if (files != null) for (File file : files) file.delete();
                dir.delete();
            }
        }
    }

    private void save(String animeId, String label, String url) throws Exception {
        String key = AnimeDownloads.episodeKey(url, label);
        File dir = AnimeDownloads.animeDir(context, animeId);
        assertTrue(dir.isDirectory() || dir.mkdirs());
        try (FileOutputStream out = new FileOutputStream(
                AnimeDownloads.videoFile(context, animeId, key))) {
            byte[] head = new byte[64];
            head[4] = 'f'; head[5] = 't'; head[6] = 'y'; head[7] = 'p';
            out.write(head);
        }
        File video = AnimeDownloads.videoFile(context, animeId, key);
        AnimeDownloads.writeMeta(context, new AnimeDownload(key, animeId,
                "FX Senshi Kurumi-chan", "", label, url, "720p", "odcdn",
                "https://cdn.invalid/a.mp4", video.length(), 1L));
    }

    @Test public void tanpaUnduhanMenampilkanPesanKosong() {
        try (ActivityController<AnimeDownloadsActivity> controller =
                     Robolectric.buildActivity(AnimeDownloadsActivity.class).setup()) {
            ShadowLooper.idleMainLooper();
            AnimeDownloadsActivity activity = controller.get();
            View empty = activity.getWindow().getDecorView()
                    .findViewWithTag("anime_downloads_empty");
            assertNotNull(empty);
            assertEquals(View.VISIBLE, empty.getVisibility());

            TextView title = (TextView) ((android.view.ViewGroup) empty).getChildAt(0);
            assertEquals("Belum ada episode yang diunduh", String.valueOf(title.getText()));
            assertNotNull("ada jalan kembali ke katalog",
                    empty.findViewWithTag("anime_downloads_empty_action"));
        }
    }

    @Test public void episodeTersimpanTampilDenganJudulDanUkuran() throws Exception {
        save("fxskc", "Episode 1",
                "https://otakudesu.blog/episode/fxskc-episode-1-sub-indo/");

        try (ActivityController<AnimeDownloadsActivity> controller =
                     Robolectric.buildActivity(AnimeDownloadsActivity.class).setup()) {
            ShadowLooper.idleMainLooper();
            AnimeDownloadsActivity activity = controller.get();
            View item = activity.getWindow().getDecorView()
                    .findViewWithTag("anime_downloads_item_fxskc");
            assertNotNull("kartu anime tersimpan harus ada", item);

            View empty = activity.getWindow().getDecorView()
                    .findViewWithTag("anime_downloads_empty");
            assertEquals("pesan kosong disembunyikan", View.GONE, empty.getVisibility());

            TextView summary = (TextView) activity.getWindow().getDecorView()
                    .findViewWithTag("anime_downloads_summary");
            assertTrue("ringkasan menyebut jumlah episode",
                    String.valueOf(summary.getText()).contains("1 episode"));

            assertNotNull("tombol lanjutkan menonton tersedia",
                    item.findViewWithTag("anime_downloads_continue_fxskc"));
        }
    }

    @Test public void episodeDaftarTerbukaSetelahJudulDiketuk() throws Exception {
        String url = "https://otakudesu.blog/episode/fxskc-episode-1-sub-indo/";
        save("fxskc", "Episode 1", url);
        String key = AnimeDownloads.episodeKey(url, "Episode 1");

        try (ActivityController<AnimeDownloadsActivity> controller =
                     Robolectric.buildActivity(AnimeDownloadsActivity.class).setup()) {
            ShadowLooper.idleMainLooper();
            AnimeDownloadsActivity activity = controller.get();
            assertNull("daftar episode masih tertutup", activity.getWindow().getDecorView()
                    .findViewWithTag("anime_downloads_episode_" + key));

            View title = activity.getWindow().getDecorView()
                    .findViewWithTag("anime_downloads_open_fxskc");
            title.performClick();
            ShadowLooper.idleMainLooper();

            assertNotNull("baris episode terbuka", activity.getWindow().getDecorView()
                    .findViewWithTag("anime_downloads_episode_" + key));
            assertNotNull("tombol putar episode tersedia", activity.getWindow().getDecorView()
                    .findViewWithTag("anime_downloads_play_" + key));
        }
    }

    @Test public void halamanTetapTerbukaTanpaInternet() {
        // Tidak ada permintaan jaringan sama sekali di halaman ini: isinya
        // dibaca dari penyimpanan aplikasi.
        try (ActivityController<AnimeDownloadsActivity> controller =
                     Robolectric.buildActivity(AnimeDownloadsActivity.class).setup()) {
            ShadowLooper.idleMainLooper();
            assertEquals(false, controller.get().isFinishing());
        }
    }
}