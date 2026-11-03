package id.shuyasal.kuromaku.offline;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import id.shuyasal.kuromaku.data.Chapter;

/**
 * The actual sequential loop of a volume download.
 *
 * <p>Kept out of {@link DownloadWorker} so the behaviour that matters — skip what
 * is already stored, stop on cancel, keep every finished chapter — can be tested
 * without a WorkManager harness. The worker's only job is to wrap this in
 * WorkManager's retry/backoff.
 */
public final class VolumeJob {

    private static final String TAG = "KuromakuDownload";

    /** Called after each chapter reaches a terminal state. */
    public interface Progress {
        void onChapter(int finished, int total);
    }

    /** Outcome of one run. */
    public static final class Result {
        public final int finished;
        public final int total;
        public final boolean cancelled;
        public final List<String> failures;

        Result(int finished, int total, boolean cancelled, List<String> failures) {
            this.finished = finished;
            this.total = total;
            this.cancelled = cancelled;
            this.failures = failures;
        }

        public boolean allDone() {
            return !cancelled && failures.isEmpty() && finished >= total;
        }
    }

    private VolumeJob() {
    }

    /**
     * Downloads the given chapters in order, skipping any that are already fully
     * stored. A cancellation stops the loop immediately; chapters finished before
     * that point stay saved, which is what makes "Batalkan" safe and resumable.
     */
    public static Result run(Context context, String novelId, int volume,
                             List<Chapter> chapters,
                             ChapterDownloader.Cancellation cancellation,
                             Progress progress) {
        OfflineStore store = OfflineStore.get(context);
        int total = chapters == null ? 0 : chapters.size();
        int finished = 0;
        List<String> failures = new ArrayList<>();
        boolean cancelled = false;

        for (int i = 0; i < total; i++) {
            Chapter chapter = chapters.get(i);
            if (cancellation != null && cancellation.isCancelled()) {
                cancelled = true;
                break;
            }
            String key = DownloadStatusStore.key(novelId, chapter.url);

            // Already complete: counted as done and never fetched again.
            if (store.isSaved(novelId, chapter.url)) {
                DownloadStatusStore.set(key, DownloadStatusStore.SAVED, null);
                finished++;
                report(progress, finished, total);
                continue;
            }

            DownloadStatusStore.set(key, DownloadStatusStore.DOWNLOADING, null);
            try {
                ChapterDownloader.download(context, novelId, chapter.url, volume,
                        chapter.label, cancellation);
                DownloadStatusStore.set(key, DownloadStatusStore.SAVED, null);
                finished++;
            } catch (ChapterDownloader.CancelledException stopped) {
                DownloadStatusStore.set(key, DownloadStatusStore.NOT_DOWNLOADED, null);
                cancelled = true;
                break;
            } catch (Exception error) {
                String reason = error.getMessage() == null
                        ? error.getClass().getSimpleName() : error.getMessage();
                Log.w(TAG, "gagal mengunduh " + chapter.url + ": " + reason);
                failures.add(reason);
                DownloadStatusStore.set(key, DownloadStatusStore.FAILED, reason);
            }
            report(progress, finished, total);
        }

        DownloadStatusStore.setProgress(novelId, volume, finished, total);
        return new Result(finished, total, cancelled, failures);
    }

    private static void report(Progress progress, int finished, int total) {
        if (progress != null) progress.onChapter(finished, total);
    }
}
