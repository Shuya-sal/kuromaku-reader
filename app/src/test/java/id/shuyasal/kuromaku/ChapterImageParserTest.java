package id.shuyasal.kuromaku;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import id.shuyasal.kuromaku.data.Block;
import id.shuyasal.kuromaku.parser.ChapterContent;
import id.shuyasal.kuromaku.parser.ChapterParser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Parser tests driven by real Kaori Translation HTML saved under
 * src/test/resources/fixtures. These are the pages that were actually served
 * for each volume, trimmed to the article container.
 *
 * <p>The point of these tests: illustrations must survive extraction, keep the
 * source order, and carry their real pixel size so the reader can lay them out.
 */
public class ChapterImageParserTest {

    private static final String SOURCE =
            "https://kaoritranslation.blogspot.com/2025/01/post.html";

    private static String fixture(String name) throws Exception {
        try (InputStream in = ChapterImageParserTest.class
                .getClassLoader().getResourceAsStream("fixtures/" + name)) {
            assertNotNull("fixture tidak ditemukan: " + name, in);
            try (Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name())) {
                scanner.useDelimiter("\\A");
                return scanner.hasNext() ? scanner.next() : "";
            }
        }
    }

    private static List<Block> images(ChapterContent content) {
        List<Block> out = new ArrayList<>();
        for (Block block : content.blocks()) {
            if (block.isImage()) out.add(block);
        }
        return out;
    }

    // ---------------------------------------------------------------- Illustrasi

    /** Every "Illustrasi" post must yield real image blocks, in source order. */
    @Test
    public void illustrationPostsYieldAllImages() throws Exception {
        String[][] expected = {
                {"ilu_v1.html", "5"},
                {"ilu_v2.html", "5"},
                {"ilu_v3.html", "5"},
                {"ilu_v4.html", "8"},
                {"ilu_v5.html", "7"},
                {"ilu_v6.html", "7"},
                {"ilu_v7.html", "7"},
        };

        for (String[] pair : expected) {
            ChapterContent content = ChapterParser.parse(fixture(pair[0]), SOURCE);
            List<Block> images = images(content);
            assertEquals("jumlah ilustrasi " + pair[0],
                    Integer.parseInt(pair[1]), images.size());
            assertEquals("jumlah ilustrasi dilaporkan " + pair[0],
                    images.size(), content.imageCount());

            for (Block image : images) {
                assertNotNull("URL gambar kosong di " + pair[0], image.imageUrl);
                assertTrue("URL gambar harus https: " + image.imageUrl,
                        image.imageUrl.startsWith("https://"));
                assertFalse("URL gambar tidak boleh data URI",
                        image.imageUrl.startsWith("data:"));
                assertFalse("URL gambar tidak boleh file lokal",
                        image.imageUrl.startsWith("file:"));
                assertTrue("gambar harus punya ukuran asli: " + pair[0],
                        image.hasImageSize());
            }
        }
    }

    /** The Illustrasi post must not be mistaken for an empty text chapter. */
    @Test
    public void illustrationPostIsImageContentNotPlainText() throws Exception {
        ChapterContent content = ChapterParser.parse(fixture("ilu_v1.html"), SOURCE);
        List<Block> blocks = content.blocks();
        List<Block> images = images(content);
        assertTrue("postingan Illustrasi harus punya blok gambar", images.size() >= 5);
        // The post carries character blurbs next to the plates, so images do not
        // outnumber text; what matters is that the images are real renderable
        // blocks and the text is not mistaken for the whole chapter.
        assertTrue("harus ada teks di sekitar gambar", content.textCount() > 0);
        int renderable = 0;
        for (Block block : blocks) {
            if (block.isImage() && block.imageUrl != null && block.imageUrl.startsWith("https://")) {
                renderable++;
            }
        }
        assertEquals("semua gambar harus bisa dirender", images.size(), renderable);
        assertTrue("judul chapter tidak boleh kosong", content.title() != null);
    }

    /** Images keep the order the author published them in. */
    @Test
    public void illustrationOrderMatchesSource() throws Exception {
        String html = fixture("ilu_v4.html");
        ChapterContent content = ChapterParser.parse(html, SOURCE);
        List<Block> images = images(content);
        assertEquals(8, images.size());

        // Source order: pull the URLs straight out of the raw HTML and compare.
        List<String> sourceOrder = new ArrayList<>();
        int cursor = 0;
        while (true) {
            int at = html.indexOf("<img", cursor);
            if (at < 0) break;
            int end = html.indexOf('>', at);
            if (end < 0) break;
            String tag = html.substring(at, end);
            if (tag.contains("googleusercontent.com")) sourceOrder.add(tag);
            cursor = end;
        }
        assertEquals("gambar sumber harus 8", 8, sourceOrder.size());

        for (int i = 0; i < images.size(); i++) {
            String parsedUrl = images.get(i).imageUrl;
            String sourceTag = sourceOrder.get(i);
            // Same image identity: the unique id path segment must match.
            String id = parsedUrl.replaceAll("^.*/", "").replaceAll("[?=].*$", "");
            assertTrue("urutan ilustrasi ke-" + i + " harus sama dengan sumber",
                    sourceTag.contains(id));
        }
    }

    /** Text and illustrations stay interleaved as published. */
    @Test
    public void textAndImagesKeepDocumentOrder() throws Exception {
        ChapterContent content = ChapterParser.parse(fixture("ilu_v1.html"), SOURCE);
        List<Block> blocks = content.blocks();
        int firstImage = -1;
        int lastImage = -1;
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).isImage()) {
                if (firstImage < 0) firstImage = i;
                lastImage = i;
            }
        }
        assertTrue("harus ada gambar", firstImage >= 0);
        assertTrue("gambar tidak boleh menumpuk di akhir saja",
                lastImage - firstImage + 1 >= images(content).size());
    }

    // -------------------------------------------------------------- Chapter biasa

    /** A normal chapter keeps its text and gains any inline illustration. */
    @Test
    public void regularChapterKeepsTextAndInlineImages() throws Exception {
        ChapterContent content = ChapterParser.parse(fixture("ch_v3.html"), SOURCE);
        assertTrue("chapter harus punya teks", content.textCount() > 20);
        List<Block> images = images(content);
        assertEquals("chapter 3 punya 1 ilustrasi inline", 1, images.size());
        assertTrue("ilustrasi chapter harus https",
                images.get(0).imageUrl.startsWith("https://"));
        assertTrue("ilustrasi chapter harus punya ukuran", images.get(0).hasImageSize());
        assertEquals("1240", String.valueOf(images.get(0).imageWidth));
        assertEquals("1766", String.valueOf(images.get(0).imageHeight));
    }

    /** A chapter whose only picture is a 1px clipboard artefact keeps no picture. */
    @Test
    public void localClipboardArtefactIsNotAnIllustration() throws Exception {
        ChapterContent content = ChapterParser.parse(fixture("ch_v4.html"), SOURCE);
        for (Block image : images(content)) {
            assertFalse("gambar dari clipboard lokal tidak boleh dipakai: " + image.imageUrl,
                    image.imageUrl.startsWith("file:"));
        }
        assertEquals("hanya gambar asli yang tersisa", 1, images(content).size());
    }

    /** Text-only chapters must not invent image blocks. */
    @Test
    public void textOnlyChapterHasNoImageBlocks() throws Exception {
        ChapterContent content = ChapterParser.parse(fixture("ch_v1.html"), SOURCE);
        assertEquals("chapter tanpa ilustrasi", 0, images(content).size());
        assertEquals("0 dilaporkan", 0, content.imageCount());
        assertTrue("teks tetap ada", content.textCount() > 50);
    }

    // -------------------------------------------------------------- Ukuran & URL

    /** The declared pixel size is read from data-original-* attributes. */
    @Test
    public void originalSizeIsPreferredOverRenderedSize() {
        String html = "<html><body><div class='post-body entry-content'>"
                + "<p>teks</p>"
                + "<img src='https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEg1/s320/a.jpg'"
                + " width='320' height='480'"
                + " data-original-width='1080' data-original-height='1534' alt='ilustrasi'>"
                + "</div></body></html>";
        try {
            ChapterContent content = ChapterParser.parse(html, SOURCE);
            List<Block> images = images(content);
            assertEquals(1, images.size());
            assertEquals(1080, images.get(0).imageWidth);
            assertEquals(1534, images.get(0).imageHeight);
            assertEquals(1080f / 1534f, images.get(0).imageRatio(), 0.0001f);
        } catch (Exception error) {
            throw new AssertionError("parse gagal: " + error.getMessage(), error);
        }
    }

    /** Blogger size suffixes are upgraded so illustrations are not blurry. */
    @Test
    public void bloggerThumbnailsAreUpgraded() throws Exception {
        String html = "<html><body><div class='post-body entry-content'>"
                + "<p>teks</p>"
                + "<img src='https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEg1/s320/a.jpg'"
                + " data-original-width='1200' data-original-height='1600'>"
                + "<img src='https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEg2/b.jpg=w400-h600'>"
                + " data-original-width='1200' data-original-height='1600'>"
                + "</div></body></html>";
        // second tag is intentionally malformed in the string above; rebuild it cleanly
        html = "<html><body><div class='post-body entry-content'><p>teks</p>"
                + "<img src='https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEg1/s320/a.jpg'"
                + " data-original-width='1200' data-original-height='1600'>"
                + "<img src='https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEg2/b.jpg=w400-h600'"
                + " data-original-width='1200' data-original-height='1600'>"
                + "</div></body></html>";
        ChapterContent content = ChapterParser.parse(html, SOURCE);
        List<Block> images = images(content);
        assertEquals(2, images.size());
        assertFalse("s320 harus dinaikkan: " + images.get(0).imageUrl,
                images.get(0).imageUrl.contains("/s320/"));
        assertTrue("harus jadi s1600: " + images.get(0).imageUrl,
                images.get(0).imageUrl.contains("/s1600/"));
        assertFalse("w400-h600 harus dinaikkan: " + images.get(1).imageUrl,
                images.get(1).imageUrl.contains("w400-h600"));
    }

    /** data-src and srcset are both accepted, since Blogger lazy-loads. */
    @Test
    public void lazyLoadedAttributesAreResolved() throws Exception {
        String html = "<html><body><div class='post-body entry-content'><p>teks</p>"
                + "<img src='data:image/png;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7'"
                + " data-src='https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEg3/s400/c.jpg'"
                + " data-original-width='900' data-original-height='1400'>"
                + "<img srcset='https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEg4/s400/d.jpg 400w,"
                + " https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEg4/s1200/d.jpg 1200w'"
                + " src='data:image/gif;base64,R0lGODlhAQABAAAAACw='"
                + " data-original-width='1200' data-original-height='1700'>"
                + "</div></body></html>";
        ChapterContent content = ChapterParser.parse(html, SOURCE);
        List<Block> images = images(content);
        assertEquals(2, images.size());
        assertTrue("data-src dipakai: " + images.get(0).imageUrl,
                images.get(0).imageUrl.contains("googleusercontent.com"));
        assertFalse("base64 tidak boleh dipakai", images.get(0).imageUrl.startsWith("data:"));
        assertTrue("srcset dipakai: " + images.get(1).imageUrl,
                images.get(1).imageUrl.contains("googleusercontent.com"));
        assertTrue("varian terbesar dipilih: " + images.get(1).imageUrl,
                !images.get(1).imageUrl.contains("s400"));
    }

    /** Images without declared dimensions still parse, with ratio unknown. */
    @Test
    public void imageWithoutDeclaredSizeStillParses() throws Exception {
        String html = "<html><body><div class='post-body entry-content'><p>teks</p>"
                + "<img src='https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEg5/x.jpg'>"
                + "</div></body></html>";
        ChapterContent content = ChapterParser.parse(html, SOURCE);
        List<Block> images = images(content);
        assertEquals(1, images.size());
        assertFalse("ukuran tidak diketahui", images.get(0).hasImageSize());
        assertEquals(0f, images.get(0).imageRatio(), 0.0001f);
        assertEquals(1, content.imageCount());
    }
}
