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
        this.id = id;
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
