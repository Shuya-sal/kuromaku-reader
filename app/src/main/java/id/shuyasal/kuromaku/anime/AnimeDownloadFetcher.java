package id.shuyasal.kuromaku.anime;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Mesin pengunduh satu episode.
 *
 * <p>Dipisahkan dari {@link AnimeDownloadWorker} supaya aturannya bisa diuji
 * terhadap server HTTP sungguhan: melanjutkan dengan {@code Range}, menolak
 * respons yang bukan berkas media, dan tidak pernah menyatakan selesai sebelum
 * ukuran serta kepala berkasnya benar.
 *
 * <p>Yang <b>tidak</b> dilakukan mesin ini: mengirim cookie/token, mengikuti
 * halaman embed, membuka DRM, atau menebak alamat media. Alamat yang diterima
 * hanyalah alamat yang sudah diterbitkan halaman episode.
 */
public final class AnimeDownloadFetcher {

    /** Hasil satu percobaan unduhan. */
    public enum Outcome {
        /** Berkas lengkap, tervalidasi, dan tercatat. */
        SAVED,
        /** Belum lengkap karena koneksi terputus; bisa dilanjutkan. */
        INCOMPLETE,
        /** Dihentikan karena pengguna membatalkan; sisa berkas dibuang. */
        CANCELLED,
        /** Gagal dan tidak akan berhasil bila diulang tanpa perubahan. */
        FAILED
    }

    /** Masukan satu unduhan; sengaja datar agar mudah dibangun dari Worker. */
    public static final class Params {
        public final String animeId;
        public final String animeTitle;
        public final String poster;
        public final String episodeLabel;
        public final String episodeUrl;
        public final String episodeKey;
        public final String quality;
        public final String server;
        public final String url;
        public final String kind;

        public Params(String animeId, String animeTitle, String poster, String episodeLabel,
                      String episodeUrl, String episodeKey, String quality, String server,
                      String url, String kind) {
            this.animeId = safe(animeId);
            this.animeTitle = safe(animeTitle);
            this.poster = safe(poster);
            this.episodeLabel = safe(episodeLabel);
            this.episodeUrl = safe(episodeUrl);
            this.episodeKey = safe(episodeKey);
            this.quality = safe(quality);
            this.server = safe(server);
            this.url = safe(url);
            this.kind = safe(kind);
        }

        private static String safe(String value) {
            return value == null ? "" : value.trim();
        }
    }

    /** Penanda bahwa pekerjaan sudah diminta berhenti (WorkManager isStopped). */
    public interface CancelSignal {
        boolean isCancelled();
    }

    /** Hasil lengkap dengan keterangan untuk ditampilkan. */
    public static final class Result {
        public final Outcome outcome;
        public final String message;
        public final long bytes;
        public final long total;

        Result(Outcome outcome, String message, long bytes, long total) {
            this.outcome = outcome;
            this.message = message == null ? "" : message;
            this.bytes = bytes;
            this.total = total;
        }
    }

    private static final String TAG = "AnimeDownload";
    private static final int BUFFER = 64 * 1024;
    private static final long REPORT_STEP = 512 * 1024;

    private AnimeDownloadFetcher() {
    }

    /** Unduh satu episode; tidak pernah melempar untuk kegagalan yang wajar. */
    public static Result run(Context context, Params params, OkHttpClient client,
                             CancelSignal cancelled) {
        if (params.episodeKey.isEmpty()) {
            return new Result(Outcome.FAILED, "Episode ini tidak punya penanda yang sah.", 0, 0);
        }
        if (!"PROGRESSIVE".equals(params.kind)) {
            return new Result(Outcome.FAILED,
                    "Server ini hanya menyediakan pemutar bertaut, bukan berkas yang bisa disimpan.",
                    0, 0);
        }
        if (params.url.isEmpty()) {
            return new Result(Outcome.FAILED,
                    "Server ini tidak menyebut alamat berkas media.", 0, 0);
        }
        if (AnimeDownloads.isSaved(context, params.animeId, params.episodeKey)) {
            File saved = AnimeDownloads.videoFile(context, params.animeId, params.episodeKey);
            return new Result(Outcome.SAVED, "Tersimpan offline", saved.length(), saved.length());
        }

        File part = AnimeDownloads.partFile(context, params.animeId, params.episodeKey);
        File target = AnimeDownloads.videoFile(context, params.animeId, params.episodeKey);
        File dir = part.getParentFile();
        if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) {
            return new Result(Outcome.FAILED, "Penyimpanan aplikasi tidak bisa dipakai.", 0, 0);
        }

        long offset = part.isFile() ? part.length() : 0L;
        AnimeDownloadStatus.progress(params.episodeKey, offset, 0);

