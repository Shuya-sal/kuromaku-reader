package id.shuyasal.kuromaku.parser;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the per-volume cover artwork from a novel's landing page.
 *
 * <p>Both sources lay the page out the same way: a heading such as
 * "Volume 5" followed by exactly one picture, then the links for that volume.
 * The cover of a volume is therefore the first picture that appears after its
 * own heading and before the next heading — never the series artwork, and never
 * a plate taken from inside a chapter.
 *
 * <p>Keeping this parse separate from the hard-coded table lets
 * {@code VolumeCoverParserTest} re-derive the covers from the saved source page
 * and fail if any volume ever points at another volume's picture.
 */
public final class VolumeCoverParser {

    /** A cover as found on the page, before any size upgrade. */
    public static final class Found {
        public final int volume;
        public final String url;
        public final int width;
        public final int height;

        Found(int volume, String url, int width, int height) {
            this.volume = volume;
            this.url = url;
            this.width = width;
            this.height = height;
        }
    }

    private static final Pattern VOLUME_HEADING =
            Pattern.compile("^\\s*volume\\s*(\\d{1,2})\\s*$", Pattern.CASE_INSENSITIVE);

    private VolumeCoverParser() {
    }

    /**
     * Covers by volume number, in page order.
     *
     * @param html full landing page HTML
     */
    public static Map<Integer, Found> parse(String html) {
        if (html == null || html.isEmpty()) return new LinkedHashMap<>();
        Document document = org.jsoup.Jsoup.parse(html, "https://kaoritranslation.blogspot.com/");
        Element body = articleBody(document);
        if (body == null) return new LinkedHashMap<>();
        return parse(body);
    }

    /**
     * Covers by volume number, from an article body that is already parsed.
     *
     * <p>The catalogue parser has the body in hand, so it must not pay for a
     * second parse of the same page just to learn the covers. Reading them from
     * the very same body also guarantees the covers and the chapters come from one
     * document, not two fetches that could disagree.
     */
    public static Map<Integer, Found> parse(Element body) {
        Map<Integer, Found> covers = new LinkedHashMap<>();
        if (body == null) return covers;

        List<Node> nodes = new ArrayList<>();
        collect(body, nodes);

        int currentVolume = 0;
        // Set while walking the picture that belongs to currentVolume.
        boolean waitingForCover = false;
        // True once a link has been seen under the current heading. A cover is
        // printed *above* the chapter list, so a picture that only appears after
        // the first link belongs to a chapter, not to the volume. Without this
        // guard an illustration would stand in for a missing cover.
        boolean linkSeen = false;
        // The page writes some headings as "Volume&nbsp;" in one span and the
        // number in the next, so a single text node is not enough. Without this
        // buffer volume 2 is skipped and its cover is never found.
        StringBuilder buffer = new StringBuilder();

        for (Node node : nodes) {
            if (node instanceof TextNode) {
                buffer.append(((TextNode) node).text().replace('\u00a0', ' ').replace('\u200b', ' '));
                String candidate = buffer.toString().replaceAll("\\s+", " ").trim();
                Matcher matcher = VOLUME_HEADING.matcher(candidate);
                if (matcher.find()) {
                    try {
                        currentVolume = Integer.parseInt(matcher.group(1));
                        waitingForCover = true;
                        linkSeen = false;
                        buffer.setLength(0);
                    } catch (NumberFormatException ignored) {
                        // leave as is
                    }
                } else if (candidate.isEmpty() || "volume".startsWith(candidate.toLowerCase(java.util.Locale.ROOT))
                        || candidate.toLowerCase(java.util.Locale.ROOT).startsWith("volume ")) {
                    // still waiting for the number
                } else {
                    buffer.setLength(0);
                }
                continue;
            }
            if (!(node instanceof Element)) continue;

            Element element = (Element) node;
            if ("a".equals(element.tagName()) && currentVolume > 0) {
                // The chapter list has started for this volume.
                linkSeen = true;
            }
            if ("img".equals(element.tagName())) buffer.setLength(0);
            if (!"img".equals(element.tagName())) continue;
            if (currentVolume <= 0 || !waitingForCover) continue;
            if (covers.containsKey(currentVolume)) continue;
            if (linkSeen) {
                // First picture came after a link: it is chapter artwork.
                waitingForCover = false;
                continue;
            }

            String url = resolve(element);
            if (url == null) continue;

            int width = dimension(element, "data-original-width", "width");
            int height = dimension(element, "data-original-height", "height");
            covers.put(currentVolume, new Found(currentVolume, url, width, height));
            waitingForCover = false;
        }
        return covers;
    }

