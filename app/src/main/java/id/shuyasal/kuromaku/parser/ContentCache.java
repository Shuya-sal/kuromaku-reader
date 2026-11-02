package id.shuyasal.kuromaku.parser;

import java.util.LinkedHashMap;
import java.util.Map;

/** Keeps a few parsed chapters in memory to avoid refetching on theme changes. */
public final class ContentCache {

    private static final int MAX = 6;
    private static final Map<String, ChapterContent> CACHE =
            new LinkedHashMap<String, ChapterContent>(MAX, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ChapterContent> eldest) {
                    return size() > MAX;
                }
            };

    private ContentCache() {
    }

    public static synchronized void put(String url, ChapterContent content) {
        if (url != null && content != null) CACHE.put(url, content);
    }

    public static synchronized ChapterContent get(String url) {
        return url == null ? null : CACHE.get(url);
    }
}
