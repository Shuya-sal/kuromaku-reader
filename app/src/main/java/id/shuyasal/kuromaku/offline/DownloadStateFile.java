package id.shuyasal.kuromaku.offline;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Status unduhan yang bertahan setelah proses aplikasi mati.
 *
 * <p>Ketiga modul (Novel, Manga Server 2, Anime Streaming) menulis keadaan
 * unduhannya ke satu berkas JSON di penyimpanan internal aplikasi. Sebelumnya
 * status hanya hidup di memori: begitu proses dibunuh Android, layar menampilkan
 * "belum diunduh" padahal berkasnya ada dan pekerjaan WorkManager mungkin masih
 * berjalan. Berkas ini menutup celah itu tanpa mengubah lokasi berkas unduhan.
 *
 * <p>Penulisan bersifat atomik ({@code .tmp} lalu {@code rename}), sehingga
 * kematian proses di tengah penulisan tidak menghasilkan JSON rusak. Setiap
 * kunci memakai awalan modulnya ({@code anime|}, {@code shinigami|},
 * {@code novel|}) agar status antar-modul tidak saling menimpa.
 *
 * <p>Berkas ini <b>bukan</b> sumber kebenaran tunggal: berkas media di disk tetap
 * yang menentukan apakah sebuah episode benar-benar tersimpan. Status di sini
 * dipakai untuk memulihkan tampilan, lalu direkonsiliasi dengan berkas nyata.
 */
public final class DownloadStateFile {

    private static final String TAG = "KuromakuDownloads";
    private static final String FILE_NAME = "download-state.json";

    /**
     * Jeda minimum antar penulisan berkas status.
     *
     * <p>Progres datang per blok byte. Menulis dan melakukan fsync setiap blok
     * membuat penyimpanan menjadi hambatan utama — dan karena penulisan ini
     * memegang kunci kelas, thread UI ikut menunggu. 500 ms cukup untuk tampilan
     * yang terasa langsung, tanpa membanjiri disk.
     */
    private static final long FLUSH_INTERVAL_MS = 500;

    private static long lastFlushAt = 0L;

    /** Satu baris keadaan unduhan. */
    public static final class Row {
        public final int state;
        public final int percent;
        public final String message;
        public final String quality;
        public final long updatedAt;

        Row(int state, int percent, String message, String quality, long updatedAt) {
            this.state = state;
            this.percent = percent;
            this.message = message == null ? "" : message;
            this.quality = quality == null ? "" : quality;
            this.updatedAt = updatedAt;
        }

        JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("state", state);
            json.put("percent", percent);
            json.put("message", message);
            json.put("quality", quality);
            json.put("updatedAt", updatedAt);
            return json;
        }

        static Row fromJson(JSONObject json) {
            return new Row(json.optInt("state"),
                    Math.max(0, Math.min(100, json.optInt("percent"))),
                    json.optString("message"),
                    json.optString("quality"),
                    json.optLong("updatedAt"));
        }
    }

    private static Context appContext;
    private static final Map<String, Row> CACHE = new LinkedHashMap<>();
    private static boolean loaded;

    private DownloadStateFile() {
    }

    /** Dipanggil sekali dari Application; aman dipanggil berulang. */
    public static synchronized void install(Context context) {
        if (context == null) return;
        appContext = context.getApplicationContext();
        if (!loaded) {
            CACHE.putAll(load(appContext));
            loaded = true;
        }
    }

    /** Benar bila penyimpanan persisten sudah siap dipakai. */
    public static synchronized boolean installed() {
        return appContext != null;
    }

    public static synchronized void put(String key, int state, int percent, String message,
                                        String quality) {
        if (key == null || key.isEmpty()) return;
        Row previous = CACHE.get(key);
        Row row = new Row(state, percent, message, quality, System.currentTimeMillis());
        CACHE.put(key, row);
        if (appContext == null) return;
        boolean stateChanged = previous == null || previous.state != state;
        long now = System.currentTimeMillis();
        if (stateChanged || now - lastFlushAt >= FLUSH_INTERVAL_MS) {
            lastFlushAt = now;
            flush(stateChanged);
        }
    }

    /** Tulis keadaan dengan waktu lampau; dipakai pengujian rekonsiliasi. */
    static synchronized void putAged(String key, int state, int percent, String message,
                                     String quality, long updatedAt) {
        if (key == null || key.isEmpty()) return;
        CACHE.put(key, new Row(state, percent, message, quality, updatedAt));
        if (appContext != null) flush();
    }

    public static synchronized Row get(String key) {
        return key == null ? null : CACHE.get(key);
    }

    public static synchronized void remove(String key) {
        if (key == null) return;
        if (CACHE.remove(key) != null && appContext != null) flush();
    }

    /** Salinan seluruh keadaan yang tersimpan. */
    public static synchronized Map<String, Row> all() {
        return new LinkedHashMap<>(CACHE);
    }

    /** Buang semua entri dengan awalan modul tertentu (dipakai reset pengujian). */
    public static synchronized void clearPrefix(String prefix) {
        CACHE.keySet().removeIf(key -> key.startsWith(prefix));
        if (appContext != null) flush();
    }

    // ------------------------------------------------------------ berkas

    private static void flush() {
        flush(true);
    }

    /**
     * @param durable true untuk perubahan status (fsync), false untuk progres
     *                biasa yang cukup di-rename atomik.
     */
    private static void flush(boolean durable) {
        File file = file(appContext);
        File dir = file.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "folder status unduhan tidak bisa dibuat");
            return;
        }
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        JSONObject json = new JSONObject();
        try {
            for (Map.Entry<String, Row> entry : CACHE.entrySet()) {
                json.put(entry.getKey(), entry.getValue().toJson());
            }
            byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(bytes);
                if (durable) out.getFD().sync();
            }
            if (!temp.renameTo(file)) {
                Log.w(TAG, "status unduhan gagal dipindahkan ke tempat akhir");
            }
        } catch (IOException | JSONException error) {
            Log.w(TAG, "status unduhan gagal disimpan", error);
        }
    }

    private static Map<String, Row> load(Context context) {
        Map<String, Row> rows = new LinkedHashMap<>();
        File file = file(context);
        if (!file.isFile()) return rows;
        String text;
        try (FileInputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            text = new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            Log.w(TAG, "status unduhan tidak bisa dibaca", error);
            return rows;
        }
        try {
            JSONObject json = new JSONObject(text);
            for (java.util.Iterator<String> keys = json.keys(); keys.hasNext(); ) {
                String key = keys.next();
                JSONObject value = json.optJSONObject(key);
                if (value != null) rows.put(key, Row.fromJson(value));
            }
        } catch (JSONException error) {
            Log.w(TAG, "status unduhan rusak; dimulai dari kosong", error);
        }
        return rows;
    }

    private static File file(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }
}