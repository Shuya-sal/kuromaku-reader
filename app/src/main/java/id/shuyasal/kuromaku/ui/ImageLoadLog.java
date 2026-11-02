package id.shuyasal.kuromaku.ui;

import android.util.Log;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Stage-by-stage diagnostics for image loading.
 *
 * <p>Every entry names the stage that failed (url, download, decode, bind) so a
 * broken illustration can be traced without guessing. URLs are redacted to host
 * plus file name: the Blogger token in the path is long and noisy, and there is
 * no reason to write it to logcat.
 */
public final class ImageLoadLog {

    private static final String TAG = "KuromakuImage";
    private static final int MAX_RECENT = 40;
    private static final Deque<String> RECENT = new ArrayDeque<>();

    private ImageLoadLog() {
    }

    /** Keeps only the host and the last path segment; drops query and token. */
    public static String redact(String url) {
        if (url == null || url.isEmpty()) return "<kosong>";
        String value = url;
        int query = value.indexOf('?');
        if (query > 0) value = value.substring(0, query);
        int scheme = value.indexOf("://");
        String rest = scheme >= 0 ? value.substring(scheme + 3) : value;
        int slash = rest.indexOf('/');
        if (slash < 0) return rest;
        String host = rest.substring(0, slash);
        String path = rest.substring(slash);
        int lastSlash = path.lastIndexOf('/');
        String file = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        return host + "/…/" + file;
    }

    public static void stage(String stage, String url) {
        record("stage=" + stage + " " + redact(url));
    }

    public static void ok(String url, int width, int height) {
        record("ok " + redact(url) + " " + width + "x" + height);
    }

    public static void fail(String stage, String url, String reason) {
        record("FAIL stage=" + stage + " " + redact(url) + " sebab=" + reason);
        Log.w(TAG, "gagal memuat gambar, stage=" + stage
                + ", sumber=" + redact(url) + ", sebab=" + reason);
    }

    public static void warn(String stage, String url, String reason) {
        record("warn stage=" + stage + " " + redact(url) + " " + reason);
        Log.d(TAG, "peringatan gambar, stage=" + stage
                + ", sumber=" + redact(url) + ", " + reason);
    }

    private static synchronized void record(String line) {
        if (RECENT.size() >= MAX_RECENT) RECENT.removeFirst();
        RECENT.addLast(line);
        Log.d(TAG, line);
    }

    /** Recent entries, newest last. Used by tests and the error pane. */
    public static synchronized List<String> recent() {
        return new ArrayList<>(RECENT);
    }

    public static synchronized void clear() {
        RECENT.clear();
    }

    /** Human-readable size, used in the failure message. */
    static String describe(int width, int height) {
        return String.format(Locale.US, "%dx%d", width, height);
    }
}
