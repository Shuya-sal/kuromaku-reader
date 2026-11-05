package id.shuyasal.kuromaku.shinigami;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ShinigamiStore {
    public static final String FILE_NAME = "kuromaku_shinigami";

    private static final String KEY_MANGA_METADATA = "manga_metadata:";
    private static final String KEY_CHAPTER_METADATA = "chapter_metadata:";
    private static final String KEY_CHAPTER_URLS = "chapter_urls:";
    private static final String KEY_EXPECTED_PAGES = "expected_pages:";
    private static final String KEY_PROGRESS = "progress:";
    private static final String KEY_PAGE = "page:";
    private static final String KEY_LAST_CHAPTER = "last_chapter:";
    private static final String KEY_LAST_MANGA = "last_manga:";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_MANGA_TITLE = "manga_title:";
    private static final String KEY_MANGA_COVER = "manga_cover:";
    private static final String KEY_CHAPTER_OWNER = "chapter_owner:";

    private static final int HISTORY_LIMIT = 60;

    public static final class HistoryEntry {
        public final String mangaId;
        public final String title;
        public final String coverUrl;
        public final String chapterId;
        public final double chapterNumber;
        public final long timestamp;

        public HistoryEntry(String mangaId, String title, String coverUrl, String chapterId,
                            double chapterNumber, long timestamp) {
            this.mangaId = mangaId == null ? "" : mangaId;
            this.title = title == null ? "" : title;
            this.coverUrl = coverUrl == null ? "" : coverUrl;
            this.chapterId = chapterId == null ? "" : chapterId;
            this.chapterNumber = chapterNumber;
            this.timestamp = timestamp;
        }
    }

    private final SharedPreferences prefs;

    public ShinigamiStore(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    public SharedPreferences prefs() {
        return prefs;
    }

    // Additive metadata cache: absent/null fields cannot erase known source facts.
    // Existing progress, history, favorites and backup keys are deliberately untouched.
    JSONObject mergeMangaMetadata(String id, JSONObject incoming) {
        return mergeMetadata(KEY_MANGA_METADATA + id, incoming);
    }

    JSONObject mergeChapterMetadata(String id, JSONObject incoming) {
        return mergeMetadata(KEY_CHAPTER_METADATA + id, incoming);
    }

    private JSONObject mergeMetadata(String key, JSONObject incoming) {
        JSONObject merged;
        try { merged = new JSONObject(prefs.getString(key, "{}")); }
        catch (Exception ignored) { merged = new JSONObject(); }
        java.util.Iterator<String> keys = incoming.keys();
        try {
            while (keys.hasNext()) {
                String field = keys.next();
                Object value = incoming.opt(field);
                if (value == null || value == JSONObject.NULL) continue;
                if (value instanceof String && ((String) value).trim().isEmpty()) continue;
                if ((field.equals("release_date") || field.equals("latest_chapter_time")
                        || field.equals("updated_at")) && ShinigamiDates.parse(String.valueOf(value)) <= 0L) continue;
                merged.put(field, value);
            }
            // Persist normalized UTC values alongside raw fields for additive migration.
            for (String field : new String[]{"release_date", "latest_chapter_time", "updated_at"}) {
                long millis = ShinigamiDates.parse(merged.optString(field, ""));
                if (millis > 0L) merged.put(field + "_millis", millis);
            }
        } catch (Exception ignored) { return incoming; }
        prefs.edit().putString(key, merged.toString()).apply();
        return merged;
    }

    public void setChapterPages(String chapterId, List<String> urls) {
        if (chapterId == null || !chapterId.matches("[A-Za-z0-9_-]{1,128}")
                || urls == null || urls.isEmpty()) return;
        JSONArray array = new JSONArray();
        for (String url : urls) {
            if (!ShinigamiConfig.isValidResource(url)) return;
            array.put(url);
        }
        prefs.edit().putString(KEY_CHAPTER_URLS + chapterId, array.toString())
                .putInt(KEY_EXPECTED_PAGES + chapterId, urls.size()).apply();
    }

    public List<String> chapterUrls(String chapterId) {
        List<String> urls = new ArrayList<>();
        if (chapterId == null) return urls;
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY_CHAPTER_URLS + chapterId, "[]"));
            for (int i = 0; i < array.length(); i++) {
                if (!(array.opt(i) instanceof String)) return new ArrayList<>();
                String url = array.getString(i);
                if (!ShinigamiConfig.isValidResource(url)) return new ArrayList<>();
                urls.add(url);
            }
        } catch (Exception ignored) { return new ArrayList<>(); }
        return urls;
    }

    public int expectedPages(String chapterId) {
        if (chapterId == null) return 0;
        return Math.max(0, prefs.getInt(KEY_EXPECTED_PAGES + chapterId, 0));
    }

    public int progress(String chapterId) {
        if (chapterId == null) return 0;
        return prefs.getInt(KEY_PROGRESS + chapterId, 0);
    }

    public void setProgress(String chapterId, int percent) {
        if (chapterId == null || chapterId.isEmpty()) return;
        prefs.edit().putInt(KEY_PROGRESS + chapterId, Math.max(0, Math.min(100, percent))).apply();
    }

    public int page(String chapterId) {
        if (chapterId == null) return 0;
        return prefs.getInt(KEY_PAGE + chapterId, 0);
    }

    public void setPage(String chapterId, int page) {
        if (chapterId == null || chapterId.isEmpty()) return;
        prefs.edit().putInt(KEY_PAGE + chapterId, Math.max(0, page)).apply();
    }

    public String lastChapter(String mangaId) {
        if (mangaId == null) return "";
        return prefs.getString(KEY_LAST_CHAPTER + mangaId, "");
    }

    public String lastManga() {
        return prefs.getString(KEY_LAST_MANGA, "");
    }

    public void setLastChapter(String mangaId, String chapterId, double chapterNumber) {
        if (mangaId == null || mangaId.isEmpty()) return;
        prefs.edit()
                .putString(KEY_LAST_CHAPTER + mangaId, chapterId == null ? "" : chapterId)
                .putString(KEY_LAST_MANGA, mangaId)
                .apply();
    }

    public void setMangaMeta(String mangaId, String title, String coverUrl) {
        if (mangaId == null || mangaId.isEmpty()) return;
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_MANGA_TITLE + mangaId, title == null ? "" : title);
        editor.putString(KEY_MANGA_COVER + mangaId, coverUrl == null ? "" : coverUrl);
        editor.apply();
    }

    public String mangaTitle(String mangaId) {
        if (mangaId == null) return "";
        return prefs.getString(KEY_MANGA_TITLE + mangaId, "");
    }

    public String mangaCover(String mangaId) {
        if (mangaId == null) return "";
        return prefs.getString(KEY_MANGA_COVER + mangaId, "");
    }

    /**
     * Catat pemilik chapter (manga) supaya tab Unduhan bisa mengelompokkan
     * berkas yang benar-benar ada di disk. Tidak menyentuh progres membaca.
     */
    public void setChapterOwner(String chapterId, String mangaId, String mangaTitle,
                                String coverUrl, double chapterNumber, String chapterLabel) {
        if (chapterId == null || chapterId.isEmpty()) return;
        JSONObject owner = new JSONObject();
        try {
            owner.put("manga_id", mangaId == null ? "" : mangaId);
            owner.put("title", mangaTitle == null ? "" : mangaTitle);
            owner.put("cover", coverUrl == null ? "" : coverUrl);
            owner.put("number", chapterNumber);
            owner.put("label", chapterLabel == null ? "" : chapterLabel);
        } catch (Exception ignored) {
            return;
        }
        prefs.edit().putString(KEY_CHAPTER_OWNER + chapterId, owner.toString()).apply();
    }

    /** Data pemilik chapter, atau {@code null} bila chapter belum pernah dicatat. */
    public JSONObject chapterOwner(String chapterId) {
        if (chapterId == null || chapterId.isEmpty()) return null;
        String raw = prefs.getString(KEY_CHAPTER_OWNER + chapterId, "");
        if (raw == null || raw.isEmpty()) return null;
        try {
            return new JSONObject(raw);
        } catch (Exception error) {
            return null;
        }
    }

    public boolean isFavorite(String mangaId) {
        if (mangaId == null) return false;
        return favorites().contains(mangaId);
    }

    public boolean toggleFavorite(String mangaId) {
        if (mangaId == null || mangaId.isEmpty()) return false;
        Set<String> current = favorites();
        boolean nowFavorite;
        if (current.contains(mangaId)) {
            current.remove(mangaId);
            nowFavorite = false;
        } else {
            current.add(mangaId);
            nowFavorite = true;
        }
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply();
        return nowFavorite;
    }

    public Set<String> favorites() {
        Set<String> stored = prefs.getStringSet(KEY_FAVORITES, null);
        return stored == null
                ? new LinkedHashSet<String>()
                : new LinkedHashSet<String>(stored);
    }

    public void removeFavorite(String mangaId) {
        if (mangaId == null) return;
        Set<String> current = favorites();
        if (!current.remove(mangaId)) return;
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply();
    }

    public List<HistoryEntry> history() {
        List<HistoryEntry> entries = new ArrayList<>();
        String raw = prefs.getString(KEY_HISTORY, "");
        if (raw == null || raw.isEmpty()) return entries;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                entries.add(new HistoryEntry(
                        item.optString("manga_id", ""),
                        item.optString("title", ""),
                        item.optString("cover", ""),
                        item.optString("chapter_id", ""),
                        item.optDouble("chapter_number", 0d),
                        item.optLong("timestamp", 0L)));
            }
        } catch (Exception ignored) {
            return new ArrayList<HistoryEntry>();
        }
        return entries;
    }

    public void recordRead(String mangaId, String title, String coverUrl, String chapterId,
                           double chapterNumber) {
        if (mangaId == null || mangaId.isEmpty()) return;
        List<HistoryEntry> entries = history();
        List<HistoryEntry> kept = new ArrayList<>();
        for (HistoryEntry entry : entries) {
            boolean sameChapter = chapterId != null && chapterId.equals(entry.chapterId);
            boolean sameManga = mangaId.equals(entry.mangaId);
            if (sameChapter || sameManga) continue;
            kept.add(entry);
        }
        kept.add(0, new HistoryEntry(mangaId, title, coverUrl, chapterId, chapterNumber,
                System.currentTimeMillis()));
        while (kept.size() > HISTORY_LIMIT) {
            kept.remove(kept.size() - 1);
        }
        writeHistory(kept);
        setMangaMeta(mangaId, title, coverUrl);
    }

    private void writeHistory(List<HistoryEntry> entries) {
        JSONArray array = new JSONArray();
        for (HistoryEntry entry : entries) {
            JSONObject item = new JSONObject();
            try {
                item.put("manga_id", entry.mangaId);
                item.put("title", entry.title);
                item.put("cover", entry.coverUrl);
                item.put("chapter_id", entry.chapterId);
                item.put("chapter_number", entry.chapterNumber);
                item.put("timestamp", entry.timestamp);
            } catch (Exception ignored) {
                continue;
            }
            array.put(item);
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply();
    }
}
