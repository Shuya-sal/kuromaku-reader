package id.shuyasal.kuromaku.shinigami;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Status unduhan Shinigami yang terpisah dari progres membaca.
 *
 * <p>Progres membaca tetap milik {@link ShinigamiStore} (kunci
 * {@code progress:} / {@code page:}). Kelas ini hanya menyimpan
 * {@code downloadProgressPercent} + state antrean di memori, sehingga angka
 * {@code 12%} milik pembaca tidak pernah tertukar dengan {@code 48%} milik
 * pengunduh. Listener selalu dipanggil di main thread agar baris chapter bisa
 * digambar ulang dengan aman.
 */
public final class ShinigamiDownloadStatus {
    public static final int NOT_DOWNLOADED = 0;
    public static final int QUEUED = 1;
    public static final int DOWNLOADING = 2;
    public static final int VERIFYING = 3;
    public static final int SAVED = 4;
    public static final int FAILED = 5;
    public static final int CANCELLED = 6;
    public static final int PARTIAL = 7;

    public interface Listener {
        void onDownloadStateChanged();
    }

    private static final class Entry {
        int state = NOT_DOWNLOADED;
        int percent = 0;
        String message = "";
        String title = "";
        String subtitle = "";
        String owner = "";
    }

    /**
     * Satu chapter yang belum selesai, untuk layar Unduhan.
     *
     * <p>Layar Unduhan sebelumnya hanya menampilkan chapter yang sudah tersimpan;
     * unduhan yang sedang berjalan tidak terlihat di sana.
     */
    public static final class Active {
        public final String chapterId;
        public final int state;
        public final int percent;
        public final String message;
        public final String title;
        public final String subtitle;
        public final String owner;

        Active(String chapterId, Entry entry) {
            this.chapterId = chapterId;
            this.state = entry.state;
            this.percent = entry.percent;
            this.message = entry.message;
            this.title = entry.title;
            this.subtitle = entry.subtitle;
            this.owner = entry.owner;
        }

        public String label() {
            if (!subtitle.isEmpty()) return subtitle;
            if (!title.isEmpty()) return title;
            return chapterId;
        }
    }

    public static final String PREFIX = "shinigami|";

    private static final Map<String, Entry> STATES = new LinkedHashMap<>();
    private static boolean hydrated;
    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String TAG = "ShinigamiDownload";

    private ShinigamiDownloadStatus() {
    }

    static String key(String chapterId) {
        return chapterId == null ? "" : chapterId.trim();
    }

    /** Pulihkan keadaan dari berkas persisten sekali per proses. */
    private static synchronized void hydrate() {
        if (hydrated) return;
        hydrated = true;
        if (!id.shuyasal.kuromaku.offline.DownloadStateFile.installed()) return;
        for (java.util.Map.Entry<String, id.shuyasal.kuromaku.offline.DownloadStateFile.Row> row
                : id.shuyasal.kuromaku.offline.DownloadStateFile.all().entrySet()) {
            if (!row.getKey().startsWith(PREFIX)) continue;
            String chapterKey = row.getKey().substring(PREFIX.length());
            if (STATES.containsKey(chapterKey)) continue;
            Entry entry = new Entry();
            entry.state = row.getValue().state;
            entry.percent = row.getValue().percent;
            entry.message = row.getValue().message;
            entry.title = row.getValue().title;
            entry.subtitle = row.getValue().subtitle;
            entry.owner = row.getValue().owner;
            STATES.put(chapterKey, entry);
        }
    }

    private static void persist(String chapterId, Entry entry) {
        id.shuyasal.kuromaku.offline.DownloadStateFile.put(PREFIX + key(chapterId),
                entry.state, entry.percent, entry.message, "",
                entry.title, entry.subtitle);
        id.shuyasal.kuromaku.offline.DownloadStateFile.setOwner(PREFIX + key(chapterId),
                entry.owner);
    }

