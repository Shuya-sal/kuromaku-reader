package id.shuyasal.kuromaku.net;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import id.shuyasal.kuromaku.data.FeedEntry;

/**
 * Reads the public Blogger JSON feed for the Kaori Translation label.
 *
 * <p>Blogger does not guarantee a fixed page size: a request with
 * {@code max-results=25} may return 22 items. The previous implementation
 * stopped whenever a page returned fewer items than requested, so everything
 * older than the second page was silently dropped (only Volumes 5-7 appeared).
 * This implementation pages until Blogger returns an empty page, advancing the
 * cursor by the number of items actually received.
 */
public final class FeedClient {

    public static final String SOURCE_HOST = "kaoritranslation.blogspot.com";
    public static final String LABEL = "Monogatari no Kuromaku";
    public static final String LABEL_URL =
            "https://kaoritranslation.blogspot.com/search/label/Monogatari%20no%20Kuromaku";

    private static final String FEED =
            "https://kaoritranslation.blogspot.com/feeds/posts/summary/-/Monogatari%20no%20Kuromaku";

    /** Feed of any label, encoded the way Blogger expects it. */
    public static String feedUrl(String labelName) {
        return "https://kaoritranslation.blogspot.com/feeds/posts/summary/-/"
                + NovelUrl.encode(labelName == null ? "" : labelName.trim()) + "?alt=json";
    }

    /** Requested page size. Blogger may return fewer; we never assume it. */
    static final int PAGE_SIZE = 25;

    /** Safety stop: 40 pages x 25 = 1000 posts, far above the current 117. */
    static final int MAX_REQUESTS = 40;

    /** Fetches a page body. Injected so pagination can be unit tested offline. */
    public interface Fetcher {
        String get(String url) throws IOException;
    }

    /** Collected entries plus a description of how complete the crawl was. */
    public static final class Result {
        public final List<FeedEntry> entries;
        public final int expectedTotal;
        public final int pagesRead;
        public final String warning;

        Result(List<FeedEntry> entries, int expectedTotal, int pagesRead, String warning) {
            this.entries = Collections.unmodifiableList(entries);
            this.expectedTotal = expectedTotal;
            this.pagesRead = pagesRead;
            this.warning = warning;
        }

        public boolean isComplete() {
            return warning == null && (expectedTotal <= 0 || entries.size() >= expectedTotal);
        }
    }

    private FeedClient() {
    }

    public static Result fetchEntries() throws IOException, JSONException {
        return fetchEntries(Http::get);
    }

    public static Result fetchEntries(Fetcher fetcher) throws IOException, JSONException {
        return fetch(FEED, fetcher);
    }

    /**
     * Every post carrying {@code labelName}, paged until Blogger returns an empty
     * page.
     *
     * <p>This is the catalogue a label address gets, and the reason a label page is
     * worth supporting: its HTML listing shows twenty posts and hides the rest
     * behind pagination, while the feed can be paged deterministically and returns
     * only that label's posts — never a sidebar link, never another novel's
     * chapter.
     */
    public static Result fetchLabel(String labelName, Fetcher fetcher)
            throws IOException, JSONException {
        return fetch(feedUrl(labelName), fetcher);
    }

    public static Result fetchLabel(String labelName) throws IOException, JSONException {
        return fetchLabel(labelName, Http::get);
    }

    /** Pages one feed URL until it runs out, counting what Blogger actually sent. */
    static Result fetch(String feedUrl, Fetcher fetcher) throws IOException, JSONException {
        List<FeedEntry> entries = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();

        int expectedTotal = -1;
        int startIndex = 1;
        int pagesRead = 0;
        int blankStreak = 0;

        while (pagesRead < MAX_REQUESTS) {
            String url = feedUrl + (feedUrl.contains("?") ? "&" : "?")
                    + "max-results=" + PAGE_SIZE + "&start-index=" + startIndex;
            String body;
            try {
                body = fetcher.get(url);
            } catch (IOException error) {
                throw new FeedException(
                        "Halaman feed ke-" + (pagesRead + 1) + " gagal diambil (start-index "
                                + startIndex + ").", pagesRead + 1, startIndex, error);
            }

            JSONObject feed;
            try {
                JSONObject root = new JSONObject(body);
                feed = root.optJSONObject("feed");
            } catch (JSONException error) {
                throw new FeedException(
                        "Respons feed halaman ke-" + (pagesRead + 1) + " tidak dapat dibaca.",
                        pagesRead + 1, startIndex, error);
            }

            if (feed == null) {
                // Blogger answers with an error document when start-index is past the end.
                break;
            }

            if (expectedTotal < 0) {
                expectedTotal = readTotal(feed);
            }

            JSONArray items = feed.optJSONArray("entry");
            int received = items == null ? 0 : items.length();
            if (received == 0) break;

            int added = 0;
            for (int i = 0; i < received; i++) {
                FeedEntry entry = parseEntry(items.optJSONObject(i));
                if (entry == null) continue;
                if (seenUrls.add(entry.url)) {
                    entries.add(entry);
                    added++;
                }
            }

            pagesRead++;
            // Advance by what Blogger actually returned, not by the requested size.
            startIndex += received;

            if (added == 0) {
                blankStreak++;
                if (blankStreak >= 2) break;
            } else {
                blankStreak = 0;
            }

            if (expectedTotal > 0 && entries.size() >= expectedTotal) break;
        }

        String warning = null;
        if (entries.isEmpty()) {
            warning = "Feed label tidak mengembalikan satu pun postingan.";
        } else if (expectedTotal > 0 && entries.size() < expectedTotal) {
            warning = "Hanya " + entries.size() + " dari " + expectedTotal
                    + " postingan yang berhasil diambil. Tekan muat ulang untuk mencoba lagi.";
        } else if (pagesRead >= MAX_REQUESTS) {
            warning = "Batas " + MAX_REQUESTS + " halaman feed tercapai ("
                    + entries.size() + " postingan).";
        }

        return new Result(entries, expectedTotal, pagesRead, warning);
    }

    /** {@code openSearch$totalResults} holds the number of posts for this label. */
    static int readTotal(JSONObject feed) {
        JSONObject total = feed.optJSONObject("openSearch$totalResults");
        if (total == null) return -1;
        try {
            return Integer.parseInt(total.optString("$t", "-1"));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    /** Parses one feed entry, returning null when it has no usable link. */
    static FeedEntry parseEntry(JSONObject item) {
        if (item == null) return null;

        JSONObject titleObject = item.optJSONObject("title");
        String title = titleObject == null ? "" : titleObject.optString("$t", "");

        JSONObject publishedObject = item.optJSONObject("published");
        String published = publishedObject == null ? "" : publishedObject.optString("$t", "");
        if (published.length() >= 10) published = published.substring(0, 10);

        String link = null;
        JSONArray links = item.optJSONArray("link");
        if (links != null) {
            for (int i = 0; i < links.length(); i++) {
                JSONObject candidate = links.optJSONObject(i);
                if (candidate == null) continue;
                if ("alternate".equals(candidate.optString("rel"))) {
                    link = candidate.optString("href", null);
                    break;
                }
            }
        }
        if (link == null || link.trim().isEmpty()) return null;
        return new FeedEntry(title, link.trim(), published);
    }
}
