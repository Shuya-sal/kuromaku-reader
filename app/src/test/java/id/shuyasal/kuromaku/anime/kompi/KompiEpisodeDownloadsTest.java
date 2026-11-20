package id.shuyasal.kuromaku.anime.kompi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Daftar unduhan resmi dari sumber.
 *
 * <p>API episode sumber ini memuat {@code downloads} berisi tautan 480p dan
 * 720p, terpisah dari {@code sources} yang merupakan pemutar. Daftar itu tidak
 * pernah dibaca, sehingga tombol Unduh hanya mengandalkan server pemutar —
 * dan server pemutar di sini kebanyakan halaman embed.
 */
public class KompiEpisodeDownloadsTest {

    private static String fixture() throws Exception {
        try (InputStream stream = KompiEpisodeDownloadsTest.class
                .getResourceAsStream("/kompi/episode_downloads.json")) {
            assertNotNull("fixture episode", stream);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) > 0) out.write(buffer, 0, read);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static KompiSource source() throws Exception {
        final String body = fixture();
        return new KompiSource((url, referer) -> body);
    }

    @Test public void daftarUnduhanResmiTerbaca() throws Exception {
        KompiSource.EpisodeInfo info = source().episode(
                "hyouken-no-majutsushi-ga-sekai-wo-suberu-ii-episode-4");
        assertFalse("sumber ini menerbitkan tautan unduhan", info.downloads.isEmpty());
        assertEquals(10, info.downloads.size());
    }

    @Test public void kualitasHanyaYangDiterbitkanSumber() throws Exception {
        KompiSource.EpisodeInfo info = source().episode(
                "hyouken-no-majutsushi-ga-sekai-wo-suberu-ii-episode-4");
        java.util.Set<String> resolutions = new java.util.HashSet<>();
        for (KompiSource.Download download : info.downloads) {
            resolutions.add(download.resolution);
        }
        assertEquals("hanya 480p dan 720p yang ada di sumber",
                new java.util.HashSet<>(java.util.Arrays.asList("480p", "720p")), resolutions);
    }

    @Test public void labelDanKualitasTerbacaUntukDitampilkan() throws Exception {
        KompiSource.EpisodeInfo info = source().episode(
                "hyouken-no-majutsushi-ga-sekai-wo-suberu-ii-episode-4");
        KompiSource.Download pertama = info.downloads.get(0);
        assertEquals("480p · GDrive", pertama.describe());
        assertTrue("alamat tidak kosong", !pertama.url.isEmpty());
    }

    @Test public void pixeldrainAdaDiKeduaKualitas() throws Exception {
        KompiSource.EpisodeInfo info = source().episode(
                "hyouken-no-majutsushi-ga-sekai-wo-suberu-ii-episode-4");
        int jumlah = 0;
        for (KompiSource.Download download : info.downloads) {
            if (download.label.toLowerCase(java.util.Locale.ROOT).contains("pixeldrain")) {
                jumlah += 1;
                // Tautan inilah yang benar-benar bisa disimpan — halaman lain
                // menjawab dengan HTML, bukan video.
                assertNotNull(KompiDetailActivity.resolveUnduhan(download.url));
            }
        }
        assertEquals("PixelDrain ada di 480p dan 720p", 2, jumlah);
    }

    @Test public void pemutarDanUnduhanTidakDicampur() throws Exception {
        KompiSource.EpisodeInfo info = source().episode(
                "hyouken-no-majutsushi-ga-sekai-wo-suberu-ii-episode-4");
        // sources = pemutar (5), downloads = berkas (10). Mencampur keduanya
        // membuat tombol unduhan menawarkan halaman embed.
        assertEquals(5, info.servers.size());
        assertEquals(10, info.downloads.size());
    }
}