    public static synchronized void set(String chapterId, int state, int percent, String message) {
        if (chapterId == null || chapterId.isEmpty()) return;
        hydrate();
        Entry entry = STATES.get(key(chapterId));
        if (entry == null) {
            entry = new Entry();
            STATES.put(key(chapterId), entry);
        }
        entry.state = state;
        entry.percent = Math.max(0, Math.min(100, percent));
        entry.message = message == null ? "" : message;
        persist(chapterId, entry);
        notifyListeners();
    }

    /** Simpan nama yang bisa dibaca pengguna; sekali saat chapter dijadwalkan. */
    public static synchronized void describe(String chapterId, String title, String subtitle,
                                             String owner) {
        if (chapterId == null || chapterId.isEmpty()) return;
        hydrate();
        Entry entry = STATES.get(key(chapterId));
        if (entry == null) {
            entry = new Entry();
            STATES.put(key(chapterId), entry);
        }
        if (title != null && !title.isEmpty()) entry.title = title;
        if (subtitle != null && !subtitle.isEmpty()) entry.subtitle = subtitle;
        if (owner != null && !owner.isEmpty()) entry.owner = owner;
        persist(chapterId, entry);
        notifyListeners();
    }

    /** Chapter yang belum selesai: antre, berjalan, atau gagal. */
    public static synchronized java.util.List<Active> active() {
        hydrate();
        java.util.List<Active> hasil = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, Entry> item : STATES.entrySet()) {
            Entry entry = item.getValue();
            if (entry == null) continue;
            // Hanya yang berjalan; yang gagal sudah tampil di daftar chapter.
            if (entry.state != QUEUED && entry.state != DOWNLOADING
                    && entry.state != VERIFYING && entry.state != PARTIAL) {
                continue;
            }
            hasil.add(new Active(item.getKey(), entry));
        }
        return hasil;
    }

    public static synchronized String title(String chapterId) {
        hydrate();
        Entry entry = STATES.get(key(chapterId));
        return entry == null ? "" : entry.title;
    }

    public static synchronized String subtitle(String chapterId) {
        hydrate();
        Entry entry = STATES.get(key(chapterId));
        return entry == null ? "" : entry.subtitle;
    }

    public static synchronized int state(String chapterId) {
        hydrate();
        Entry entry = STATES.get(key(chapterId));
        return entry == null ? NOT_DOWNLOADED : entry.state;
    }

    public static synchronized int percent(String chapterId) {
        hydrate();
        Entry entry = STATES.get(key(chapterId));
        return entry == null ? 0 : entry.percent;
    }

    public static synchronized String message(String chapterId) {
        hydrate();
        Entry entry = STATES.get(key(chapterId));
        return entry == null ? "" : entry.message;
    }

    public static synchronized void clear(String chapterId) {
        STATES.remove(key(chapterId));
        id.shuyasal.kuromaku.offline.DownloadStateFile.remove(PREFIX + key(chapterId));
        notifyListeners();
    }

    public static synchronized void addListener(Listener listener) {
        if (listener != null && !LISTENERS.contains(listener)) LISTENERS.add(listener);
    }

    public static synchronized void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    /** Lupakan keadaan di memori tanpa menyentuh berkas (meniru proses mati). */
    public static synchronized void forgetForTesting() {
        STATES.clear();
        hydrated = false;
    }

    public static synchronized void reset() {
        STATES.clear();
        LISTENERS.clear();
        hydrated = false;
        id.shuyasal.kuromaku.offline.DownloadStateFile.clearPrefix(PREFIX);
    }

    private static void notifyListeners() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            deliver();
            return;
        }
        MAIN.post(ShinigamiDownloadStatus::deliver);
    }

    private static void deliver() {
        List<Listener> copy;
        synchronized (ShinigamiDownloadStatus.class) {
            copy = new ArrayList<>(LISTENERS);
        }
        for (Listener listener : copy) {
            try {
                listener.onDownloadStateChanged();
            } catch (RuntimeException error) {
                Log.e(TAG, "listener unduhan gagal", error);
            }
        }
    }
}
