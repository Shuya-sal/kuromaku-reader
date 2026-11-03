package id.shuyasal.kuromaku.offline;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory view of which chapters are queued, downloading, saved or failed.
 *
 * <p>The download itself runs on a background worker; the library page only
 * needs to know what to draw next to each chapter and to be told when something
 * changed. This class is the single place that state lives, so a chapter cannot
 * show "Tersimpan" while the store says otherwise: the status is derived from
 * the store plus the live queue.
 *
 * <p>Keys are {@code novelId + "|" + chapterUrl}, which keeps the two novels
 * apart even if a chapter URL were somehow reused.
 */
public final class DownloadStatusStore {

    public static final int NOT_DOWNLOADED = 0;
    public static final int QUEUED = 1;
    public static final int DOWNLOADING = 2;
    public static final int SAVED = 3;
    public static final int FAILED = 4;
    public static final int CANCELLED = 5;
    /** Text stored but at least one illustration is still missing. */
    public static final int PARTIAL = 6;
    /** Bytes are written and being read back to confirm they landed. */
    public static final int VERIFYING = 7;

    /**
     * Byte progress of the chapter currently being fetched.
     *
     * <p>{@code percent} is -1 when the server declared no {@code Content-Length}
     * for a picture: the UI then shows a spinner and no number, because a
     * percentage nobody measured is a lie the reader cannot check.
     */
    public static final class Bytes {
        public final int percent;
        public final int imageIndex;
        public final int imageCount;

        Bytes(int percent, int imageIndex, int imageCount) {
            this.percent = percent;
            this.imageIndex = imageIndex;
            this.imageCount = imageCount;
        }
    }

    /** Listener notified on the main thread whenever any status changes. */
    public interface Listener {
        void onDownloadStateChanged();
    }

    private static final Map<String, Integer> STATE = new LinkedHashMap<>();
    private static final Map<String, String> MESSAGES = new LinkedHashMap<>();
    private static final Map<String, Bytes> BYTES = new LinkedHashMap<>();
    private static final List<Listener> LISTENERS = new ArrayList<>();

    /** Progress of a running volume job: finished chapters and the total. */
    private static final Map<String, int[]> PROGRESS = new LinkedHashMap<>();

    private static final String TAG = "KuromakuDownloads";

    /** Every listener is delivered here, never on the worker's thread. */
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private DownloadStatusStore() {
    }

    public static String key(String novelId, String url) {
        return (novelId == null ? "" : novelId) + "|" + (url == null ? "" : url.trim());
    }

    public static synchronized void set(String key, int state, String message) {
        STATE.put(key, state);
        if (message == null || message.isEmpty()) {
            MESSAGES.remove(key);
        } else {
            MESSAGES.put(key, message);
        }
        notifyListeners();
    }

    public static synchronized int state(String key) {
        Integer state = STATE.get(key);
        return state == null ? NOT_DOWNLOADED : state;
    }

    public static synchronized String message(String key) {
        String message = MESSAGES.get(key);
        return message == null ? "" : message;
    }

    /**
     * Records byte progress for a running chapter.
     *
     * <p>Notifies listeners at most on every whole percent, so a fast connection
     * does not rebuild the list hundreds of times.
     */
    public static synchronized void setBytes(String key, int percent, int imageIndex, int imageCount) {
        Bytes previous = BYTES.get(key);
        if (previous != null && previous.percent == percent
                && previous.imageIndex == imageIndex && previous.imageCount == imageCount) {
            return;
        }
        BYTES.put(key, new Bytes(percent, imageIndex, imageCount));
        notifyListeners();
    }

    /** Byte progress for a chapter, or null when nothing is known. */
    public static synchronized Bytes bytes(String key) {
        return BYTES.get(key);
    }

    public static synchronized void clear(String key) {
        STATE.remove(key);
        MESSAGES.remove(key);
        BYTES.remove(key);
        notifyListeners();
    }

    /** Records how far a volume job has come: {@code [finished, total]}. */
    public static synchronized void setProgress(String novelId, int volume, int finished, int total) {
        PROGRESS.put(novelId + "#" + volume, new int[]{finished, total});
        notifyListeners();
    }

    public static synchronized int[] progress(String novelId, int volume) {
        int[] value = PROGRESS.get(novelId + "#" + volume);
        return value == null ? new int[]{0, 0} : value.clone();
    }

    public static synchronized void clearProgress(String novelId, int volume) {
        PROGRESS.remove(novelId + "#" + volume);
        notifyListeners();
    }

    /**
     * Resolves what the library should print for one chapter: a live queue state
     * wins, otherwise the disk decides. Never reports "Tersimpan" for a chapter
     * the store cannot open.
     */
    public static int resolve(String novelId, String url, OfflineStore store) {
        String key = key(novelId, url);
        int live = state(key);
        if (live == QUEUED || live == DOWNLOADING) return live;
        if (store != null && store.isSaved(novelId, url)) return SAVED;
        return live;
    }

    public static synchronized void addListener(Listener listener) {
        if (listener != null && !LISTENERS.contains(listener)) LISTENERS.add(listener);
    }

    public static synchronized void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    /**
     * Delivers a change to every listener on the main thread.
     *
     * <p>The worker updates state from its own thread. Calling listeners straight
     * from there made the activity rebuild its page off the main thread: view
     * work threw, the page stayed half-built behind a live toolbar, and the user
     * saw a blank screen. When the change already happens on the main thread the
     * delivery stays synchronous, so nothing about ordering changes for callers.
     */
    private static void notifyListeners() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            deliver();
            return;
        }
        MAIN.post(DownloadStatusStore::deliver);
    }

    private static void deliver() {
        List<Listener> copy;
        synchronized (DownloadStatusStore.class) {
            copy = new ArrayList<>(LISTENERS);
        }
        for (Listener listener : copy) {
            try {
                listener.onDownloadStateChanged();
            } catch (RuntimeException error) {
                // One screen failing to repaint must not stop the others, and must
                // never surface as a blank page with no trace.
                Log.e(TAG, "listener unduhan gagal", error);
            }
        }
    }

    /** Drops every transient state; used by tests and on a full reset. */
    public static synchronized void reset() {
        STATE.clear();
        MESSAGES.clear();
        PROGRESS.clear();
        LISTENERS.clear();
    }
}
