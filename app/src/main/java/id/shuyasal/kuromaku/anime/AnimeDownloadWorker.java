package id.shuyasal.kuromaku.anime;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/**
 * Worker unduhan satu episode anime.
 *
 * <p>Worker ini hanya menyiapkan masukan, memeriksa izin, memanggil
 * {@link AnimeDownloadFetcher}, lalu menerjemahkan hasilnya menjadi status
 * WorkManager. Seluruh aturan pengunduhan ada di fetcher supaya bisa diuji
 * terhadap server sungguhan.
 */
public class AnimeDownloadWorker extends Worker {

    public static final String KEY_ANIME_ID = "anime_id";
    public static final String KEY_ANIME_TITLE = "anime_title";
    public static final String KEY_POSTER = "poster";
    public static final String KEY_EPISODE_LABEL = "episode_label";
    public static final String KEY_EPISODE_URL = "episode_url";
    public static final String KEY_EPISODE_KEY = "episode_key";
    public static final String KEY_QUALITY = "quality";
    public static final String KEY_SERVER = "server";
    public static final String KEY_URL = "url";
    public static final String KEY_KIND = "kind";

    private static final String TAG = "AnimeDownload";

    public AnimeDownloadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override public Result doWork() {
        Context context = getApplicationContext();
        AnimeDownloadFetcher.Params params = new AnimeDownloadFetcher.Params(
                text(KEY_ANIME_ID), text(KEY_ANIME_TITLE), text(KEY_POSTER),
                text(KEY_EPISODE_LABEL), text(KEY_EPISODE_URL), text(KEY_EPISODE_KEY),
                text(KEY_QUALITY), text(KEY_SERVER), text(KEY_URL), text(KEY_KIND));

        if (params.episodeKey.isEmpty()) {
            Log.w(TAG, "unduhan tanpa penanda episode dilewati");
            return Result.failure();
        }
        if (!AnimeDownloadPolicy.allowed(context)) {
            AnimeDownloadStatus.set(params.episodeKey, AnimeDownloadStatus.FAILED, 0,
                    "Unduhan offline belum diizinkan.");
            return Result.failure();
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();

        AnimeDownloadFetcher.Result result = AnimeDownloadFetcher.run(context, params, client,
                this::isStopped);

        switch (result.outcome) {
            case SAVED:
                AnimeDownloadStatus.set(params.episodeKey, AnimeDownloadStatus.SAVED, 100,
                        "Tersimpan offline");
                return Result.success();
            case INCOMPLETE:
                AnimeDownloadStatus.set(params.episodeKey, AnimeDownloadStatus.PAUSED,
                        percent(result), result.message);
                // Dilanjutkan otomatis bila sistem mengizinkan.
                return Result.retry();
            default:
                AnimeDownloadStatus.set(params.episodeKey, AnimeDownloadStatus.FAILED,
                        percent(result), result.message);
                return Result.failure();
        }
    }

    private static int percent(AnimeDownloadFetcher.Result result) {
        if (result.total <= 0) return 0;
        return (int) Math.max(0L, Math.min(100L, result.bytes * 100L / result.total));
    }

    private String text(String key) {
        String value = getInputData().getString(key);
        return value == null ? "" : value;
    }
}