package id.shuyasal.kuromaku.parser;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.Novel;
import id.shuyasal.kuromaku.data.Volume;

/**
 * Reads a novel straight from a Kaori Translation catalogue page, without knowing
 * which novel it is.
 *
 * <p>The two novels that ship with the app each got their own parser because
 * their pages differ. A novel the user adds by URL has no such luxury, so this
 * class works from what the pages have in common and reports honestly when it
 * cannot:
 *
 * <ul>
 *   <li><b>Metadata</b> is read from labelled lines ("Author :", "Illustrator :",
 *       "Genre :", "Japanese Tittle :", "Status :") and from the article's own
 *       heading. A field the page does not print stays empty — it is never filled
 *       with a guess, and the preview says which ones are missing.</li>
 *   <li><b>Chapters</b> are the article links inside the post body that either
 *       read like a chapter ("Chapter 12", "Prolog", "Epilog", "Afterword",
 *       "Illustrasi") or point at a slug containing {@code chapter-N} /
 *       {@code volume-N}. Navigation, sidebars, share widgets, label pages, the
 *       front page and other series' posts are excluded.</li>
 *   <li><b>Volumes</b> come from the "Volume N" heading above a link, then from
 *       the link text, then from the slug. A catalogue that never states volumes
 *       keeps its chapters in one unnamed volume rather than inventing numbers.</li>
 *   <li><b>Warnings</b> list what could not be mapped, so the preview can say
 *       "3 tautan tidak dapat dipetakan" instead of silently dropping them.</li>
 * </ul>
 *
 * <p>This is a heuristic reader, not a guarantee. Pages that hide the chapter list
 * behind JavaScript, use a different source, or print chapters as plain text
 * without links will be refused with a readable message instead of being stored
 * as an empty novel.
 */
public final class NovelDiscovery {

    /** Thrown when the page cannot be understood as a catalogue. */
    public static final class Unreadable extends Exception {
        public Unreadable(String message) {
            super(message);
        }
    }

    /** Everything the preview needs, plus the parsed catalogue. */
    public static final class Result {
        public final String sourceUrl;
        public final String title;
        public final String titleJp;
        public final String fullTitle;
        public final String author;
        public final String illustrator;
        public final String genres;
        public final String status;
        public final String coverUrl;
        public final float coverRatio;
        public final String[] synopsis;
        public final List<Volume> volumes;
        /** Non-fatal problems: unmapped links, missing metadata, missing volumes. */
        public final List<String> warnings;

        Result(String sourceUrl, String title, String titleJp, String fullTitle, String author,
               String illustrator, String genres, String status, String coverUrl, float coverRatio,
               String[] synopsis, List<Volume> volumes, List<String> warnings) {
            this.sourceUrl = sourceUrl;
            this.title = title;
            this.titleJp = titleJp;
            this.fullTitle = fullTitle;
            this.author = author;
            this.illustrator = illustrator;
            this.genres = genres;
            this.status = status;
            this.coverUrl = coverUrl;
            this.coverRatio = coverRatio;
            this.synopsis = synopsis;
            this.volumes = volumes;
            this.warnings = warnings;
        }

        public int chapterCount() {
            int total = 0;
            for (Volume volume : volumes) total += volume.chapterCount();
            return total;
        }

        /** Volumes that actually contain chapters. */
        public int filledVolumes() {
            int total = 0;
            for (Volume volume : volumes) {
                if (volume.chapterCount() > 0) total++;
            }
            return total;
        }

        /** Human list of metadata fields the page did not provide. */
        public List<String> missingMetadata() {
            List<String> missing = new ArrayList<>();
            if (titleJp.isEmpty()) missing.add("judul Jepang");
            if (author.isEmpty()) missing.add("penulis");
            if (illustrator.isEmpty()) missing.add("ilustrator");
            if (genres.isEmpty()) missing.add("genre");
            if (synopsis.length == 0) missing.add("sinopsis");
            if (coverUrl.isEmpty()) missing.add("cover");
            return missing;
        }
    }

