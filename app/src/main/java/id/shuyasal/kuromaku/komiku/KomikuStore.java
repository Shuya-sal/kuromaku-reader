package id.shuyasal.kuromaku.komiku;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Riwayat, progres, dan favorit Manga Server 3.
 *
 * <p>Disimpan terpisah dari Manga Server 2: setiap sumber punya riwayatnya
 * sendiri, dan hanya status kanonis yang nanti dibagi lewat lapisan sinkron.
 * Penyimpanan memakai kunci berupa alamat chapter/manga, bukan nomor urut,
 * supaya data tidak tertukar ketika katalog berubah urutan.
 */
public final class KomikuStore {

    public static final String FILE_NAME = "kuromaku_komiku";

    private static final String KEY_HISTORY = "history";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_PROGRESS = "progress";

    /** Satu baris riwayat baca. */
    public static final class Entry {
        public final String mangaId;
        public final String title;
        public final String coverUrl;
        public final String chapterUrl;
        public final String chapterLabel;
        public final double number;
        public final double fraction;
        public final long timestamp;

        public Entry(String mangaId, String title, String coverUrl, String chapterUrl,
                     String chapterLabel, double number, double fraction, long timestamp) {
            this.mangaId = mangaId == null ? "" : mangaId;
            this.title = title == null ? "" : title;
            this.coverUrl = coverUrl == null ? "" : coverUrl;
            this.chapterUrl = chapterUrl == null ? "" : chapterUrl;
            this.chapterLabel = chapterLabel == null ? "" : chapterLabel;
            this.number = number;
            this.fraction = fraction;
            this.timestamp = timestamp;
        }

        JSONObject toJson() throws Exception {
            JSONObject out = new JSONObject();
            out.put("manga_id", mangaId);
            out.put("title", title);
            out.put("cover", coverUrl);
            out.put("chapter_url", chapterUrl);
            out.put("chapter_label", chapterLabel);
            out.put("number", number);
            out.put("fraction", fraction);
            out.put("at", timestamp);
            return out;
        }

        static Entry fromJson(JSONObject json) {
            return new Entry(json.optString("manga_id"), json.optString("title"),
                    json.optString("cover"), json.optString("chapter_url"),
                    json.optString("chapter_label"), json.optDouble("number", -1),
                    json.optDouble("fraction", 0), json.optLong("at", 0));
        }
    }

    private final SharedPreferences prefs;

    public KomikuStore(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    public SharedPreferences prefs() {
        return prefs;
    }

    // ------------------------------------------------------------- riwayat

    public List<Entry> history() {
        List<Entry> out = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY_HISTORY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                out.add(Entry.fromJson(array.getJSONObject(i)));
            }
        } catch (Exception error) {
            // Riwayat rusak tidak boleh membuat layar gagal dibuka.
        }
        return out;
    }

    /** Riwayat terbaru untuk satu judul, atau {@code null}. */
    public Entry lastFor(String mangaId) {
        for (Entry entry : history()) {
            if (entry.mangaId.equals(mangaId)) return entry;
        }
        return null;
    }

    /** Catat bacaan; satu baris per judul, yang terbaru di atas. */
    public void recordChapter(String mangaId, String title, String coverUrl,
                              String chapterUrl, String chapterLabel, double number,
                              double fraction) {
        List<Entry> list = history();
        list.removeIf(entry -> entry.mangaId.equals(mangaId == null ? "" : mangaId));
        list.add(0, new Entry(mangaId, title, coverUrl, chapterUrl, chapterLabel, number,
                fraction, System.currentTimeMillis()));
        while (list.size() > 100) list.remove(list.size() - 1);
        JSONArray array = new JSONArray();
        for (Entry entry : list) {
            try {
                array.put(entry.toJson());
            } catch (Exception ignored) {
                // Baris yang tidak bisa disimpan dilewati, bukan menggagalkan semua.
            }
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply();
    }

    public void clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply();
    }

    // ------------------------------------------------------------- progres

    /** Progres baca satu chapter, 0..1. */
    public double progress(String chapterUrl) {
        return readMap(KEY_PROGRESS).optDouble(chapterUrl, 0);
    }

    public void setProgress(String chapterUrl, double fraction) {
        if (chapterUrl == null || chapterUrl.isEmpty()) return;
        double value = Math.max(0, Math.min(1, fraction));
        JSONObject map = readMap(KEY_PROGRESS);
        try {
            map.put(chapterUrl, value);
        } catch (Exception error) {
            return;
        }
        prefs.edit().putString(KEY_PROGRESS, map.toString()).apply();
    }

    // ------------------------------------------------------------ favorit

    public boolean isFavorite(String mangaId) {
        JSONObject map = readMap(KEY_FAVORITES);
        String id = mangaId == null ? "" : mangaId;
        // Nilai disimpan sebagai objek (judul, cover, alamat), jadi keberadaan
        // kunci — bukan nilai boolean — yang menandakan favorit.
        return map.has(id) && map.optJSONObject(id) != null;
    }

    /** Hasil: keadaan favorit setelah diubah. */
    public boolean toggleFavorite(String mangaId, String title, String coverUrl, String url) {
        String id = mangaId == null ? "" : mangaId;
        JSONObject map = readMap(KEY_FAVORITES);
        boolean now;
        try {
            now = !(map.has(id) && map.optJSONObject(id) != null);
            if (now) {
                JSONObject item = new JSONObject();
                item.put("title", title == null ? "" : title);
                item.put("cover", coverUrl == null ? "" : coverUrl);
                item.put("url", url == null ? "" : url);
                item.put("at", System.currentTimeMillis());
                map.put(id, item);
            } else {
                map.remove(id);
            }
        } catch (Exception error) {
            return isFavorite(id);
        }
        prefs.edit().putString(KEY_FAVORITES, map.toString()).apply();
        return now;
    }

    /** Daftar favorit urut terbaru. */
    public List<KomikuManga> favorites() {
        List<KomikuManga> out = new ArrayList<>();
        JSONObject map = readMap(KEY_FAVORITES);
        List<String> ids = new ArrayList<>();
        for (java.util.Iterator<String> it = map.keys(); it.hasNext(); ) ids.add(it.next());
        ids.sort((a, b) -> Long.compare(map.optJSONObject(b) == null ? 0
                        : map.optJSONObject(b).optLong("at"),
                map.optJSONObject(a) == null ? 0 : map.optJSONObject(a).optLong("at")));
        for (String id : ids) {
            JSONObject item = map.optJSONObject(id);
            if (item == null) continue;
            out.add(new KomikuManga(id, item.optString("title"), item.optString("url"),
                    item.optString("cover"), "", "", ""));
        }
        return out;
    }

    private JSONObject readMap(String key) {
        try {
            return new JSONObject(prefs.getString(key, "{}"));
        } catch (Exception error) {
            return new JSONObject();
        }
    }
}