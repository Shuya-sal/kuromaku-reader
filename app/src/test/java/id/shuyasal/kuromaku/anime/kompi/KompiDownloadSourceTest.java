package id.shuyasal.kuromaku.anime.kompi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import id.shuyasal.kuromaku.anime.AnimeDownloadTask;

/**
 * Unduhan Anime Server 2 dengan bentuk data sumber yang sebenarnya.
 *
 * <p>Keluhan aslinya "masih belum bisa download". Penyebabnya dua:
 * <ul>
 *   <li>Server Pixeldrain disebut {@code direct} oleh sumber, tetapi hanya
 *       dikenali lewat nama host sehingga tidak pernah dicoba.</li>
 *   <li>Sebagian episode berjudul {@code null}, dan teks "null" itu ikut
 *       menjadi nama berkas.</li>
 * </ul>
 * Contoh di bawah diambil apa adanya dari API sumber untuk episode 7
 * "Hyouken no Majutsushi ga Sekai wo Suberu II".
 */
public class KompiDownloadSourceTest {

    /** Sumber-sumber nyata episode 7, dipersingkat hanya pada bagian yang dipakai. */
    private static final String SUMBER_ASLI =
            "[{\"label\":\"Server GDrive\",\"kind\":\"embed\","
            + "\"url\":\"https://www.blogger.com/video.g?token=abc\"},"
            + "{\"label\":\"HD Pahe\",\"kind\":\"embed\","
            + "\"url\":\"https://player.abyssplayer.com/V6OLaevoSP\"},"
            + "{\"label\":\"Pixel HD (Tanpa Iklan)\",\"kind\":\"direct\","
            + "\"url\":\"https://pixeldrain.com/u/sN2nH8CH\"},"
            + "{\"label\":\"Server VidHide\",\"kind\":\"embed\","
            + "\"url\":\"https://morencius.com/embed/x5kkvi2q06lp\"},"
            + "{\"label\":\"Server Mp4upload\",\"kind\":\"embed\","
            + "\"url\":\"https://www.mp4upload.com/embed-ankliymp4r4z.html\"}]";

    /** Sumber tanpa jaringan: resolve Pixeldrain tidak butuh unduhan. */
    private KompiSource tanpaJaringan() {
        return new KompiSource((url, referer) -> {
            throw new java.io.IOException("jaringan tidak dipakai di tes ini");
        });
    }

    @Test public void serverDirectDikenaliSebagaiBerkas() {
        assertTrue("kind direct harus dianggap berkas",
                KompiSource.isDirectKind("direct"));
        assertTrue("host pixeldrain tetap dikenali",
                KompiSource.isFileHost("https://pixeldrain.com/u/sN2nH8CH"));
        assertFalse("halaman pemutar bukan berkas",
                KompiSource.isFileHost("https://morencius.com/embed/x5kkvi2q06lp"));
        assertFalse("blogger bukan berkas",
                KompiSource.isFileHost("https://www.blogger.com/video.g?token=abc"));
    }

    @Test public void pixeldrainMenghasilkanAlamatBerkasYangBisaDiunduh() throws Exception {
        KompiSource source = tanpaJaringan();
        String media = source.resolve("https://pixeldrain.com/u/sN2nH8CH");
        assertEquals("https://pixeldrain.com/api/file/sN2nH8CH?download", media);

        // Aturan unduhan aplikasi harus menerima alamat itu apa adanya.
        AnimeDownloadTask task = new AnimeDownloadTask("anime", "Judul", "", "Episode 7",
                "ep-7", "", "Anime Server 2", media, "PROGRESSIVE");
        assertTrue("berkas pixeldrain harus bisa diunduh", task.downloadable());
        assertTrue("alasan kosong berarti boleh", task.reasonNotDownloadable().isEmpty());
    }

    @Test public void halamanPemutarTidakPernahDianggapBerkas() {
        AnimeDownloadTask embed = new AnimeDownloadTask("anime", "Judul", "", "Episode 7",
                "ep-7", "", "HD Pahe", "https://player.abyssplayer.com/V6OLaevoSP",
                "PROGRESSIVE");
        assertFalse("halaman pemutar tidak boleh diunduh", embed.downloadable());
        assertFalse("alasan harus dijelaskan", embed.reasonNotDownloadable().isEmpty());
    }

    @Test public void judulNullDigantiLabelEpisode() throws Exception {
        JSONObject node = new JSONObject("{\"slug\":\"ep-4\",\"number\":4,\"title\":null}");
        assertEquals("Episode 4", KompiSource.judulEpisode(node, 4));

        // optString mengubah null menjadi teks "null"; itu pun tidak boleh lolos.
        JSONObject teksNull = new JSONObject("{\"title\":\"null\",\"number\":5}");
        assertEquals("Episode 5", KompiSource.judulEpisode(teksNull, 5));

        JSONObject berjudul = new JSONObject("{\"title\":\"Episode 01\"}");
        assertEquals("Episode 01", KompiSource.judulEpisode(berjudul, 1));
    }

    @Test public void semuaEpisodePunyaNamaYangBisaDibaca() throws Exception {
        // Bentuk daftar episode nyata: 1-3 berjudul, 4-7 null.
        String daftar = "[{\"slug\":\"e1\",\"number\":1,\"title\":\"Judul Episode 01\"},"
                + "{\"slug\":\"e4\",\"number\":4,\"title\":null},"
                + "{\"slug\":\"e7\",\"number\":7,\"title\":null}]";
        org.json.JSONArray array = new org.json.JSONArray(daftar);
        for (int i = 0; i < array.length(); i++) {
            JSONObject node = array.getJSONObject(i);
            String label = KompiSource.judulEpisode(node, node.optInt("number", -1));
            assertFalse("tidak boleh kosong", label.trim().isEmpty());
            assertFalse("tidak boleh teks null", label.equalsIgnoreCase("null"));
        }
    }

    @Test public void daftarSumberAsliMengandungSatuYangBisaDiunduh() throws Exception {
        org.json.JSONArray array = new org.json.JSONArray(SUMBER_ASLI);
        KompiSource source = tanpaJaringan();
        int bisa = 0;
        for (int i = 0; i < array.length(); i++) {
            JSONObject node = array.getJSONObject(i);
            String url = node.getString("url");
            String kind = node.optString("kind", "");
            // Hanya server yang benar-benar berkas yang dicoba; halaman pemutar
            // memang tidak boleh menghasilkan unduhan.
            if (!KompiSource.isFileHost(url)) continue;
            if (KompiSource.isDirectKind(kind)) {
                // Pixeldrain: alamat berkasnya dibentuk, tidak butuh jaringan.
                if (source.resolve(url) != null) bisa += 1;
                continue;
            }
            try {
                if (source.resolve(url) != null) bisa += 1;
            } catch (KompiSource.SourceException perluJaringan) {
                // Uji luring tidak memanggil jaringan; Mp4upload perlu mengurai
                // halaman, jadi tidak dihitung di sini.
            }
        }
        assertNotNull("daftar sumber harus terbaca", array);
        assertEquals("tepat satu sumber menghasilkan berkas", 1, bisa);
    }
}