    /** Classes that hold page chrome rather than the article. */
    private static final String[] BOILERPLATE = {
            "nav", "header", "footer", "aside", ".navbar", ".menu", ".sidebar", "#sidebar",
            ".widget", ".share", ".post-share-buttons", ".blog-pager", ".comments",
            ".related-posts", ".popular-posts", ".breadcrumb", ".post-footer", ".site-header",
            ".site-footer", ".ad", ".adsbygoogle", "script", "style", "noscript"
    };

    private static final Pattern LABEL_LINE = Pattern.compile(
            "^([A-Za-z][A-Za-z .]{2,24})\\s*[:：]\\s*(.+)$");

    /** "Author:" with nothing after it; the value is on the following line. */
    private static final Pattern LABEL_ONLY = Pattern.compile(
            "^[A-Za-z][A-Za-z .]{2,24}\\s*[:：]$");

    /** A bare word that may be a label when the next line is just ":". */
    private static final Pattern LABEL_WORD = Pattern.compile("^[A-Za-z][A-Za-z .]{2,24}$");

    private static final Pattern VOLUME_IN_TEXT =
            Pattern.compile("(?:^|\\b)volume\\s*(\\d{1,2})(?:\\b|$)", Pattern.CASE_INSENSITIVE);

    /** Slug of a chapter post: ...-chapter-12, ...-volume-3, ...-epilog ... */
    private static final Pattern CHAPTER_SLUG =
            Pattern.compile("(chapter|volume|prolog|epilog|afterword|ilustrasi|illustrasi|interlude)",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern IMAGE_SIZE = Pattern.compile("/(s|w)(\\d{2,4})(?:-h\\d+)?/");

    private NovelDiscovery() {
    }

    /**
     * Preview built from a novel that is already stored.
     *
     * <p>Used when the user pastes a URL that is already on the shelf: the page is
     * not fetched again, and the preview shows the stored metadata together with
     * whatever catalogue the session already holds. The warning makes clear that
     * this is a stored copy, not a fresh read, so nobody mistakes it for a fetch
     * that just succeeded.
     */
    public static Result fromStored(Novel novel, List<Volume> volumes) {
        List<Volume> safeVolumes = volumes == null ? new ArrayList<Volume>() : volumes;
        List<String> warnings = new ArrayList<>();
        warnings.add("Data ini berasal dari perpustakaan, bukan hasil pembacaan ulang halaman.");
        return new Result(
                novel.landingUrl == null ? "" : novel.landingUrl,
                novel.title,
                novel.titleJp,
                novel.fullTitle,
                novel.author,
                novel.illustrator,
                novel.genres,
                novel.status,
                novel.coverUrl,
                novel.coverRatio,
                novel.synopsis,
                safeVolumes,
                warnings);
    }

    /**
     * Parses a catalogue page.
     *
     * @param html the fetched page
     * @param url  the normalised catalogue URL, used as the base for relative links
     * @throws Unreadable when no chapter can be recognised
     */
    public static Result parse(String html, String url) throws Unreadable {
        if (html == null || html.trim().isEmpty()) {
            throw new Unreadable("Halaman katalog kosong atau tidak dapat diunduh.");
        }
        Document document = org.jsoup.Jsoup.parse(html, url == null ? "" : url);
        stripBoilerplate(document);

        Element body = articleBody(document);
        if (body == null) {
            throw new Unreadable("Bagian artikel novel tidak ditemukan di halaman itu. "
                    + "Pastikan URL menunjuk ke halaman novel, bukan halaman label atau beranda.");
        }

        List<String> warnings = new ArrayList<>();
        List<Node> nodes = flatten(body);

        // Everything above the chapter list is the description; everything from
        // the first "Volume N" heading or chapter link onwards is the list itself.
        // Splitting there is what keeps an illustration or a volume cover from
        // being mistaken for the series artwork.
        int listStart = chapterListStart(nodes, url);
        List<Node> head = listStart > 0 ? nodes.subList(0, listStart) : nodes;

        String headText = textOf(head);
        Map<String, String> fields = labelledFields(headText);
        String title = title(document, body, fields);
        String titleJp = first(fields, "japanese tittle", "japanese title", "judul jepang",
                "japanese");
        String author = first(fields, "author", "penulis");
        String illustrator = first(fields, "illustrator", "ilustrator");
        String genres = first(fields, "genre", "genres");
        String status = first(fields, "status");
        String cover = coverUrl(body);
        float ratio = coverRatio(body, cover);
        String[] synopsis = synopsis(headText, warnings);

        Map<Integer, List<Chapter>> grouped = new LinkedHashMap<>();
        Set<Integer> headings = new LinkedHashSet<>();
        Set<String> seen = new LinkedHashSet<>();
        int unmapped = 0;
        int unknownVolume = 0;
        // Volume heading currently in effect. Local, never static: two parses
        // running at once must not share it.
        int pendingVolume = 0;
        // Blogger splits a heading across nodes: "Volume&nbsp;" lands in one span
        // and "2" in the next. Accumulate text until something else interrupts it,
        // otherwise Volume 2 and everything after it would pile up under Volume 1.
        StringBuilder buffer = new StringBuilder();

        for (Node node : nodes) {
            if (node instanceof TextNode) {
                buffer.append(((TextNode) node).text());
                int heading = ChapterNaming.volumeHeading(buffer.toString());
                if (heading > 0) {
                    headings.add(heading);
                    pendingVolume = heading;
                    buffer.setLength(0);
                } else if (!ChapterNaming.looksLikeVolumeStart(buffer.toString())) {
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
            buffer.setLength(0);

            String href = absolute(element);
            if (href.isEmpty()) {
                if (looksLikeChapterText(element.text())) unmapped++;
                continue;
            }
            if (isSamePage(href, url)) continue;
            String text = ChapterNaming.tidy(element.text());
            if (!isChapterLink(text, href)) continue;
            if (!seen.add(href)) continue;

            int volume = pendingVolume > 0 ? pendingVolume : volumeFrom(text, href);
            if (volume <= 0) unknownVolume++;
            String label = ChapterNaming.label(text.isEmpty() ? slugTail(href) : text);
            int order = ChapterNaming.order(text, label);

            Chapter chapter = new Chapter(text, label, href, "", order, volume);
            List<Chapter> list = grouped.get(volume);
            if (list == null) {
                list = new ArrayList<>();
                grouped.put(volume, list);
            }
            list.add(chapter);
        }
        List<Volume> volumes = new ArrayList<>();
        for (Map.Entry<Integer, List<Chapter>> entry : grouped.entrySet()) {
            List<Chapter> chapters = entry.getValue();
            chapters.sort((left, right) -> left.order != right.order
                    ? left.order - right.order
                    : left.label.compareToIgnoreCase(right.label));
            volumes.add(new Volume(entry.getKey(), chapters));
        }
        for (int heading : headings) {
            if (!grouped.containsKey(heading)) volumes.add(new Volume(heading, new ArrayList<>()));
        }
        volumes.sort((left, right) -> {
            boolean leftUnknown = left.number <= 0;
            boolean rightUnknown = right.number <= 0;
            if (leftUnknown != rightUnknown) return leftUnknown ? 1 : -1;
            return left.number - right.number;
        });

        int total = 0;
        for (Volume volume : volumes) total += volume.chapterCount();
        if (total == 0) {
            throw new Unreadable("Tidak ada tautan chapter yang dikenali di halaman itu. "
                    + "Halaman ini mungkin artikel biasa, atau daftar chapternya dimuat "
                    + "dengan JavaScript sehingga tidak bisa dibaca aplikasi.");
        }
        if (author.isEmpty() && illustrator.isEmpty() && genres.isEmpty()) {
            warnings.add("Metadata tidak tertulis di halaman; kolom yang kosong dibiarkan kosong.");
        }
        if (unmapped > 0) {
            warnings.add(unmapped + " teks chapter tidak memiliki tautan dan tidak dimasukkan.");
        }
        if (unknownVolume > 0) {
            warnings.add(unknownVolume + " chapter tidak menyebut volume; dikelompokkan sebagai "
                    + "\"Volume lain\" karena halaman tidak menulis nomornya.");
        }
        if (title.isEmpty()) {
            warnings.add("Judul tidak ditemukan di halaman; judul diambil dari URL.");
            title = slugTail(url);
        }
        if (cover.isEmpty()) {
            warnings.add("Cover tidak ditemukan; kartu memakai placeholder.");
        }

        return new Result(url == null ? "" : url, title, titleJp, title, author, illustrator,
                genres, status, cover, ratio, synopsis, volumes, warnings);
    }

    // ------------------------------------------------------------------ metadata

    /** Reads "Key : value" lines out of the article text. */
    static Map<String, String> labelledFields(String text) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (text == null) return fields;

        // Blogger splits these lines across text nodes: "Author:" arrives as its
        // own line with the value on the next one, and "Genre" is sometimes
        // followed by a lone ":" before the value. Both shapes are stitched back
        // together here, otherwise every novel would report "no metadata".
        List<String> lines = new ArrayList<>();
        for (String rawLine : text.split("\n")) {
            String line = ChapterNaming.tidy(rawLine);
            if (line.isEmpty()) continue;
            // A bold label ends its own line, so the colon and the value arrive
            // next: "Penulis" then ": Penulis Uji". Rejoin them, otherwise every
            // field written with <b> would read as its own value.
            if (line.startsWith(":") && !lines.isEmpty()) {
                String previous = lines.get(lines.size() - 1);
                lines.set(lines.size() - 1, ChapterNaming.tidy(previous + " " + line));
                continue;
            }
            lines.add(line);
        }

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.length() > 600) continue;

            Matcher matcher = LABEL_LINE.matcher(line);
            if (matcher.matches()) {
                put(fields, matcher.group(1), matcher.group(2));
                continue;
            }

            // "Author:" / "Author" + ":" — the value is on the next line.
            String label = null;
            if (LABEL_ONLY.matcher(line).matches()) {
                label = line.substring(0, line.lastIndexOf(':')).trim();
            } else if (i + 1 < lines.size() && ":".equals(lines.get(i + 1))
                    && LABEL_WORD.matcher(line).matches()) {
                label = line;
                i++;
            }
            if (label == null) continue;
            if (i + 1 >= lines.size()) continue;
            String value = lines.get(i + 1);
            if (value.length() > 300 || LABEL_ONLY.matcher(value).matches()) continue;
            put(fields, label, value);
        }
        return fields;
    }

