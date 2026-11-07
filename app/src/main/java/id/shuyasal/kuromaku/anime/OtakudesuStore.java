package id.shuyasal.kuromaku.anime;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Penyimpanan lokal Anime Otakudesu.
 *
 * <p>Berkas preferensi terpisah ({@value #FILE_NAME}) sehingga tidak pernah
 * menyentuh data Novel, Manga Voratoon, maupun Manga Shinigami. Favorit,
 * riwayat, dan episode yang sudah dibuka disimpan sebagai data pengguna; tidak
 * ada yang dihapus saat aplikasi dibuka kembali.
 *
 * <p>Progres menonton yang disimpan adalah progres <em>episode</em> (sudah
 * sampai episode berapa dari total yang diketahui sumber). Aplikasi tidak
 * menyimpan posisi detik, karena pemutaran terjadi di pemutar resmi sumber,
 * bukan di player internal.
 */
public final class OtakudesuStore {

    public static final String FILE_NAME = "kuromaku_otakudesu";

    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_FAVORITES_V2 = "favorites_v2";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_HISTORY_V2 = "history_v2";
    private static final String KEY_LAST_EPISODE = "last_episode:";
    private static final String KEY_WATCHED = "watched:";
    private static final int HISTORY_LIMIT = 40;
    private static final int WATCHED_LIMIT = 500;

    /** Satu anime favorit, lengkap dengan metadata agar kartu bisa digambar. */
    public static final class Favorite {
        public final String animeId;
        public final String title;
        public final String posterUrl;

        public Favorite(String animeId, String title, String posterUrl) {
            this.animeId = animeId == null ? "" : animeId;
            this.title = title == null ? "" : title;
            this.posterUrl = posterUrl == null ? "" : posterUrl;
        }
    }

    /** Satu baris riwayat menonton. */
    public static final class HistoryEntry {
        public final String animeId;
        public final String title;
        public final String posterUrl;
        public final String episodeLabel;
        public final String episodeUrl;
        public final long timestamp;
        public final int number;
        public final int total;

        public HistoryEntry(String animeId, String title, String posterUrl,
                            String episodeLabel, String episodeUrl, long timestamp) {
            this(animeId, title, posterUrl, episodeLabel, episodeUrl, timestamp, 0, 0);
        }

        public HistoryEntry(String animeId, String title, String posterUrl,
                            String episodeLabel, String episodeUrl, long timestamp,
                            int number, int total) {
            this.animeId = animeId == null ? "" : animeId;
            this.title = title == null ? "" : title;
            this.posterUrl = posterUrl == null ? "" : posterUrl;
            this.episodeLabel = episodeLabel == null ? "" : episodeLabel;
            this.episodeUrl = episodeUrl == null ? "" : episodeUrl;
            this.timestamp = timestamp;
            this.number = Math.max(0, number);
            this.total = Math.max(0, total);
        }

        /** 0..100, atau -1 bila total episode belum diketahui. */
        public int progressPercent() {
            return percent(number, total);
        }
    }

    private final SharedPreferences prefs;

    public OtakudesuStore(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    /** Preferensi mentah, dipakai lapisan backup/restore. */
    public SharedPreferences preferences() {
        return prefs;
    }

    static int percent(int number, int total) {
        if (total <= 0 || number < 0) return -1;
        int value = Math.round(number * 100f / total);
        return Math.max(0, Math.min(100, value));
    }

    // -------------------------------------------------------------- favorit

    /** ID favorit saja; dipakai untuk pengecekan cepat. */
    public Set<String> favorites() {
        Set<String> ids = new LinkedHashSet<>();
        for (Favorite favorite : favoriteEntries()) ids.add(favorite.animeId);
        return ids;
    }

    /** Favorit lengkap dengan judul dan poster. */
    public List<Favorite> favoriteEntries() {
        List<Favorite> entries = new ArrayList<>();
        String raw = prefs.getString(KEY_FAVORITES_V2, "");
        if (raw != null && !raw.isEmpty()) {
            try {
                JSONArray array = new JSONArray(raw);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item == null) continue;
                    String id = item.optString("id", "");
                    if (id.isEmpty()) continue;
                    entries.add(new Favorite(id, item.optString("title", ""),
                            item.optString("poster", "")));
                }
                return entries;
            } catch (Exception ignored) {
                // Data rusak: jatuh ke format lama di bawah.
            }
        }
        // Migrasi format lama: hanya ID, tanpa metadata.
        Set<String> legacy = prefs.getStringSet(KEY_FAVORITES, null);
        if (legacy != null) {
            for (String id : legacy) {
                if (id != null && !id.isEmpty()) entries.add(new Favorite(id, "", ""));
            }
        }
        return entries;
    }

    public boolean isFavorite(String animeId) {
        if (animeId == null || animeId.isEmpty()) return false;
        for (Favorite favorite : favoriteEntries()) {
            if (animeId.equals(favorite.animeId)) return true;
        }
        return false;
    }

    public boolean toggleFavorite(String animeId) {
        return toggleFavorite(animeId, "", "");
    }

    public boolean toggleFavorite(String animeId, String title, String posterUrl) {
        if (animeId == null || animeId.isEmpty()) return false;
        List<Favorite> current = favoriteEntries();
        boolean removed = false;
        for (int i = 0; i < current.size(); i++) {
            if (animeId.equals(current.get(i).animeId)) {
                current.remove(i);
                removed = true;
                break;
            }
        }
        if (!removed) current.add(0, new Favorite(animeId, title, posterUrl));
        writeFavorites(current);
        return !removed;
    }

    private void writeFavorites(List<Favorite> entries) {
        JSONArray array = new JSONArray();
        for (Favorite favorite : entries) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", favorite.animeId);
                item.put("title", favorite.title);
                item.put("poster", favorite.posterUrl);
            } catch (Exception ignored) {
                continue;
            }
            array.put(item);
        }
        prefs.edit().putString(KEY_FAVORITES_V2, array.toString()).apply();
    }

    // -------------------------------------------------------------- riwayat

    public List<HistoryEntry> history() {
        List<HistoryEntry> entries = new ArrayList<>();
        String raw = prefs.getString(KEY_HISTORY_V2, "");
        if (raw != null && !raw.isEmpty()) {
            try {
                JSONArray array = new JSONArray(raw);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item == null) continue;
                    String id = item.optString("anime_id", "");
                    if (id.isEmpty()) continue;
                    entries.add(new HistoryEntry(id,
                            item.optString("title", ""),
                            item.optString("poster", ""),
                            item.optString("episode", ""),
                            item.optString("episode_url", ""),
                            item.optLong("timestamp", 0L),
                            item.optInt("number", 0),
                            item.optInt("total", 0)));
                }
                return entries;
            } catch (Exception ignored) {
                // Data rusak: coba format lama.
            }
        }
        String legacy = prefs.getString(KEY_HISTORY, "");
        if (legacy == null || legacy.isEmpty()) return entries;
        try {
            JSONArray array = new JSONArray(legacy);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                entries.add(new HistoryEntry(
                        item.optString("anime_id", ""),
                        item.optString("title", ""),
                        item.optString("poster", ""),
                        item.optString("episode", ""),
                        item.optString("episode_url", ""),
                        item.optLong("timestamp", 0L)));
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        return entries;
    }

    /** Catat episode yang dibuka; satu baris per anime (paling baru menang). */
    public void recordWatched(String animeId, String title, String posterUrl,
                              String episodeLabel, String episodeUrl) {
        recordWatched(animeId, title, posterUrl, episodeLabel, episodeUrl, 0, 0);
    }

    public void recordWatched(String animeId, String title, String posterUrl,
                              String episodeLabel, String episodeUrl, int number, int total) {
        if (animeId == null || animeId.isEmpty()) return;
        List<HistoryEntry> kept = new ArrayList<>();
        int previousNumber = 0;
        int previousTotal = total;
        for (HistoryEntry entry : history()) {
            if (animeId.equals(entry.animeId)) {
                previousNumber = Math.max(previousNumber, entry.number);
                if (previousTotal <= 0) previousTotal = entry.total;
                continue;
            }
            kept.add(entry);
        }
        // Progres tidak boleh mundur hanya karena episode lama dibuka lagi.
        int progressNumber = Math.max(previousNumber, Math.max(0, number));
        kept.add(0, new HistoryEntry(animeId, title, posterUrl, episodeLabel, episodeUrl,
                System.currentTimeMillis(), progressNumber, previousTotal));
        while (kept.size() > HISTORY_LIMIT) kept.remove(kept.size() - 1);
        writeHistory(kept);
        if (episodeLabel != null && !episodeLabel.isEmpty()) {
            prefs.edit().putString(KEY_LAST_EPISODE + animeId, episodeLabel).apply();
        }
        if (episodeUrl != null && !episodeUrl.isEmpty()) {
            markWatched(animeId, episodeUrl);
        }
    }

    public String lastEpisode(String animeId) {
        if (animeId == null) return "";
        return prefs.getString(KEY_LAST_EPISODE + animeId, "");
    }

    /** Baris riwayat untuk satu anime, atau null bila belum pernah dibuka. */
    public HistoryEntry historyFor(String animeId) {
        if (animeId == null) return null;
        for (HistoryEntry entry : history()) {
            if (animeId.equals(entry.animeId)) return entry;
        }
        return null;
    }

    private void writeHistory(List<HistoryEntry> entries) {
        JSONArray array = new JSONArray();
        for (HistoryEntry entry : entries) {
            JSONObject item = new JSONObject();
            try {
                item.put("anime_id", entry.animeId);
                item.put("title", entry.title);
                item.put("poster", entry.posterUrl);
                item.put("episode", entry.episodeLabel);
                item.put("episode_url", entry.episodeUrl);
                item.put("timestamp", entry.timestamp);
                item.put("number", entry.number);
                item.put("total", entry.total);
            } catch (Exception ignored) {
                continue;
            }
            array.put(item);
        }
        prefs.edit().putString(KEY_HISTORY_V2, array.toString()).apply();
    }

    // ------------------------------------------------ episode yang sudah dibuka

    public Set<String> watched(String animeId) {
        Set<String> entries = new LinkedHashSet<>();
        if (animeId == null || animeId.isEmpty()) return entries;
        String raw = prefs.getString(KEY_WATCHED + animeId, "");
        if (raw == null || raw.isEmpty()) return entries;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                String id = array.optString(i, "");
                if (!id.isEmpty()) entries.add(id);
            }
        } catch (Exception ignored) {
            return new LinkedHashSet<>();
        }
        return entries;
    }

    public boolean isWatched(String animeId, String episodeId) {
        return episodeId != null && watched(animeId).contains(episodeId);
    }

    public void markWatched(String animeId, String episodeId) {
        if (animeId == null || animeId.isEmpty()) return;
        if (episodeId == null || episodeId.isEmpty()) return;
        Set<String> current = watched(animeId);
        if (!current.add(episodeId)) return;
        while (current.size() > WATCHED_LIMIT) {
            String first = current.iterator().next();
            current.remove(first);
        }
        JSONArray array = new JSONArray();
        for (String id : current) array.put(id);
        prefs.edit().putString(KEY_WATCHED + animeId, array.toString()).apply();
    }
}