    private static Element articleBody(Document document) {
        String[] selectors = {
                "div.post-body.entry-content",
                "div.post-body",
                "div.entry-content",
                "article .post-body",
                "article"
        };
        for (String selector : selectors) {
            if (!document.select(selector).isEmpty()) return document.select(selector).first();
        }
        return document.body();
    }

    private static void collect(Node node, List<Node> out) {
        for (Node child : node.childNodes()) {
            if (child instanceof TextNode) {
                out.add(child);
            } else if (child instanceof Element) {
                Element element = (Element) child;
                String tag = element.tagName();
                if ("script".equals(tag) || "style".equals(tag) || "noscript".equals(tag)) continue;
                if ("a".equals(tag)) {
                    // A link wrapping the cover still exposes its picture, so keep
                    // descending, but do not record the anchor itself.
                    collect(element, out);
                    continue;
                }
                out.add(element);
                collect(element, out);
            }
        }
    }

    /** First usable picture URL on the element. */
    private static String resolve(Element img) {
        String[] attributes = {"data-src", "data-original", "src"};
        for (String attribute : attributes) {
            String value = usable(img.attr(attribute));
            if (value != null) return upscale(value);
        }
        String srcset = img.attr("srcset");
        if (!srcset.trim().isEmpty()) {
            // srcset lists the widest candidate last; take the largest so the
            // cover is not shown from a 320 px thumbnail.
            String best = null;
            int bestWidth = -1;
            for (String candidate : srcset.split(",")) {
                String[] parts = candidate.trim().split("\\s+");
                String value = usable(parts[0]);
                if (value == null) continue;
                int width = 0;
                if (parts.length > 1) {
                    String size = parts[1].replaceAll("[^0-9]", "");
                    try {
                        width = Integer.parseInt(size);
                    } catch (NumberFormatException ignored) {
                        width = 0;
                    }
                }
                if (width > bestWidth) {
                    bestWidth = width;
                    best = value;
                }
            }
            if (best != null) return upscale(best);
        }
        return null;
    }

    /**
     * Blogger serves the same picture at any width through a size segment such
     * as {@code /s320/}. Ask for a large copy so a cover is never blurry when the
     * user taps it to enlarge.
     */
    public static String upscale(String url) {
        if (url == null || url.isEmpty()) return url;
        if (!url.contains("blogger.googleusercontent.com")
                && !url.contains("bp.blogspot.com")) return url;
        String sized = url
                .replaceAll("/s\\d{2,4}(-[a-z0-9-]+)?/", "/s1600/")
                .replaceAll("/w\\d{2,4}-h\\d{2,4}(-[a-z0-9-]+)?/", "/s1600/")
                .replaceAll("=s\\d{2,4}(-[a-z0-9-]+)?$", "=s1600")
                .replaceAll("=w\\d{2,4}-h\\d{2,4}(-[a-z0-9-]+)?$", "=s1600");
        return sized;
    }

    private static String usable(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty()) return null;
        if (value.startsWith("data:") || value.startsWith("file:")) return null;
        if (value.startsWith("//")) return "https:" + value;
        if (!value.startsWith("http://") && !value.startsWith("https://")) return null;
        return value.replace("http://", "https://");
    }

    private static int dimension(Element img, String primary, String fallback) {
        for (String attribute : new String[]{primary, fallback}) {
            String raw = img.attr(attribute).trim();
            if (raw.isEmpty()) continue;
            try {
                int value = Integer.parseInt(raw.replaceAll("[^0-9]", ""));
                if (value > 0) return value;
            } catch (NumberFormatException ignored) {
                // next attribute
            }
        }
        return 0;
    }
}
