package id.shuyasal.kuromaku.net;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import id.shuyasal.kuromaku.data.FeedEntry;

/**
 * Reads the public Blogger JSON feed for the Kaori Translation label.
 * Only the summary feed (title + link + date) is requested, so nothing is
 * scraped that the library does not need, and no HTML is executed.
 */
public final class FeedClient {

    public static final String SOURCE_HOST = "kaoritranslation.blogspot.com";
    public static final String LABEL = "Monogatari no Kuromaku";
    public static final String LABEL_URL =
            "https://kaoritranslation.blogspot.com/search/label/Monogatari%20no%20Kuromaku";

    private static final String FEED =
            "https://kaoritranslation.blogspot.com/feeds/posts/summary/-/Monogatari%20no%20Kuromaku";

    private static final int PAGE_SIZE = 25;
    private static final int MAX_PAGES = 12;

    private FeedClient() {
    }

    public static List<FeedEntry> fetchEntries() throws IOException, JSONException {
        List<FeedEntry> entries = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();

        for (int page = 0; page < MAX_PAGES; page++) {
            int startIndex = page * PAGE_SIZE + 1;
            String url = FEED + "?alt=json&max-results=" + PAGE_SIZE + "&start-index=" + startIndex;
            JSONObject root = new JSONObject(Http.get(url));
            JSONObject feed = root.optJSONObject("feed");
            if (feed == null) break;

            JSONArray items = feed.optJSONArray("entry");
            if (items == null || items.length() == 0) break;

            int added = 0;
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                String title = item.optJSONObject("title") == null
                        ? "" : item.optJSONObject("title").optString("$t", "");
                String published = item.optJSONObject("published") == null
                        ? "" : item.optJSONObject("published").optString("$t", "");
                if (published.length() >= 10) published = published.substring(0, 10);

                String link = null;
                JSONArray links = item.optJSONArray("link");
                if (links != null) {
                    for (int l = 0; l < links.length(); l++) {
                        JSONObject candidate = links.optJSONObject(l);
                        if (candidate == null) continue;
                        if ("alternate".equals(candidate.optString("rel"))) {
                            link = candidate.optString("href", null);
                            break;
                        }
                    }
                }
                if (link == null || link.isEmpty()) continue;
                if (seenUrls.add(link)) {
                    entries.add(new FeedEntry(title, link, published));
                    added++;
                }
            }

            if (items.length() < PAGE_SIZE || added == 0) break;
        }

        return entries;
    }
}
