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
        String quality = "";
        String title = "";
        String subtitle = "";
        String owner = "";
    }

    /**
     * Satu unduhan yang tidak (atau belum) selesai, untuk layar Unduhan.
     *
     * <p>Layar Unduhan sebelumnya hanya menampilkan berkas yang sudah ada di
     * disk. Akibatnya unduhan yang sedang berjalan tidak terlihat di mana pun
     * selain daftar episode anime masing-masing — padahal di situlah pengguna
     * mencari "apa yang sedang diunduh".
     */
    public static final class Active {
        public final String episodeKey;
        public final int state;
        public final int percent;
        public final long bytes;
        public final long total;
        public final String message;
        public final String quality;
        public final String title;
        public final String subtitle;
        public final String owner;

        Active(String episodeKey, Entry entry) {
            this.episodeKey = episodeKey;
            this.state = entry.state;
            this.percent = entry.percent;
            this.bytes = entry.bytes;
            this.total = entry.total;
            this.message = entry.message;
            this.quality = entry.quality;
            this.title = entry.title;
            this.subtitle = entry.subtitle;
            this.owner = entry.owner;
        }

        /** Nama yang layak ditampilkan; tidak pernah kosong. */
        public String label() {
            if (!subtitle.isEmpty()) return subtitle;
            if (!title.isEmpty()) return title;
            return episodeKey;
        }
    }

    public static final String PREFIX = "anime|";

    private static final Map<String, Entry> STATES = new LinkedHashMap<>();
    private static boolean hydrated;
    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String TAG = "AnimeDownload";

    private AnimeDownloadStatus() {
    }

    private static String key(String episodeKey) {
        return episodeKey == null ? "" : episodeKey.trim();
    }

    /**
     * Muat sekali keadaan dari berkas persisten.
     *
     * <p>Dijalankan malas pada akses pertama, setelah {@code Application}
     * memasang penyimpanan. Tanpa ini, status kembali "belum diunduh" setiap
     * proses aplikasi dibunuh, padahal pekerjaan dan berkasnya masih ada.
     */
    private static synchronized void hydrate() {
        if (hydrated) return;
        hydrated = true;
        if (!id.shuyasal.kuromaku.offline.DownloadStateFile.installed()) return;
        for (Map.Entry<String, id.shuyasal.kuromaku.offline.DownloadStateFile.Row> row
                : id.shuyasal.kuromaku.offline.DownloadStateFile.all().entrySet()) {
            if (!row.getKey().startsWith(PREFIX)) continue;
            String episodeKey = row.getKey().substring(PREFIX.length());
            // Keadaan di memori selalu lebih baru daripada berkas: jangan
            // menimpanya dengan salinan lama saat hidrasi.
            if (STATES.containsKey(episodeKey)) continue;
            Entry entry = new Entry();
            entry.state = row.getValue().state;
            entry.percent = row.getValue().percent;
            entry.message = row.getValue().message;
            entry.quality = row.getValue().quality;
            entry.title = row.getValue().title;
            entry.subtitle = row.getValue().subtitle;
            entry.owner = row.getValue().owner;
            STATES.put(episodeKey, entry);
        }
    }

    private static void persist(String episodeKey, Entry entry) {
        id.shuyasal.kuromaku.offline.DownloadStateFile.put(PREFIX + key(episodeKey),
                entry.state, entry.percent, entry.message, entry.quality,
                entry.title, entry.subtitle);
        id.shuyasal.kuromaku.offline.DownloadStateFile.setOwner(PREFIX + key(episodeKey),
                entry.owner);
    }

    public static synchronized void set(String episodeKey, int state, int percent, String message) {
        set(episodeKey, state, percent, message, "");
    }

    /**
     * Seperti {@link #set(String, int, int, String)} sekaligus mencatat kualitas
     * yang dipilih, sehingga pembatalan bisa menunjuk pekerjaan yang tepat dan
     * label "Tersimpan · 720p" tidak menebak.
     */
    public static synchronized void set(String episodeKey, int state, int percent, String message,
                                       String quality) {
        if (episodeKey == null || episodeKey.isEmpty()) return;
        Entry entry = entry(episodeKey);
        hydrate();
        if (quality != null && !quality.isEmpty()) entry.quality = quality.trim();
        entry.state = state;
        entry.percent = Math.max(0, Math.min(100, percent));
        entry.message = message == null ? "" : message;
        persist(episodeKey, entry);
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
        persist(episodeKey, entry);
        notifyListeners();
    }

    /**
     * Simpan nama yang bisa dibaca pengguna untuk episode ini.
     *
     * <p>Dikirim sekali saat unduhan dijadwalkan; pembaruan progres berikutnya
     * mempertahankannya.
     */
    public static synchronized void describe(String episodeKey, String title, String subtitle) {
        describe(episodeKey, title, subtitle, "");
    }

    public static synchronized void describe(String episodeKey, String title, String subtitle,
                                             String owner) {
        String kunci = key(episodeKey);
        if (kunci.isEmpty()) return;
        hydrate();
        Entry entry = entry(kunci);
        if (title != null && !title.isEmpty()) entry.title = title;
        if (subtitle != null && !subtitle.isEmpty()) entry.subtitle = subtitle;
        if (owner != null && !owner.isEmpty()) entry.owner = owner;
        persist(kunci, entry);
        notifyListeners();
    }

    /**
     * Unduhan yang belum selesai: antre, berjalan, dijeda, atau gagal.
     *
     * <p>Yang sudah {@code SAVED} tidak dimasukkan — itu sudah tampil sebagai
     * berkas di layar Unduhan.
     */
    public static synchronized java.util.List<Active> active() {
        hydrate();
        java.util.List<Active> hasil = new java.util.ArrayList<>();
        for (Map.Entry<String, Entry> item : STATES.entrySet()) {
            Entry entry = item.getValue();
            if (entry == null) continue;
            // Hanya yang benar-benar berjalan. Yang gagal tetap terlihat di daftar
            // episode dengan tombol Coba Lagi, sehingga tidak menumpuk di sini.
            if (entry.state != QUEUED && entry.state != DOWNLOADING
                    && entry.state != VERIFYING && entry.state != PAUSED) {
                continue;
            }
            hasil.add(new Active(item.getKey(), entry));
        }
        return hasil;
    }

    public static synchronized String title(String episodeKey) {
        hydrate();
        Entry entry = STATES.get(key(episodeKey));
        return entry == null ? "" : entry.title;
    }

    public static synchronized String subtitle(String episodeKey) {
        hydrate();
        Entry entry = STATES.get(key(episodeKey));
        return entry == null ? "" : entry.subtitle;
    }

    public static synchronized void clear(String episodeKey) {
        STATES.remove(key(episodeKey));
        id.shuyasal.kuromaku.offline.DownloadStateFile.remove(PREFIX + key(episodeKey));
        notifyListeners();
    }

    public static synchronized int state(String episodeKey) {
        hydrate();
        Entry entry = STATES.get(key(episodeKey));
        return entry == null ? NOT_DOWNLOADED : entry.state;
    }

    public static synchronized int percent(String episodeKey) {
        hydrate();
        Entry entry = STATES.get(key(episodeKey));
        return entry == null ? 0 : entry.percent;
    }

    public static synchronized long bytes(String episodeKey) {
        hydrate();
        Entry entry = STATES.get(key(episodeKey));
        return entry == null ? 0L : entry.bytes;
    }

    public static synchronized long total(String episodeKey) {
        Entry entry = STATES.get(key(episodeKey));
        return entry == null ? 0L : entry.total;
    }

    /** Kualitas yang sedang/terakhir diunduh untuk episode ini ("" bila belum). */
    public static synchronized String quality(String episodeKey) {
        hydrate();
        Entry entry = STATES.get(key(episodeKey));
        return entry == null ? "" : entry.quality;
    }

    public static synchronized String message(String episodeKey) {
        hydrate();
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

    /**
     * Lupakan keadaan di memori tanpa menyentuh berkas.
     *
     * <p>Meniru proses aplikasi yang mati: dipakai pengujian untuk membuktikan
     * status benar-benar dibaca ulang dari penyimpanan.
     */
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