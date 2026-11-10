package id.shuyasal.kuromaku.komiku;

import android.content.Context;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Status unduhan Komiku di memori.
 *
 * <p>Status di sini hanya untuk chapter yang sedang berjalan (antre, mengunduh,
 * gagal, dibatalkan). Status "tersimpan" tidak pernah diklaim dari sini — yang
 * menentukan adalah berkas halaman yang benar-benar lolos validasi
 * ({@link KomikuDownloads#isDownloaded}). Jadi tidak ada kemungkinan tombol
 * menampilkan "Tersimpan offline" untuk chapter yang berkasnya tidak lengkap.
 */
public final class KomikuDownloadStatus {

    public static final int NOT_DOWNLOADED = 0;
    public static final int QUEUED = 1;
    public static final int DOWNLOADING = 2;
    public static final int SAVED = 3;
    public static final int FAILED = 4;
    public static final int CANCELLED = 5;
    /** Sebagian halaman tersimpan; belum lengkap, bisa dilanjutkan. */
    public static final int PARTIAL = 6;

    public static final String PREFIX = "komiku|";

    public static final class Active {
        public final String chapterKey;
        public final String title;
        public final String subtitle;
        public final String mangaId;
        public final int state;
        public final int percent;
        public final String message;

        Active(String chapterKey, String title, String subtitle, String mangaId,
               int state, int percent, String message) {
            this.chapterKey = chapterKey;
            this.title = title == null ? "" : title;
            this.subtitle = subtitle == null ? "" : subtitle;
            this.mangaId = mangaId == null ? "" : mangaId;
            this.state = state;
            this.percent = percent;
            this.message = message == null ? "" : message;
        }

        public boolean running() {
            return state == QUEUED || state == DOWNLOADING;
        }
    }

    private static final Map<String, JSONObject> STATE = new LinkedHashMap<>();

    private KomikuDownloadStatus() {
    }

    public static synchronized void describe(String chapterKey, String title,
                                             String subtitle, String mangaId) {
        JSONObject item = entry(chapterKey);
        try {
            item.put("title", title == null ? "" : title);
            item.put("subtitle", subtitle == null ? "" : subtitle);
            item.put("mangaId", mangaId == null ? "" : mangaId);
        } catch (Exception ignored) {
            // Kunci di atas selalu ada; kegagalan di sini tidak mematikan apa pun.
        }
    }

    public static synchronized void set(String chapterKey, int newState, int percent,
                                        String message) {
        if (chapterKey == null || chapterKey.isEmpty()) return;
        JSONObject item = entry(chapterKey);
        try {
            item.put("state", newState);
            item.put("percent", Math.max(0, Math.min(100, percent)));
            item.put("message", message == null ? "" : message);
            item.put("updatedAt", System.currentTimeMillis());
        } catch (Exception ignored) {
            // Sama seperti di atas.
        }
    }

    public static synchronized int state(String chapterKey) {
        return entry(chapterKey).optInt("state", NOT_DOWNLOADED);
    }

    public static synchronized int percent(String chapterKey) {
        return entry(chapterKey).optInt("percent", 0);
    }

    public static synchronized String message(String chapterKey) {
        return entry(chapterKey).optString("message", "");
    }

    public static synchronized String title(String chapterKey) {
        return entry(chapterKey).optString("title", "");
    }

    public static synchronized String subtitle(String chapterKey) {
        return entry(chapterKey).optString("subtitle", "");
    }

    public static synchronized void clear(String chapterKey) {
        if (chapterKey == null) return;
        STATE.remove(chapterKey);
    }

    /** Chapter yang masih berjalan atau gagal, terbaru di atas. */
    public static synchronized List<Active> active() {
        List<Active> out = new ArrayList<>();
        for (Map.Entry<String, JSONObject> entry : STATE.entrySet()) {
            JSONObject item = entry.getValue();
            int state = item.optInt("state", NOT_DOWNLOADED);
            if (state == NOT_DOWNLOADED) continue;
            out.add(new Active(entry.getKey(), item.optString("title", ""),
                    item.optString("subtitle", ""), item.optString("mangaId", ""),
                    state, item.optInt("percent", 0), item.optString("message", "")));
        }
        out.sort((a, b) -> Integer.compare(b.state, a.state));
        return out;
    }

    /** Label status yang dibaca pengguna untuk satu chapter. */
    public static String label(Context context, String chapterKey, int expectedPages) {
        KomikuDownloads local = new KomikuDownloads(context);
        if (expectedPages > 0 && local.isDownloaded(chapterKey, expectedPages)) {
            return "Tersimpan offline";
        }
        int saved = expectedPages > 0 ? local.savedPages(chapterKey, expectedPages) : 0;
        int state = state(chapterKey);
        if (state == QUEUED) return "Menunggu antrean";
        if (state == DOWNLOADING) {
            int percent = percent(chapterKey);
            return percent > 0 ? "Mengunduh " + percent + "%" : "Mengunduh...";
        }
        if (state == FAILED) return "Unduhan gagal";
        if (state == CANCELLED) return "Dibatalkan";
        if (saved > 0 && expectedPages > 0) {
            return "Sebagian " + saved + "/" + expectedPages;
        }
        return "Belum diunduh";
    }

    private static JSONObject entry(String chapterKey) {
        if (chapterKey == null) chapterKey = "";
        JSONObject item = STATE.get(chapterKey);
        if (item == null) {
            item = new JSONObject();
            try {
                item.put("state", NOT_DOWNLOADED);
                item.put("percent", 0);
            } catch (Exception ignored) {
                // Tidak mungkin gagal untuk kunci tetap.
            }
            STATE.put(chapterKey, item);
        }
        return item;
    }
}