    private static void put(Map<String, String> fields, String key, String value) {
        String normalisedKey = key.toLowerCase(Locale.ROOT).trim();
        String trimmed = value == null ? "" : value.trim();
        if (normalisedKey.isEmpty() || trimmed.isEmpty()) return;
        if (!fields.containsKey(normalisedKey)) fields.put(normalisedKey, trimmed);
    }

    /** Debug helper for tests: the labelled fields exactly as parsed. */
    public static String debugFields(String html, String url) {
        org.jsoup.nodes.Document document = org.jsoup.Jsoup.parse(html, url);
        stripBoilerplate(document);
        Element body = articleBody(document);
        if (body == null) return "{}";
        java.util.List<Node> nodes = flatten(body);
        int start = chapterListStart(nodes, url);
        return labelledFields(textOf(start > 0 ? nodes.subList(0, start) : nodes)).toString();
    }

    /** Debug helper for tests: the description text as it is parsed. */
    public static String debugHeadText(String html, String url) {
        org.jsoup.nodes.Document document = org.jsoup.Jsoup.parse(html, url);
        stripBoilerplate(document);
        Element body = articleBody(document);
        if (body == null) return "";
        java.util.List<Node> nodes = flatten(body);
        int start = chapterListStart(nodes, url);
        return textOf(start > 0 ? nodes.subList(0, start) : nodes);
    }

