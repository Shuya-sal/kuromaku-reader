package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import id.shuyasal.kuromaku.data.Block;
import id.shuyasal.kuromaku.parser.ChapterContent;
import id.shuyasal.kuromaku.parser.ChapterParser;
import id.shuyasal.kuromaku.parser.ParseException;

/** Parser tests against representative Blogspot markup from the source blog. */
public class ChapterParserTest {

    private static final String URL = "https://kaoritranslation.blogspot.com/2026/03/test.html";

    /** Markup shaped like a real Kaori Translation post, including site chrome. */
    private static String page(String body) {
        return "<!DOCTYPE html><html><head>"
                + "<title>Monogatari no Kuromaku Volume 7 Chapter 5 - Kaori TL</title>"
                + "<meta property='og:title' content='Monogatari no Kuromaku Volume 7 Chapter 5'/>"
                + "</head><body>"
                + "<header class='header-outer'><nav><a href='/'>Beranda</a>"
                + "<a href='/p/daftar.html'>Daftar Novel</a></nav></header>"
                + "<div class='sidebar-container'><div class='widget'>Sidebar iklan</div></div>"
                + "<main><article class='article hentry'>"
                + "<h1 class='entry-title'>Monogatari no Kuromaku Volume 7 Chapter 5</h1>"
                + "<div class='post-info f'>Penulis: Kaori</div>"
                + "<ins class='adsbygoogle' data-ad-client='ca-pub-1'></ins>"
                + "<div class='post-body entry-content' data-id='1'>" + body + "</div>"
                + "<div class='post-footer'><div class='post-share'>Bagikan</div></div>"
                + "<div id='comments'><h4>3 komentar</h4></div>"
                + "</article></main>"
                + "<footer class='footer-outer'>Footer situs</footer>"
                + "</body></html>";
    }

    @Test
    public void keepsTextAndImagesInDocumentOrder() throws Exception {
        String body = "<p align='center'><b>Chapter 5</b></p>"
                + "<p>Paragraf pertama cerita.</p>"
                + "<div class='separator'><a href='#'>"
                + "<img src='https://blogger.googleusercontent.com/img/a/AAA=s320' "
                + "data-original-height='1600' data-original-width='1100' alt='Ilustrasi 1'/></a></div>"
                + "<p>Paragraf kedua setelah ilustrasi.</p>"
                + "<p>Paragraf ketiga.</p>"
                + "<img data-src='https://blogger.googleusercontent.com/img/b/BBB=s400' alt='Ilustrasi 2'/>"
                + "<p>Paragraf penutup.</p>";

        ChapterContent content = ChapterParser.parse(page(body), URL);
        List<Block> blocks = content.blocks();

        List<String> shape = new ArrayList<>();
        for (Block block : blocks) {
            shape.add(block.isImage() ? "IMG" : "TXT");
        }

        // heading, text, image, text, text, image, text
        assertEquals("[TXT, TXT, IMG, TXT, TXT, IMG, TXT]", shape.toString());
        assertEquals(2, content.imageCount());

        List<String> images = new ArrayList<>();
        for (Block block : blocks) {
            if (block.isImage()) images.add(block.imageUrl);
        }
        assertTrue(images.get(0).startsWith("https://blogger.googleusercontent.com/"));
        assertTrue("thumbnail should be upgraded to full size, got " + images.get(0),
                images.get(0).contains("s1600") && !images.get(0).contains("=s320"));
        assertFalse("lazy attribute must be preferred over placeholder",
                images.get(1).contains("=s400"));
    }

    @Test
    public void neverKeepsTextVerbatimOnly() throws Exception {
        String body = "<p>Kalimat yang harus bertahan utuh.</p>"
                + "<p>Kalimat kedua juga harus ada.</p>";
        ChapterContent content = ChapterParser.parse(page(body), URL);
        StringBuilder all = new StringBuilder();
        for (Block block : content.blocks()) {
            if (block.text != null) all.append(block.text).append(' ');
        }
        assertTrue(all.toString().contains("Kalimat yang harus bertahan utuh."));
        assertTrue(all.toString().contains("Kalimat kedua juga harus ada."));
    }

