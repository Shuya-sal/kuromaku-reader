package id.shuyasal.kuromaku.sync;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Peta judul kanonis dan progres baca bersama antar sumber.
 *
 * <p>Yang disimpan di sini <b>bukan</b> salinan database sumber. Ini satu
 * lapisan kecil di atasnya: identitas karya bersama, pasangan judul per
 * sumber, dan status chapter kanonis. Riwayat mentah tetap milik masing-masing
 * sumber sehingga melepas tautan tidak menghapus apa pun.
 *
 * <p>Penyimpanan memakai JSON dalam satu berkas preferensi. Setiap penulisan
 * mengganti seluruh isi berkas, jadi operasi bersifat idempotent: mencatat
 * kejadian yang sama dua kali tidak menambah data.
 */
public final class SyncStore {

    public static final String FILE_NAME = "kuromaku_sync";

    /** Sumber yang ikut sinkron. */
    public static final String SOURCE_SHINIGAMI = "shinigami";
    public static final String SOURCE_KOMIKU = "komiku";

    private static final String KEY_SERIES = "series";
    private static final String KEY_PROGRESS = "progress";

    /** Satu karya bersama dan pasangan judulnya di tiap sumber. */
    public static final class Series {
        public final String id;
        public final String preferredTitle;
        public final long createdAt;
        /** Pasangan per sumber: sourceId → {manga_id, url, title, confirmed}. */
        public final JSONObject links;

        Series(String id, String preferredTitle, long createdAt, JSONObject links) {
            this.id = id;
            this.preferredTitle = preferredTitle;
            this.createdAt = createdAt;
            this.links = links;
        }

        /** Alamat judul pada sumber tertentu, atau kosong. */
        public String mangaIdFor(String sourceId) {
            JSONObject link = links.optJSONObject(sourceId);
            return link == null ? "" : link.optString("manga_id", "");
        }

        public String titleFor(String sourceId) {
            JSONObject link = links.optJSONObject(sourceId);
            return link == null ? "" : link.optString("title", "");
        }

        public boolean linked(String sourceId) {
            return !mangaIdFor(sourceId).isEmpty();
        }

        /** Sumber lain yang sudah dipasangkan (selain {@code sourceId}). */
        public List<String> otherSources(String sourceId) {
            List<String> out = new ArrayList<>();
            for (java.util.Iterator<String> it = links.keys(); it.hasNext(); ) {
                String key = it.next();
                if (!key.equals(sourceId) && linked(key)) out.add(key);
            }
            return out;
        }

        JSONObject toJson() throws Exception {
            JSONObject out = new JSONObject();
            out.put("id", id);
            out.put("title", preferredTitle);
            out.put("created_at", createdAt);
            out.put("links", links);
            return out;
        }

        static Series fromJson(JSONObject json) {
            return new Series(json.optString("id"), json.optString("title"),
                    json.optLong("created_at", 0), json.optJSONObject("links") == null
                    ? new JSONObject() : json.optJSONObject("links"));
        }
    }

    /** Status baca satu chapter kanonis. */
    public static final class Progress {
        public final String chapterKey;
        public final boolean completed;
        public final String lastSource;
        public final long lastOpenedAt;
        public final long updatedAt;

        Progress(String chapterKey, boolean completed, String lastSource,
                 long lastOpenedAt, long updatedAt) {
            this.chapterKey = chapterKey;
            this.completed = completed;
            this.lastSource = lastSource;
            this.lastOpenedAt = lastOpenedAt;
            this.updatedAt = updatedAt;
        }
    }

    private final SharedPreferences prefs;

