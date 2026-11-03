package id.shuyasal.kuromaku.net;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import id.shuyasal.kuromaku.data.Catalog;
import id.shuyasal.kuromaku.data.CatalogueMerge;
import id.shuyasal.kuromaku.data.FeedEntry;
import id.shuyasal.kuromaku.data.LibraryBuilder;
import id.shuyasal.kuromaku.data.Novel;
import id.shuyasal.kuromaku.data.Volume;
import id.shuyasal.kuromaku.parser.LandingCatalogParser;
import id.shuyasal.kuromaku.parser.NovelDiscovery;

/**
 * Builds one novel's catalogue from its own source shape.
 *
 * <p>Three shapes exist, and a user-added novel can be any of them:
 *
 * <ul>
 *   <li><b>feed</b> — a Blogger label (Monogatari). Every post carrying the label
 *       is a chapter; the volume is printed in each title.</li>
 *   <li><b>landing</b> — a post that lists "Volume N" headings followed by that
 *       volume's links (Magical★Explorer).</li>
 *   <li><b>discovered</b> — whatever a pasted address turns out to be. A dated
 *       article is scraped link by link; a label address is read through its feed,
 *       with the page itself used only for the description and metadata.</li>
 * </ul>
 *
 * <p>Neither path invents a chapter and neither can return another novel's posts:
 * the feed is scoped to one label, and the scraper rejects links whose slug does
 * not belong to the series on the page.
 */
public final class CatalogLoader {

    /** What was read, plus a short description for the status line. */
    public static final class Loaded {
        public final Catalog catalog;
        public final String summary;
        public final String warning;
        /**
         * The page that was just read, for a novel whose catalogue is discovered
         * generically. Null for the feed/landing shapes, which do not carry a
         * full metadata read. The host uses it to refresh stored metadata from the
         * same single request instead of fetching the page twice.
         */
        public final NovelDiscovery.Result discovery;

        Loaded(Catalog catalog, String summary, String warning) {
            this(catalog, summary, warning, null);
        }

        Loaded(Catalog catalog, String summary, String warning,
               NovelDiscovery.Result discovery) {
            this.catalog = catalog;
            this.summary = summary;
            this.warning = warning;
            this.discovery = discovery;
        }
    }

    private CatalogLoader() {
    }

    public static Loaded load(Novel novel) throws IOException, JSONException {
        if (novel == null) throw new IOException("Novel tidak dikenal.");
        Catalog catalog;
        NovelDiscovery.Result discovery = null;
        String extra = null;

        if (novel.catalogMode == Novel.CATALOG_FEED) {
            catalog = fromFeed(novel);
        } else if (novel.catalogMode == Novel.CATALOG_ARTICLE_FEED) {
            Loaded article = fromArticleAndFeed(novel);
            catalog = article.catalog;
            discovery = article.discovery;
            extra = article.warning;
        } else if (novel.catalogMode == Novel.CATALOG_LANDING) {
            catalog = fromLanding(novel);
        } else if (NovelUrl.isLabel(novel.landingUrl)) {
            // A label address: chapters from the feed, description from the page.
            Loaded labelled = fromLabel(novel);
            catalog = labelled.catalog;
            discovery = labelled.discovery;
            extra = labelled.warning;
        } else {
            discovery = inspectFor(novel);
            catalog = new Catalog(novel.id, discovery.volumes);
        }

        Catalog withCovers = catalog.withCovers(novel.id);

        int volumes = 0;
        for (Volume volume : withCovers.volumes) {
            if (volume.chapterCount() > 0) volumes++;
        }
        String summary = withCovers.size() + " chapter · " + withCovers.volumes.size()
                + " volume (" + volumes + " berisi chapter)";
        String warning = withCovers.size() == 0
                ? "Tidak ada chapter yang ditemukan pada halaman katalog." : extra;
        return new Loaded(withCovers, summary, warning, discovery);
    }

    private static Catalog fromFeed(Novel novel) throws IOException, JSONException {
        FeedClient.Result result = FeedClient.fetchEntries();
        if (result.entries.isEmpty()) {
            throw new IOException("Feed label " + novel.title + " tidak mengembalikan postingan.");
        }
        return LibraryBuilder.build(novel.id, result.entries);
    }