        // Alamat yang tidak bisa diurai OkHttp akan melempar IllegalArgumentException.
        // Itu bukan kegagalan jaringan, dan tidak boleh lolos ke thread pekerja.
        okhttp3.HttpUrl parsed = okhttp3.HttpUrl.parse(params.url);
        if (parsed == null) {
            return new Result(Outcome.FAILED,
                    "Alamat berkas dari server tidak sah, jadi tidak bisa diunduh.", 0, 0);
        }
        Request.Builder builder = new Request.Builder().url(parsed).get();
        if (offset > 0) builder.header("Range", "bytes=" + offset + "-");

        long total = 0L;
        long written = offset;
        boolean append = offset > 0;

        try (Response response = client.newCall(builder.build()).execute()) {
            int code = response.code();
            if (code == 401 || code == 403 || code == 429) {
                return new Result(Outcome.FAILED,
                        "Server menolak unduhan (kode " + code + ").", written, total);
            }
            if (code == 416) {
                // Rentang tidak masuk akal: berkas sementara dibuang dan mulai lagi.
                part.delete();
                return new Result(Outcome.INCOMPLETE,
                        "Unduhan dimulai ulang dari awal.", 0, 0);
            }
            if (!response.isSuccessful()) {
                return new Result(Outcome.FAILED,
                        "Server menjawab dengan kode " + code + ".", written, total);
            }
            ResponseBody body = response.body();
            if (body == null) {
                return new Result(Outcome.FAILED, "Server tidak mengirim berkas.", written, total);
            }

            if (code == 200 && offset > 0) {
                // Server mengabaikan Range: tulis ulang supaya tidak ada data ganda.
                append = false;
                written = 0L;
            }
            String contentRange = response.header("Content-Range");
            long contentLength = body.contentLength();
            if (contentRange != null) {
                int slash = contentRange.lastIndexOf('/');
                if (slash > 0) {
                    try {
                        total = Long.parseLong(contentRange.substring(slash + 1).trim());
                    } catch (NumberFormatException ignored) {
                        total = 0L;
                    }
                }
            }
            if (total <= 0 && contentLength > 0) total = written + contentLength;

            AnimeDownloadStatus.progress(params.episodeKey, written, total);

            long lastReport = 0L;
            try (InputStream in = body.byteStream();
                 FileOutputStream out = new FileOutputStream(part, append)) {
                byte[] buffer = new byte[BUFFER];
                while (true) {
                    if (cancelled != null && cancelled.isCancelled()) {
                        out.flush();
                        return new Result(Outcome.CANCELLED, "Dibatalkan", written, total);
                    }
                    int read = in.read(buffer);
                    if (read < 0) break;
                    out.write(buffer, 0, read);
                    written += read;
                    if (written - lastReport >= REPORT_STEP) {
                        lastReport = written;
                        AnimeDownloadStatus.progress(params.episodeKey, written, total);
                    }
                }
                out.flush();
                out.getFD().sync();
            }
            AnimeDownloadStatus.progress(params.episodeKey, written, total);
        } catch (IOException error) {
            return new Result(Outcome.INCOMPLETE,
                    "Koneksi terputus saat mengunduh.", written, total);
        }

        // ---- pemeriksaan sebelum berkas dinyatakan tersimpan ----
        long size = part.length();
        if (size <= 0) {
            part.delete();
            return new Result(Outcome.FAILED, "Berkas yang diterima kosong.", 0, 0);
        }
        if (total > 0 && size != total) {
            // Belum lengkap: jangan pernah tampil sebagai 100%.
            return new Result(Outcome.INCOMPLETE, "Unduhan belum lengkap.", size, total);
        }
        if (!AnimeDownloads.looksLikeMedia(part)) {
            part.delete();
            return new Result(Outcome.FAILED,
                    "Berkas yang diterima bukan video yang bisa diputar.", 0, 0);
        }
        if (target.exists() && !target.delete()) {
            part.delete();
            return new Result(Outcome.FAILED, "Berkas lama tidak bisa diganti.", 0, 0);
        }
        if (!part.renameTo(target)) {
            part.delete();
            return new Result(Outcome.FAILED, "Berkas tidak bisa dipindahkan ke penyimpanan.",
                    0, 0);
        }

        AnimeDownload meta = new AnimeDownload(params.episodeKey, params.animeId,
                params.animeTitle, params.poster, params.episodeLabel, params.episodeUrl,
                params.quality, params.server, params.url, target.length(),
                System.currentTimeMillis());
        AnimeDownloads.writeMeta(context, meta);
        // Status ikut ditulis di sini supaya pemanggil mana pun (worker, uji,
        // atau layar lain) melihat keadaan yang sama.
        AnimeDownloadStatus.set(params.episodeKey, AnimeDownloadStatus.SAVED, 100,
                "Tersimpan offline");
        Log.i(TAG, "episode tersimpan: " + target.length() + " byte");
        return new Result(Outcome.SAVED, "Tersimpan offline", target.length(), target.length());
    }
}