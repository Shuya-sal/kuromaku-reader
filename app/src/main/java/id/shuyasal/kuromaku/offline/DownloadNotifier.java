package id.shuyasal.kuromaku.offline;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/**
 * Progress notification for volume downloads.
 *
 * <p>Android requires a visible, cancellable signal for work that runs while the
 * user is elsewhere, and the volume queue can easily outlive the screen. The
 * notification is updated from the worker's own progress — never by polling —
 * and it is dismissed as soon as the batch ends. On Android 13+ posting silently
 * does nothing until the user grants notification permission, which is why the
 * download itself never depends on this class.
 */
public final class DownloadNotifier {

    private static final String CHANNEL_ID = "kuromaku_downloads";
    private static final int NOTIFICATION_ID = 4211;

    private DownloadNotifier() {
    }

    static synchronized void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return;

        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "Unduhan offline", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Progres mengunduh chapter untuk dibaca tanpa internet.");
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    /** Shows or updates the progress notification; a no-op when not permitted. */
    public static void progress(Context context, String novelTitle, int finished, int total) {
        if (context == null) return;
        try {
            ensureChannel(context);
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return;

            int max = Math.max(1, total);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle("Mengunduh " + (novelTitle == null ? "chapter" : novelTitle))
                    .setContentText(finished + " dari " + total + " chapter tersimpan")
                    .setProgress(max, Math.min(finished, max), false)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW);
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build());
        } catch (Exception ignored) {
            // A missing permission or disabled channel must never break a download.
        }
    }

    public static void done(Context context, String novelTitle, int saved, int total) {
        if (context == null) return;
        try {
            ensureChannel(context);
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return;

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("Unduhan selesai")
                    .setContentText(saved + " dari " + total + " chapter siap dibaca offline")
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW);
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build());
        } catch (Exception ignored) {
            // Same as above.
        }
    }

    public static void cancel(Context context) {
        if (context == null) return;
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID);
        } catch (Exception ignored) {
            // Nothing to clear.
        }
    }
}
