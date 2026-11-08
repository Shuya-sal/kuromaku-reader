package id.shuyasal.kuromaku.shinigami;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Worker unduhan Shinigami: satu chapter per work, berurutan per halaman.
 *
 * <p>Aturan: lewati halaman yang file-nya sudah lengkap dan valid (resume),
 * unduh yang belum, validasi tiap gambar sebelum commit, dan hanya tandai
 * {@code Tersimpan offline} bila SEMUA halaman lolos validasi. Batalkan
 * berhenti di halaman berjalan; halaman yang sudah lengkap tetap tersimpan.
 * Tidak menyentuh progres baca. Tidak melewati autentikasi/DRM/CAPTCHA:
 * respons 401/403/429 langsung gagal dengan pesan jelas.
 */
public class ShinigamiDownloadWorker extends Worker {
    private static final String TAG = "ShinigamiDownload";

    public ShinigamiDownloadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /**
     * ForegroundInfo untuk WorkManager.
     *
     * <p>Bawaan {@code Worker} melempar {@code IllegalStateException} bila
     * WorkManager perlu mempromosikan pekerjaan ini di luar {@code doWork()},
     * misalnya saat pekerjaan dijalankan ulang setelah proses dibunuh sistem.
     */
    @NonNull
    @Override
    public com.google.common.util.concurrent.ListenableFuture<androidx.work.ForegroundInfo>
            getForegroundInfoAsync() {
        List<String> chapters = chapters();
        String kunci = chapters.isEmpty() ? "" : chapters.get(0);
        int notificationId = 5300 + Math.abs(kunci.hashCode() % 400);
        androidx.work.impl.utils.futures.SettableFuture<androidx.work.ForegroundInfo> future =
                androidx.work.impl.utils.futures.SettableFuture.create();
        future.set(id.shuyasal.kuromaku.offline.ForegroundDownloads.info(
                getApplicationContext(), notificationId, "Mengunduh chapter manga",
                "Melanjutkan unduhan", -1, ShinigamiDownloadQueue.workName(kunci)));
        return future;
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            return runJob();
        } catch (Throwable error) {
            // Satu chapter manga yang gagal tidak boleh mematikan proses.
            Log.e(TAG, "unduhan manga gagal tak terduga", error);
            return Result.failure(failData("Unduhan gagal. Silakan coba lagi."));
        }
    }

    private Result runJob() {
        if (!ShinigamiDownloadPolicy.allowed(getApplicationContext())) {
            return Result.failure(failData("Unduhan menunggu persetujuan pengguna. "
                    + "Buka halaman manga lalu tekan Unduh untuk menyetujui."));
        }
        List<String> chapters = chapters();
        if (chapters.isEmpty()) return Result.failure(failData("Chapter tidak dikenal."));

        // Chapter berisi puluhan gambar: jadikan foreground supaya tidak
        // dimatikan sistem saat pengguna menekan Home atau pindah aplikasi.
        foregroundId = 5300 + Math.abs(chapters.get(0).hashCode() % 400);
        foregroundTag = ShinigamiDownloadQueue.workName(chapters.get(0));
        try {
            setForegroundAsync(id.shuyasal.kuromaku.offline.ForegroundDownloads.info(
                    getApplicationContext(), foregroundId, "Mengunduh chapter manga",
                    "Menyiapkan halaman", -1, foregroundTag));
        } catch (RuntimeException error) {
            Log.w(TAG, "notifikasi progres tidak bisa ditampilkan", error);
        }
        final String guardKey = "shinigami|" + chapters.get(0);
        if (id.shuyasal.kuromaku.offline.DownloadCrashGuard
                .isSuspect(getApplicationContext(), guardKey)) {
            return Result.failure(failData("Unduhan sebelumnya terhenti. Silakan coba lagi."));
        }
        id.shuyasal.kuromaku.offline.DownloadCrashGuard
                .begin(getApplicationContext(), guardKey);
        if (!id.shuyasal.kuromaku.offline.DownloadGate.acquire()) {
            id.shuyasal.kuromaku.offline.DownloadCrashGuard.end(getApplicationContext());
            return Result.retry();
        }
        boolean anyRetry = false;
        try {
        for (String chapterId : chapters) {
            if (isStopped()) {
                ShinigamiDownloadStatus.set(chapterId, ShinigamiDownloadStatus.CANCELLED,
                        ShinigamiDownloadStatus.percent(chapterId), "");
                return Result.failure(failData("Unduhan dibatalkan."));
            }
            Result outcome = downloadOne(chapterId);
            if (outcome == Result.retry()) anyRetry = true;
            if (outcome == Result.failure()) {
                // Lanjut ke chapter berikutnya; status gagal sudah tercatat per chapter.
            }
        }
        } finally {
            id.shuyasal.kuromaku.offline.DownloadGate.release();
            id.shuyasal.kuromaku.offline.DownloadCrashGuard.end(getApplicationContext());
        }
        return anyRetry ? Result.retry() : Result.success();
    }

    private Result downloadOne(String chapterId) {
        ShinigamiStore store = new ShinigamiStore(getApplicationContext());
        ShinigamiDownloader local = new ShinigamiDownloader(getApplicationContext());
        List<String> urls = store.chapterUrls(chapterId);
        int expected = store.expectedPages(chapterId);
        if (urls.isEmpty() || expected <= 0 || urls.size() != expected) {
            try {
                urls = new ShinigamiSource(getApplicationContext()).chapterImages(chapterId).urls;
                expected = urls.size();
            } catch (ShinigamiSource.SourceException error) {
                ShinigamiDownloadStatus.set(chapterId, ShinigamiDownloadStatus.FAILED, 0,
                        safeMessage(error.getMessage()));
                return Result.failure();
            }
        }
        int total = urls.size();
        ShinigamiDownloadStatus.set(chapterId, ShinigamiDownloadStatus.DOWNLOADING,
                savedCount(local, chapterId, total) * 100 / Math.max(1, total), "");
        OkHttpClient client = client();
        String referer = new ShinigamiConfig(getApplicationContext()).siteBase() + "/";
        for (int index = 0; index < total; index++) {
            if (isStopped()) {
                ShinigamiDownloadStatus.set(chapterId, ShinigamiDownloadStatus.CANCELLED,
                        percentDone(local, chapterId, total), "");
                return Result.failure();
            }
            if (local.pageFile(chapterId, index) != null) {
                setProgressAsync(progress(chapterId, local, total));
                continue;
            }
            String url = urls.get(index);
            if (!ShinigamiConfig.isValidResource(url)) {
                ShinigamiDownloadStatus.set(chapterId, ShinigamiDownloadStatus.FAILED,
                        percentDone(local, chapterId, total), "Alamat gambar ditolak: bukan HTTPS/host sumber.");
                return Result.failure();
            }
            try {
                fetchToFile(client, referer, url, target(local, chapterId, index));
            } catch (Retryable rue) {
                ShinigamiDownloadStatus.set(chapterId, ShinigamiDownloadStatus.FAILED,
                        percentDone(local, chapterId, total), safeMessage(rue.getMessage()));
                return Result.retry();
            } catch (Exception error) {
                ShinigamiDownloadStatus.set(chapterId, ShinigamiDownloadStatus.FAILED,
                        percentDone(local, chapterId, total), safeMessage(error.getMessage()));
                Log.w(TAG, "gagal mengunduh halaman chapter", error);
                return Result.failure();
            }
            if (local.pageFile(chapterId, index) == null) {
                ShinigamiDownloadStatus.set(chapterId, ShinigamiDownloadStatus.FAILED,
                        percentDone(local, chapterId, total), "Gambar tidak lolos validasi.");
                return Result.failure();
            }
            ShinigamiDownloadStatus.set(chapterId, ShinigamiDownloadStatus.DOWNLOADING,
                    percentDone(local, chapterId, total), "");
            setProgressAsync(progress(chapterId, local, total));
            notifyProgress(chapterId, index + 1, total);
        }
        ShinigamiDownloadStatus.set(chapterId, ShinigamiDownloadStatus.VERIFYING, 100, "");
        if (local.isDownloaded(chapterId, total)) {
            ShinigamiDownloadStatus.set(chapterId, ShinigamiDownloadStatus.SAVED, 100, "");
            return Result.success();
        }
        ShinigamiDownloadStatus.set(chapterId, ShinigamiDownloadStatus.FAILED, percentDone(local, chapterId, total),
                "Sebagian halaman belum lengkap.");
        return Result.failure();
    }

    private void fetchToFile(OkHttpClient client, String referer, String url, File target) throws Exception {
        Request request = new Request.Builder().url(url)
                .header("User-Agent", id.shuyasal.kuromaku.net.Http.USER_AGENT)
                .header("Referer", referer)
                .header("Accept", "image/webp,image/*;q=0.8").build();
        try (Response response = client.newCall(request).execute()) {
            int code = response.code();
            if (code == 429) throw new Retryable("Batas permintaan sumber tercapai (HTTP 429).");
            if (code == 401 || code == 403) {
                throw new Exception("Sumber membatasi akses (HTTP " + code + "). Unduhan tidak dilewati.");
            }
            if (code < 200 || code >= 300) throw new Exception("Gambar gagal dimuat (HTTP " + code + ").");
            ResponseBody body = response.body();
            if (body == null) throw new Exception("Respons gambar kosong.");
            if (body.contentLength() > 32L * 1024 * 1024) throw new Exception("Gambar terlalu besar.");
            try (InputStream in = body.byteStream()) {
                ShinigamiPageStore.save(in, target, code, response.header("Content-Type"));
            }
        } catch (InterruptedException stopped) {
            throw new Exception("Unduhan dibatalkan.");
        }
    }

    /** Perbarui notifikasi foreground untuk halaman yang baru selesai. */
    private void notifyProgress(String chapterId, int halaman, int total) {
        if (foregroundId <= 0) return;
        int persen = total <= 0 ? -1 : Math.max(0, Math.min(100, halaman * 100 / total));
        id.shuyasal.kuromaku.offline.ForegroundDownloads.update(getApplicationContext(),
                foregroundId, "Mengunduh chapter manga",
                halaman + " dari " + total + " halaman", persen, foregroundTag);
    }

    @Override public void onStopped() {
        super.onStopped();
        if (foregroundId > 0) {
            id.shuyasal.kuromaku.offline.ForegroundDownloads.clear(getApplicationContext(),
                    foregroundId);
        }
    }

    /** Id notifikasi foreground dan tag pembatalannya, dipasang di doWork. */
    private int foregroundId;
    private String foregroundTag = "";

    private List<String> chapters() {
        String raw = getInputData().getString(ShinigamiDownloadQueue.KEY_CHAPTERS);
        List<String> ids = new ArrayList<>();
        if (raw == null) return ids;
        for (String part : raw.split("\n")) {
            String id = part == null ? "" : part.trim();
            if (!id.isEmpty()) ids.add(id);
        }
        return ids;
    }

    private static int savedCount(ShinigamiDownloader local, String chapterId, int total) {
        return ShinigamiPageStore.savedCount(local, chapterId, total);
    }

    private static int percentDone(ShinigamiDownloader local, String chapterId, int total) {
        return ShinigamiPageStore.percent(local, chapterId, total);
    }

    private static Data progress(String chapterId, ShinigamiDownloader local, int total) {
        return new Data.Builder()
                .putString("chapter_id", chapterId)
                .putInt("saved", savedCount(local, chapterId, total))
                .putInt("total", total)
                .putInt("percent", percentDone(local, chapterId, total))
                .build();
    }

    private static Data failData(String message) {
        return new Data.Builder().putString("error", message).build();
    }

    private static OkHttpClient client() {
        return new OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .callTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
                .followRedirects(false).followSslRedirects(false)
                .retryOnConnectionFailure(false).build();
    }

    private static File target(ShinigamiDownloader local, String chapterId, int index) {
        File dir = local.chapterDir(chapterId);
        if (!dir.isDirectory()) //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        return new File(dir, index + ".img");
    }

    private static String safeMessage(String message) {
        if (message == null || message.isEmpty()) return "Unduhan gagal. Coba lagi.";
        String clean = message.replaceAll("(?i)(cookie|token|signature|sig|auth)[^\\s]*", "[disensor]");
        return clean.length() > 160 ? clean.substring(0, 160) : clean;
    }

    private static final class Retryable extends Exception {
        Retryable(String message) { super(message); }
    }
}