    /** Debug helper for tests: every image in the article with its declared size. */
    public static String debugImages(String html, String url) {
        org.jsoup.nodes.Document document = org.jsoup.Jsoup.parse(html, url);
        stripBoilerplate(document);
        Element body = articleBody(document);
        if (body == null) return "";
        StringBuilder out = new StringBuilder();
        for (org.jsoup.nodes.Element image : body.select("img")) {
            out.append(declaredWidth(image)).append('x').append(declaredHeight(image))
                    .append(' ').append(imageSource(image)).append('\n');
        }
        return out.toString();
    }

    private static String first(Map<String, String> fields, String... keys) {
        for (String key : keys) {
            String value = fields.get(key);
            if (value != null && !value.isEmpty()) return value;
        }
        return "";
    }

    /** Article heading, falling back to the document title and then the URL. */
    private static String title(Document document, Element body, Map<String, String> fields) {
        String[] selectors = {
                "h1.post-title", "h3.post-title", "h1.entry-title", "h2.entry-title",
                ".post-title", "article h1", "article h2", "h1"
        };
        for (String selector : selectors) {
            Elements found = document.select(selector);
            if (found.isEmpty()) continue;
            String text = ChapterNaming.tidy(found.first().text());
            if (!text.isEmpty() && text.length() <= 200) return text;
        }
        String alternate = first(fields, "alternate tittle", "alternate title");
        if (!alternate.isEmpty()) return alternate;

        Element meta = document.selectFirst("meta[property=og:title]");
        if (meta != null) {
            String text = ChapterNaming.tidy(meta.attr("content"));
            if (!text.isEmpty()) return stripSiteName(text);
        }
        String fromTag = ChapterNaming.tidy(document.title());
        if (!fromTag.isEmpty()) return stripSiteName(fromTag);

        Element heading = body.selectFirst("b, strong");
        if (heading != null) {
            String text = ChapterNaming.tidy(heading.text());
            if (!text.isEmpty() && text.length() <= 120) return text;
        }
        return "";
    }

