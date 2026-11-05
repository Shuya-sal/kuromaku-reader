package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ShinigamiSourceParserTest {

    private String fixture(String name) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/shinigami/" + name)) {
            assertNotNull("fixture " + name + " harus ada", in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void theCatalogueIsParsedIntoMangaWithStableIdentity() throws Exception {
        JSONObject root = new JSONObject(fixture("manga_list.json"));
        List<ShinigamiManga> items = new java.util.ArrayList<>();
        org.json.JSONArray data = root.getJSONArray("data");
        for (int i = 0; i < data.length(); i++) {
            items.add(ShinigamiSource.parseManga(data.getJSONObject(i)));
        }
        assertFalse("katalog harus berisi manga", items.isEmpty());

        ShinigamiManga first = items.get(0);
        assertEquals("a62115a2-74d6-4e5b-b047-b7876385d168", first.id);
        assertEquals("Miss Ji-Hye, The Uncool Adult", first.title);
        assertFalse("cover harus terisi", first.coverUrl.isEmpty());
        assertTrue("cover harus memakai https", first.coverUrl.startsWith("https://"));
        assertEquals(26d, first.latestChapterNumber, 0.0001d);
        assertEquals(1, first.status);
        assertEquals("Berjalan", first.statusLabel());
        assertTrue("genre harus terbaca", first.genres.contains("Romance"));
        assertEquals("Manhwa", first.format);
        assertTrue("penulis harus terbaca", first.authors.contains("Horiho"));
    }

    @Test
    public void theCatalogueMetaCarriesThePagingNumbers() throws Exception {
        JSONObject root = new JSONObject(fixture("manga_list.json"));
        JSONObject meta = root.getJSONObject("meta");
        assertEquals(1, meta.getInt("page"));
        assertEquals(2, meta.getInt("page_size"));
        assertEquals(500, meta.getInt("total_page"));
        assertEquals(1000, meta.getLong("total_record"));
    }

    @Test
    public void theDetailPayloadAgreesWithTheCatalogue() throws Exception {
        JSONObject listRoot = new JSONObject(fixture("manga_list.json"));
        ShinigamiManga fromList =
                ShinigamiSource.parseManga(listRoot.getJSONArray("data").getJSONObject(0));

        JSONObject detailRoot = new JSONObject(fixture("manga_detail.json"));
        JSONObject detailObject = ShinigamiSource.firstObject(detailRoot);
        assertNotNull("detail harus berisi satu objek", detailObject);
        ShinigamiManga fromDetail = ShinigamiSource.parseManga(detailObject);

        assertEquals(fromList.id, fromDetail.id);
        assertEquals(fromList.title, fromDetail.title);
        assertEquals(fromList.coverUrl, fromDetail.coverUrl);
    }

    @Test
    public void theChapterListIsParsedWithItsIdentity() throws Exception {
        JSONObject root = new JSONObject(fixture("chapter_list.json"));
        org.json.JSONArray data = root.getJSONArray("data");
        org.json.JSONObject first = data.getJSONObject(0);
        String chapterId = first.getString("chapter_id");
        assertFalse("chapter harus punya identitas", chapterId.isEmpty());
        assertEquals("3c495f42-03d3-4264-948d-4b0d0c093f6a", first.getString("manga_id"));
        assertEquals(3862, first.getInt("chapter_number"));
    }

    @Test
    public void theChapterImagesAreJoinedInTheOrderTheSourceGives() throws Exception {
        JSONObject root = new JSONObject(fixture("chapter_detail.json"));
        JSONObject chapter = ShinigamiSource.firstObject(root);
        assertNotNull(chapter);

        String base = chapter.getString("base_url");
        String path = chapter.getJSONObject("chapter").getString("path");
        org.json.JSONArray files = chapter.getJSONObject("chapter").getJSONArray("data");
        assertTrue("chapter harus punya gambar", files.length() > 0);

        List<String> urls = new java.util.ArrayList<>();
        for (int i = 0; i < files.length(); i++) {
            urls.add(ShinigamiSource.joinUrl(base, path, files.getString(i)));
        }
        assertEquals("https://assets.shngm.id/chapter/manga_a62115a2-74d6-4e5b-b047-b7876385d168/"
                + "chapter_a7ca05c6-62fd-4e0b-b56e-08d66abb9f58/0-29b37e.webp", urls.get(0));
        assertEquals("https://assets.shngm.id/chapter/manga_a62115a2-74d6-4e5b-b047-b7876385d168/"
                + "chapter_a7ca05c6-62fd-4e0b-b56e-08d66abb9f58/8-632514.webp", urls.get(8));
    }

    @Test
    public void theGenreListCarriesTheSlugUsedByTheFilter() throws Exception {
        JSONObject root = new JSONObject(fixture("genre_list.json"));
        org.json.JSONArray data = root.getJSONArray("data");
        boolean found = false;
        for (int i = 0; i < data.length(); i++) {
            JSONObject genre = data.getJSONObject(i);
            if ("romance".equals(genre.optString("slug"))) {
                assertEquals("Romance", genre.optString("name"));
                found = true;
            }
        }
        assertTrue("genre romance harus ada", found);
    }

    @Test
    public void theUrlJoinNeverDoublesASlash() {
        assertEquals("https://a.test/b/c.webp",
                ShinigamiSource.joinUrl("https://a.test", "/b/", "c.webp"));
        assertEquals("https://a.test/b/c.webp",
                ShinigamiSource.joinUrl("https://a.test", "b", "c.webp"));
        assertEquals("https://a.test/c.webp",
                ShinigamiSource.joinUrl("https://a.test", "", "c.webp"));
    }

    @Test
    public void aSearchWordIsEncodedForTheQuery() {
        assertEquals("one%20piece", ShinigamiSource.encode("one piece"));
        assertEquals("a%26b", ShinigamiSource.encode("a&b"));
    }
}
