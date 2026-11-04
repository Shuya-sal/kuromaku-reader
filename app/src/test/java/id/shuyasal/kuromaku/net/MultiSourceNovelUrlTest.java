package id.shuyasal.kuromaku.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class MultiSourceNovelUrlTest {

    @Test
    public void normalizeMegumiNovelUrls() throws Exception {
        String input = "https://meguminovel.com/kage-no-jitsuryokusha-ni-naritakute-bahasa-indonesia/";
        String normalized = NovelUrl.normalize(input);
        assertEquals("https://meguminovel.com/kage-no-jitsuryokusha-ni-naritakute-bahasa-indonesia/", normalized);
        assertEquals(NovelUrl.Source.MEGUMI, NovelUrl.sourceOf(normalized));
        assertEquals("megumi-kage-no-jitsuryokusha-ni-naritakute-bahasa-indonesia", NovelUrl.id(normalized));

        String withQuery = "meguminovel.com/kage-no-jitsuryokusha-ni-naritakute-bahasa-indonesia?ref=chat#section";
        assertEquals("https://meguminovel.com/kage-no-jitsuryokusha-ni-naritakute-bahasa-indonesia/", NovelUrl.normalize(withQuery));
    }

    @Test
    public void normalizeSakuraNovelUrls() throws Exception {
        String input = "https://sakuranovel.id/series/necromancer-academys-genius-summoner/";
        String normalized = NovelUrl.normalize(input);
        assertEquals("https://sakuranovel.id/series/necromancer-academys-genius-summoner/", normalized);
        assertEquals(NovelUrl.Source.SAKURA, NovelUrl.sourceOf(normalized));
        assertEquals("sakura-necromancer-academys-genius-summoner", NovelUrl.id(normalized));

        String withoutSeries = "sakuranovel.id/necromancer-academys-genius-summoner";
        assertEquals("https://sakuranovel.id/series/necromancer-academys-genius-summoner/", NovelUrl.normalize(withoutSeries));
    }

    @Test
    public void normalizeKaoriUrlsPreservesBackwardCompatibility() throws Exception {
        String article = "https://kaoritranslation.blogspot.com/2025/09/ln-monogatari-no-kuromaku-ni-tensei.html";
        assertEquals(article, NovelUrl.normalize(article));
        assertEquals(NovelUrl.Source.KAORI, NovelUrl.sourceOf(article));
        assertEquals("ln-monogatari-no-kuromaku-ni-tensei", NovelUrl.id(article));

        String label = "https://kaoritranslation.blogspot.com/search/label/Monogatari%20no%20Kuromaku";
        assertEquals(label, NovelUrl.normalize(label));
        assertEquals(NovelUrl.Source.KAORI, NovelUrl.sourceOf(label));
        assertEquals("label-monogatari-no-kuromaku", NovelUrl.id(label));
    }

    @Test
    public void rejectUnsupportedHostsWithClearMessage() {
        try {
            NovelUrl.normalize("https://example.com/novel-keren/");
            fail("harus melempar exception");
        } catch (NovelUrl.Invalid error) {
            assertNotNull(error.getMessage());
            assertTrue(error.getMessage().contains("Kaori Translation, Megumi Novel, atau Sakura Novel"));
        }
    }

    @Test
    public void rejectFrontPages() {
        try {
            NovelUrl.normalize("https://meguminovel.com/");
            fail("halaman depan megumi harus ditolak");
        } catch (NovelUrl.Invalid error) {
            assertTrue(error.getMessage().contains("halaman depan"));
        }

        try {
            NovelUrl.normalize("https://sakuranovel.id/");
            fail("halaman depan sakura harus ditolak");
        } catch (NovelUrl.Invalid error) {
            assertTrue(error.getMessage().contains("halaman depan"));
        }
    }
}