    private static Catalog fromLanding(Novel novel) throws IOException {
        String html = Http.get(novel.landingUrl);
        if (html == null || html.isEmpty()) {
            throw new IOException("Halaman katalog " + novel.landingUrl + " kosong.");
        }
        List<Volume> volumes = LandingCatalogParser.parse(html, novel.slugPrefix, novel.id);
        if (volumes.isEmpty()) {
            throw new IOException("Tautan chapter tidak ditemukan di halaman katalog.");
        }
        return new Catalog(novel.id, volumes);
    }

    /**
     * Catalogue from the source's own list article, repaired and extended by the
     * label feed.
     *
     * <p>The article is the authority: it prints every volume heading and the
     * chapter links in reading order, which no feed can express. The feed is a
     * helper with three jobs — resolve a link the article printed twice, add a
     * chapter the article never listed, and stand in as the whole catalogue when
     * the article cannot be read at all.
     *
     * <p>Failure is not silent and never produces an empty shelf: if the article
     * fails but the feed answers, the feed builds the catalogue and the warning says
     * the article was skipped; if both fail the exception travels to the caller,
     * which keeps the catalogue it already had instead of replacing it with nothing.
     */
    private static Loaded fromArticleAndFeed(Novel novel) throws IOException, JSONException {
        String pageUrl = novel.landingUrl;
        String html = null;
        List<Volume> pageVolumes = null;
        String pageProblem = null;
        try {
            html = Fetcher.get().page(pageUrl);
            if (html == null || html.trim().isEmpty()) {
                pageProblem = "halaman katalog kosong";
            } else {
                pageVolumes = LandingCatalogParser.parse(html, novel.slugPrefix, novel.id);
                if (pageVolumes.isEmpty()) {
                    pageProblem = "tidak ada tautan chapter di halaman katalog";
                    pageVolumes = null;
                }
            }
        } catch (Exception error) {
            pageProblem = reason(error);
        }

        List<FeedEntry> chapterPosts = new ArrayList<>();
        String feedProblem = null;
        if (novel.feedLabel != null && !novel.feedLabel.isEmpty()) {
            try {
                FeedClient.Result feed = FeedClient.fetchLabel(novel.feedLabel, url -> {
                    try {
                        return Fetcher.get().page(url);
                    } catch (IOException error) {
                        throw error;
                    } catch (Exception error) {
                        throw new IOException(error.getMessage() == null
                                ? error.getClass().getSimpleName() : error.getMessage(), error);
                    }
                });
                for (FeedEntry entry : feed.entries) {
                    // The feed also carries the description post, which is not a
                    // chapter and has no volume.
                    if (LibraryBuilder.isChapterPost(entry.title)) chapterPosts.add(entry);
                }
            } catch (Exception error) {
                feedProblem = reason(error);
            }
        }

        List<Volume> volumes;
        CatalogueMerge.Result merged = null;
        if (pageVolumes != null) {
            merged = CatalogueMerge.merge(novel.id, pageVolumes, chapterPosts);
            volumes = merged.volumes;
        } else if (!chapterPosts.isEmpty()) {
            volumes = LibraryBuilder.build(novel.id, chapterPosts).volumes;
        } else {
            String problem = "Katalog " + novel.title + " tidak dapat dibaca. ";
            if (pageProblem != null) problem += "Halaman katalog: " + pageProblem + ". ";
            if (feedProblem != null) problem += "Feed label: " + feedProblem + ". ";
            if (novel.feedLabel == null || novel.feedLabel.isEmpty()) {
                problem += "Novel ini tidak punya label feed sebagai cadangan.";
            }
            throw new IOException(problem.trim());
        }

        // Metadata from the article, when it was readable. A failure here leaves the
        // stored metadata alone rather than guessing.
        NovelDiscovery.Result discovery = null;
        if (html != null) {
            try {
                discovery = NovelDiscovery.describe(html, pageUrl);
            } catch (NovelDiscovery.Unreadable ignored) {
                // The chapter list parsed, so the body exists; this only means the
                // description fields could not be read.
            }
        }

        StringBuilder notes = new StringBuilder();
        if (merged != null) {
            notes.append("Daftar chapter diambil dari artikel katalog sumber");
            if (merged.repaired > 0) {
                notes.append(", ").append(merged.repaired)
                        .append(" tautan yang salah di artikel diperbaiki dari feed label");
            }
            if (merged.added > 0) {
                notes.append(", ").append(merged.added)
                        .append(" chapter yang belum ada di artikel ditambahkan dari feed label");
            }
            notes.append('.');
        } else {
            notes.append("Artikel katalog tidak terbaca (").append(pageProblem)
                    .append("), jadi daftar chapter diambil dari feed label “")
                    .append(novel.feedLabel).append("” saja.");
        }
        if (pageVolumes != null && feedProblem != null) {
            notes.append(" Feed label gagal dibaca (").append(feedProblem)
                    .append("); daftar chapter memakai artikel saja, tanpa perbaikan tautan.");
        }
        if (pageVolumes != null && chapterPosts.isEmpty() && feedProblem == null
                && novel.feedLabel != null && !novel.feedLabel.isEmpty()) {
            notes.append(" Feed label tidak mengembalikan chapter, jadi tidak ada yang bisa "
                    + "dipakai untuk memeriksa tautan artikel.");
        }

        return new Loaded(new Catalog(novel.id, volumes), "", notes.toString(), discovery);
    }

