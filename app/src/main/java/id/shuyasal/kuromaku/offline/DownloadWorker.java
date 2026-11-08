package id.shuyasal.kuromaku.offline;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import id.shuyasal.kuromaku.data.Chapter;

/**
 * Background worker that stores one chapter, or a whole volume chapter by chapter.
 *
 * <p>WorkManager owns the job, so a download survives the app being closed and is
 * retried with exponential backoff when the source is unreachable. Only one
 * batch runs per novel at a time (unique work name = novel + volume), and the
 * downloader itself caps simultaneous requests, so the source blog is never
 * flooded.
 *
 * <p>Every chapter is committed on its own: stopping halfway leaves the chapters
 * that already finished saved, and resuming skips them because the store is
 * checked before each fetch. Nothing is ever downloaded twice.
 */
public class DownloadWorker extends Worker {

    public static final String KEY_NOVEL = "novel_id";
    public static final String KEY_VOLUME = "volume";
    public static final String KEY_URLS = "urls";
    public static final String KEY_LABELS = "labels";

    private static final String TAG = "KuromakuDownload";

    public DownloadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            return runJob();
        } catch (Throwable error) {
            // Kegagalan apa pun di sini harus berakhir sebagai status pekerjaan,
            // bukan sebagai kematian proses: satu chapter yang rusak tidak boleh
            // membuat aplikasi tidak bisa dibuka.
            Log.e(TAG, "unduhan novel gagal tak terduga", error);
            return Result.failure();
        }
    }

    private Result runJob() {
        String novelId = getInputData().getString(KEY_NOVEL);
        int volume = getInputData().getInt(KEY_VOLUME, 0);
        List<String> urls = readList(KEY_URLS);
        List<String> labels = readList(KEY_LABELS);
        if (novelId == null || urls.isEmpty()) return Result.failure();

        final String guardKey = "novel|" + novelId + "|" + volume;
        if (id.shuyasal.kuromaku.offline.DownloadCrashGuard
                .isSuspect(getApplicationContext(), guardKey)) {
            return Result.failure();
        }
        id.shuyasal.kuromaku.offline.DownloadCrashGuard
                .begin(getApplicationContext(), guardKey);

        List<Chapter> chapters = new ArrayList<>();
        for (int i = 0; i < urls.size(); i++) {
            String label = i < labels.size() ? labels.get(i) : "";
            chapters.add(new Chapter(label, label, urls.get(i), "", i, volume));
        }

        id.shuyasal.kuromaku.data.Novel known =
                id.shuyasal.kuromaku.data.Library.get(getApplicationContext()).byId(novelId);
        final String title = known == null ? "Novel" : known.shortTitle();
        // Foreground: satu volume bisa berisi puluhan chapter dan berjalan lama.
        final int notificationId = 5500 + Math.abs(novelId.hashCode() % 300);
        try {
            setForegroundAsync(ForegroundDownloads.info(getApplicationContext(), notificationId,
                    "Mengunduh " + title, "Menyiapkan chapter", -1, "novel-" + novelId));
        } catch (RuntimeException error) {
            Log.w(TAG, "notifikasi progres tidak bisa ditampilkan", error);
        }

        if (!id.shuyasal.kuromaku.offline.DownloadGate.acquire()) {
            return Result.retry();
        }
        VolumeJob.Result result;
        try {
            result = VolumeJob.run(getApplicationContext(), novelId, volume, chapters,
                this::isStopped, (finished, total) -> {
                    setProgressAsync(new Data.Builder()
                            .putInt("finished", finished)
                            .putInt("total", total)
                            .build());
                    // Updated from the worker's own progress, never by polling:
                    // Android requires a visible signal for work that outlives
                    // the screen.
                    DownloadNotifier.progress(getApplicationContext(), title, finished, total);
                    ForegroundDownloads.update(getApplicationContext(), notificationId,
                            "Mengunduh " + title,
                            finished + " dari " + total + " chapter",
                            total <= 0 ? -1 : finished * 100 / total,
                            "novel-" + novelId);
                });
        } finally {
            id.shuyasal.kuromaku.offline.DownloadGate.release();
            id.shuyasal.kuromaku.offline.DownloadCrashGuard.end(getApplicationContext());
        }

        if (result.cancelled) {
            DownloadStatusStore.clearProgress(novelId, volume);
            DownloadNotifier.cancel(getApplicationContext());
            return Result.failure();
        }
        if (result.hasPartial()) {
            // Text is on disk; artwork is not. Retrying the whole job would refetch
            // the chapters that are only missing a picture, which is exactly what
            // "Lanjutkan Download" is for — so the job ends here and the UI offers it.
            DownloadStatusStore.setProgress(novelId, volume, result.finished, result.total);
            DownloadNotifier.done(getApplicationContext(), title, result.finished, result.total);
            return Result.failure();
        }
        if (!result.failures.isEmpty()) {
            // Retry with backoff; chapters that already succeeded are skipped on
            // the next run, so only what is missing is requested again.
            if (getRunAttemptCount() < 1) return Result.retry();
            DownloadNotifier.done(getApplicationContext(), title, result.finished, result.total);
            return Result.failure();
        }
        DownloadNotifier.done(getApplicationContext(), title, result.finished, result.total);
        return Result.success();
    }

    private List<String> readList(String key) {
        List<String> values = new ArrayList<>();
        String raw = getInputData().getString(key);
        if (raw == null || raw.isEmpty()) return values;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) values.add(array.optString(i, ""));
        } catch (Exception error) {
            Log.w(TAG, "input kerja rusak: " + key, error);
        }
        return values;
    }

    /** Serialises chapter urls (and labels) for one job. */
    public static Data input(String novelId, int volume, List<Chapter> chapters) {
        JSONArray urls = new JSONArray();
        JSONArray labels = new JSONArray();
        for (Chapter chapter : chapters) {
            urls.put(chapter.url);
            labels.put(chapter.label);
        }
        return new Data.Builder()
                .putString(KEY_NOVEL, novelId)
                .putInt(KEY_VOLUME, volume)
                .putString(KEY_URLS, urls.toString())
                .putString(KEY_LABELS, labels.toString())
                .build();
    }

    /** Request for one job with exponential backoff, capped by WorkManager. */
    public static OneTimeWorkRequest request(Data input) {
        return new OneTimeWorkRequest.Builder(DownloadWorker.class)
                .setInputData(input)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
    }

    /** Name of the work that owns this chapter's queue, used for cancellation. */
    public static String workName(String novelId, int volume) {
        return "kuromaku-download-" + novelId + "-v" + volume;
    }

    /** Name of the unique job for one chapter. */
    public static String chapterWorkName(String novelId, String url) {
        return "kuromaku-download-chapter-" + novelId + "-" + OfflineStore.keyFor(novelId, url);
    }

    /** Manager accessor so the UI never constructs it inconsistently. */
    public static WorkManager manager(Context context) {
        return WorkManager.getInstance(context.getApplicationContext());
    }
}