    private static String stripSiteName(String title) {
        String[] separators = {" | ", " - ", " – "};
        for (String separator : separators) {
            int cut = title.lastIndexOf(separator);
            if (cut > 12) return title.substring(0, cut).trim();
        }
        return title;
    }

    /**
     * Cover artwork: the first portrait picture in the article.
     *
     * <p>Two mistakes are avoided here. The description normally opens with a
     * wide promotional banner, so "the first image" is wrong; and a volume's
     * illustrations are wide, so "the largest image" is wrong too. The series
     * cover is the first picture that is taller than it is wide, which is true on
     * both source layouts. Pictures nested inside a link are included, because
     * that is exactly how these pages wrap their covers.
     */
    static String coverUrl(Element body) {
        String firstImage = "";
        String firstPortrait = "";
        for (Element image : body.select("img")) {
            String source = imageSource(image);
            if (source.isEmpty()) continue;
            if (firstImage.isEmpty()) firstImage = source;
            int width = declaredWidth(image);
            int height = declaredHeight(image);
            if (width > 0 && height > width) {
                firstPortrait = source;
                break;
            }
        }
        if (!firstPortrait.isEmpty()) return upgrade(firstPortrait);
        return firstImage.isEmpty() ? "" : upgrade(firstImage);
    }

    private static String imageSource(Element image) {
        String[] attributes = {"data-src", "data-original", "data-lazy-src", "src"};
        for (String attribute : attributes) {
            String value = image.attr(attribute).trim();
            if (value.isEmpty()) continue;
            if (value.startsWith("data:") || value.startsWith("file:")) continue;
            return value;
        }
        return "";
    }