    /** Short, human-readable cause for a warning line. */
    private static String reason(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.trim().isEmpty()
                ? (error == null ? "tanpa keterangan" : error.getClass().getSimpleName())
                : message.trim();
    }

    /**
     * Catalogue of a novel whose address is a Blogger label.
     *
     * <p>The label's JSON feed is the catalogue: it is paged by the app until
     * Blogger returns nothing, so all 44 chapters appear even though the HTML
     * listing only prints twenty per page. The label page is then read once, only
     * for the description — and a failure there is reported, not swallowed, because
     * a novel with chapters but no cover should say so.
     */
    private static Loaded fromLabel(Novel novel) throws IOException, JSONException {
        String label;
        try {
            label = NovelUrl.label(novel.landingUrl);
        } catch (NovelUrl.Invalid error) {
            throw new IOException(error.getMessage(), error);
        }
        // Goes through the Fetcher seam, not Http directly: the reload path has to be
        // exercisable without a network, exactly like the add-novel preview is.
        FeedClient.Result feed = FeedClient.fetchLabel(label, url -> {
            try {
                return Fetcher.get().page(url);
            } catch (IOException error) {
                throw error;
            } catch (Exception error) {
                throw new IOException(error.getMessage() == null
                        ? error.getClass().getSimpleName() : error.getMessage(), error);
            }
        });
        if (feed.entries.isEmpty()) {
            throw new IOException("Label “" + label + "” tidak memiliki postingan. "
                    + "Pastikan nama labelnya benar dan masih dipakai di blog sumber.");
        }
        // A label feed lists every post carrying the label, and the series
        // description post carries it too. It is not a chapter. The add-novel path
        // already filtered it; this path did not, so a reload quietly turned the
        // description into a chapter at the end of Volume 0.
        List<FeedEntry> chapterEntries = new ArrayList<>();
        int descriptionPosts = 0;
        for (FeedEntry entry : feed.entries) {
            if (LibraryBuilder.isChapterPost(entry.title)) chapterEntries.add(entry);
            else descriptionPosts++;
        }
        if (chapterEntries.isEmpty()) {
            throw new IOException("Label “" + label + "” hanya berisi " + descriptionPosts
                    + " postingan deskripsi dan tidak satu pun chapter. Pakai URL artikel novelnya.");
        }
        Catalog catalog = LibraryBuilder.build(novel.id, chapterEntries);

        // Same rule as the preview: a number the user can check against the list.
        int catalogued = 0;
        for (Volume volume : catalog.volumes) catalogued += volume.chapterCount();
        int merged = chapterEntries.size() - catalogued;

        List<String> warnings = new ArrayList<>();
        warnings.add(catalogued + " chapter diambil dari feed label “" + label + "”.");
        if (descriptionPosts > 0) {
            warnings.add(descriptionPosts + " postingan deskripsi novel tidak dihitung sebagai "
                    + "chapter, karena isinya bukan bagian dari cerita.");
        }
        if (merged > 0) {
            warnings.add(merged + " judul chapter terbit lebih dari sekali dan digabung, "
                    + "supaya tidak ada chapter ganda di daftar.");
        }
        if (feed.pagesRead > 1) {
            warnings.add("Feed dibaca " + feed.pagesRead + " halaman; daftar chapter lengkap.");
        }
        // What the user is told on the library screen. A complete feed used to leave
        // this null, so a reload that dropped the description post said nothing at
        // all and looked like a chapter had gone missing.
        StringBuilder extraText = new StringBuilder();
        if (descriptionPosts > 0) {
            extraText.append(descriptionPosts).append(" postingan deskripsi novel tidak "
                    + "dihitung sebagai chapter.");
        }
        if (merged > 0) {
            if (extraText.length() > 0) extraText.append(' ');
            extraText.append(merged).append(" judul chapter terbit lebih dari sekali dan "
                    + "digabung.");
        }
        if (feed.warning != null) {
            if (extraText.length() > 0) extraText.append(' ');
            extraText.append(feed.warning);
        }
        String extra = extraText.length() == 0 ? null : extraText.toString();
        if (feed.warning != null) warnings.add(feed.warning);

        NovelDiscovery.Result page = null;
        try {
            String html = Fetcher.get().page(novel.landingUrl);
            page = NovelDiscovery.describe(html, novel.landingUrl);
        } catch (NovelDiscovery.Unreadable error) {
            warnings.add("Deskripsi tidak terbaca dari halaman label: " + error.getMessage());
        } catch (Exception error) {
            warnings.add("Halaman label gagal dimuat; metadata dibiarkan kosong.");
        }

        NovelDiscovery.Result discovery = describeLabelPage(novel.landingUrl, page, label, warnings);
        NovelDiscovery.Result withChapters = NovelDiscovery.catalogueFromFeed(
                discovery.sourceUrl, discovery.title, discovery.titleJp, discovery.author,
                discovery.illustrator, discovery.genres, discovery.status, discovery.coverUrl,
                discovery.coverRatio, discovery.synopsis, catalog.volumes, warnings);

        return new Loaded(catalog, "", extra, withChapters);
    }