    public SyncStore(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    public SharedPreferences prefs() {
        return prefs;
    }

    // -------------------------------------------------------------- series

    public List<Series> series() {
        List<Series> out = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY_SERIES, "[]"));
            for (int i = 0; i < array.length(); i++) {
                out.add(Series.fromJson(array.getJSONObject(i)));
            }
        } catch (Exception error) {
            // Data rusak tidak boleh membuat layar gagal dibuka.
        }
        return out;
    }

    /** Karya yang sudah memasangkan judul pada sumber ini, atau {@code null}. */
    public Series forSource(String sourceId, String mangaId) {
        if (sourceId == null || mangaId == null || mangaId.isEmpty()) return null;
        for (Series item : series()) {
            if (mangaId.equals(item.mangaIdFor(sourceId))) return item;
        }
        return null;
    }

    /**
     * Pasangkan dua judul.
     *
     * <p>Bila salah satu judul sudah punya karya kanonis, karya itu dipakai —
     * bukan membuat karya baru. Ini mencegah satu judul punya dua identitas
     * setelah pengguna menautkan berkali-kali.
     */
    public Series link(String sourceA, String mangaIdA, String titleA, String urlA,
                       String sourceB, String mangaIdB, String titleB, String urlB,
                       String method) {
        if (mangaIdA == null || mangaIdA.isEmpty()) return null;
        if (mangaIdB == null || mangaIdB.isEmpty()) return null;

        List<Series> all = series();
        Series target = null;
        for (Series item : all) {
            if (mangaIdA.equals(item.mangaIdFor(sourceA))
                    || mangaIdB.equals(item.mangaIdFor(sourceB))) {
                target = item;
                break;
            }
        }

        JSONObject links = target == null ? new JSONObject() : target.links;
        String id = target == null ? newId() : target.id;
        String preferred = target == null || target.preferredTitle.isEmpty()
                ? (titleA == null || titleA.isEmpty() ? titleB : titleA) : target.preferredTitle;
        long created = target == null ? System.currentTimeMillis() : target.createdAt;

        putLink(links, sourceA, mangaIdA, titleA, urlA, method);
        putLink(links, sourceB, mangaIdB, titleB, urlB, method);

        Series updated = new Series(id, preferred, created, links);
        List<Series> next = new ArrayList<>();
        if (target != null) {
            for (Series item : all) if (!item.id.equals(target.id)) next.add(item);
        } else {
            next.addAll(all);
        }
        next.add(0, updated);
        write(next);
        return updated;
    }

    private static void putLink(JSONObject links, String source, String mangaId,
                                String title, String url, String method) {
        try {
            JSONObject link = new JSONObject();
            link.put("manga_id", mangaId == null ? "" : mangaId);
            link.put("title", title == null ? "" : title);
            link.put("url", url == null ? "" : url);
            link.put("method", method == null ? "manual" : method);
            link.put("confirmed_at", System.currentTimeMillis());
            links.put(source, link);
        } catch (Exception ignored) {
            // Satu pasangan gagal disimpan tidak boleh membatalkan yang lain.
        }
    }

    /**
     * Lepas tautan satu sumber dari karyanya.
     *
     * <p>Progres kanonis dan riwayat sumber <b>tidak</b> dihapus: pengguna bisa
     * menautkan ulang tanpa kehilangan apa pun.
     */
    public Series unlink(String sourceId, String mangaId) {
        if (sourceId == null || mangaId == null) return null;
        List<Series> all = series();
        Series found = null;
        for (Series item : all) {
            if (mangaId.equals(item.mangaIdFor(sourceId))) {
                found = item;
                break;
            }
        }
        if (found == null) return null;

        found.links.remove(sourceId);
        List<Series> next = new ArrayList<>();
        for (Series item : all) if (!item.id.equals(found.id)) next.add(item);

        // Karya tanpa pasangan sama sekali tidak berguna lagi, tetapi progres
        // kanonisnya dibiarkan supaya tautan baru bisa memakai riwayat lama.
        if (found.links.length() > 0) next.add(0, found);
        write(next);
        return found.links.length() > 0 ? found : null;
    }

    // ------------------------------------------------------------ progress

    /** Semua progres kanonis satu karya: chapterKey → Progress. */
    public List<Progress> progressFor(String seriesId) {
        List<Progress> out = new ArrayList<>();
        JSONObject map = progressMap();
        JSONObject series = map.optJSONObject(seriesId);
        if (series == null) return out;
        for (java.util.Iterator<String> it = series.keys(); it.hasNext(); ) {
            String key = it.next();
            JSONObject row = series.optJSONObject(key);
            if (row == null) continue;
            out.add(new Progress(key, row.optBoolean("completed", false),
                    row.optString("last_source", ""),
                    row.optLong("last_opened_at", 0), row.optLong("updated_at", 0)));
        }
        return out;
    }

    public Progress progress(String seriesId, String chapterKey) {
        if (seriesId == null || chapterKey == null) return null;
        JSONObject row = progressMap().optJSONObject(seriesId);
        if (row == null) return null;
        JSONObject item = row.optJSONObject(chapterKey);
        if (item == null) return null;
        return new Progress(chapterKey, item.optBoolean("completed", false),
                item.optString("last_source", ""), item.optLong("last_opened_at", 0),
                item.optLong("updated_at", 0));
    }

    /**
     * Catat chapter dibuka dari salah satu sumber.
     *
     * <p>Aturan konflik yang dijaga:
     * <ul>
     *   <li>status {@code completed} tidak pernah dicabut oleh pembukaan
     *       chapter lama;</li>
     *   <li>kejadian lebih tua (timestamp lebih kecil) diabaikan, sehingga
     *       sinkron 2→3→2 tidak berputar;</li>
     *   <li>waktu buka terakhir dicatat terpisah dari penanda selesai.</li>
     * </ul>
     */
    public void recordOpened(String seriesId, String chapterKey, String sourceId,
                             boolean completed, long at) {
        if (seriesId == null || chapterKey == null || chapterKey.isEmpty()) return;
        if (at <= 0) at = System.currentTimeMillis();

        JSONObject map = progressMap();
        JSONObject series = map.optJSONObject(seriesId);
        if (series == null) {
            series = new JSONObject();
            try {
                map.put(seriesId, series);
            } catch (Exception error) {
                return;
            }
        }

        JSONObject existing = series.optJSONObject(chapterKey);
        boolean wasCompleted = existing != null && existing.optBoolean("completed", false);
        long updatedAt = existing == null ? 0 : existing.optLong("updated_at", 0);
        long lastOpened = existing == null ? 0 : existing.optLong("last_opened_at", 0);

        if (existing != null && at < updatedAt) return;

        try {
            JSONObject row = new JSONObject();
            row.put("completed", wasCompleted || completed);
            row.put("last_source", sourceId == null ? "" : sourceId);
            row.put("last_opened_at", Math.max(lastOpened, at));
            row.put("updated_at", Math.max(updatedAt, at));
            series.put(chapterKey, row);
        } catch (Exception error) {
            return;
        }
        prefs.edit().putString(KEY_PROGRESS, map.toString()).apply();
    }

    /** Chapter kanonis tertinggi yang sudah selesai, atau {@code null}. */
    public String lastCompleted(String seriesId) {
        String best = null;
        double bestNumber = -1;
        for (Progress item : progressFor(seriesId)) {
            if (!item.completed) continue;
            double number = ChapterKey.numberValue(item.chapterKey.replace("n:", ""));
            if (number > bestNumber) {
                bestNumber = number;
                best = item.chapterKey;
            }
        }
        return best;
    }

    public void clearAll() {
        prefs.edit().remove(KEY_SERIES).remove(KEY_PROGRESS).apply();
    }

    private JSONObject progressMap() {
        try {
            return new JSONObject(prefs.getString(KEY_PROGRESS, "{}"));
        } catch (Exception error) {
            return new JSONObject();
        }
    }

    private void write(List<Series> items) {
        JSONArray array = new JSONArray();
        for (Series item : items) {
            try {
                array.put(item.toJson());
            } catch (Exception ignored) {
                // Baris yang tidak bisa ditulis dilewati.
            }
        }
        prefs.edit().putString(KEY_SERIES, array.toString()).apply();
    }

    private static String newId() {
        return "cs_" + Long.toString(System.currentTimeMillis(), 36)
                + Integer.toString(new java.util.Random().nextInt(1296), 36);
    }
}