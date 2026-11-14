package id.shuyasal.kuromaku.anime.kompi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pembaca Animekompi diuji dengan balasan JSON yang disimpan, bukan jaringan.
 *
 * <p>Yang dijaga: bagian beranda terbaca, episode terbaru dulu, hanya server
 * yang benar-benar memberi berkas yang ditandai, dan halaman pemutar tidak
 * pernah diubah menjadi alamat video karangan.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class KompiSourceTest {

    private static final String BERANDA = "{"
            + "\"latestEpisodes\":[{\"slug\":\"ep-7\",\"numberLabel\":\"07\","
            + "\"animeSlug\":\"tokyo-revengers\",\"animeTitle\":\"Tokyo Revengers\","
            + "\"animeType\":\"TV\",\"animeStatus\":\"ongoing\"}],"
            + "\"popularWeekly\":[{\"slug\":\"toaru-anbu\",\"title\":\"Toaru Anbu no Item\","
            + "\"cover\":\"/api/img/covers/toaru.jpg\",\"type\":\"TV\",\"status\":\"ongoing\"}],"
            + "\"ongoing\":[],\"movies\":[]}";

    private static final String PENCARIAN = "{\"anime\":["
            + "{\"slug\":\"toaru-anbu\",\"title\":\"Toaru Anbu no Item\","
            + "\"cover\":\"/api/img/covers/toaru.jpg\",\"type\":\"TV\",\"status\":\"ongoing\"}]}";

    private static final String DETAIL = "{\"anime\":{\"slug\":\"toaru-anbu\","
            + "\"title\":\"Toaru Anbu no Item\",\"cover\":\"/api/img/covers/toaru.jpg\","
            + "\"synopsis\":\"Academy City.\",\"type\":\"TV\",\"status\":\"ongoing\",\"score\":8.2},"
            + "\"episodes\":["
            + "{\"slug\":\"toaru-ep-1\",\"number\":1},"
            + "{\"slug\":\"toaru-ep-6\",\"number\":6}]}";

    private static final String EPISODE = "{\"episode\":{\"numberLabel\":\"06\"},"
            + "\"anime\":{\"title\":\"Toaru Anbu no Item\"},"
            + "\"sources\":["
            + "{\"label\":\"Server GDrive\",\"url\":\"https://www.blogger.com/video.g?token=x\"},"
            + "{\"label\":\"Pixel HD\",\"url\":\"https://pixeldrain.com/u/icvVEfG3\"},"
            + "{\"label\":\"Mp4upload\",\"url\":\"https://www.mp4upload.com/embed-abc.html\"},"
            + "{\"label\":\"Server VidHide\",\"url\":\"https://morencius.com/embed/xyz\"}]}";

    private static final String PEMUTAR_MP4UPLOAD = "<html><script>"
            + "player.setup({sources: [{src: \"https://a3.mp4upload.com:183/d/token/video.mp4\","
            + " type: \"video/mp4\"}]});</script></html>";

    private static KompiSource dengan(Map<String, String> balasan) {
        return new KompiSource((url, referer) -> {
            for (Map.Entry<String, String> entry : balasan.entrySet()) {
                if (url.contains(entry.getKey())) return entry.getValue();
            }
            throw new IOException("Tidak ada balasan untuk " + url);
        });
    }

    private static KompiSource standar() {
        Map<String, String> balasan = new HashMap<>();
        balasan.put("/api/home", BERANDA);
        balasan.put("/api/search", PENCARIAN);
        balasan.put("/api/anime/", DETAIL);
        balasan.put("/api/episodes/", EPISODE);
        balasan.put("mp4upload.com", PEMUTAR_MP4UPLOAD);
        return dengan(balasan);
    }

    @Test public void bagianBerandaTerbaca() throws Exception {
        List<KompiSource.Section> sections = standar().home();
        assertEquals("dua bagian berisi data", 2, sections.size());
        assertEquals("Episode Terbaru", sections.get(0).name);
        assertEquals("anime terbaru memakai slug anime, bukan slug episode",
                "tokyo-revengers", sections.get(0).items.get(0).slug);
        assertEquals("Episode 07", sections.get(0).items.get(0).note);
        assertEquals("gambar dibuat absolut",
                KompiSource.BASE + "/api/img/covers/toaru.jpg",
                sections.get(1).items.get(0).cover);
        assertEquals("TV · Ongoing", sections.get(1).items.get(0).note);
    }

    @Test public void pencarianMengembalikanKartu() throws Exception {
        List<KompiSource.Item> items = standar().search("toaru");
        assertEquals(1, items.size());
        assertEquals("Toaru Anbu no Item", items.get(0).title);
    }

    @Test public void detailMengurutkanEpisodeTerbaruDulu() throws Exception {
        KompiSource.Detail detail = standar().detail("toaru-anbu");
        assertEquals(2, detail.episodes.size());
        assertEquals("episode 6 lebih dulu", "toaru-ep-6", detail.episodes.get(0).slug);
        assertEquals("episode 1 terakhir", "toaru-ep-1", detail.episodes.get(1).slug);
        assertTrue("keterangan memuat tipe dan status",
                detail.info.contains("TV") && detail.info.contains("ongoing"));
        assertTrue("keterangan memuat jumlah episode", detail.info.contains("2 episode"));
    }

    @Test public void hanyaServerBerkasYangDitandai() throws Exception {
        KompiSource.EpisodeInfo info = standar().episode("toaru-ep-6");
        assertEquals(4, info.servers.size());
        assertFalse("GDrive hanya halaman pemutar", info.servers.get(0).file);
        assertTrue("Pixel HD memberi berkas", info.servers.get(1).file);
        assertTrue("Mp4upload memberi berkas", info.servers.get(2).file);
        assertFalse("VidHide hanya halaman pemutar", info.servers.get(3).file);
        assertEquals("06", info.number);
        assertEquals("Toaru Anbu no Item", info.title);
    }

    @Test public void pixelDrainMenjadiAlamatBerkasResmi() throws Exception {
        String media = standar().resolve("https://pixeldrain.com/u/icvVEfG3");
        assertEquals("https://pixeldrain.com/api/file/icvVEfG3?download", media);
    }

    @Test public void mp4uploadMengambilAlamatVideoDariHalamannya() throws Exception {
        String media = standar().resolve("https://www.mp4upload.com/embed-abc.html");
        assertEquals("https://a3.mp4upload.com:183/d/token/video.mp4", media);
    }

    @Test public void serverHalamanPemutarTidakDiubahMenjadiVideo() throws Exception {
        assertNull(standar().resolve("https://www.blogger.com/video.g?token=x"));
        assertNull(standar().resolve("https://morencius.com/embed/xyz"));
        assertNull(standar().resolve("https://player.abyssplayer.com/SpmGnH8Sv"));
    }

    @Test public void refererMengikutiServer() {
        assertEquals("https://www.mp4upload.com/", KompiSource.refererFor(
                "https://www.mp4upload.com/embed-abc.html"));
        assertEquals("https://pixeldrain.com/", KompiSource.refererFor(
                "https://pixeldrain.com/u/icvVEfG3"));
    }

    @Test public void kegagalanSumberDisampaikan() {
        KompiSource rusak = new KompiSource((url, referer) -> {
            throw new IOException("Sumber membalas HTTP 522.");
        });
        try {
            rusak.home();
            fail("seharusnya gagal");
        } catch (KompiSource.SourceException error) {
            assertNotNull(error.getMessage());
            assertTrue(error.getMessage().contains("522"));
        }
    }

    @Test public void balasanBukanJsonTidakDianggapDaftarKosong() {
        KompiSource bukanJson = new KompiSource((url, referer) -> "<html>bukan json</html>");
        try {
            bukanJson.home();
            fail("seharusnya gagal");
        } catch (KompiSource.SourceException error) {
            assertTrue("alasannya jelas", error.getMessage().contains("JSON"));
        }
    }
}
