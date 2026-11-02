package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import id.shuyasal.kuromaku.parser.VolumeCoverParser;

/**
 * Volume covers must come from the page itself and must not be reused.
 *
 * <p>The whole point of this feature is that each volume shows its own cover, so
 * the tests check both that the parser finds every volume image on the real
 * landing pages and that no two volumes end up sharing one URL.
 */
public class VolumeCoverParserTest {

    private static String fixture(String name) throws Exception {
        try (InputStream in = VolumeCoverParserTest.class
                .getClassLoader().getResourceAsStream("catalog/" + name)) {
            assertNotNull("fixture tidak ditemukan: " + name, in);
            try (Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name())) {
                scanner.useDelimiter("\\A");
                return scanner.hasNext() ? scanner.next() : "";
            }
        }
    }

    private static Set<String> urls(Map<Integer, VolumeCoverParser.Found> covers) {
        Set<String> out = new HashSet<>();
        for (VolumeCoverParser.Found found : covers.values()) out.add(found.url);
        return out;
    }

    /** Magical★Explorer prints a cover after every "Volume N" heading. */
    @Test
    public void magicalCoversFoundForEveryVolume() throws Exception {
        Map<Integer, VolumeCoverParser.Found> covers =
                VolumeCoverParser.parse(fixture("magical_landing.html"));
        assertFalse("tidak ada cover terdeteksi", covers.isEmpty());
        for (int volume = 1; volume <= 14; volume++) {
            assertTrue("cover volume " + volume + " tidak ditemukan: " + covers.keySet(),
                    covers.containsKey(volume));
            VolumeCoverParser.Found found = covers.get(volume);
            assertEquals(volume, found.volume);
            assertTrue("cover volume " + volume + " harus URL gambar: " + found.url,
                    found.url.startsWith("https://"));
            // Volume 1 artwork on this page is hosted outside Blogger, so the only
            // requirement is a real https image, not a particular host.
            assertTrue("cover volume " + volume + " harus berkas gambar: " + found.url,
                    found.url.matches("(?i).*\\.(jpe?g|png|webp)(\\?.*)?$"));
        }
    }

    /** No two volumes may share the same cover image. */
    @Test
    public void magicalCoversAreAllDifferent() throws Exception {
        Map<Integer, VolumeCoverParser.Found> covers =
                VolumeCoverParser.parse(fixture("magical_landing.html"));
        assertEquals("setiap volume harus punya cover sendiri", covers.size(),
                urls(covers).size());
    }

    /** Monogatari landing also lists per-volume covers after each heading. */
    @Test
    public void monogatariCoversFoundForEveryVolume() throws Exception {
        Map<Integer, VolumeCoverParser.Found> covers =
                VolumeCoverParser.parse(fixture("monogatari_landing.html"));
        for (int volume = 1; volume <= 7; volume++) {
            assertTrue("cover volume " + volume + " tidak ditemukan: " + covers.keySet(),
                    covers.containsKey(volume));
        }
        assertEquals("cover Monogatari harus berbeda tiap volume", covers.size(),
                urls(covers).size());
    }

    /** The stored URL must ask for a large image, not the 320 px thumbnail. */
    @Test
    public void coverUrlsAreUpscaled() {
        String html = "<html><body><div class='post-body entry-content'>"
                + "<p>Volume 1</p>"
                + "<div class='separator'><a href='#'>"
                + "<img src='https://blogger.googleusercontent.com/img/b/R29vZ2xl/abc/s320/v1.jpg' "
                + "data-original-width='1080' data-original-height='1536' /></a></div>"
                + "</div></body></html>";
        Map<Integer, VolumeCoverParser.Found> covers = VolumeCoverParser.parse(html);
        assertNotNull("cover harus ada", covers.get(1));
        String url = covers.get(1).url;
        assertTrue("ukuran harus dinaikkan: " + url,
                url.contains("/s1600/") || url.contains("=s1600") || url.contains("/w1600"));
    }

    /** A URL that carries no size segment is left exactly as the page prints it. */
    @Test
    public void coverUrlWithoutSizeSegmentIsUnchanged() {
        String url = "https://bagisharenovel.wordpress.com/wp-content/uploads/2021/12/001.png?w=722";
        assertEquals(url, VolumeCoverParser.upscale(url));
    }

    /** A volume without any image must simply have no entry, never a wrong one. */
    @Test
    public void volumeWithoutImageGetsNoCover() {
        String html = "<html><body><div class='post-body entry-content'>"
                + "<p>Volume 1</p>"
                + "<div class='separator'><img src='https://blogger.googleusercontent.com/img/a/v1.jpg' /></div>"
                + "<p>Volume 2</p>"
                + "<p><a href='https://kaoritranslation.blogspot.com/2025/04/ln-x-volume-2-chapter-1.html'>Chapter 1</a></p>"
                + "</div></body></html>";
        Map<Integer, VolumeCoverParser.Found> covers = VolumeCoverParser.parse(html);
        assertTrue(covers.containsKey(1));
        assertFalse("volume 2 tidak punya gambar, jangan pakai cover lain",
                covers.containsKey(2));
    }

    /** Only the first picture after a heading is the cover, not later plates. */
    @Test
    public void onlyTheFirstPictureAfterEachHeadingIsTheCover() {
        String html = "<html><body><div class='post-body entry-content'>"
                + "<p>Volume 1</p>"
                + "<div class='separator'><img src='https://blogger.googleusercontent.com/img/a/c1.jpg' /></div>"
                + "<p><a href='https://kaoritranslation.blogspot.com/2025/04/ln-x-volume-1-chapter-1.html'>Chapter 1</a></p>"
                + "<div class='separator'><img src='https://blogger.googleusercontent.com/img/a/plate1.jpg' /></div>"
                + "<p>Volume 2</p>"
                + "<div class='separator'><img src='https://blogger.googleusercontent.com/img/a/c2.jpg' /></div>"
                + "</div></body></html>";
        Map<Integer, VolumeCoverParser.Found> covers = VolumeCoverParser.parse(html);
        assertTrue(covers.get(1).url.contains("c1.jpg"));
        assertTrue(covers.get(2).url.contains("c2.jpg"));
    }
}
