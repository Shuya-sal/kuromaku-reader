package id.shuyasal.kuromaku.anime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Daftar genre dan halaman genre, diuji terhadap HTML asli halaman sumber.
 *
 * <p>Markup halaman genre berbeda dari katalog, jadi kesalahan urai akan
 * terlihat langsung sebagai "genre kosong" di aplikasi. Fixture di sini adalah
 * salinan halaman sungguhan yang diambil dari sumber.
 */
public class OtakudesuGenreTest {

    private static String resource(String name) throws IOException {
        try (InputStream in = OtakudesuGenreTest.class.getResourceAsStream(
                "/otakudesu/" + name)) {
            if (in == null) throw new IOException("fixture tidak ditemukan: " + name);
            byte[] bytes = new byte[in.available()];
            int read = 0;
            while (read < bytes.length) {
                int step = in.read(bytes, read, bytes.length - read);
                if (step <= 0) break;
                read += step;
            }
            return new String(bytes, 0, read, StandardCharsets.UTF_8);
        }
    }

    private final OtakudesuSource source = new OtakudesuSource();

    // ------------------------------------------------------------- slug

    @Test public void slugDiambilDariAlamatLengkapMaupunRelatif() {
        assertEquals("action", OtakudesuSource.genreSlug("https://otakudesu.blog/genres/action/"));
        assertEquals("action", OtakudesuSource.genreSlug("/genres/action/"));
        assertEquals("sci-fi", OtakudesuSource.genreSlug("/genres/sci-fi/page/2/"));
        assertEquals("", OtakudesuSource.genreSlug("https://otakudesu.blog/anime/x/"));
        assertEquals("", OtakudesuSource.genreSlug(null));
    }

    // ------------------------------------------------- daftar genre

    @Test public void daftarGenreDiuraiDariHalamanNyata() throws Exception {
        String html = resource("genre_list.html");
        java.util.LinkedHashMap<String, String> found = new java.util.LinkedHashMap<>();
        for (org.jsoup.nodes.Element link
                : org.jsoup.Jsoup.parse(html).select("a[href*=/genres/]")) {
            String slug = OtakudesuSource.genreSlug(link.attr("href"));
            if (!slug.isEmpty() && !link.text().trim().isEmpty()) {
                found.putIfAbsent(slug, link.text().trim());
            }
        }
        assertFalse("halaman genre-list harus memuat genre", found.isEmpty());
        assertTrue("ada genre action", found.containsKey("action"));
        assertTrue("ada genre romance", found.containsKey("romance"));
        assertEquals("Action", found.get("action"));
    }

    // ------------------------------------------------- halaman genre

    @Test public void halamanGenreMenghasilkanDaftarAnimeLengkap() throws Exception {
        OtakudesuAnime.Page page = source.parseGenreList(resource("genre_action.html"), 1);

        assertFalse("genre action tidak boleh kosong", page.items.isEmpty());
        assertTrue("jumlah anime wajar: " + page.items.size(), page.items.size() >= 10);

        OtakudesuAnime first = page.items.get(0);
        assertFalse("judul tidak kosong", first.title.isEmpty());
        assertTrue("alamat anime sah", first.url.contains("/anime/"));
        assertFalse("id anime tidak kosong", first.animeId.isEmpty());
        assertFalse("poster terisi", first.posterUrl.isEmpty());
        assertTrue("poster berupa gambar", first.posterUrl.startsWith("http"));

        // Tidak ada anime ganda dalam satu halaman.
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (OtakudesuAnime anime : page.items) {
            assertTrue("id ganda: " + anime.animeId, ids.add(anime.animeId));
        }
    }

    @Test public void genreTercatatPadaSetiapKartu() throws Exception {
        OtakudesuAnime.Page page = source.parseGenreList(resource("genre_action.html"), 1);
        boolean adaGenre = false;
        for (OtakudesuAnime anime : page.items) {
            if (!anime.genres.isEmpty()) adaGenre = true;
        }
        assertTrue("kartu genre harus memuat nama genre", adaGenre);
        assertTrue("action tercatat di salah satu kartu",
                page.items.stream().anyMatch(item -> item.genres.contains("Action")));
    }

    @Test public void jumlahHalamanGenreTerbaca() throws Exception {
        OtakudesuAnime.Page page = source.parseGenreList(resource("genre_action.html"), 1);
        assertTrue("halaman genre punya lebih dari satu halaman", page.totalPages > 1);
    }

    @Test public void halamanGenreKosongMemberiPesanJelas() {
        try {
            source.parseGenreList("<html><body><p>tidak ada</p></body></html>", 1);
            org.junit.Assert.fail("seharusnya melempar SourceException");
        } catch (OtakudesuSource.SourceException error) {
            assertTrue(error.getMessage().contains("tidak memuat daftar anime"));
        }
    }

    @Test public void genreTanpaSlugDitolak() {
        try {
            source.genre("  ", 1);
            org.junit.Assert.fail("seharusnya melempar SourceException");
        } catch (OtakudesuSource.SourceException error) {
            assertTrue(error.getMessage().contains("Genre tidak dipilih"));
        }
    }

    @Test public void namaGenreDirapikanDanSlugDipakaiBilaNamaKosong() {
        OtakudesuSource.Genre genre = new OtakudesuSource.Genre("sci-fi", "  Sci-Fi  ");
        assertEquals("sci-fi", genre.slug);
        assertEquals("Sci-Fi", genre.name);
        assertTrue(genre.matches("sci-fi"));
        assertTrue(genre.matches(" SCI-FI "));
        assertFalse(genre.matches("action"));

        OtakudesuSource.Genre tanpaNama = new OtakudesuSource.Genre("action", "   ");
        assertEquals("action", tanpaNama.name);
    }
}