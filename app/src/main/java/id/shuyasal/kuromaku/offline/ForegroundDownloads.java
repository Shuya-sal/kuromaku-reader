package id.shuyasal.kuromaku.offline;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.ForegroundInfo;

/**
 * Notifikasi progres untuk pekerjaan unduhan yang berjalan sebagai foreground
 * worker.
 *
 * <p>Sejak Android 8 pekerjaan latar yang panjang harus terlihat oleh pengguna;
 * sejak Android 12 pekerjaan itu juga dibatasi waktunya bila tidak dijalankan
 * sebagai foreground service. Karena itu unduhan besar (episode video, chapter
 * manga, volume novel) memakai {@link ForegroundInfo} dengan notifikasi progres
 * yang punya aksi <b>Batal</b>.
 *
 * <p>Notifikasi ini pelengkap, bukan syarat: bila izin notifikasi ditolak,
 * unduhan tetap berjalan dan statusnya tetap terbaca di aplikasi.
 */
public final class ForegroundDownloads {

    public static final String CHANNEL_ID = "kuromaku_downloads_foreground";
    public static final String ACTION_CANCEL = "id.shuyasal.kuromaku.DOWNLOAD_CANCEL";
    public static final String EXTRA_TAG = "work_tag";
    public static final String EXTRA_TITLE = "work_title";

    private ForegroundDownloads() {
    }

    /**
     * Tipe foreground service untuk unduhan.
     *
     * <p><b>Wajib eksplisit.</b> Konstruktor {@code ForegroundInfo(id, notifikasi)}
     * memberi tipe {@code FOREGROUND_SERVICE_TYPE_NONE}. Android 16 (API 36)
     * melarang memulai foreground service bertipe {@code none} untuk aplikasi
     * dengan {@code targetSdk >= 35}:
     *
     * <pre>
     * android.app.InvalidForegroundServiceTypeException:
     *   Starting FGS with type none ... targetSDK=35 has been prohibited
     *     at android.app.Service.startForeground
     *     at SystemForegroundService$Api31Impl.startForeground
     * </pre>
     *
     * <p>Akibatnya proses mati seketika, tepat saat unduhan mulai — di ketiga
     * modul, karena semuanya memakai jalur notifikasi ini. Tipe harus sama
     * dengan yang dideklarasikan di manifest ({@code dataSync}).
     */
    public static final int SERVICE_TYPE =
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;

    /** ForegroundInfo siap pakai untuk WorkManager, dengan tipe yang sah. */
    public static ForegroundInfo info(Context context, int id, String title, String text,
                                      int percent, String cancelTag) {
        return new ForegroundInfo(id, build(context, id, title, text, percent, cancelTag),
                SERVICE_TYPE);
    }

    /** Perbarui notifikasi yang sama tanpa membuat yang baru. */
    public static void update(Context context, int id, String title, String text, int percent,
                              String cancelTag) {
        if (context == null) return;
        try {
            ensureChannel(context);
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return;
            NotificationManagerCompat.from(context)
                    .notify(id, build(context, id, title, text, percent, cancelTag));
        } catch (RuntimeException ignored) {
            // Izin ditolak atau kanal dimatikan: unduhan tidak boleh ikut gagal.
        }
    }

    /** Hapus notifikasi setelah pekerjaan selesai atau dibatalkan. */
    public static void clear(Context context, int id) {
        if (context == null) return;
        try {
            NotificationManagerCompat.from(context).cancel(id);
        } catch (RuntimeException ignored) {
            // tidak ada yang perlu dilakukan
        }
    }

    private static Notification build(Context context, int id, String title, String text,
                                      int percent, String cancelTag) {
        ensureChannel(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title == null ? "Mengunduh" : title)
                .setContentText(text == null ? "" : text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS);

        if (percent >= 0) {
            builder.setProgress(100, Math.max(0, Math.min(100, percent)), false);
        } else {
            // Tanpa total yang diketahui, spinner lebih jujur daripada persen palsu.
            builder.setProgress(0, 0, true);
        }

        if (cancelTag != null && !cancelTag.isEmpty()) {
            Intent cancel = new Intent(context, DownloadCancelReceiver.class)
                    .setAction(ACTION_CANCEL)
                    .putExtra(EXTRA_TAG, cancelTag)
                    .putExtra(EXTRA_TITLE, title);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pending = PendingIntent.getBroadcast(context, id, cancel, flags);
            builder.addAction(0, "Batal", pending);
        }
        return builder.build();
    }

    private static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "Unduhan berjalan", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Progres unduhan yang berjalan di latar belakang.");
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }
}