package id.shuyasal.kuromaku.anime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Pemeriksaan penyimpanan unduhan anime.
 *
 * <p>Yang diuji adalah hal-hal yang menentukan apakah sebuah episode benar-benar
 * bisa ditonton offline: berkas harus ada, ukurannya cocok dengan catatan, dan
 * kepalanya berformat media. Berkas setengah jadi atau berkas yang bukan video
 * tidak boleh pernah muncul sebagai "Tersimpan offline".
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class AnimeDownloadsTest {

    private Context context;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        clearRoot();
        AnimeDownloadStatus.reset();
    }

    @After public void tearDown() {
        clearRoot();
        AnimeDownloadStatus.reset();
    }

    private void clearRoot() {
        File root = AnimeDownloads.root(context);
        File[] dirs = root.listFiles();
        if (dirs != null) {
            for (File dir : dirs) {
                File[] files = dir.listFiles();
                if (files != null) for (File file : files) file.delete();
                dir.delete();
            }
        }
        root.delete();
    }

    /** Berkas palsu yang kepalanya benar-benar berformat MP4. */
    private File mediaFile(String animeId, String episodeKey, int extraBytes) throws Exception {
        File dir = AnimeDownloads.animeDir(context, animeId);
        assertTrue(dir.isDirectory() || dir.mkdirs());
        File file = AnimeDownloads.videoFile(context, animeId, episodeKey);
        try (FileOutputStream out = new FileOutputStream(file)) {
            byte[] head = new byte[24];
            head[4] = 'f';
            head[5] = 't';
            head[6] = 'y';
            head[7] = 'p';
            out.write(head);
            if (extraBytes > 0) out.write(new byte[extraBytes]);
        }
        return file;
    }

    private AnimeDownload meta(String animeId, String episodeKey, long bytes) {
        return new AnimeDownload(episodeKey, animeId, "FX Senshi Kurumi-chan",
                "https://contoh.invalid/poster.jpg", "Episode 1",
                "https://otakudesu.blog/episode/fxskc-episode-1-sub-indo/",
                "720p", "odcdn", "https://cdn.invalid/a_720p.mp4", bytes, 1_700_000_000_000L);
    }

    // ------------------------------------------------------------- kunci disk

    @Test public void kunciEpisodeStabilDanAmanSebagaiNamaBerkas() {
        String url = "https://otakudesu.blog/episode/fxskc-episode-1-sub-indo/";
        String pertama = AnimeDownloads.episodeKey(url, "Episode 1");
        String kedua = AnimeDownloads.episodeKey(url, "Episode 1");
        assertEquals("kunci harus sama untuk episode yang sama", pertama, kedua);
        assertTrue("kunci hanya huruf/angka", pertama.matches("[0-9a-f]+"));
        assertFalse(pertama.contains("/"));
        assertFalse(pertama.contains(".."));
    }

    @Test public void kunciBerbedaUntukEpisodeBerbeda() {
        String satu = AnimeDownloads.episodeKey(
                "https://otakudesu.blog/episode/fxskc-episode-1-sub-indo/", "Episode 1");
        String dua = AnimeDownloads.episodeKey(
                "https://otakudesu.blog/episode/fxskc-episode-2-sub-indo/", "Episode 2");
        assertFalse("dua episode tidak boleh berbagi berkas", satu.equals(dua));
    }

    // ------------------------------------------------------------ validasi

    @Test public void episodeTersimpanHanyaBilaUkuranDanKepalaCocok() throws Exception {
        String anime = "fxskc";
        String key = AnimeDownloads.episodeKey(
                "https://otakudesu.blog/episode/fxskc-episode-1-sub-indo/", "Episode 1");
        File file = mediaFile(anime, key, 100);
        AnimeDownloads.writeMeta(context, meta(anime, key, file.length()));

        assertTrue("berkas lengkap dianggap tersimpan",
                AnimeDownloads.isSaved(context, anime, key));
        assertEquals(file.length(), AnimeDownloads.find(context, anime, key).bytes);
    }

    @Test public void ukuranTidakCocokTidakDianggapTersimpan() throws Exception {
        String anime = "fxskc";
        String key = "abc123";
        File file = mediaFile(anime, key, 100);
        // Catatan menyebut ukuran lain: berarti unduhan belum lengkap.
        AnimeDownloads.writeMeta(context, meta(anime, key, file.length() + 5000));

        assertFalse("ukuran tidak cocok berarti belum lengkap",
                AnimeDownloads.isSaved(context, anime, key));
        assertNull(AnimeDownloads.find(context, anime, key));
    }

    @Test public void berkasBukanVideoTidakDianggapTersimpan() throws Exception {
        String anime = "fxskc";
        String key = "bukanvideo";
        File dir = AnimeDownloads.animeDir(context, anime);
        assertTrue(dir.isDirectory() || dir.mkdirs());
        File file = AnimeDownloads.videoFile(context, anime, key);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write("<html>bukan video</html>".getBytes(StandardCharsets.UTF_8));
        }
        AnimeDownloads.writeMeta(context, meta(anime, key, file.length()));

        assertFalse("halaman HTML tidak boleh tampil sebagai episode tersimpan",
                AnimeDownloads.isSaved(context, anime, key));
    }

    @Test public void berkasKosongTidakDianggapTersimpan() throws Exception {
        String anime = "fxskc";
        String key = "kosong";
        File dir = AnimeDownloads.animeDir(context, anime);
        assertTrue(dir.isDirectory() || dir.mkdirs());
        File file = AnimeDownloads.videoFile(context, anime, key);
        assertTrue(file.createNewFile());
        assertFalse(AnimeDownloads.isSaved(context, anime, key));
    }

    // -------------------------------------------------------------- koleksi

    @Test public void koleksiDikelompokkanPerAnimeDanDiurutkanEpisode() throws Exception {
        String anime = "fxskc";
        String satu = AnimeDownloads.episodeKey("https://otakudesu.blog/e/1", "Episode 1");
        String dua = AnimeDownloads.episodeKey("https://otakudesu.blog/e/2", "Episode 2");
        String sepuluh = AnimeDownloads.episodeKey("https://otakudesu.blog/e/10", "Episode 10");

        for (String[] item : new String[][]{{sepuluh, "Episode 10"}, {dua, "Episode 2"},
                {satu, "Episode 1"}}) {
            File file = mediaFile(anime, item[0], 10);
            AnimeDownloads.writeMeta(context, new AnimeDownload(item[0], anime,
                    "FX Senshi Kurumi-chan", "", item[1], "https://otakudesu.blog/e/" + item[1],
                    "720p", "odcdn", "https://cdn.invalid/x.mp4", file.length(), 1L));
        }

        List<AnimeDownloads.Anime> list = AnimeDownloads.list(context);
        assertEquals(1, list.size());
        AnimeDownloads.Anime group = list.get(0);
        assertEquals(3, group.count());
        assertEquals("Episode 1", group.episodes.get(0).episodeLabel);
        assertEquals("Episode 2", group.episodes.get(1).episodeLabel);
        assertEquals("Episode 10", group.episodes.get(2).episodeLabel);
        assertTrue(group.bytes() > 0);
    }

    @Test public void berkasSetengahJadiTidakMunculDiKoleksi() throws Exception {
        String anime = "fxskc";
        String key = "setengah";
        File dir = AnimeDownloads.animeDir(context, anime);
        assertTrue(dir.isDirectory() || dir.mkdirs());
        File part = AnimeDownloads.partFile(context, anime, key);
        try (FileOutputStream out = new FileOutputStream(part)) {
            out.write(new byte[4096]);
        }
        assertTrue("berkas sementara tetap ada untuk dilanjutkan", part.isFile());
        assertTrue("koleksi tidak boleh memuat unduhan setengah jadi",
                AnimeDownloads.list(context).isEmpty());
    }

    // --------------------------------------------------------------- hapus

    @Test public void menghapusUnduhanTidakMenghapusProgresMenonton() throws Exception {
        String anime = "fxskc";
        String url = "https://otakudesu.blog/episode/fxskc-episode-1-sub-indo/";
        String key = AnimeDownloads.episodeKey(url, "Episode 1");
        File file = mediaFile(anime, key, 50);
        AnimeDownloads.writeMeta(context, new AnimeDownload(key, anime, "FX", "", "Episode 1",
                url, "720p", "odcdn", "https://cdn.invalid/a.mp4", file.length(), 1L));

        OtakudesuStore store = new OtakudesuStore(context);
        store.saveProgress(anime, OtakudesuSource.episodeId(url), 600_000L, 1_400_000L, false);
        assertNotNull(store.progress(anime, OtakudesuSource.episodeId(url)));

        assertTrue(AnimeDownloads.delete(context, anime, key));

        assertFalse("berkas unduhan hilang", AnimeDownloads.isSaved(context, anime, key));
        assertTrue("progres menonton harus tetap ada",
                store.progress(anime, OtakudesuSource.episodeId(url)) != null);
        assertEquals("posisi menonton tidak berubah", 600_000L,
                store.progress(anime, OtakudesuSource.episodeId(url)).positionMs);
    }

    @Test public void menghapusSemuaUnduhanSatuAnime() throws Exception {
        String anime = "fxskc";
        for (String label : new String[]{"Episode 1", "Episode 2"}) {
            String key = AnimeDownloads.episodeKey("https://otakudesu.blog/e/" + label, label);
            File file = mediaFile(anime, key, 20);
            AnimeDownloads.writeMeta(context, new AnimeDownload(key, anime, "FX", "", label,
                    "https://otakudesu.blog/e/" + label, "720p", "odcdn",
                    "https://cdn.invalid/x.mp4", file.length(), 1L));
        }
        assertEquals(1, AnimeDownloads.list(context).size());

        AnimeDownloads.deleteAnime(context, anime);

        assertTrue(AnimeDownloads.list(context).isEmpty());
        assertFalse(AnimeDownloads.animeDir(context, anime).isDirectory());
    }

    @Test public void membersihkanBerkasSementaraTidakMenyentuhBerkasLengkap() throws Exception {
        String anime = "fxskc";
        String key = "lengkap";
        File file = mediaFile(anime, key, 30);
        AnimeDownloads.writeMeta(context, meta(anime, key, file.length()));
        File part = AnimeDownloads.partFile(context, anime, key);
        try (FileOutputStream out = new FileOutputStream(part)) {
            out.write(new byte[10]);
        }

        AnimeDownloads.clearPartials(context, anime, key);

        assertFalse("berkas sementara dibuang", part.exists());
        assertTrue("berkas lengkap tetap", AnimeDownloads.isSaved(context, anime, key));
    }

    @Test public void ukuranDiformatUntukDibacaManusia() {
        assertEquals("—", AnimeDownload.size(0));
        assertEquals("1.0 MB", AnimeDownload.size(1024L * 1024L));
        assertEquals("143 MB", AnimeDownload.size(149_839_872L));
        assertEquals("1.5 GB", AnimeDownload.size((long) (1.5 * 1024 * 1024 * 1024)));
    }
}