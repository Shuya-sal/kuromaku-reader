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
 * Reads a novel straight from a source page, without knowing which novel it is.
 *
 * <p>The two novels that ship with the app each got their own parser because
 * their pages differ. A novel the user adds by URL has no such luxury, so this
 * class works from what the pages have in common and reports honestly when it
 * cannot.
 *
 * <h3>What went wrong before (2.9) and why</h3>
 *
 * <p>Two reported URLs failed for two unrelated reasons, and neither was a matter
 * of "the parser needs to be more flexible":
 *
 * <ol>
 *   <li><b>Label pages were refused before they were ever fetched.</b>
 *       {@code /search/label/Nama%20Novel} was rejected as "bukan halaman
 *       artikel". That threw away the most reliable catalogue the source has:
 *       its JSON feed lists every post carrying the label and nothing else.</li>
 *   <li><b>The article body was deleted before parsing.</b> The boilerplate strip
 *       removed every {@code .widget}, and on this template the article lives in
 *       {@code div.widget.Blog#Blog1 > div.blog-posts > article > div.post-body}.
 *       Removing the widget removed the article with it, leaving ~40 characters of
 *       page chrome — so a page with 45 perfectly good chapter links reported
 *       "Tidak ada tautan chapter yang dikenali".</li>
 * </ol>
 *
 * <h3>How a catalogue is recognised now</h3>
 *
 * <ul>
 *   <li>The article is located <em>first</em>, then cleaned <em>inside</em>, so no
 *       ancestor of the article can ever be removed.</li>
 *   <li>Every anchor in the article is collected — lists, tables, buttons and
 *       anchors with no visible text included — because a catalogue is often a
 *       bare list of links.</li>
 *   <li>A link is a chapter when its text or slug says so ("Chapter 12", "Bab 3",
 *       "Ch. 4", "Vol. 2 Chapter 1", "Prolog", "Epilog", "Afterword",
 *       "Illustrasi", "Interlude III"), or when it sits inside a volume group with
 *       at least three such links and belongs to the same series. A specific
 *       chapter wording is therefore <em>not</em> required when the grouping
 *       itself is unambiguous.</li>
 *   <li>Navigation, share widgets, label pages, feeds, static pages and other
 *       series' posts are rejected, each with a recorded reason.</li>
 *   <li>When nothing is recognised confidently, the result is not thrown away:
 *       the preview lists every candidate link so the user can pick the real
 *       chapters. Nothing is stored until then.</li>
 * </ul>
 */
public final class NovelDiscovery {

    /** Thrown when the page cannot be understood as a catalogue at all. */
    public static final class Unreadable extends Exception {
        public Unreadable(String message) {
            super(message);
        }
    }

    /** One link the parser considered, accepted or not. */
    public static final class Candidate {
        public final String url;
        public final String text;
        public final int volume;
        public final boolean accepted;
        /** Why it was accepted, or why it was rejected. Never empty. */
        public final String reason;
        /** Position in the article, which is the order the translator laid out. */
        public final int index;
        /**
         * True when the user may pick this link by hand.
         *
         * <p>Two kinds of rejection exist. A link that is not a post of this novel at
         * all — navigation, a share widget, a label page, another series' chapter — is
         * never selectable: the user asking for "the real chapters" must not be able
         * to import an ad. A link that is a post of this series but whose text does
         * not say "chapter" is exactly what the picker is for, so it stays
         * selectable while unverified.
         */
        public final boolean selectable;

        Candidate(String url, String text, int volume, boolean accepted, String reason,
                  int index) {
            this(url, text, volume, accepted, reason, index, accepted || softRejection(reason));
        }

        /**
         * Full form: {@code selectable} is passed in rather than derived.
         *
         * <p>The second pass rewrites a rejected link's reason to "sendiri di grup
         * volume", which reads like uncertainty even for a link that was refused as
         * another series or as the page itself. Deriving selectability from the
         * rewritten text therefore made every rejected link pickable; the verdict has
         * to survive the rewrite.
         */
        Candidate(String url, String text, int volume, boolean accepted, String reason,
                  int index, boolean selectable) {
            this.url = url;
            this.text = text;
            this.volume = volume;
            this.accepted = accepted;
            this.reason = reason;
            this.index = index;
            this.selectable = selectable;
        }

        /**
         * True when the reason only means "not proven to be a chapter" rather than
         * "not a chapter of this novel". Only these may be picked by hand.
         */
        private static boolean softRejection(String reason) {
            if (reason == null) return false;
            // "dalam grup volume" = a post of this series whose text is not chapter-like.
            // "tidak berpola chapter" = same thing before the volume group check.
            // Anything else ("milik novel lain", "menunjuk halaman ini sendiri",
            // "bukan tautan postingan", "halaman label/feed") is a verdict, not doubt.
            return reason.contains("tidak berpola chapter") || "dalam grup volume".equals(reason);
        }

        Candidate(String url, String text, int volume, boolean accepted, String reason) {
            this(url, text, volume, accepted, reason, 0);
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

        /** Links found inside the article, before any filtering. */
        public final int linksFound;
        /** Links that became chapters. */
        public final int linksAccepted;
        /** Every link considered, with the reason it was taken or dropped. */
        public final List<Candidate> candidates;
        /**
         * True when the parser found links but is not sure enough to call them
         * chapters: the preview must ask the user to choose.
         */
        public final boolean needsConfirmation;

        Result(String sourceUrl, String title, String titleJp, String fullTitle, String author,
               String illustrator, String genres, String status, String coverUrl, float coverRatio,
               String[] synopsis, List<Volume> volumes, List<String> warnings, int linksFound,
               int linksAccepted, List<Candidate> candidates, boolean needsConfirmation) {
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
            this.linksFound = linksFound;
            this.linksAccepted = linksAccepted;
            this.candidates = candidates;
            this.needsConfirmation = needsConfirmation;
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

        /** Candidate links that were rejected, for the diagnostics panel. */
        public List<Candidate> rejected() {
            List<Candidate> out = new ArrayList<>();
            for (Candidate candidate : candidates) {
                if (!candidate.accepted) out.add(candidate);
            }
            return out;
        }

        /** Links the user may pick by hand: the ones that could be chapters. */
        public List<Candidate> selectableCandidates() {
            List<Candidate> out = new ArrayList<>();
            for (Candidate candidate : candidates) {
                if (candidate.selectable) out.add(candidate);
            }
            return out;
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
    private static final String[] ARTICLE_CHROME = {
            "script", "style", "noscript", "iframe", "ins", ".adsbygoogle", ".ad",
            ".share", ".post-share-buttons", ".post-footer", ".comments", ".comment-form",
            ".related-posts", ".related-bottom", ".popular-posts", ".blog-pager",
            ".breadcrumb", ".post-pagination", ".toc-overlay", ".post-labels"
    };

    /** Text that ends the catalogue: everything after it is site chrome. */
    private static final String[] TRAILING_MARKERS = {
            "related posts", "postingan terkait", "artikel terkait", "you might also like",
            "rekomendasi", "komentar", "comments", "bagikan", "share this"
    };

    /** Path shapes that are never a chapter of the novel being read. */
    private static final String[] NON_CHAPTER_PATHS = {"/search/", "/feeds/", "/p/", "/label/"};

    /** Hrefs that point at a bitmap rather than at a page. */
    private static final Pattern IMAGE_HREF = Pattern.compile(
            "\\.(jpg|jpeg|png|gif|webp|avif|svg)(\\?|$)|googleusercontent\\.com/img/");

    /**
     * Text that is only a number: "2" torn away from "Interlude 2" because the source
     * wrote the numeral as its own anchor. Such an anchor is half a label, not a link.
     */
    private static final Pattern FRAGMENT_TEXT = Pattern.compile(
            "^[0-9]{1,3}$|^[ivxlcdm]{1,6}$", Pattern.CASE_INSENSITIVE);

    /** Link text that a following bare numeral would continue: "Interlude", "Bab 3". */
    private static final Pattern DESCRIPTOR_TAIL = Pattern.compile(
            "(interlude|chapter|chap|bab|vol|volume|epilog|prolog|ilustrasi|illustrasi"
                    + "|part|bagian)\\s*$", Pattern.CASE_INSENSITIVE);

    private static final Pattern LABEL_LINE = Pattern.compile(
            "^([A-Za-z][A-Za-z .]{2,24})\\s*[:：]\\s*(.+)$");

    /** "Author:" with nothing after it; the value is on the following line. */
    private static final Pattern LABEL_ONLY = Pattern.compile(
            "^[A-Za-z][A-Za-z .]{2,24}\\s*[:：]$");

    /** A bare word that may be a label when the next line is just ":". */
    private static final Pattern LABEL_WORD = Pattern.compile("^[A-Za-z][A-Za-z .]{2,24}$");

    /** A volume heading written as its own line: "Volume 3", "Vol. 2", "BAB 4". */
    private static final Pattern VOLUME_HEADING_LINE = Pattern.compile(
            "^\\s*(?:volume|vol)\\.?\\s*(\\d{1,2})\\s*[:\\-]?\\s*$", Pattern.CASE_INSENSITIVE);

    /** "Volume 3" as a link's whole text: a volume landing page, not a chapter. */
    private static final Pattern VOLUME_ONLY_TEXT = Pattern.compile(
            "^\\s*(?:volume|vol)\\.?\\s*\\d{1,2}\\s*$", Pattern.CASE_INSENSITIVE);

    private static final Pattern IMAGE_SIZE = Pattern.compile("/(s|w)(\\d{2,4})(?:-h\\d+)?/");

    /** Blogger dated post path, also used to spot the novel's own posts. */
    private static final Pattern DATED_POST = Pattern.compile("/20\\d\\d/\\d\\d/");

    private NovelDiscovery() {
    }

    /**
     * Preview built from a novel that is already stored.
     *
     * <p>Used when the user pastes an address that is already on the shelf: the
     * page is not fetched again, and the preview shows the stored metadata together
     * with whatever catalogue the session already holds. The warning makes clear
     * that this is a stored copy, not a fresh read.
     */
    public static Result fromStored(Novel novel, List<Volume> volumes) {
        List<Volume> safeVolumes = volumes == null ? new ArrayList<Volume>() : volumes;
        List<String> warnings = new ArrayList<>();
        warnings.add("Data ini berasal dari perpustakaan, bukan hasil pembacaan ulang halaman.");
        int count = 0;
        for (Volume volume : safeVolumes) count += volume.chapterCount();
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
                warnings,
                count,
                count,
                new ArrayList<Candidate>(),
                false);
    }

    /**
     * Parses a catalogue page.
     *
     * @param html the fetched page
     * @param url  the normalised address, used as the base for relative links
     * @throws Unreadable when the article cannot be found, or when nothing at all
     *                    looks like a chapter link
     */
    public static Result parse(String html, String url) throws Unreadable {
        return analyse(html, url, true);
    }

    /**
     * Reads the article's metadata and links without insisting on a chapter list.
     *
     * <p>Needed for a label address: its chapters come from the JSON feed, so the
     * page itself is only useful for the description, cover and metadata. Reporting
     * "no chapters" for such a page would be true but useless.
     *
     * @throws Unreadable only when the article body cannot be found at all
     */
    public static Result describe(String html, String url) throws Unreadable {
        return analyse(html, url, false);
    }

    private static Result analyse(String html, String url, boolean requireChapters)
            throws Unreadable {
        if (html == null || html.trim().isEmpty()) {
            throw new Unreadable("Halaman katalog kosong atau tidak dapat diunduh.");
        }
        Document document = org.jsoup.Jsoup.parse(html, url == null ? "" : url);

        // The article is located BEFORE anything is removed: on this template the
        // post body is a descendant of div.widget.Blog, so stripping widgets first
        // deleted the article itself and left ~40 characters of page chrome.
        Element body = articleBody(document);
        if (body == null) {
            throw new Unreadable("Bagian artikel novel tidak ditemukan di halaman itu. "
                    + "Pastikan alamat menunjuk ke halaman novel, bukan halaman beranda.");
        }
        cleanArticle(body);

        List<String> warnings = new ArrayList<>();
        List<Node> nodes = flatten(body);
        String pageStem = stem(url);

        // Everything above the chapter list is the description; everything from the
        // first volume heading or first chapter-shaped link onwards is the list.
        int listStart = chapterListStart(nodes, url);
        List<Node> head = listStart > 0 ? nodes.subList(0, listStart) : nodes;

        String headText = textOf(head);
        Map<String, String> fields = labelledFields(headText);
        String title = title(document, body, fields);
        String titleJp = first(fields, "japanese tittle", "japanese title", "judul jepang",
                "judul lain", "japanese");
        String author = first(fields, "author", "penulis");
        String illustrator = first(fields, "illustrator", "ilustrator", "artist", "artis");
        String genres = first(fields, "genre", "genres");
        String status = first(fields, "status");
        String cover = coverUrl(body);
        float ratio = coverRatio(body, cover);
        String[] synopsis = synopsis(headText, warnings);

        // No list detected means the whole article is a candidate area: reporting
        // "0 tautan" for a page full of links would be a lie, and the count is what
        // tells the user whether this is a one-chapter post or an unreadable page.
        boolean listDetected = listStart < nodes.size();
        int scanFrom = listDetected ? listStart : 0;
        int listEnd = catalogueEnd(nodes, scanFrom);

        // Pass 1: collect every anchor in the catalogue area.
        List<Link> links = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        int pendingVolume = 0;
        Set<Integer> headings = new LinkedHashSet<>();
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (node instanceof TextNode) {
                buffer.append(((TextNode) node).text());
                int heading = ChapterNaming.volumeHeading(buffer.toString());
                if (heading > 0) {
                    headings.add(heading);
                    if (i >= scanFrom && i < listEnd) pendingVolume = heading;
                    buffer.setLength(0);
                } else if (!ChapterNaming.looksLikeVolumeStart(buffer.toString())) {
                    buffer.setLength(0);
                }
                continue;
            }
            if (!(node instanceof Element)) continue;
            Element element = (Element) node;
            if (!"a".equals(element.tagName())) continue;
            buffer.setLength(0);
            if (i < scanFrom || i >= listEnd) continue;
            links.add(new Link(absolute(element), ChapterNaming.tidy(element.text()),
                    pendingVolume, i));
        }

        mergeSplitLabels(links, nodes);

        // Pass 2: decide which of them are chapters.
        String dominant = dominantStem(links, pageStem);
        List<Candidate> candidates = new ArrayList<>();
        List<Chapter> chapters = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int groupLinks = 0;
        int currentGroup = -1;
        for (Link link : links) {
            if (link.volume != currentGroup) {
                currentGroup = link.volume;
                groupLinks = 0;
            }
            groupLinks++;
            String reason = rejection(link, url, pageStem, dominant);
            if (reason == null) {
                reason = "chapter dikenali dari teks/URL";
            }
            boolean accepted = !reason.startsWith("ditolak");
            if (!accepted && link.volume > 0 && plausibleChapterText(link.text)
                    && isSoftRejection(reason)) {
                // No chapter wording, but it sits inside a volume group: accept it
                // once the group itself is clearly a list (checked below).
                //
                // Only a *soft* rejection is promoted here. A link refused because it
                // is navigation, a non-post, or another series stays refused: rewriting
                // its reason to "dalam grup volume" used to launder a "Baca juga
                // review sebelumnya" link into a chapter candidate, which is how the
                // manual picker ended up offering links that must never be imported.
                reason = "dalam grup volume";
            }
            candidates.add(new Candidate(link.url, link.text, link.volume, accepted, reason,
                    link.index));
            if (!accepted) continue;
            if (!seen.add(link.url)) continue;
            chapters.add(new Chapter(link.text, ChapterNaming.label(
                    link.text.isEmpty() ? slugTail(link.url) : link.text),
                    link.url, "", link.index, link.volume));
        }

        // A volume group is only trusted when it holds several links: one stray
        // "dalam grup volume" link is a mention, not a list.
        int groupCount = 0;
        int[] perVolume = new int[32];
        for (Candidate candidate : candidates) {
            if (!"dalam grup volume".equals(candidate.reason)) continue;
            int volume = Math.max(0, Math.min(31, candidate.volume));
            perVolume[volume]++;
        }
        List<Candidate> confirmed = new ArrayList<>();
        List<Chapter> finalChapters = new ArrayList<>();
        for (Candidate candidate : candidates) {
            boolean take = candidate.accepted;
            if ("dalam grup volume".equals(candidate.reason)) {
                int volume = Math.max(0, Math.min(31, candidate.volume));
                take = perVolume[volume] >= 3;
            }
            // The generic "one link alone in a volume group" verdict may only replace
            // the *group* reason. An image wrapper and a torn-off numeral already have
            // a precise reason of their own, and overwriting it made the diagnostics
            // panel say "sendiri di grup volume" about a picture.
            String kept = candidate.reason;
            if (!take && "dalam grup volume".equals(kept)) {
                kept = "ditolak: sendiri di grup volume, bukan daftar";
            }
            confirmed.add(new Candidate(candidate.url, candidate.text, candidate.volume, take,
                    kept, candidate.index, take || candidate.selectable));
            if (take) {
                groupCount++;
                finalChapters.add(new Chapter(candidate.text, ChapterNaming.label(
                        candidate.text.isEmpty() ? slugTail(candidate.url) : candidate.text),
                        candidate.url, "", candidate.index, candidate.volume));
            }
        }

        List<Volume> volumes = groupByVolume(finalChapters, headings);
        int accepted = groupCount;

        // No chapter found, but the page still gives a reason to think it is a list:
        // do not throw it away, hand the links to the preview so the user can pick.
        // A review post with two "Baca juga" links is not a list, and offering it as
        // one would be the same guess in a different costume.
        boolean needsConfirmation = accepted == 0 && looksLikeUnlabelledList(confirmed);
        if (accepted == 0 && !needsConfirmation && requireChapters) {
            String shapes = links.isEmpty()
                    ? "Tidak ada satu pun tautan di dalam artikel."
                    : "Ditemukan " + links.size() + " tautan di dalam artikel, tetapi tidak ada "
                    + "yang berpola chapter (Chapter/Bab/Ch/Vol/Prolog/Epilog/Ilustrasi) dan "
                    + "tidak ada pengelompokan volume yang bisa dipakai.";
            throw new Unreadable("Tidak ada tautan chapter yang dikenali di halaman itu. "
                    + shapes + " Halaman ini mungkin artikel satu chapter atau beranda blog.");
        }

        if (accepted > 0) {
            warnings.add("Ditemukan " + links.size() + " tautan di artikel; " + accepted
                    + " dikenali sebagai chapter, " + (links.size() - accepted) + " ditolak.");
        } else {
            warnings.add("Ditemukan " + links.size() + " tautan, tetapi belum ada yang bisa "
                    + "dipastikan sebagai chapter. Pilih tautan yang benar di bawah.");
        }
        int unmapped = 0;
        for (Node node : nodes.subList(Math.min(listStart, nodes.size()), nodes.size())) {
            if (node instanceof TextNode
                    && ChapterNaming.isRecognised(((TextNode) node).text())
                    && !hasNearbyAnchor(nodes, node)) {
                unmapped++;
            }
        }
        if (unmapped > 0) {
            warnings.add(unmapped + " teks chapter tidak memiliki tautan dan tidak dimasukkan.");
        }
        int unknownVolume = 0;
        for (Chapter chapter : finalChapters) if (chapter.volume <= 0) unknownVolume++;
        if (unknownVolume > 0) {
            warnings.add(unknownVolume + " chapter tidak menyebut volume; dikelompokkan sebagai "
                    + "\"Volume lain\" karena halaman tidak menulis nomornya.");
        }
        if (author.isEmpty() && illustrator.isEmpty() && genres.isEmpty()) {
            warnings.add("Metadata tidak tertulis di halaman; kolom yang kosong dibiarkan kosong.");
        }
        if (title.isEmpty()) {
            warnings.add("Judul tidak ditemukan di halaman; judul diambil dari alamat.");
            title = slugTail(url);
        }
        if (cover.isEmpty()) {
            warnings.add("Cover tidak ditemukan; kartu memakai placeholder.");
        }

        return new Result(url == null ? "" : url, title, titleJp, title, author, illustrator,
                genres, status, cover, ratio, synopsis, volumes, warnings, links.size(),
                accepted, confirmed, needsConfirmation);
    }

    /**
     * Result for a catalogue that came from a Blogger label feed rather than from
     * scraped links.
     *
     * <p>Nothing is scraped here, so there are no candidates and no rejected links:
     * {@code linksFound} and {@code linksAccepted} both report the number of posts
     * the feed returned, and the warning says so in words. The metadata may be
     * empty when the label page did not expose it — that is stated, not invented.
     */
    public static Result catalogueFromFeed(String sourceUrl, String title, String titleJp,
                                           String author, String illustrator, String genres,
                                           String status, String coverUrl, float coverRatio,
                                           String[] synopsis, List<Volume> volumes,
                                           List<String> warnings) {
        return catalogueFromFeed(sourceUrl, title, titleJp, author, illustrator, genres, status,
                coverUrl, coverRatio, synopsis, volumes, warnings, -1);
    }

    /**
     * Same, with the number of posts the feed held before filtering.
     *
     * <p>{@code linksFound} has to report what was actually found, not what survived:
     * a feed of 44 posts that yields 32 chapters is "44 ditemukan, 32 dipakai", and
     * reporting "32 ditemukan" hides the twelve that were dropped.
     */
    public static Result catalogueFromFeed(String sourceUrl, String title, String titleJp,
                                           String author, String illustrator, String genres,
                                           String status, String coverUrl, float coverRatio,
                                           String[] synopsis, List<Volume> volumes,
                                           List<String> warnings, int postsFound) {
        List<Volume> safeVolumes = volumes == null ? new ArrayList<Volume>() : volumes;
        List<String> safeWarnings = warnings == null ? new ArrayList<String>() : warnings;
        int count = 0;
        for (Volume volume : safeVolumes) count += volume.chapterCount();
        return new Result(sourceUrl == null ? "" : sourceUrl,
                title == null ? "" : title,
                titleJp == null ? "" : titleJp,
                title == null ? "" : title,
                author == null ? "" : author,
                illustrator == null ? "" : illustrator,
                genres == null ? "" : genres,
                status == null ? "" : status,
                coverUrl == null ? "" : coverUrl,
                coverRatio > 0 ? coverRatio : 0.70f,
                synopsis == null ? new String[0] : synopsis,
                safeVolumes, safeWarnings, postsFound < 0 ? count : postsFound, count,
                new ArrayList<Candidate>(), false);
    }

    /**
     * Rebuilds a catalogue from links the user picked by hand.
     *
     * <p>The candidates come from the parse that just ran, so the selection can
     * only ever contain links that were actually on the page. Volume numbers and
     * reading order are kept as parsed; nothing is invented.
     */
    public static Result reselect(Result base, List<String> urls) throws Unreadable {
        if (base == null) throw new Unreadable("Tidak ada pratinjau untuk dipilih.");
        Set<String> wanted = new LinkedHashSet<>(urls == null ? new ArrayList<String>() : urls);
        List<Chapter> chapters = new ArrayList<>();
        Set<Integer> headings = new LinkedHashSet<>();
        for (Candidate candidate : base.candidates) {
            if (!wanted.contains(candidate.url)) continue;
            // A URL that was never on the page, or that was rejected as navigation,
            // another series or a non-post, is ignored even when it is in the list:
            // the picker chooses among real candidates, it does not import links.
            if (!candidate.selectable) continue;
            chapters.add(new Chapter(candidate.text, ChapterNaming.label(
                    candidate.text.isEmpty() ? slugTail(candidate.url) : candidate.text),
                    candidate.url, "", candidate.index, candidate.volume));
            if (candidate.volume > 0) headings.add(candidate.volume);
        }
        if (chapters.isEmpty()) {
            throw new Unreadable("Tidak ada tautan yang dipilih, jadi belum ada chapter "
                    + "yang bisa disimpan.");
        }
        List<String> warnings = new ArrayList<>(base.warnings);
        warnings.add(chapters.size() + " tautan dipilih manual oleh pengguna.");
        return new Result(base.sourceUrl, base.title, base.titleJp, base.fullTitle, base.author,
                base.illustrator, base.genres, base.status, base.coverUrl, base.coverRatio,
                base.synopsis, groupByVolume(chapters, headings), warnings, base.linksFound,
                chapters.size(), base.candidates, false);
    }

    // ------------------------------------------------------------- link classes

    /** One anchor inside the catalogue area. */
    private static final class Link {
        final String url;
        String text;
        final int volume;
        final int index;
        /**
         * True when this anchor is only a fragment of the previous one's label.
         *
         * <p>The source sometimes writes {@code <a>Interlude </a><a>2</a>}: the numeral
         * lives in its own anchor pointing at a different post. Treated separately, the
         * chapter is labelled "Interlude" and a stray "2" becomes a candidate. The
         * numeral is folded back into the label, and this anchor is reported as merged
         * rather than as a chapter of its own.
         */
        boolean merged;

        Link(String url, String text, int volume, int index) {
            this.url = url;
            this.text = text;
            this.volume = volume;
            this.index = index;
        }
    }

    /**
     * Folds a bare-numeral anchor back into the label it belongs to.
     *
     * <p>Only anchors that are adjacent — nothing but blank text between them — and that
     * follow a link whose text ends with a descriptor are merged. "Chapter" + "4" is one
     * chapter; "Chapter 3" and an unrelated "2" two paragraphs later are not.
     */
    private static void mergeSplitLabels(List<Link> links, List<Node> nodes) {
        for (int i = 1; i < links.size(); i++) {
            Link current = links.get(i);
            Link previous = links.get(i - 1);
            if (previous.merged) continue;
            String fragment = current.text.trim();
            if (!FRAGMENT_TEXT.matcher(fragment).matches()) continue;
            if (!DESCRIPTOR_TAIL.matcher(previous.text.trim()).find()) continue;
            boolean adjacent = true;
            for (int n = previous.index + 1; n < current.index; n++) {
                Node between = nodes.get(n);
                if (between instanceof TextNode && !((TextNode) between).text().trim().isEmpty()) {
                    adjacent = false;
                    break;
                }
            }
            if (!adjacent) continue;
            previous.text = previous.text.trim() + " " + fragment;
            current.merged = true;
        }
    }

    /**
     * Why this link is not a chapter, or {@code null} when the text or slug says it
     * is one.
     */
    private static String rejection(Link link, String pageUrl, String pageStem, String dominant) {
        String href = link.url;
        if (href == null || href.isEmpty()) return "ditolak: tautan tanpa alamat";
        String lower = href.toLowerCase(Locale.ROOT);
        if (link.merged) return "ditolak: potongan judul, sudah digabung ke tautan sebelumnya";
        if (lower.startsWith("javascript:") || lower.startsWith("mailto:")
                || lower.startsWith("#") || lower.startsWith("data:")) {
            return "ditolak: bukan tautan halaman";
        }
        if (isSamePage(href, pageUrl)) return "ditolak: menunjuk halaman ini sendiri";
        // Plenty of posts wrap a cover or an illustration in <a>, so the anchor's href
        // is the image itself. It is a link on the page and the count has to include it,
        // but calling it "sendiri di grup volume" explained nothing.
        if (IMAGE_HREF.matcher(lower).find()) return "ditolak: tautan gambar, bukan chapter";
        if (FRAGMENT_TEXT.matcher(link.text.trim()).matches()) {
            return "ditolak: potongan judul chapter, bukan tautan tersendiri";
        }
        for (String path : NON_CHAPTER_PATHS) {
            if (lower.contains(path)) return "ditolak: halaman label/feed/statis, bukan chapter";
        }
        if (VOLUME_ONLY_TEXT.matcher(link.text).matches()) {
            return "ditolak: tautan volume, bukan chapter";
        }
        boolean recognised = ChapterNaming.isRecognised(link.text)
                || ChapterNaming.isRecognised(slugTail(href));
        if (recognised) {
            if (!sameSeries(href, pageStem, dominant)) {
                return "ditolak: milik novel lain (nama URL berbeda)";
            }
            return null;
        }
        if (!DATED_POST.matcher(lower).find()) {
            return "ditolak: bukan tautan postingan";
        }
        if (!sameSeries(href, pageStem, dominant)) {
            return "ditolak: milik novel lain (nama URL berbeda)";
        }
        return "ditolak: teks tidak berpola chapter";
    }

    /**
     * True when the only thing wrong with a link is that nobody called it a chapter.
     *
     * <p>A post of this series whose text reads "Bagian Pembuka" may still be a
     * chapter of a list — that is uncertainty, and the picker exists for it. A link
     * that is navigation, a static page, another series, or not a post at all is not
     * uncertain, it is wrong; those reasons are never rewritten.
     */
    private static boolean isSoftRejection(String reason) {
        return "ditolak: teks tidak berpola chapter".equals(reason);
    }

    /** True when the link clearly belongs to the same series as the page. */
    private static boolean sameSeries(String href, String pageStem, String dominant) {
        String stem = stem(href);
        if (stem.isEmpty()) return false;
        if (!dominant.isEmpty() && overlap(stem, dominant) >= 0.5f) return true;
        if (!pageStem.isEmpty() && overlap(stem, pageStem) >= 0.5f) return true;
        // A single-word slug ("prolog.html") inherits the page's series by default.
        return stem.indexOf('-') < 4 && !dominant.isEmpty() && overlap(stem, dominant) >= 0.34f;
    }

    /** The slug the majority of recognised chapter links share. */
    private static String dominantStem(List<Link> links, String pageStem) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Link link : links) {
            if (!ChapterNaming.isRecognised(link.text)
                    && !ChapterNaming.isRecognised(slugTail(link.url))) continue;
            String stem = stem(link.url);
            if (stem.isEmpty()) continue;
            Integer current = counts.get(stem);
            counts.put(stem, current == null ? 1 : current + 1);
        }
        String best = "";
        int bestCount = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return best.isEmpty() ? pageStem : best;
    }

    /** Slug of a post without Blogger's numeric suffix: {@code ...-mob_28} → {@code ...-mob}. */
    static String stem(String url) {
        if (url == null || url.isEmpty()) return "";
        String path = url;
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        int hash = path.indexOf('#');
        if (hash >= 0) path = path.substring(0, hash);
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (name.endsWith(".html")) name = name.substring(0, name.length() - 5);
        name = name.toLowerCase(Locale.ROOT);
        name = name.replaceAll("_\\d{1,4}$", "");
        return name;
    }

    /** Share of words two slugs have in common, 0..1. */
    static float overlap(String left, String right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) return 0f;
        Set<String> a = new LinkedHashSet<>(java.util.Arrays.asList(left.split("[-_]+")));
        Set<String> b = new LinkedHashSet<>(java.util.Arrays.asList(right.split("[-_]+")));
        a.remove("");
        b.remove("");
        if (a.isEmpty() || b.isEmpty()) return 0f;
        int shared = 0;
        for (String word : a) if (b.contains(word)) shared++;
        return shared / (float) Math.min(a.size(), b.size());
    }

    /** Text that could be a chapter, even without the usual wording. */
    private static boolean plausibleChapterText(String text) {
        if (text == null) return false;
        String tidy = ChapterNaming.tidy(text);
        if (tidy.isEmpty() || tidy.length() > 60) return false;
        if (VOLUME_ONLY_TEXT.matcher(tidy).matches()) return false;
        // Navigation words that show up inside a volume group on some templates.
        String lower = tidy.toLowerCase(Locale.ROOT);
        return !(lower.equals("beranda") || lower.equals("home") || lower.equals("lanjut")
                || lower.equals("sebelumnya") || lower.equals("selanjutnya")
                || lower.equals("next") || lower.equals("previous") || lower.equals("toc"));
    }

    /**
     * True when a page with no recognised chapter still looks like a catalogue.
     *
     * <p>Two signals, both structural rather than a guess about wording: a volume
     * heading was seen, or several links share one slug stem (a coherent list
     * rather than unrelated "read also" targets).
     */
    private static boolean looksLikeUnlabelledList(List<Candidate> candidates) {
        Map<String, Integer> stems = new LinkedHashMap<>();
        int usable = 0;
        for (Candidate candidate : candidates) {
            if (candidate.accepted) continue;
            if (candidate.volume > 0) return true;
            String stem = stem(candidate.url);
            if (stem.isEmpty()) continue;
            usable++;
            Integer count = stems.get(stem);
            stems.put(stem, count == null ? 1 : count + 1);
        }
        if (usable < 3) return false;
        for (Map.Entry<String, Integer> entry : stems.entrySet()) {
            if (entry.getValue() >= 3) return true;
        }
        return false;
    }

    private static boolean hasNearbyAnchor(List<Node> nodes, Node textNode) {
        int at = nodes.indexOf(textNode);
        if (at < 0) return false;
        int from = Math.max(0, at - 2);
        int to = Math.min(nodes.size(), at + 3);
        for (int i = from; i < to; i++) {
            Node node = nodes.get(i);
            if (node instanceof Element && "a".equals(((Element) node).tagName())) return true;
        }
        return false;
    }

    /** Anchors after the catalogue: "Related Posts" and friends end the list. */
    private static int catalogueEnd(List<Node> nodes, int from) {
        for (int i = Math.max(0, from); i < nodes.size(); i++) {
            Node node = nodes.get(i);
            String text;
            if (node instanceof TextNode) {
                text = ((TextNode) node).text();
            } else if (node instanceof Element) {
                String tag = ((Element) node).tagName();
                if ("a".equals(tag) || "img".equals(tag)) continue;
                text = ((Element) node).ownText();
            } else {
                continue;
            }
            String lower = ChapterNaming.tidy(text).toLowerCase(Locale.ROOT);
            if (lower.isEmpty() || lower.length() > 40) continue;
            for (String marker : TRAILING_MARKERS) {
                if (lower.equals(marker) || lower.startsWith(marker)) return i;
            }
        }
        return nodes.size();
    }

    private static List<Volume> groupByVolume(List<Chapter> chapters, Set<Integer> headings) {
        Map<Integer, List<Chapter>> grouped = new LinkedHashMap<>();
        for (Chapter chapter : chapters) {
            List<Chapter> list = grouped.get(chapter.volume);
            if (list == null) {
                list = new ArrayList<>();
                grouped.put(chapter.volume, list);
            }
            list.add(chapter);
        }
        List<Volume> volumes = new ArrayList<>();
        for (Map.Entry<Integer, List<Chapter>> entry : grouped.entrySet()) {
            List<Chapter> list = entry.getValue();
            // The page's own order is the reading order; the link index is that order.
            list.sort((left, right) -> left.order != right.order
                    ? left.order - right.order
                    : left.label.compareToIgnoreCase(right.label));
            volumes.add(new Volume(entry.getKey(), list));
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
        return volumes;
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
        Document document = org.jsoup.Jsoup.parse(html, url);
        Element body = articleBody(document);
        if (body == null) return "{}";
        cleanArticle(body);
        List<Node> nodes = flatten(body);
        int start = chapterListStart(nodes, url);
        return labelledFields(textOf(start > 0 ? nodes.subList(0, start) : nodes)).toString();
    }

    /** Debug helper for tests: the description text as it is parsed. */
    public static String debugHeadText(String html, String url) {
        Document document = org.jsoup.Jsoup.parse(html, url);
        Element body = articleBody(document);
        if (body == null) return "";
        cleanArticle(body);
        List<Node> nodes = flatten(body);
        int start = chapterListStart(nodes, url);
        return textOf(start > 0 ? nodes.subList(0, start) : nodes);
    }

    /** Debug helper for tests: every image in the article with its declared size. */
    public static String debugImages(String html, String url) {
        Document document = org.jsoup.Jsoup.parse(html, url);
        Element body = articleBody(document);
        if (body == null) return "";
        cleanArticle(body);
        StringBuilder out = new StringBuilder();
        for (Element image : body.select("img")) {
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

    /** Article heading, falling back to the document title and then the address. */
    private static String title(Document document, Element body, Map<String, String> fields) {
        // Inside the article first: on a template whose <header> prints the site
        // name in an <h1>, a document-wide "first h1" would title the novel after
        // the blog.
        String[] inside = {"h1.post-title", "h3.post-title", "h1.entry-title", "h2.entry-title",
                "h1", "h2"};
        for (String selector : inside) {
            Elements found = body.select(selector);
            if (found.isEmpty()) continue;
            String text = ChapterNaming.tidy(found.first().text());
            if (!text.isEmpty() && text.length() <= 200) return text;
        }

        String[] selectors = {
                "h1.post-title", "h3.post-title", "h1.entry-title", "h2.entry-title",
                ".post-title", "article h1", "article h2", "h1"
        };
        for (String selector : selectors) {
            for (Element candidate : document.select(selector)) {
                if (insideChrome(candidate)) continue;
                String text = ChapterNaming.tidy(candidate.text());
                if (text.isEmpty() || text.length() > 200) continue;
                return text;
            }
        }
        String alternate = first(fields, "alternate tittle", "alternate title", "judul lain");
        if (!alternate.isEmpty()) return head(alternate, 200);

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

    /** True when the element belongs to page chrome rather than the article. */
    private static boolean insideChrome(Element element) {
        for (Element parent : element.parents()) {
            String tag = parent.tagName();
            if ("header".equals(tag) || "nav".equals(tag) || "footer".equals(tag)
                    || "aside".equals(tag)) return true;
        }
        return false;
    }

    /** First line of a multi-line value, trimmed to a sane title length. */
    private static String head(String value, int limit) {
        int newline = value.indexOf('\n');
        String first = newline > 0 ? value.substring(0, newline) : value;
        first = ChapterNaming.tidy(first);
        return first.length() > limit ? first.substring(0, limit - 1) + "…" : first;
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
     * <p>"First image" is wrong (descriptions open with a wide promotional
     * banner); "largest image" is wrong too (volume illustrations are wide). The
     * cover is the first picture taller than it is wide, and pictures nested inside
     * a link count, because that is how these pages wrap their covers.
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
        int list = indexOfAny(text.substring(from), "list volume", "daftar volume", "volume 1",
                "vol 1");
        if (list > 0) end = from + list;

        List<String> paragraphs = new ArrayList<>();
        for (String raw : text.substring(from, end).split("\n")) {
            String line = ChapterNaming.tidy(raw);
            if (line.isEmpty()) continue;
            if (line.length() < 25 && paragraphs.isEmpty()) continue;
            if (ChapterNaming.isRecognised(line)) break;
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
            if (!"a".equals(element.tagName())) {
                buffer.setLength(0);
                continue;
            }
            buffer.setLength(0);
            String href = absolute(element);
            if (href.isEmpty() || isSamePage(href, pageUrl)) continue;
            String text = ChapterNaming.tidy(element.text());
            if (ChapterNaming.isRecognised(text) || ChapterNaming.isRecognised(slugTail(href))) {
                return i;
            }
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
                if ("a".equals(tag) || "img".equals(tag)) out.append(element.text()).append('\n');
            }
        }
        return out.toString();
    }

    // ---------------------------------------------------------------- structure

    /**
     * Removes page chrome <em>inside</em> the article.
     *
     * <p>Deliberately never called on the document: the article on this template is
     * a descendant of {@code div.widget.Blog}, so removing widgets document-wide
     * deleted the article and every chapter link with it. Cleaning happens on the
     * body element only, and no ancestor of the body is touched.
     */
    private static void cleanArticle(Element body) {
        for (String selector : ARTICLE_CHROME) {
            Elements found = body.select(selector);
            for (Element element : found) {
                if (element == body) continue;
                if (containsArticle(element)) continue;
                element.remove();
            }
        }
    }

    /** True when removing this element would remove the article body itself. */
    private static boolean containsArticle(Element element) {
        return element.selectFirst("div.post-body, div.entry-content") != null;
    }

    /** Text an article must hold before {@code <body>} itself is accepted as one. */
    private static final int MIN_ARTICLE_TEXT = 120;

    /**
     * The element holding the article, or {@code null} when the page has none.
     *
     * <p>The last resort — the whole {@code <body>} — is only accepted when it
     * actually reads like an article. A page whose body is nothing but a nav bar
     * with a "Chapter 1" link would otherwise be parsed as a catalogue with one
     * chapter, which is exactly the false positive the preview must never show.
     */
    private static Element articleBody(Document document) {
        String[] selectors = {
                "div.post-body.entry-content", "div.post-body", "div.entry-content",
                "article .post-body", "article", "main", "div#content"
        };
        for (String selector : selectors) {
            Elements found = document.select(selector);
            for (Element candidate : found) {
                if (candidate.text().trim().isEmpty()) continue;
                return candidate;
            }
        }
        Element body = document.body();
        if (body == null) return null;
        String text = ChapterNaming.tidy(body.text());
        if (text.length() < MIN_ARTICLE_TEXT) return null;
        // A body whose every word sits inside nav/header/footer is page chrome.
        int chrome = 0;
        for (Element part : body.select("nav, header, footer, aside")) {
            chrome += ChapterNaming.tidy(part.text()).length();
        }
        return chrome >= text.length() * 3 / 4 ? null : body;
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
        String left = href.replaceAll("/+$", "").replaceAll("\\?.*$", "");
        String right = url.replaceAll("/+$", "").replaceAll("\\?.*$", "");
        return left.equals(right);
    }

    /** True when the link is part of this novel rather than site chrome. */
    static boolean isChapterLink(String text, String href) {
        if (href == null || href.isEmpty()) return false;
        String lower = href.toLowerCase(Locale.ROOT);
        for (String path : NON_CHAPTER_PATHS) {
            if (lower.contains(path)) return false;
        }
        if (VOLUME_ONLY_TEXT.matcher(text == null ? "" : text).matches()) return false;
        if (ChapterNaming.isRecognised(text)) return true;
        if (!DATED_POST.matcher(lower).find()) return false;
        return ChapterNaming.isRecognised(slugTail(href));
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
