package id.shuyasal.kuromaku.parser;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.Volume;

/**
 * Builds a catalogue from the chapter links printed on a novel's landing page.
 *
 * <p>Magical★Explorer does not publish one Blogger label for the whole series,
 * and its posts are not named consistently, so the label feed cannot be used as
 * the source of truth. The landing page, however, lists every volume heading and
 * the chapter links that belong to it. Those links are the catalogue:
 *
 * <ul>
 *   <li>A link is only accepted when its URL carries this novel's slug prefix, so
 *       a stray link to another series can never be imported.</li>
 *   <li>The volume comes from the "Volume N" heading above the link, not from the
 *       URL and not from an assumed label.</li>
 *   <li>Chapter labels are normalised, including misspellings such as
 *       "Cahpter 2", so the reading order stays correct.</li>
 *   <li>Text that has no link never becomes a chapter: a heading that was never
 *       published must not turn into a dead entry.</li>
 * </ul>
 */
public final class LandingCatalogParser {

    /** Reading order keys inside a volume. */
    static final int ORDER_PROLOG = 10;
    static final int ORDER_CHAPTER_BASE = 100;
    static final int ORDER_INTERLUDE = 380;
    static final int ORDER_EPILOG = 500;
    static final int ORDER_AFTERWORD = 520;
    static final int ORDER_ILLUSTRATION = 600;
    static final int ORDER_UNKNOWN = 300;

    /** Heading above a group of links. Tolerates &nbsp; and stray markup. */
    private static final Pattern VOLUME_HEADING =
            Pattern.compile("^\\s*volume\\s*(\\d{1,2})\\s*$", Pattern.CASE_INSENSITIVE);

    /**
     * "Chapter 7", plus the source's own typos. The page spells volume 3 chapter
     * 2 as "Cahpter 2", so the letters are allowed to swap: without this the
     * chapter would fall to the end of the volume instead of sitting second.
     */
    private static final Pattern CHAPTER =
            Pattern.compile("(?:ch(?:ap|pa)ter|cahpter|cap?hter)\\s*(\\d{1,3})",
                    Pattern.CASE_INSENSITIVE);

    /** "Chapter 8+Afterword", "Epilog+Afterword", "Chapter 10+Afterword". */
    private static final Pattern EXTRA =
            Pattern.compile("(afterword|epilog\\w*|ilustrasi|illustrasi|illustration|prolog\\w*)",
                    Pattern.CASE_INSENSITIVE);

    private LandingCatalogParser() {
    }

