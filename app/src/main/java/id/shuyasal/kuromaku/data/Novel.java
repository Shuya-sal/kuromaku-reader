package id.shuyasal.kuromaku.data;

/**
 * One novel in the library: title, verified metadata, cover and how its
 * catalogue is discovered.
 *
 * <p>Every text field here was read from that novel's own page on
 * Kaori Translation. Nothing is inferred: when the source does not state the
 * publication status, {@link #status} says so instead of guessing.
 */
public final class Novel {

    /** Catalogue comes from the Blogger JSON feed for a label. */
    public static final int CATALOG_FEED = 0;
    /** Catalogue comes from the chapter links listed on the landing page. */
    public static final int CATALOG_LANDING = 1;

    /**
     * Catalogue discovered generically: the chapter links and volume headings are
     * read from whatever structure the page happens to use, without a per-novel
     * slug prefix. This is how a novel the user adds by URL is read.
     */
    public static final int CATALOG_DISCOVERED = 2;

    /**
     * Catalogue read from the source's own list article, with the label feed used
     * to repair and extend it.
     *
     * <p>Monogatari publishes one article that lists every volume and every chapter
     * link, so that article is the authority on volume order and labels — a feed
     * cannot show which volume a post belongs to except by parsing its title. But
     * the article is maintained by hand, and it is wrong in one place: its Volume 2
     * "Prolog" anchor points at the Chapter 1 post. The feed still has the real
     * prologue, so both are read and reconciled by
     * {@link id.shuyasal.kuromaku.data.CatalogueMerge}: the article decides the
     * shape, the feed fills a hole or a wrong link, and neither can blank the other.
     */
    public static final int CATALOG_ARTICLE_FEED = 3;

    /**
     * What {@link #status} says when the source page states no publication status.
     *
     * <p>Kaori Translation's landing pages carry a genre line and a translator line
     * but no status, so the reader says this in words rather than guessing "Ongoing"
     * or "Completed". The sentence is user-visible, so it lives here once: the two
     * built-in novels, the metadata copy in {@link NovelInfo} and the path that saves
     * a novel the user added by URL all read this constant instead of repeating the
     * text. Of the four copies this replaced, three were held by a test and one - the
     * fallback in the save path - was not: replacing that literal with
     * "Status tidak diketahui" left all 356 tests green, which is why the decision now
     * lives in {@link #statusOrDefault(String)} where it can be asserted directly.
     */
    public static final String STATUS_NOT_STATED = "Tidak dicantumkan di halaman sumber";

    /**
     * The status to store for a novel whose source page stated none.
     *
     * <p>A novel added by URL is read off its post, and that post may carry no status
     * line at all. Storing the empty string would leave a blank row in the detail
     * page, so the reader substitutes {@link #STATUS_NOT_STATED} - the same sentence
     * the built-in novels use - and never guesses. The decision lives here rather than
     * inline in the save path so it can be tested: when it was a literal in
     * MainActivity, replacing that literal with "Status tidak diketahui" left all 356
     * tests green.
     */
    public static String statusOrDefault(String status) {
        return status == null || status.trim().isEmpty() ? STATUS_NOT_STATED : status;
    }

    /**
     * True for the two novels that ship with the app. A built-in novel can still
     * be removed from the library, but the app knows its verified metadata
     * without ever fetching it, so it does not depend on the network to appear.
     */
    public final boolean builtIn;

    /** Stable id used for preferences, caching and cover lookup. */
    public final String id;
    /** Romanised title as used by the source. */
    public final String title;
    /** Japanese title as printed on the source page. */
    public final String titleJp;
    /** Longest title form, used where space allows. */
    public final String fullTitle;

    public final String author;
    public final String illustrator;
    public final String genres;
    public final String status;
    public final String translation;

    /** Label page for the feed-based catalogue, also the attribution target. */
    public final String sourceLabelUrl;
    /** Post that holds metadata, synopsis and the volume list. */
    public final String landingUrl;

    /** Series cover (the volume 1 artwork). */
    public final String coverUrl;
    public final float coverRatio;

    public final String[] synopsis;
    public final int synopsisPreview;

    public final int catalogMode;
    /** Slug prefix shared by this novel's chapter posts, e.g. {@code ln-magical-explorer-}. */
    public final String slugPrefix;
    /** Blogger label used by the JSON feed, empty for landing-page catalogues. */
    public final String feedLabel;

    public Novel(String id, String title, String titleJp, String fullTitle,
                 String author, String illustrator, String genres, String status,
                 String translation, String sourceLabelUrl, String landingUrl,
                 String coverUrl, float coverRatio, String[] synopsis, int synopsisPreview,
                 int catalogMode, String slugPrefix, String feedLabel) {
        this(id, title, titleJp, fullTitle, author, illustrator, genres, status, translation,
                sourceLabelUrl, landingUrl, coverUrl, coverRatio, synopsis, synopsisPreview,
                catalogMode, slugPrefix, feedLabel, false);
    }

    public Novel(String id, String title, String titleJp, String fullTitle,
                 String author, String illustrator, String genres, String status,
                 String translation, String sourceLabelUrl, String landingUrl,
                 String coverUrl, float coverRatio, String[] synopsis, int synopsisPreview,
                 int catalogMode, String slugPrefix, String feedLabel, boolean builtIn) {
        this.id = id;
        this.builtIn = builtIn;
        this.title = title;
        this.titleJp = titleJp;
        this.fullTitle = fullTitle;
        this.author = author;
        this.illustrator = illustrator;
        this.genres = genres;
        this.status = status;
        this.translation = translation;
        this.sourceLabelUrl = sourceLabelUrl;
        this.landingUrl = landingUrl;
        this.coverUrl = coverUrl;
        this.coverRatio = coverRatio > 0 ? coverRatio : 0.70f;
        this.synopsis = synopsis == null ? new String[0] : synopsis;
        this.synopsisPreview = Math.max(1, synopsisPreview);
        this.catalogMode = catalogMode;
        this.slugPrefix = slugPrefix == null ? "" : slugPrefix;
        this.feedLabel = feedLabel == null ? "" : feedLabel;
    }

    /** Cover height in px for a given width, matching the real artwork. */
    public int coverHeight(int widthPx) {
        if (widthPx <= 0) return 0;
        return Math.max(1, Math.round(widthPx / coverRatio));
    }

    /** Short label for the library card, e.g. "Monogatari no Kuromaku". */
    public String shortTitle() {
        return title;
    }
}
