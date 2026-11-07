package id.shuyasal.kuromaku.anime;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Status unduhan episode anime di memori.
 *
 * <p>Terpisah dari progres menonton ({@link OtakudesuStore}) dan dari status
 * unduhan manga Shinigami: kunci di sini adalah episode anime, bukan chapter
 * manga. Angka yang tampil selalu berasal dari byte yang benar-benar tersimpan,
 * bukan dari perkiraan.
 *
 * <p>Kelas ini <b>tidak</b> dipakai untuk mengklaim bahwa sebuah episode sudah
 * tersimpan. Klaim itu hanya boleh datang dari {@link AnimeDownloads#isSaved},
 * yang memeriksa berkasnya langsung.
 */
public final class AnimeDownloadStatus {

    public static final int NOT_DOWNLOADED = 0;
    public static final int QUEUED = 1;
    public static final int DOWNLOADING = 2;
    public static final int VERIFYING = 3;
    public static final int SAVED = 4;
    public static final int FAILED = 5;
    public static final int PAUSED = 6;

    public interface Listener {
        void onDownloadStateChanged();
    }

    private static final class Entry {
        int state = NOT_DOWNLOADED;
        int percent = 0;
        long bytes = 0L;
        long total = 0L;
        String message = "";
    }

    private static final Map<String, Entry> STATES = new LinkedHashMap<>();
    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String TAG = "AnimeDownload";

    private AnimeDownloadStatus() {
    }

    private static String key(String episodeKey) {
        return episodeKey == null ? "" : episodeKey.trim();
    }

    public static synchronized void set(String episodeKey, int state, int percent, String message) {
        if (episodeKey == null || episodeKey.isEmpty()) return;
        Entry entry = entry(episodeKey);
        entry.state = state;
        entry.percent = Math.max(0, Math.min(100, percent));
        entry.message = message == null ? "" : message;
        notifyListeners();
    }

    /** Progres berdasarkan byte nyata: persen hanya bila total diketahui. */
    public static synchronized void progress(String episodeKey, long bytes, long total) {
        if (episodeKey == null || episodeKey.isEmpty()) return;
        Entry entry = entry(episodeKey);
        entry.state = DOWNLOADING;
        entry.bytes = Math.max(0L, bytes);
        entry.total = Math.max(0L, total);
        entry.percent = total > 0
                ? (int) Math.max(0L, Math.min(100L, bytes * 100L / total))
                : 0;
        notifyListeners();
    }

    public static synchronized void clear(String episodeKey) {
        STATES.remove(key(episodeKey));
        notifyListeners();
    }

    public static synchronized int state(String episodeKey) {
        Entry entry = STATES.get(key(episodeKey));
        return entry == null ? NOT_DOWNLOADED : entry.state;
    }

    public static synchronized int percent(String episodeKey) {
        Entry entry = STATES.get(key(episodeKey));
        return entry == null ? 0 : entry.percent;
    }

    public static synchronized long bytes(String episodeKey) {
        Entry entry = STATES.get(key(episodeKey));
        return entry == null ? 0L : entry.bytes;
    }

    public static synchronized long total(String episodeKey) {
        Entry entry = STATES.get(key(episodeKey));
        return entry == null ? 0L : entry.total;
    }

    public static synchronized String message(String episodeKey) {
        Entry entry = STATES.get(key(episodeKey));
        return entry == null ? "" : entry.message;
    }

    /** Keterangan progres untuk ditampilkan; jujur saat total belum diketahui. */
    public static synchronized String progressLabel(String episodeKey) {
        Entry entry = STATES.get(key(episodeKey));
        if (entry == null) return "";
        if (entry.state == SAVED) return "Tersimpan offline";
        if (entry.state == PAUSED) return "Terjeda";
        if (entry.state == QUEUED) return "Menunggu";
        if (entry.state == VERIFYING) return "Memeriksa berkas…";
        if (entry.state == FAILED) return entry.message.isEmpty() ? "Gagal" : entry.message;
        if (entry.total > 0) return entry.percent + "% · " + AnimeDownload.size(entry.bytes);
        return AnimeDownload.size(entry.bytes) + " terunduh";
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

    private static Entry entry(String episodeKey) {
        Entry entry = STATES.get(key(episodeKey));
        if (entry == null) {
            entry = new Entry();
            STATES.put(key(episodeKey), entry);
        }
        return entry;
    }

    private static void notifyListeners() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            deliver();
            return;
        }
        MAIN.post(AnimeDownloadStatus::deliver);
    }

    private static void deliver() {
        List<Listener> copy;
        synchronized (AnimeDownloadStatus.class) {
            copy = new ArrayList<>(LISTENERS);
        }
        for (Listener listener : copy) {
            try {
                listener.onDownloadStateChanged();
            } catch (RuntimeException error) {
                Log.e(TAG, "listener unduhan anime gagal", error);
            }
        }
    }
}