    /**
     * Description of a label address, as far as the label page can honestly give it.
     *
     * <p>A label listing prints chapter posts, not the novel's own description post,
     * so whatever picture it carries is a chapter illustration — using it as the
     * series cover would be a silent lie. Metadata is therefore kept only when the
     * page actually printed labelled fields, and the resulting gap is stated in the
     * warnings with the one thing the user can do about it.
     */
    private static NovelDiscovery.Result describeLabelPage(String url, NovelDiscovery.Result page,
                                                           String label, List<String> warnings) {
        // The label name is the novel's name as the translator typed it. The page's
        // own heading is the newest chapter's title ("… Volume 3 Afterword"), so
        // using it would label the whole novel after one chapter.
        String title = label;
        boolean described = page != null && (!page.author.isEmpty()
                || !page.illustrator.isEmpty() || !page.genres.isEmpty());
        if (!described) {
            if (page != null && !page.coverUrl.isEmpty()) {
                warnings.add("Halaman label tidak memuat deskripsi novel, jadi cover tidak "
                        + "diambil dari ilustrasi chapter. Kartu memakai placeholder.");
            }
            warnings.add("Untuk cover, penulis, dan sinopsis, tambahkan novel ini lewat URL "
                    + "artikelnya, bukan URL label.");
        }
        return NovelDiscovery.catalogueFromFeed(
                url,
                title,
                page == null || !described ? "" : page.titleJp,
                page == null || !described ? "" : page.author,
                page == null || !described ? "" : page.illustrator,
                page == null || !described ? "" : page.genres,
                page == null || !described ? "" : page.status,
                page == null || !described ? "" : page.coverUrl,
                page == null || !described ? 0.70f : page.coverRatio,
                page == null || !described ? new String[0] : page.synopsis,
                null,
                warnings);
    }
    /** The page read as a catalogue, with a plain-language failure. */
    private static NovelDiscovery.Result inspectFor(Novel novel) throws IOException {
        try {
            return inspect(novel.landingUrl);
        } catch (NovelDiscovery.Unreadable error) {
            throw new IOException(error.getMessage(), error);
        }
    }

    /**
     * Fetches and reads an address without storing anything.
     *
     * <p>Used when the user adds a novel and when an existing one is refreshed, so
     * the parse the preview shows is exactly the parse the reader will use. A label
     * address goes through its feed; anything else is scraped from the page.
     * The fetch goes through {@link Fetcher}, which is what lets the whole
     * add/refresh path be tested offline.
     *
     * @throws IOException                when the page cannot be downloaded
     * @throws NovelDiscovery.Unreadable  when the page is not a readable catalogue
     */
    public static NovelDiscovery.Result inspect(String normalisedUrl)
            throws IOException, NovelDiscovery.Unreadable {
        if (NovelUrl.isLabel(normalisedUrl)) return inspectLabel(normalisedUrl);

        String html;
        try {
            html = Fetcher.get().page(normalisedUrl);
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            // A fixture source may throw a plain Exception; the caller only needs
            // the message, so it is normalised into an IOException here.
            throw new IOException(error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage(), error);
        }
        if (html == null || html.trim().isEmpty()) {
            throw new IOException("Halaman katalog kosong atau tidak dapat diunduh.");
        }
        return NovelDiscovery.parse(html, normalisedUrl);
    }

