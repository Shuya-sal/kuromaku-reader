package id.shuyasal.kuromaku.anime;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ingatan jangka pendek hasil pemeriksaan server.
 *
 * <p>Membuka panel dua kali untuk episode yang sama tidak perlu memeriksa ulang
 * semua mirror: hasil yang masih segar dipakai langsung. Kunci menyertakan
 * alamat episode, jadi hasil episode A tidak pernah muncul di episode B.
 *
 * <p>Umur simpan dibuat pendek karena alamat berkas penyedia bisa kedaluwarsa.
 * Setelah TTL lewat, mirror diperiksa lagi seperti biasa.
 */
final class ServerProbeCache {

    /** Hasil dianggap segar selama sepuluh menit. */
    static final long TTL_MS = 10 * 60 * 1000L;

    /** Batas entri agar ingatan tidak tumbuh tanpa henti. */
    private static final int MAX_ENTRIES = 200;

    private static final class Entry {
        final OtakudesuServerPicker.Probed result;
        final long storedAt;

        Entry(OtakudesuServerPicker.Probed result, long storedAt) {
            this.result = result;
            this.storedAt = storedAt;
        }
    }

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

    private ServerProbeCache() {
    }

    static String key(String episodeUrl, String mirrorKey) {
        return (episodeUrl == null ? "" : episodeUrl) + '\u0000'
                + (mirrorKey == null ? "" : mirrorKey);
    }

    /** Hasil yang masih segar, atau {@code null} bila belum ada/kedaluwarsa. */
    static OtakudesuServerPicker.Probed get(String episodeUrl, String mirrorKey, long now) {
        Entry entry = ENTRIES.get(key(episodeUrl, mirrorKey));
        if (entry == null) return null;
        if (now - entry.storedAt > TTL_MS) {
            ENTRIES.remove(key(episodeUrl, mirrorKey));
            return null;
        }
        return entry.result;
    }

    static void put(String episodeUrl, String mirrorKey, OtakudesuServerPicker.Probed result,
                    long now) {
        if (result == null) return;
        String key = key(episodeUrl, mirrorKey);
        ENTRIES.remove(key);
        ENTRIES.put(key, new Entry(result, now));
        trim();
    }

    private static void trim() {
        Iterator<String> keys = ENTRIES.keySet().iterator();
        while (ENTRIES.size() > MAX_ENTRIES && keys.hasNext()) {
            keys.next();
            keys.remove();
        }
    }

    /** Buang hasil satu episode; dipakai saat pengguna meminta periksa ulang. */
    static void forget(String episodeUrl) {
        String prefix = (episodeUrl == null ? "" : episodeUrl) + '\u0000';
        ENTRIES.keySet().removeIf(key -> key.startsWith(prefix));
    }

    static void clear() {
        ENTRIES.clear();
    }

    static int size() {
        return ENTRIES.size();
    }
}