    /**
     * Parses the landing page.
     *
     * @param html       full landing page HTML
     * @param slugPrefix part of the URL shared by this novel's posts,
     *                   e.g. {@code ln-magical-explorer-}
     * @param novelId    id stored on the returned catalogue
     */
    public static List<Volume> parse(String html, String slugPrefix, String novelId) {
        List<Volume> result = new ArrayList<>();
        if (html == null || html.isEmpty()) return result;

        Document document = org.jsoup.Jsoup.parse(html, "https://kaoritranslation.blogspot.com/");
        Element body = articleBody(document);
        if (body == null) return result;

        // volume number -> chapters, in document order
        List<Integer> order = new ArrayList<>();
        List<List<Chapter>> grouped = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();

        int currentVolume = 0;
        // A heading can be split across nodes: the source prints "Volume&nbsp;"
        // inside one span and the number inside the next. Accumulate text until
        // something that is not a heading interrupts it, otherwise Volume 2 and
        // every volume after it would be missed and their links would pile up
        // under Volume 1.
        StringBuilder buffer = new StringBuilder();
        // Headings are remembered on their own: volume 14 exists on the page with
        // its cover but without a single chapter link yet. It must still appear,
        // empty, instead of being invented chapters or silently dropped.
        Set<Integer> headings = new LinkedHashSet<>();
        for (Node node : flatten(body)) {
            if (node instanceof TextNode) {
                buffer.append(((TextNode) node).text());
                int found = volumeHeading(buffer.toString());
                if (found > 0) {
                    currentVolume = found;
                    headings.add(found);
                    buffer.setLength(0);
                } else if (!looksLikeVolumeStart(buffer.toString())) {
                    buffer.setLength(0);
                }
                continue;
            }
            if (!(node instanceof Element)) continue;

            Element element = (Element) node;
            if ("img".equals(element.tagName())) {
                buffer.setLength(0);
                continue;
            }
            if (!"a".equals(element.tagName())) continue;

            String href = element.attr("abs:href");
            if (href == null || href.isEmpty()) href = element.attr("href");
            if (!belongsToNovel(href, slugPrefix)) continue;

            String raw = element.text();
            if (raw == null || raw.trim().isEmpty()) continue;

            // A volume can print two labels over one URL when the source links the
            // wrong post (Monogatari volume 2 lists "Prolog" and "Chapter 1" both
            // pointing at the chapter 1 post). Deduplicating on the URL alone kept
            // the first label and silently dropped the second, so a chapter vanished
            // and the surviving label looked like it owned the wrong post.
            // Keying on URL + volume + label keeps both, and still refuses a link
            // that is printed twice under the same name.
            String key = href + "|" + currentVolume + "|" + raw.trim().toLowerCase(Locale.ROOT);
            if (!seenUrls.add(key)) continue;

            int volume = currentVolume > 0 ? currentVolume : volumeFromUrl(href);
            String label = label(raw);
            int sortOrder = order(raw, label);

            Chapter chapter = new Chapter(raw.trim(), label, href, "", sortOrder, volume);
            int slot = order.indexOf(volume);
            if (slot < 0) {
                order.add(volume);
                List<Chapter> list = new ArrayList<>();
                list.add(chapter);
                grouped.add(list);
            } else {
                grouped.get(slot).add(chapter);
            }
        }

        for (int i = 0; i < order.size(); i++) {
            List<Chapter> chapters = grouped.get(i);
            chapters.sort((left, right) -> {
                if (left.order != right.order) return left.order - right.order;
                return left.label.compareToIgnoreCase(right.label);
            });
            result.add(new Volume(order.get(i), chapters));
        }
        for (int heading : headings) {
            if (!order.contains(heading)) result.add(new Volume(heading, new ArrayList<Chapter>()));
        }
        // Volumes ascending; an unknown volume goes last.
        result.sort((left, right) -> {
            boolean leftUnknown = left.number <= 0;
            boolean rightUnknown = right.number <= 0;
            if (leftUnknown != rightUnknown) return leftUnknown ? 1 : -1;
            return left.number - right.number;
        });
        return result;
    }

    /** Article container, same selectors as the chapter parser. */
    private static Element articleBody(Document document) {
        String[] selectors = {
                "div.post-body.entry-content",
                "div.post-body",
                "div.entry-content",
                "article .post-body",
                "article"
        };
        for (String selector : selectors) {
            Elements found = document.select(selector);
            if (!found.isEmpty()) return found.first();
        }
        return document.body();
    }

    /** Document-order walk that keeps text nodes, so headings are not lost. */
    private static List<Node> flatten(Element root) {
        List<Node> out = new ArrayList<>();
        collect(root, out);
        return out;
    }

    private static void collect(Node node, List<Node> out) {
        for (Node child : node.childNodes()) {
            if (child instanceof TextNode) {
                out.add(child);
            } else if (child instanceof Element) {
                Element element = (Element) child;
                String tag = element.tagName();
                if ("script".equals(tag) || "style".equals(tag)) continue;
                out.add(element);
                // Anchors are leaves for our purposes; their text is read above.
                if ("a".equals(tag)) continue;
                collect(element, out);
            }
        }
    }