    /** Preview of a label address: feed for chapters, page for the description. */
    static NovelDiscovery.Result inspectLabel(String normalisedUrl)
            throws IOException, NovelDiscovery.Unreadable {
        String label;
        try {
            label = NovelUrl.label(normalisedUrl);
        } catch (NovelUrl.Invalid error) {
            throw new NovelDiscovery.Unreadable(error.getMessage());
        }

        FeedClient.Result feed;
        try {
            feed = FeedClient.fetchLabel(label, url -> {
                // One seam for both the real client and the fixture source: a
                // checked Exception from the source becomes an IOException here so
                // the feed pager only has to handle one failure shape.
                try {
                    return Fetcher.get().page(url);
                } catch (IOException error) {
                    throw error;
                } catch (Exception error) {
                    throw new IOException(error.getMessage() == null
                            ? error.getClass().getSimpleName() : error.getMessage(), error);
                }
            });
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException(error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage(), error);
        }
        if (feed.entries.isEmpty()) {
            throw new NovelDiscovery.Unreadable("Label “" + label + "” tidak memiliki postingan. "
                    + "Periksa ejaan nama label di URL, atau pakai URL artikel novelnya.");
        }
        // A label feed returns every post carrying that label, and that includes the
        // series description post — the one whose title is the novel's own name. It is
        // not a chapter: counting it put the description at the end of Volume 0.
        List<FeedEntry> chapterEntries = new ArrayList<>();
        int descriptionPosts = 0;
        for (FeedEntry entry : feed.entries) {
            if (LibraryBuilder.isChapterPost(entry.title)) chapterEntries.add(entry);
            else descriptionPosts++;
        }
        if (chapterEntries.isEmpty()) {
            throw new NovelDiscovery.Unreadable("Label “" + label + "” hanya berisi "
                    + descriptionPosts + " postingan deskripsi dan tidak satu pun chapter. "
                    + "Pakai URL artikel novelnya.");
        }
        Catalog catalog = LibraryBuilder.build("", chapterEntries);

        // Every number printed below has to equal the length of the list on screen.
        // The first version said "43 chapter diambil dari feed" while the list showed
        // 42, because it counted the feed posts before the duplicate merge — the kind
        // of off-by-one that sends the user looking for a chapter that never existed.
        int catalogued = 0;
        for (Volume volume : catalog.volumes) catalogued += volume.chapterCount();
        int merged = chapterEntries.size() - catalogued;

        List<String> warnings = new ArrayList<>();
        warnings.add(catalogued + " chapter diambil dari feed label “" + label + "”"
                + (feed.pagesRead > 1 ? " (" + feed.pagesRead + " halaman feed)." : "."));
        if (descriptionPosts > 0) {
            warnings.add(descriptionPosts + " postingan deskripsi novel tidak dihitung sebagai "
                    + "chapter, karena isinya bukan bagian dari cerita.");
        }
        if (merged > 0) {
            warnings.add(chapterEntries.size() + " postingan chapter di feed; " + merged
                    + " di antaranya menerbitkan ulang judul yang sama dan digabung, "
                    + "jadi daftar berisi " + catalogued + " chapter unik.");
        }
        if (feed.warning != null) warnings.add(feed.warning);
        warnings.add("Halaman label menampilkan 20 postingan per halaman; aplikasi membaca "
                + "seluruh feed, jadi jumlah di atas adalah jumlah sebenarnya.");

        NovelDiscovery.Result page = null;
        try {
            String html = Fetcher.get().page(normalisedUrl);
            page = NovelDiscovery.describe(html, normalisedUrl);
        } catch (NovelDiscovery.Unreadable error) {
            warnings.add("Deskripsi tidak ditemukan di halaman label: " + error.getMessage());
        } catch (Exception error) {
            warnings.add("Halaman label gagal dimuat; cover dan metadata dibiarkan kosong.");
        }

        NovelDiscovery.Result described = describeLabelPage(normalisedUrl, page, label, warnings);
        return NovelDiscovery.catalogueFromFeed(
                described.sourceUrl, described.title, described.titleJp, described.author,
                described.illustrator, described.genres, described.status, described.coverUrl,
                described.coverRatio, described.synopsis, catalog.volumes, warnings,
                feed.entries.size());
    }
}
