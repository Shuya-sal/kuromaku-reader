package id.shuyasal.kuromaku.offline;

import android.content.Context;

import androidx.work.WorkManager;

import java.util.ArrayList;
import java.util.List;

import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.Novel;

/**
 * Single entry point the UI uses to download, retry, cancel and inspect offline
 * chapters.
 *
 * <p>The actual work is handed to {@link DownloadWorker}, so a queue survives the
 * activity being destroyed or the app being closed. This class never fetches
 * anything itself on the calling thread — every method returns immediately.
 */
public final class DownloadQueue {

    private DownloadQueue() {
    }

    /** Queues one chapter for a background download. */
    public static void enqueueChapter(Context context, Novel novel, Chapter chapter) {
        if (context == null || novel == null || chapter == null) return;
        String key = DownloadStatusStore.key(novel.id, chapter.url);
        DownloadStatusStore.set(key, DownloadStatusStore.QUEUED, null);

        List<Chapter> one = new ArrayList<>();
        one.add(chapter);
        WorkManager manager = DownloadWorker.manager(context);
        manager.enqueueUniqueWork(
                DownloadWorker.chapterWorkName(novel.id, chapter.url),
                androidx.work.ExistingWorkPolicy.KEEP,
                DownloadWorker.request(DownloadWorker.input(novel.id, chapter.volume, one)));
    }

    /**
     * Queues every chapter of a volume that is not stored yet.
     *
     * <p>Chapters already complete on disk are left out of the job entirely, so
     * "Download Semua" twice does not refetch anything, and a resumed volume only
     * spends requests on what is still missing.
     */
    public static int enqueueVolume(Context context, Novel novel, int volume,
                                    List<Chapter> chapters) {
        if (context == null || novel == null || chapters == null) return 0;
        OfflineStore store = OfflineStore.get(context);

        List<Chapter> missing = new ArrayList<>();
        for (Chapter chapter : chapters) {
            if (chapter == null || chapter.url == null || chapter.url.isEmpty()) continue;
            if (store.isSaved(novel.id, chapter.url)) {
                DownloadStatusStore.set(DownloadStatusStore.key(novel.id, chapter.url),
                        DownloadStatusStore.SAVED, null);
                continue;
            }
            missing.add(chapter);
            DownloadStatusStore.set(DownloadStatusStore.key(novel.id, chapter.url),
                    DownloadStatusStore.QUEUED, null);
        }
        if (missing.isEmpty()) {
            DownloadStatusStore.setProgress(novel.id, volume, chapters.size(), chapters.size());
            return 0;
        }

        DownloadStatusStore.setProgress(novel.id, volume, chapters.size() - missing.size(),
                chapters.size());
        DownloadWorker.manager(context).enqueueUniqueWork(
                DownloadWorker.workName(novel.id, volume),
                androidx.work.ExistingWorkPolicy.KEEP,
                DownloadWorker.request(DownloadWorker.input(novel.id, volume, missing)));
        return missing.size();
    }

    /** Stops a running volume job; chapters already finished stay saved. */
    public static void cancelVolume(Context context, Novel novel, int volume) {
        if (context == null || novel == null) return;
        WorkManager manager = DownloadWorker.manager(context);
        manager.cancelUniqueWork(DownloadWorker.workName(novel.id, volume));
        DownloadStatusStore.clearProgress(novel.id, volume);
        DownloadNotifier.cancel(context);
    }

    public static void cancelChapter(Context context, Novel novel, Chapter chapter) {
        if (context == null || novel == null || chapter == null) return;
        DownloadWorker.manager(context).cancelUniqueWork(
                DownloadWorker.chapterWorkName(novel.id, chapter.url));
        DownloadStatusStore.set(DownloadStatusStore.key(novel.id, chapter.url),
                DownloadStatusStore.CANCELLED, null);
    }

    /** Status shown next to one chapter in the list. */
    public static int state(Context context, Novel novel, Chapter chapter) {
        return DownloadStatusStore.resolve(novel.id, chapter.url, OfflineStore.get(context));
    }

    public static String statusLabel(Context context, Novel novel, Chapter chapter) {
        int state = state(context, novel, chapter);
        switch (state) {
            case DownloadStatusStore.QUEUED:
                return "Menunggu";
            case DownloadStatusStore.DOWNLOADING:
                return "Mengunduh…";
            case DownloadStatusStore.VERIFYING:
                return "Memverifikasi…";
            case DownloadStatusStore.SAVED:
                return "Tersimpan";
            case DownloadStatusStore.PARTIAL:
                return "Tersimpan sebagian";
            case DownloadStatusStore.FAILED:
                return "Gagal";
            case DownloadStatusStore.CANCELLED:
                return "Dibatalkan";
            default:
                return "Belum diunduh";
        }
    }

    /**
     * Human note for a partial chapter: how much artwork is still missing.
     *
     * <p>Only printed when the stored index actually knows a picture is owed, so
     * the page never claims "sebagian" about a chapter that is whole.
     */
    public static String partialNote(Context context, Novel novel, Chapter chapter) {
        if (context == null || novel == null || chapter == null) return "";
        OfflineStore.Entry entry = OfflineStore.get(context).entry(novel.id, chapter.url);
        if (entry == null || entry.complete()) return "";
        return entry.missingImages() + " ilustrasi belum tersimpan";
    }

    /** How many chapters of a volume are already stored. */
    public static int savedInVolume(Context context, Novel novel, int volume) {
        return OfflineStore.get(context).savedCountInVolume(novel.id, volume);
    }

    /** [finished, total] for a volume job, or null when nothing runs. */
    public static int[] volumeProgress(Context context, Novel novel, int volume,
                                       List<Chapter> chapters) {
        int[] live = DownloadStatusStore.progress(novel.id, volume);
        if (live[1] > 0) return live;
        int total = chapters == null ? 0 : chapters.size();
        int saved = savedInVolume(context, novel, volume);
        return total == 0 ? null : new int[]{saved, total};
    }

    /** Removes one chapter's offline copy. Reading progress is untouched. */
    public static void deleteChapter(Context context, Novel novel, Chapter chapter) {
        if (context == null || novel == null || chapter == null) return;
        OfflineStore.get(context).deleteChapter(novel.id, chapter.url);
        DownloadStatusStore.clear(DownloadStatusStore.key(novel.id, chapter.url));
    }

    public static int deleteVolume(Context context, Novel novel, int volume) {
        if (context == null || novel == null) return 0;
        int removed = OfflineStore.get(context).deleteVolume(novel.id, volume);
        DownloadStatusStore.clearProgress(novel.id, volume);
        DownloadNotifier.cancel(context);
        return removed;
    }

    public static void deleteNovel(Context context, Novel novel) {
        if (context == null || novel == null) return;
        OfflineStore.get(context).deleteNovel(novel.id);
    }
}
