package id.shuyasal.kuromaku.net;

import org.json.JSONException;

import java.io.IOException;
import java.util.List;

import id.shuyasal.kuromaku.data.Catalog;
import id.shuyasal.kuromaku.data.LibraryBuilder;
import id.shuyasal.kuromaku.data.Novel;
import id.shuyasal.kuromaku.data.Volume;
import id.shuyasal.kuromaku.parser.LandingCatalogParser;

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

        Loaded(Catalog catalog, String summary, String warning) {
            this.catalog = catalog;
            this.summary = summary;
            this.warning = warning;
        }
    }

    private CatalogLoader() {
    }

    public static Loaded load(Novel novel) throws IOException, JSONException {
        if (novel == null) throw new IOException("Novel tidak dikenal.");
        Catalog catalog = novel.catalogMode == Novel.CATALOG_LANDING
                ? fromLanding(novel)
                : fromFeed(novel);
        Catalog withCovers = catalog.withCovers(novel.id);

        int volumes = 0;
        for (Volume volume : withCovers.volumes) {
            if (volume.chapterCount() > 0) volumes++;
        }
        String summary = withCovers.size() + " chapter · " + withCovers.volumes.size()
                + " volume (" + volumes + " berisi chapter)";
        String warning = withCovers.size() == 0
                ? "Tidak ada chapter yang ditemukan pada halaman katalog." : null;
        return new Loaded(withCovers, summary, warning);
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
}
