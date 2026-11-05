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
    }

    private static final Map<String, Entry> STATES = new LinkedHashMap<>();
    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String TAG = "ShinigamiDownload";

    private ShinigamiDownloadStatus() {
    }

    static String key(String chapterId) {
        return chapterId == null ? "" : chapterId.trim();
    }

    public static synchronized void set(String chapterId, int state, int percent, String message) {
        if (chapterId == null || chapterId.isEmpty()) return;
        Entry entry = STATES.get(key(chapterId));
        if (entry == null) {
            entry = new Entry();
            STATES.put(key(chapterId), entry);
        }
        entry.state = state;
        entry.percent = Math.max(0, Math.min(100, percent));
        entry.message = message == null ? "" : message;
        notifyListeners();
    }

    public static synchronized int state(String chapterId) {
        Entry entry = STATES.get(key(chapterId));
        return entry == null ? NOT_DOWNLOADED : entry.state;
    }

    public static synchronized int percent(String chapterId) {
        Entry entry = STATES.get(key(chapterId));
        return entry == null ? 0 : entry.percent;
    }

    public static synchronized String message(String chapterId) {
        Entry entry = STATES.get(key(chapterId));
        return entry == null ? "" : entry.message;
    }

    public static synchronized void clear(String chapterId) {
        STATES.remove(key(chapterId));
        notifyListeners();
    }

    public static synchronized void addListener(Listener listener) {
        if (listener != null && !LISTENERS.contains(listener)) LISTENERS.add(listener);
    }

    public static synchronized void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    public static synchronized void reset() {
        STATES.clear();
        LISTENERS.clear();
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
