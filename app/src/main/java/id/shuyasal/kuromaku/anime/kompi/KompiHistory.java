package id.shuyasal.kuromaku.anime.kompi;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Riwayat tontonan Anime Server 2.
 *
 * <p>Disimpan terpisah dari riwayat Anime Server 1 ({@code OtakudesuStore}):
 * penanda anime dari dua sumber tidak sama, jadi menggabungkannya membuat satu
 * sumber menimpa entri sumber lain. Berkas ini hanya menambah data baru; tidak
 * pernah menghapus riwayat sumber lain.
 *
 * <p>Yang disimpan per anime: judul, poster, episode terakhir, alamat episode,
 * nomor episode, dan waktu tonton. Posisi detik video tidak disimpan karena
 * episode baru selalu dimulai dari awal.
 */
public final class KompiHistory {

    static final String FILE_NAME = "kuromaku_kompi_history";
    private static final String KEY_ENTRIES = "entries";
    /** Batas entri supaya berkas tidak tumbuh tanpa henti. */
    static final int MAX_ENTRIES = 60;

    /** Satu anime yang pernah ditonton. */
    public static final class Entry {
        public final String slug;
        public final String title;
        public final String poster;
        public final String episodeLabel;
        public final String episodeSlug;
        public final int episodeNumber;
        public final long watchedAt;

        public Entry(String slug, String title, String poster, String episodeLabel,
                     String episodeSlug, int episodeNumber, long watchedAt) {
            this.slug = slug == null ? "" : slug.trim();
            this.title = title == null ? "" : title.trim();
            this.poster = poster == null ? "" : poster.trim();
            this.episodeLabel = episodeLabel == null ? "" : episodeLabel.trim();
            this.episodeSlug = episodeSlug == null ? "" : episodeSlug.trim();
            this.episodeNumber = episodeNumber;
            this.watchedAt = watchedAt;
        }

        /** "Episode 3" atau label apa adanya bila sumber tidak memberi nomor. */
        public String episodeText() {
            if (!episodeLabel.isEmpty()) return episodeLabel;
            return episodeNumber > 0 ? "Episode " + episodeNumber : "Episode";
        }
    }

    private KompiHistory() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    /** Catat tontonan; entri lama untuk anime yang sama diganti yang baru. */
    public static void record(Context context, String slug, String title, String poster,
                              String episodeLabel, String episodeSlug, int episodeNumber) {
        if (context == null || slug == null || slug.trim().isEmpty()) return;
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(slug, title, poster, episodeLabel, episodeSlug, episodeNumber,
                System.currentTimeMillis()));
        for (Entry existing : all(context)) {
            if (!existing.slug.equalsIgnoreCase(slug)) entries.add(existing);
        }
        while (entries.size() > MAX_ENTRIES) entries.remove(entries.size() - 1);
        write(context, entries);
    }

    /** Riwayat terbaru lebih dulu. */
    public static List<Entry> all(Context context) {
        List<Entry> out = new ArrayList<>();
        if (context == null) return out;
        String raw = prefs(context).getString(KEY_ENTRIES, "");
        if (raw == null || raw.isEmpty()) return out;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject node = array.optJSONObject(i);
                if (node == null) continue;
                String slug = node.optString("slug", "");
                if (slug.isEmpty()) continue;
                out.add(new Entry(slug, node.optString("title", ""),
                        node.optString("poster", ""), node.optString("episodeLabel", ""),
                        node.optString("episodeSlug", ""), node.optInt("episodeNumber", 0),
                        node.optLong("watchedAt", 0)));
            }
        } catch (JSONException ignored) {
            // Berkas rusak: riwayat dianggap kosong, bukan membuat aplikasi berhenti.
        }
        out.sort((a, b) -> Long.compare(b.watchedAt, a.watchedAt));
        return out;
    }

    /** Entri untuk satu anime, atau null bila belum pernah ditonton. */
    public static Entry forSlug(Context context, String slug) {
        if (slug == null) return null;
        for (Entry entry : all(context)) {
            if (entry.slug.equalsIgnoreCase(slug.trim())) return entry;
        }
        return null;
    }

    /** Hapus satu entri; anime lain tidak tersentuh. */
    public static void remove(Context context, String slug) {
        if (context == null || slug == null) return;
        List<Entry> kept = new ArrayList<>();
        for (Entry entry : all(context)) {
            if (!entry.slug.equalsIgnoreCase(slug.trim())) kept.add(entry);
        }
        write(context, kept);
    }

    private static void write(Context context, List<Entry> entries) {
        JSONArray array = new JSONArray();
        for (Entry entry : entries) {
            JSONObject node = new JSONObject();
            try {
                node.put("slug", entry.slug);
                node.put("title", entry.title);
                node.put("poster", entry.poster);
                node.put("episodeLabel", entry.episodeLabel);
                node.put("episodeSlug", entry.episodeSlug);
                node.put("episodeNumber", entry.episodeNumber);
                node.put("watchedAt", entry.watchedAt);
            } catch (JSONException ignored) {
                continue;
            }
            array.put(node);
        }
        prefs(context).edit().putString(KEY_ENTRIES, array.toString()).apply();
    }
}
