package id.shuyasal.kuromaku.net;

import org.json.JSONException;

import java.io.IOException;
import java.util.List;

import id.shuyasal.kuromaku.data.Catalog;
import id.shuyasal.kuromaku.data.LibraryBuilder;
import id.shuyasal.kuromaku.data.Novel;
import id.shuyasal.kuromaku.data.Volume;
import id.shuyasal.kuromaku.parser.LandingCatalogParser;
import id.shuyasal.kuromaku.parser.NovelDiscovery;

/**
 * Builds one novel's catalogue from its own source shape.
 *
 * <p>Monogatari publishes a Blogger label, so its chapters come from the JSON
 * feed and are grouped by the volume printed in each title. Magical★Explorer has
 * no single label: its landing page lists "Volume N" followed by that volume's
 * chapter links, so those links are used as they are. Neither path invents a
 * chapter and neither can return another novel's posts.
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
        if (novel.catalogMode == Novel.CATALOG_FEED) {
            catalog = fromFeed(novel);
        } else if (novel.catalogMode == Novel.CATALOG_LANDING) {
            catalog = fromLanding(novel);
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
                ? "Tidak ada chapter yang ditemukan pada halaman katalog." : null;
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
     * Catalogue of a user-added novel.
     *
     * <p>The page is read again on every refresh, which is what makes a newly
     * published chapter appear after "Muat Ulang" without touching the stored
     * novel's identity, title or reading progress.
     */
    private static Catalog fromDiscovery(Novel novel) throws IOException {
        return new Catalog(novel.id, inspectFor(novel).volumes);
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
     * Fetches and reads a catalogue URL without storing anything.
     *
     * <p>Used when the user adds a novel and when an existing one is refreshed, so
     * the parse the preview shows is exactly the parse the reader will use. The
     * fetch goes through {@link Fetcher}, which is what lets the whole add/refresh
     * path be tested offline.
     *
     * @throws IOException                when the page cannot be downloaded
     * @throws NovelDiscovery.Unreadable  when the page is not a readable catalogue
     */
    public static NovelDiscovery.Result inspect(String normalisedUrl)
            throws IOException, NovelDiscovery.Unreadable {
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
}
