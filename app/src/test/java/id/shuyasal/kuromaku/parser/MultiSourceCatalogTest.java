package id.shuyasal.kuromaku.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

import id.shuyasal.kuromaku.data.Block;
import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.Volume;

public class MultiSourceCatalogTest {

    private String readResource(String path) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalArgumentException("Fixture tidak ditemukan: " + path);
        }
        try (Scanner scanner = new Scanner(stream, StandardCharsets.UTF_8.name())) {
            return scanner.useDelimiter("\\A").next();
        }
    }

    @Test
    public void megumiCatalogParserExtractsMetadataAndVolumesInCorrectOrder() throws Exception {
        String html = readResource("catalog/megumi_kage_catalog.html");
        String url = "https://meguminovel.com/kage-no-jitsuryokusha-ni-naritakute-bahasa-indonesia/";

        NovelDiscovery.Result result = NovelDiscovery.parse(html, url);

        assertNotNull(result);
        assertEquals("Kage no Jitsuryokusha ni Naritakute Bahasa Indonesia", result.title);
        assertEquals("https://meguminovel.com/wp-content/uploads/kage-no-jitsuryokusha-ni-naritakute-scaled-724x1024.jpg", result.coverUrl);
        assertTrue(result.synopsis.length > 0);
        assertTrue(result.synopsis[0].contains("kekuatan yang tersembunyi"));
        assertTrue(result.genres.contains("Action"));
        assertTrue(result.genres.contains("Fantasy"));
        assertEquals("Ongoing", result.status);

        List<Volume> volumes = result.volumes;
        assertEquals(2, volumes.size());

        Volume vol4 = volumes.get(0);
        assertEquals(4, vol4.number);
        assertEquals(5, vol4.chapterCount());
        assertTrue(vol4.chapters.get(0).title.contains("Chapter 3"));
        assertTrue(vol4.chapters.get(4).title.contains("Epilog"));

        Volume vol5 = volumes.get(1);
        assertEquals(5, vol5.number);
        assertEquals(7, vol5.chapterCount());
        assertTrue(vol5.chapters.get(0).title.contains("Prolog"));
        assertTrue(vol5.chapters.get(1).title.contains("Chapter 1"));
        assertTrue(vol5.chapters.get(5).title.contains("Chapter 5"));
        assertTrue(vol5.chapters.get(6).title.contains("Epilog"));
    }

    @Test
    public void megumiChapterParserExtractsContentAndStripsWidgets() throws Exception {
        String html = readResource("fixtures/megumi_kage_chapter.html");
        String url = "https://meguminovel.com/ln-kage-no-jitsuryokusha-ni-naritakute-vol-5-chapter-1/";

        ChapterContent content = ChapterParser.parse(html, url);

        assertNotNull(content);
        assertEquals("LN Kage no Jitsuryokusha ni Naritakute! Vol 5 Chapter 1", content.title());
        assertFalse(content.blocks().isEmpty());

        boolean hasImage = false;
        boolean hasSaweria = false;
        boolean hasNav = false;

        for (Block b : content.blocks()) {
            if (b.isImage()) {
                hasImage = true;
                assertEquals("https://meguminovel.com/wp-content/uploads/kage-vol-5-ch1-ilu1.jpg", b.imageUrl);
            }
            if (b.text != null) {
                if (b.text.contains("Saweria") || b.text.contains("Donasi")) hasSaweria = true;
                if (b.text.contains("Chapter Sebelumnya") || b.text.contains("Chapter Selanjutnya")) hasNav = true;
            }
        }

        assertTrue("Ilustrasi harus ditemukan", hasImage);
        assertFalse("Widget Saweria harus dibersihkan", hasSaweria);
        assertFalse("Navigasi harus dibersihkan", hasNav);
    }

    @Test
    public void sakuraCatalogParserExtractsMetadataAndAscendingChapters() throws Exception {
        String html = readResource("catalog/sakura_necromancer_catalog.html");
        String url = "https://sakuranovel.id/series/necromancer-academys-genius-summoner/";

        NovelDiscovery.Result result = NovelDiscovery.parse(html, url);

        assertNotNull(result);
        assertEquals("Necromancer Academy’s Genius Summoner", result.title);
        assertEquals("https://sakuranovel.id/wp-content/uploads/2024/01/Necromancer-Academys-Genius-Summoner.jpg", result.coverUrl);
        assertEquals("일제사격", result.author);
        assertEquals("Ongoing", result.status);
        assertTrue(result.genres.contains("Action"));
        assertTrue(result.synopsis.length > 0);

        List<Volume> volumes = result.volumes;
        assertEquals(1, volumes.size());

        Volume vol1 = volumes.get(0);
        assertEquals(1, vol1.number);
        assertEquals(3, vol1.chapterCount());

        assertEquals("Chapter 1 – Episode 1", vol1.chapters.get(0).title);
        assertEquals("Chapter 2 – Episode 2", vol1.chapters.get(1).title);
        assertEquals("Chapter 3 – Episode 3", vol1.chapters.get(2).title);
    }

    @Test
    public void sakuraChapterParserExtractsContentAndStripsAdsAndNav() throws Exception {
        String html = readResource("fixtures/sakura_necromancer_chapter.html");
        String url = "https://sakuranovel.id/necromancer-academys-genius-summoner-chapter-1-episode-1-bahasa-indonesia/";

        ChapterContent content = ChapterParser.parse(html, url);

        assertNotNull(content);
        assertEquals("Necromancer Academy’s Genius Summoner Chapter 1 – Episode 1", content.title());

        boolean hasAds = false;
        boolean hasNav = false;
        boolean hasText = false;

        for (Block b : content.blocks()) {
            if (b.text != null) {
                if (b.text.contains("Iklan Sponsor")) hasAds = true;
                if (b.text.contains("Prev") || b.text.contains("Next")) hasNav = true;
                if (b.text.contains("Gerbang akademi Kizen")) hasText = true;
            }
        }

        assertTrue("Teks paragraf harus ditemukan", hasText);
        assertFalse("Iklan harus dibersihkan", hasAds);
        assertFalse("Navigasi Prev/Next harus dibersihkan", hasNav);
    }
}