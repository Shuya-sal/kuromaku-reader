package id.shuyasal.kuromaku.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import id.shuyasal.kuromaku.data.Block;

/**
 * Turns a Blogspot post page into an ordered list of readable blocks.
 *
 * <p>Only the post body is inspected. Scripts, ad containers, iframes, share
 * widgets and navigation chrome are dropped before walking the tree, so nothing
 * from the surrounding website reaches the reader.
 */
public final class ChapterParser {

    private static final Set<String> SKIP_TAGS = new HashSet<>(Arrays.asList(
            "script", "style", "noscript", "iframe", "ins", "form", "button",
            "input", "select", "textarea", "svg", "canvas", "video", "audio",
            "object", "embed", "template", "nav", "aside", "footer"));

    private static final Set<String> SKIP_CLASS_HINTS = new HashSet<>(Arrays.asList(
            "adsbygoogle", "ad-container", "ad-slot", "sponsored", "post-share",
            "share-buttons", "share-button", "reactions", "post-footer",
            "blog-pager", "post-nav", "comments", "comment-form", "related-posts",
            "popup", "modal", "cookie", "newsletter", "ignielad", "igniel-ad"));

    private static final Set<String> BLOCK_TAGS = new HashSet<>(Arrays.asList(
            "p", "div", "section", "article", "main", "center", "table", "tbody",
            "tr", "td", "ul", "ol", "dl", "dd", "dt", "figure"));

    /** Selectors of the article container, most specific first. */
    private static final String[] BODY_SELECTORS = {
            "div.post-body.entry-content",
            "div.post-body",
            "div.entry-content",
            "article .post-body",
            "article"
    };

    /**
     * Blogger serves resized variants in two shapes: the legacy path form
     * ({@code /s320/name.jpg}) and the newer query form ({@code ...=s320}).
     * Both are rewritten to a large variant so illustrations keep their detail.
     */
    private static final Pattern BLOGGER_EQUALS_SIZE = Pattern.compile(
            "=(s\\d+(-c)?|w\\d+(-h\\d+)?(-p)?(-k)?(-no-nu)?|s\\d+-rw)");

    private static final Pattern BLOGGER_PATH_SIZE = Pattern.compile(
            "/(s\\d+(-c)?|w\\d+(-h\\d+)?)(?=/)");

    private static final String FULL_SIZE = "s1600";

    /** Attribute order for illustrations: lazy-load sources win over placeholders. */
    private static final String[] IMAGE_ATTRIBUTES =
            {"data-src", "data-original", "data-lazy-src", "src"};

    private static final int MAX_BLOCKS = 4000;

    private ChapterParser() {
    }

    public static ChapterContent parse(String html, String sourceUrl) throws ParseException {
        if (html == null || html.trim().isEmpty()) {
            throw new ParseException("Halaman kosong diterima dari sumber.");
        }

        Document doc;
        try {
            doc = Jsoup.parse(html, sourceUrl == null ? "" : sourceUrl);
        } catch (Exception error) {
            throw new ParseException("HTML tidak dapat dibaca.", error);
        }

        Element body = findBody(doc);
        if (body == null) {
            throw new ParseException("Isi artikel tidak ditemukan pada halaman sumber.");
        }

        scrub(body);

        String title = findTitle(doc);

        List<Block> blocks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        walk(body, blocks, buffer);
        flush(buffer, blocks);

        blocks = tidy(blocks);

        if (blocks.isEmpty()) {
            throw new ParseException("Isi artikel kosong setelah pembersihan.");
        }

        return new ChapterContent(title, sourceUrl, blocks);
    }

    private static Element findBody(Document doc) {
        for (String selector : BODY_SELECTORS) {
            Elements found = doc.select(selector);
            if (!found.isEmpty()) {
                return found.first();
            }
        }
        return null;
    }

    private static String findTitle(Document doc) {
        String[] selectors = {"h1.entry-title", ".post-title h1", ".post-title",
                "h1.post-title", "meta[property=og:title]", "title"};
        for (String selector : selectors) {
            Elements found = doc.select(selector);
            if (found.isEmpty()) continue;
            Element element = found.first();
            String raw = element.hasAttr("content")
                    ? element.attr("content") : element.text();
            String cleaned = clean(raw);
            if (cleaned.isEmpty()) continue;
            cleaned = cleaned.replaceAll("\\s*[-|]\\s*Kaori TL\\s*$", "").trim();
            if (!cleaned.isEmpty()) return cleaned;
        }
        return "Tanpa judul";
    }

    /** Removes everything that is not article content. */
    private static void scrub(Element body) {
        for (String tag : SKIP_TAGS) {
            body.select(tag).remove();
        }
        for (Element element : body.getAllElements()) {
            String classes = element.className().toLowerCase();
            if (classes.isEmpty()) continue;
            for (String hint : SKIP_CLASS_HINTS) {
                if (classes.contains(hint)) {
                    element.remove();
                    break;
                }
            }
        }
        // "more" anchors and other empty inline leftovers
        body.select("a[name=more]").remove();
        body.select("br.clear").remove();
    }