    private static int declaredWidth(Element image) {
        for (String attribute : new String[]{"data-original-width", "width"}) {
            String value = image.attr(attribute).trim();
            if (value.isEmpty()) continue;
            try {
                return Integer.parseInt(value.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ignored) {
                // try the next attribute
            }
        }
        return 0;
    }

    /** Width divided by height of the picture chosen as the cover. */
    private static float coverRatio(Element body, String cover) {
        if (cover == null || cover.isEmpty()) return 0.70f;
        for (Element image : body.select("img")) {
            String source = imageSource(image);
            if (source.isEmpty()) continue;
            if (!upgrade(source).equals(cover) && !source.equals(cover)) continue;
            int width = declaredWidth(image);
            int height = declaredHeight(image);
            if (width > 0 && height > 0) return width / (float) height;
        }
        return 0.70f;
    }

    private static int declaredHeight(Element image) {
        for (String attribute : new String[]{"data-original-height", "height"}) {
            String value = image.attr(attribute).trim();
            if (value.isEmpty()) continue;
            try {
                int parsed = Integer.parseInt(value.replaceAll("[^0-9]", ""));
                if (parsed > 0) return parsed;
            } catch (NumberFormatException ignored) {
                // try the next attribute
            }
        }
        return 0;
    }

    private static int number(String... values) {
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) continue;
            try {
                int parsed = Integer.parseInt(value.replaceAll("[^0-9]", ""));
                if (parsed > 0) return parsed;
            } catch (NumberFormatException ignored) {
                // next
            }
        }
        return 0;
    }

    /** Blogger size upgrade: a thumbnail is useless as a cover. */
    static String upgrade(String url) {
        if (url == null || url.isEmpty()) return "";
        Matcher matcher = IMAGE_SIZE.matcher(url);
        if (!matcher.find()) return url;
        int size = 0;
        try {
            size = Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException ignored) {
            return url;
        }
        if (size >= 1000) return url;
        return matcher.replaceFirst("/" + matcher.group(1) + "1600/");
    }

    /** Paragraphs between "Sinopsis" and the volume list. */
    private static String[] synopsis(String text, List<String> warnings) {
        if (text == null) return new String[0];
        int start = indexOfAny(text, "sinopsis", "synopsis", "ringkasan");
        if (start < 0) return new String[0];
        int from = text.indexOf('\n', start);
        if (from < 0) return new String[0];
        int end = text.length();
        int list = indexOfAny(text.substring(from), "list volume", "daftar volume", "volume 1");
        if (list > 0) end = from + list;

        List<String> paragraphs = new ArrayList<>();
        for (String raw : text.substring(from, end).split("\n")) {
            String line = ChapterNaming.tidy(raw);
            if (line.isEmpty()) continue;
            if (line.length() < 25 && paragraphs.isEmpty()) continue;
            paragraphs.add(line);
            if (paragraphs.size() >= 12) break;
        }
        if (paragraphs.isEmpty()) {
            warnings.add("Sinopsis tidak terbaca dari halaman.");
        }
        return paragraphs.toArray(new String[0]);
    }

    private static int indexOfAny(String text, String... needles) {
        String lower = text.toLowerCase(Locale.ROOT);
        int best = -1;
        for (String needle : needles) {
            int at = lower.indexOf(needle);
            if (at >= 0 && (best < 0 || at < best)) best = at;
        }
        return best;
    }

    /**
     * Index of the first node that belongs to the chapter list: a volume heading
     * or a chapter-shaped link. Everything before it is the description.
     *
     * <p>Returns {@code nodes.size()} when the page has no list at all, so the
     * whole article is treated as description and the missing list is reported by
     * the caller instead of being confused with a cover.
     */
    static int chapterListStart(List<Node> nodes, String pageUrl) {
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (node instanceof TextNode) {
                buffer.append(((TextNode) node).text());
                if (ChapterNaming.volumeHeading(buffer.toString()) > 0) return i;
                if (!ChapterNaming.looksLikeVolumeStart(buffer.toString())) {
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
            buffer.setLength(0);
            String href = absolute(element);
            if (href.isEmpty() || isSamePage(href, pageUrl)) continue;
            String text = ChapterNaming.tidy(element.text());
            if (isChapterLink(text, href)) return i;
        }
        return nodes.size();
    }

    /**
     * Plain text of a node list, one line per block.
     *
     * <p>Only text nodes and the two leaf elements contribute. Taking
     * {@code Element.text()} for every element would repeat the same sentence once
     * per ancestor level, which previously made "Genre" read as its own value and
     * merged a multi-paragraph synopsis into one run-on paragraph.
     */
    static String textOf(List<Node> nodes) {
        StringBuilder out = new StringBuilder();
        for (Node node : nodes) {
            if (node instanceof TextNode) {
                out.append(((TextNode) node).text()).append('\n');
            } else if (node instanceof Element) {
                Element element = (Element) node;
                String tag = element.tagName();
                if ("script".equals(tag) || "style".equals(tag) || "noscript".equals(tag)) continue;
                if ("br".equals(tag)) {
                    out.append('\n');
                    continue;
                }
                // Anchors and images are leaves in the flattened list, so their own
                // text is the only chance to capture it.
                if ("a".equals(tag) || "img".equals(tag)) out.append(element.text()).append('\n');
            }
        }
        return out.toString();
    }

    // ---------------------------------------------------------------- structure

    private static void stripBoilerplate(Document document) {
        for (String selector : BOILERPLATE) {
            document.select(selector).remove();
        }
    }

    private static Element articleBody(Document document) {
        String[] selectors = {
                "div.post-body.entry-content", "div.post-body", "div.entry-content",
                "article .post-body", "article", "main", "div#content", "body"
        };
        for (String selector : selectors) {
            Element found = document.selectFirst(selector);
            if (found != null && !found.text().trim().isEmpty()) return found;
        }
        return null;
    }

    /** Plain text with one line per block, so labels stay on their own line. */
    private static String bodyText(Element body) {
        StringBuilder out = new StringBuilder();
        collectText(body, out);
        return out.toString();
    }

    private static void collectText(Node node, StringBuilder out) {
        for (Node child : node.childNodes()) {
            if (child instanceof TextNode) {
                out.append(((TextNode) child).text()).append('\n');
            } else if (child instanceof Element) {
                Element element = (Element) child;
                String tag = element.tagName();
                if ("script".equals(tag) || "style".equals(tag) || "noscript".equals(tag)) continue;
                if ("br".equals(tag)) {
                    out.append('\n');
                    continue;
                }
                collectText(element, out);
                if ("p".equals(tag) || "div".equals(tag) || "li".equals(tag)
                        || "h1".equals(tag) || "h2".equals(tag) || "h3".equals(tag)) {
                    out.append('\n');
                }
            }
        }
    }

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
                if ("script".equals(tag) || "style".equals(tag) || "noscript".equals(tag)) continue;
                out.add(element);
                if ("a".equals(tag) || "img".equals(tag)) continue;
                collect(element, out);
            }
        }
    }

    private static String absolute(Element anchor) {
        String href = anchor.attr("abs:href");
        if (href == null || href.isEmpty()) href = anchor.attr("href");
        return href == null ? "" : href.trim();
    }

    private static boolean isSamePage(String href, String url) {
        if (href == null || url == null) return false;
        return href.replaceAll("/+$", "").equals(url.replaceAll("/+$", ""));
    }

    /** True when the link is part of this novel rather than site chrome. */
    static boolean isChapterLink(String text, String href) {
        if (href == null || href.isEmpty()) return false;
        if (!href.contains("/20")) return false; // Blogger dated post path
        String lower = href.toLowerCase(Locale.ROOT);
        if (lower.contains("/search/") || lower.contains("/feeds/") || lower.contains("/p/")) {
            return false;
        }
        if (looksLikeChapterText(text)) return true;
        return CHAPTER_SLUG.matcher(href).find();
    }

    private static boolean looksLikeChapterText(String text) {
        if (text == null) return false;
        String tidy = ChapterNaming.tidy(text);
        if (tidy.isEmpty() || tidy.length() > 80) return false;
        return CHAPTER_SLUG.matcher(tidy).find();
    }

    /** Volume number from the link text, then from the slug. */
    private static int volumeFrom(String text, String href) {
        if (text != null) {
            Matcher matcher = VOLUME_IN_TEXT.matcher(text);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException ignored) {
                    // fall through to the slug
                }
            }
        }
        return ChapterNaming.volumeFromUrl(href);
    }

    /** Last path segment, used as a label when the anchor has no text. */
    private static String slugTail(String url) {
        if (url == null || url.isEmpty()) return "";
        String path = url;
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (name.endsWith(".html")) name = name.substring(0, name.length() - 5);
        return name.replace('-', ' ').replace('_', ' ');
    }
}