    @Test
    public void stripsSiteChromeAndAdvertising() throws Exception {
        String body = "<p>Isi asli.</p>"
                + "<script>var ad = 1;</script>"
                + "<ins class='adsbygoogle'></ins>"
                + "<iframe src='https://doubleclick.net/ad'></iframe>"
                + "<div class='post-share'>Bagikan ke Facebook</div>"
                + "<div class='related-posts'>Baca juga</div>"
                + "<p>Isi penutup.</p>";

        ChapterContent content = ChapterParser.parse(page(body), URL);
        for (Block block : content.blocks()) {
            String text = block.text == null ? "" : block.text.toLowerCase();
            assertFalse(text.contains("adsbygoogle"));
            assertFalse(text.contains("bagikan"));
            assertFalse(text.contains("baca juga"));
            assertFalse(text.contains("sidebar"));
            assertFalse(text.contains("beranda"));
            assertTrue(block.imageUrl == null || !block.imageUrl.contains("doubleclick"));
        }
    }

    @Test
    public void keepsHeadingsQuotesAndListItems() throws Exception {
        String body = "<h2>Bagian Pertama</h2>"
                + "<p>Isi bagian pertama.</p>"
                + "<blockquote>Kutipan penting.</blockquote>"
                + "<ul><li>Poin satu</li><li>Poin dua</li></ul>";

        ChapterContent content = ChapterParser.parse(page(body), URL);
        boolean heading = false;
        boolean quote = false;
        int items = 0;
        for (Block block : content.blocks()) {
            if (block.type == Block.TYPE_HEADING && "Bagian Pertama".equals(block.text)) heading = true;
            if (block.type == Block.TYPE_QUOTE && "Kutipan penting.".equals(block.text)) quote = true;
            if (block.type == Block.TYPE_LIST_ITEM) items++;
        }
        assertTrue("heading must survive", heading);
        assertTrue("blockquote must survive", quote);
        assertEquals(2, items);
    }

    @Test
    public void ignoresLocalFileAndDataImages() throws Exception {
        String body = "<p>Teks.</p>"
                + "<img src='file:///C:/Users/ASUS/AppData/Local/Temp/clip_image001.png'/>"
                + "<img src='data:image/png;base64,AAAA'/>"
                + "<img src='https://blogger.googleusercontent.com/img/a/CCC'/>";
        ChapterContent content = ChapterParser.parse(page(body), URL);
        assertEquals(1, content.imageCount());
        assertEquals("https://blogger.googleusercontent.com/img/a/CCC",
                content.blocks().get(content.blocks().size() - 1).imageUrl);
    }

    @Test
    public void throwsWhenBodyIsMissing() {
        String html = "<html><body><nav>menu</nav><div class='sidebar'>x</div></body></html>";
        try {
            ChapterParser.parse(html, URL);
            fail("expected ParseException");
        } catch (ParseException expected) {
            assertTrue(expected.getMessage().toLowerCase().contains("tidak ditemukan"));
        }
    }

    @Test
    public void throwsWhenBodyHasNoReadableText() {
        String html = page("<script>only script</script><ins class='adsbygoogle'></ins>");
        try {
            ChapterParser.parse(html, URL);
            fail("expected ParseException");
        } catch (ParseException expected) {
            assertTrue(expected.getMessage().toLowerCase().contains("kosong"));
        }
    }

    @Test
    public void titleComesFromEntryTitle() throws Exception {
        ChapterContent content = ChapterParser.parse(page("<p>Isi.</p>"), URL);
        assertEquals("Monogatari no Kuromaku Volume 7 Chapter 5", content.title());
        assertEquals(URL, content.sourceUrl());
    }

    @Test
    public void paragraphGapsDoNotSplitWordsTogether() throws Exception {
        String body = "<div><span>Bagian </span><span>kalimat </span><b>tebal</b></div>"
                + "<p>Paragraf lain.</p>";
        ChapterContent content = ChapterParser.parse(page(body), URL);
        String first = content.blocks().get(0).text;
        assertEquals("Bagian kalimat tebal", first);
    }

    @Test
    public void duplicateImagesAreDroppedButOrderKept() throws Exception {
        String body = "<img src='https://blogger.googleusercontent.com/img/a/DDD'/>"
                + "<p>Di antara.</p>"
                + "<img src='https://blogger.googleusercontent.com/img/a/DDD'/>"
                + "<img src='https://blogger.googleusercontent.com/img/a/EEE'/>";
        ChapterContent content = ChapterParser.parse(page(body), URL);
        assertEquals(2, content.imageCount());
    }
}