    private static void walk(Node node, List<Block> out, StringBuilder buffer) {
        for (Node child : new ArrayList<>(node.childNodes())) {
            if (out.size() > MAX_BLOCKS) return;

            if (child instanceof TextNode) {
                buffer.append(((TextNode) child).text());
                continue;
            }
            if (!(child instanceof Element)) continue;

            Element element = (Element) child;
            String tag = element.tagName().toLowerCase();

            if (SKIP_TAGS.contains(tag)) continue;

            if ("img".equals(tag)) {
                flush(buffer, out);
                String url = resolveImage(element);
                if (url != null) {
                    // The source declares the printed pixel size; carrying it
                    // through lets the reader reserve a correctly shaped box
                    // before the bitmap arrives (otherwise the ImageView can
                    // measure to zero width and the illustration never shows).
                    out.add(Block.image(url, clean(element.attr("alt")),
                            dimension(element, "data-original-width", "width"),
                            dimension(element, "data-original-height", "height")));
                }
                continue;
            }

            if ("br".equals(tag)) {
                flush(buffer, out);
                continue;
            }

            if ("hr".equals(tag)) {
                flush(buffer, out);
                out.add(Block.divider());
                continue;
            }

            if (tag.length() == 2 && tag.charAt(0) == 'h' && Character.isDigit(tag.charAt(1))) {
                flush(buffer, out);
                String text = clean(element.text());
                if (!text.isEmpty()) {
                    int level = tag.charAt(1) - '0';
                    out.add(Block.heading(text, level));
                }
                continue;
            }

            if ("blockquote".equals(tag)) {
                flush(buffer, out);
                String text = clean(element.text());
                if (!text.isEmpty()) out.add(Block.quote(text));
                continue;
            }

            if ("li".equals(tag)) {
                flush(buffer, out);
                String text = clean(element.text());
                if (!text.isEmpty()) out.add(Block.listItem(text));
                continue;
            }

            boolean block = BLOCK_TAGS.contains(tag);
            if (block) flush(buffer, out);
            walk(element, out, buffer);
            if (block) flush(buffer, out);
        }
    }

    private static void flush(StringBuilder buffer, List<Block> out) {
        String text = clean(buffer.toString());
        buffer.setLength(0);
        if (text.isEmpty()) return;
        out.add(Block.paragraph(text));
    }

    /** Collapses whitespace, removes soft hyphens and non-breaking spaces. */
    static String clean(String raw) {
        if (raw == null) return "";
        String text = raw
                .replace('\u00a0', ' ')
                .replace('\u200b', ' ')
                .replace("\u00ad", "")
                .replace("\r", " ")
                .replace("\n", " ")
                .replace("\t", " ");
        text = text.replaceAll("\\s{2,}", " ").trim();
        return text;
    }

    /** Picks the best available image URL and upgrades Blogger thumbnails. */
    static String resolveImage(Element img) {
        String url = null;
        for (String attribute : IMAGE_ATTRIBUTES) {
            url = usable(img.attr(attribute));
            if (url != null) break;
        }
        // Lazy-loader posts keep a base64 placeholder in src and the real file in
        // srcset. A placeholder must never end the search, otherwise the whole
        // illustration is dropped and the reader shows an empty gap.
        if (url == null) url = usable(fromSrcSet(img.attr("srcset")));
        if (url == null) return null;

        url = url.replace("http://", "https://");
        if (url.contains("googleusercontent.com")) {
            Matcher equalsForm = BLOGGER_EQUALS_SIZE.matcher(url);
            if (equalsForm.find()) {
                url = equalsForm.replaceFirst("=" + FULL_SIZE);
            } else {
                Matcher pathForm = BLOGGER_PATH_SIZE.matcher(url);
                if (pathForm.find()) {
                    url = pathForm.replaceFirst("/" + FULL_SIZE);
                }
            }
        }
        return url;
    }

    /**
     * Normalises one candidate URL, or returns null when it cannot be fetched.
     * {@code data:} and {@code file:} values are placeholders, not pictures, so
     * the caller is expected to keep looking rather than stop here.
     */
    private static String usable(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty()) return null;
        if (value.startsWith("data:") || value.startsWith("file:")) return null;
        if (value.startsWith("//")) return "https:" + value;
        if (!value.startsWith("http://") && !value.startsWith("https://")) return null;
        return value;
    }

    private static String fromSrcSet(String srcset) {
        if (srcset == null || srcset.trim().isEmpty()) return null;
        String best = null;
        int bestWidth = -1;
        for (String candidate : srcset.split(",")) {
            String[] parts = candidate.trim().split("\\s+");
            if (parts.length == 0) continue;
            String candidateUrl = parts[0];
            int width = 0;
            if (parts.length > 1 && parts[1].endsWith("w")) {
                try {
                    width = Integer.parseInt(parts[1].substring(0, parts[1].length() - 1));
                } catch (NumberFormatException ignored) {
                }
            }
            if (width > bestWidth) {
                bestWidth = width;
                best = candidateUrl;
            }
        }
        return best;
    }

    /**
     * Reads a pixel dimension, preferring the untouched original size over the
     * rendered {@code width}/{@code height} attributes, which Blogger shrinks.
     */
    static int dimension(Element img, String primary, String fallback) {
        for (String attribute : new String[]{primary, fallback}) {
            String raw = img.attr(attribute).trim();
            if (raw.isEmpty()) continue;
            try {
                int value = Integer.parseInt(raw.replaceAll("[^0-9]", ""));
                if (value > 0) return value;
            } catch (NumberFormatException ignored) {
                // try the next attribute
            }
        }
        return 0;
    }

    /** Drops duplicate images and repeated paragraphs, then trims the tail. */
    private static List<Block> tidy(List<Block> blocks) {
        List<Block> out = new ArrayList<>();
        Set<String> seenImages = new LinkedHashSet<>();
        String lastText = null;

        for (Block block : blocks) {
            if (block.isImage()) {
                if (block.imageUrl == null) continue;
                if (!seenImages.add(block.imageUrl)) continue;
                out.add(block);
                lastText = null;
                continue;
            }
            if (block.text == null || block.text.isEmpty()) continue;
            if (block.text.equals(lastText)) continue;
            out.add(block);
            lastText = block.text;
        }

        while (!out.isEmpty() && out.get(out.size() - 1).type == Block.TYPE_DIVIDER) {
            out.remove(out.size() - 1);
        }
        return out;
    }
}