    /** "Volume 7" heading → 7; anything else → 0. */
    static int volumeHeading(String text) {
        if (text == null) return 0;
        String normalised = text.replace('\u00a0', ' ').replace('\u200b', ' ')
                .replaceAll("\\s+", " ").trim();
        if (normalised.isEmpty()) return 0;
        Matcher matcher = VOLUME_HEADING.matcher(normalised);
        if (!matcher.find()) return 0;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /**
     * True while the accumulated text could still turn into a volume heading,
     * e.g. "Volume" or "Volume&nbsp;" before the number arrives.
     */
    static boolean looksLikeVolumeStart(String text) {
        if (text == null) return false;
        String normalised = text.replace('\u00a0', ' ').replace('\u200b', ' ')
                .replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        if (normalised.isEmpty()) return true;
        if (normalised.length() > 20) return false;
        return "volume".startsWith(normalised) || normalised.startsWith("volume");
    }

    /** True when the link points at this novel and nothing else. */
    static boolean belongsToNovel(String href, String slugPrefix) {
        if (href == null || href.isEmpty()) return false;
        if (!href.contains("kaoritranslation.blogspot.com")) return false;
        if (!href.contains("/20")) return false; // dated post path
        if (slugPrefix == null || slugPrefix.isEmpty()) return true;
        String path = href.toLowerCase(Locale.ROOT);
        if (!path.contains(slugPrefix.toLowerCase(Locale.ROOT))) return false;
        // Reject the landing page itself: it is not a chapter.
        return !path.endsWith(".html?m=1") || path.contains("-chapter") || path.contains("-chapter-");
    }

    /** Volume number taken from a slug such as {@code ...-volume-6-chapter-3}. */
    static int volumeFromUrl(String href) {
        if (href == null) return 0;
        Matcher matcher = Pattern.compile("volume-(\\d{1,2})").matcher(href);
        if (!matcher.find()) return 0;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /**
     * Short label shown in the library. Misspellings are corrected here so the
     * list reads correctly, while the original title stays in {@code Chapter.title}.
     */
    static String label(String raw) {
        if (raw == null) return "Bab";
        String text = raw.replace('\u00a0', ' ').trim();

        Matcher chapter = CHAPTER.matcher(text);
        if (chapter.find()) {
            int number = 0;
            try {
                number = Integer.parseInt(chapter.group(1));
            } catch (NumberFormatException ignored) {
                // keep 0
            }
            String base = "Chapter " + number;
            Matcher extra = EXTRA.matcher(text);
            if (extra.find()) {
                String tail = extra.group(1).toLowerCase(Locale.ROOT);
                if (tail.startsWith("afterword")) base += " + Afterword";
                else if (tail.startsWith("epilog")) base += " + Epilog";
            }
            return base;
        }

        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("prolog")) return "Prolog";
        if (lower.contains("interlude")) return "Interlude";
        if (lower.contains("ilustrasi") || lower.contains("illustrasi")
                || lower.contains("illustration")) return "Illustrasi";
        boolean epilog = lower.contains("epilog");
        boolean afterword = lower.contains("afterword") || lower.contains("kata penutup");
        if (epilog && afterword) return "Epilog + Afterword";
        if (lower.contains("epilog")) return "Epilog";
        if (lower.contains("afterword") || lower.contains("kata penutup")) return "Afterword";
        if (text.length() > 48) return text.substring(0, 47) + "…";
        return text;
    }

    /** Reading order inside a volume. Chapter 0 sorts before Chapter 1. */
    static int order(String raw, String label) {
        if (raw == null) return ORDER_UNKNOWN;
        String lower = raw.toLowerCase(Locale.ROOT);

        Matcher chapter = CHAPTER.matcher(raw);
        if (chapter.find()) {
            int number = 0;
            try {
                number = Integer.parseInt(chapter.group(1));
            } catch (NumberFormatException ignored) {
                // keep 0
            }
            return ORDER_CHAPTER_BASE + number;
        }
        if (lower.contains("prolog")) return ORDER_PROLOG;
        if (lower.contains("interlude")) return ORDER_INTERLUDE;
        if (lower.contains("ilustrasi") || lower.contains("illustrasi")
                || lower.contains("illustration")) return ORDER_ILLUSTRATION;
        if (lower.contains("epilog")) return ORDER_EPILOG;
        if (lower.contains("afterword")) return ORDER_AFTERWORD;
        return ORDER_UNKNOWN;
    }
}
