package id.shuyasal.kuromaku.shinigami;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class ShinigamiDownloader {
    private final Context context;
    private final Transport transport;

    /** Cara mengambil byte halaman; dapat diganti pada pengujian. */
    public interface Transport {
        /** Membuka aliran gambar; melempar {@link IOException} bila respons ditolak. */
        Stream open(String url, String referer) throws IOException;
    }

    /** Aliran gambar beserta kode HTTP dan MIME untuk validasi. */
    public static final class Stream implements java.io.Closeable {
        public final int code;
        public final String mime;
        public final InputStream body;
        private final java.io.Closeable holder;

        public Stream(int code, String mime, InputStream body) {
            this(code, mime, body, null);
        }

        Stream(int code, String mime, InputStream body, java.io.Closeable holder) {
            this.code = code;
            this.mime = mime;
            this.body = body;
            this.holder = holder;
        }

        @Override public void close() throws IOException {
            try {
                body.close();
            } finally {
                if (holder != null) holder.close();
            }
        }
    }

    public interface Progress {
        void onProgress(int done, int total);
    }

    public static final class Result {
        public final boolean complete;
        public final int saved;
        public final int total;
        public final String message;
        Result(boolean complete, int saved, int total, String message) {
            this.complete = complete;
            this.saved = saved;
            this.total = total;
            this.message = message;
        }
    }

    public ShinigamiDownloader(Context context) {
        this(context, new HttpTransport());
    }

    ShinigamiDownloader(Context context, Transport transport) {
        this.context = context.getApplicationContext();
        this.transport = transport;
    }

    public File chapterDir(String chapterId) {
        if (chapterId == null || !chapterId.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new IllegalArgumentException("Identitas chapter tidak valid.");
        }
        return new File(new File(context.getFilesDir(), "shinigami"), chapterId);
    }

    public static final String LABEL_SAVED = "Tersimpan offline";
    public static final String LABEL_NOT_SAVED = "Belum diunduh";
    public static final String LABEL_RUNNING = "Mengunduh...";
    public static final String LABEL_FAILED = "Unduhan gagal";
    private static final java.util.Set<String> RUNNING =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    public boolean isDownloaded(String chapterId, int expectedPages) {
        if (expectedPages <= 0) return false;
        for (int index = 0; index < expectedPages; index++) {
            if (pageFile(chapterId, index) == null) return false;
        }
        return true;
    }

    public void setRunning(String chapterId, boolean running) {
        if (chapterId == null) return;
        if (running) RUNNING.add(chapterId); else RUNNING.remove(chapterId);
    }

    public boolean isRunning(String chapterId) {
        return chapterId != null && RUNNING.contains(chapterId);
    }

    public String storageLabel(String chapterId, int expectedPages) {
        if (isDownloaded(chapterId, expectedPages)) return LABEL_SAVED;
        int state = ShinigamiDownloadStatus.state(chapterId);
        if (state == ShinigamiDownloadStatus.FAILED
                || state == ShinigamiDownloadStatus.CANCELLED
                || state == ShinigamiDownloadStatus.PARTIAL) return LABEL_FAILED;
        if (state == ShinigamiDownloadStatus.QUEUED
                || state == ShinigamiDownloadStatus.DOWNLOADING
                || state == ShinigamiDownloadStatus.VERIFYING
                || isRunning(chapterId)) {
            int percent = ShinigamiDownloadStatus.percent(chapterId);
            return percent > 0 ? "Mengunduh " + percent + "%" : LABEL_RUNNING;
        }
        return LABEL_NOT_SAVED;
    }

    public File pageFile(String chapterId, int index) {
        if (index < 0 || chapterId == null || !chapterId.matches("[A-Za-z0-9_-]{1,128}")) return null;
        File file = new File(chapterDir(chapterId), index + ".img");
        if (!file.isFile() || file.length() < 8) return null;
        byte[] head = new byte[16];
        int read;
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            read = in.read(head);
        } catch (IOException error) {
            return null;
        }
        if (read < 8) return null;
        try {
            ShinigamiImageRequest.validate(200, "image/*", Arrays.copyOf(head, read));
        } catch (IOException error) {
            return null;
        }
        // Dimensions alone are not proof of a complete image (truncated files can have them).
        try {
            if (file.length() > 32L * 1024 * 1024 || !hasCompleteContainer(file, head)) return null;
            android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            android.graphics.BitmapFactory.Options sampled = new android.graphics.BitmapFactory.Options();
            sampled.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565;
            sampled.inSampleSize = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / sampled.inSampleSize > 1024)
                sampled.inSampleSize *= 2;
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), sampled);
            if (bitmap == null) return null;
            bitmap.recycle();
            return file;
        } catch (IOException | RuntimeException error) {
            return null;
        }
    }

    private static boolean hasCompleteContainer(File file, byte[] head) throws IOException {
        try (java.io.RandomAccessFile input = new java.io.RandomAccessFile(file, "r")) {
            long size = input.length();
            if (head[0] == 'R' && head[1] == 'I') {
                long declared = ((long) head[4] & 255) | (((long) head[5] & 255) << 8)
                        | (((long) head[6] & 255) << 16) | (((long) head[7] & 255) << 24);
                return declared + 8 == size;
            }
            if ((head[0] & 255) == 137) {
                if (size < 12) return false;
                input.seek(size - 12);
                byte[] end = new byte[12]; input.readFully(end);
                return java.util.Arrays.equals(end, new byte[]{0,0,0,0,73,69,78,68,(byte)174,66,96,(byte)130});
            }
            if (head[0] == 'G') { input.seek(size - 1); return input.read() == 59; }
            input.seek(size - 2); return input.read() == 255 && input.read() == 217;
        }
    }

    /**
     * Unduh chapter ke penyimpanan khusus aplikasi.
     *
     * <p>Halaman yang sudah lengkap dan valid dilewati (resume), tiap gambar
     * divalidasi sebelum disimpan, dan status baru dianggap selesai bila semua
     * halaman lolos. Progres baca tidak disentuh di sini.
     */
    public Result download(String chapterId, List<String> urls, Progress progress) throws IOException {
        if (!ShinigamiDownloadPolicy.allowed(context)) {
            throw new IOException(ShinigamiDownloadPolicy.blockedMessage());
        }
        if (urls == null || urls.isEmpty()) throw new IOException("Daftar halaman chapter kosong.");
        int total = urls.size();
        String referer = new ShinigamiConfig(context).siteBase() + "/";
        setRunning(chapterId, true);
        ShinigamiDownloadStatus.set(chapterId, ShinigamiDownloadStatus.DOWNLOADING, 0, "");
        try {
            for (int index = 0; index < total; index++) {
                File target = ShinigamiPageStore.fileFor(this, chapterId, index);
                if (pageFile(chapterId, index) != null) {
                    report(progress, chapterId, total);
                    continue;
                }
                String url = urls.get(index);
                if (!ShinigamiConfig.isValidResource(url)) {
                    return fail(chapterId, total, "Alamat gambar ditolak: bukan HTTPS/host sumber.");
                }
                IOException last = null;
                for (int attempt = 0; attempt < 2; attempt++) {
                    try {
                        fetchOnce(url, referer, target);
                        last = null;
                        break;
                    } catch (IOException error) {
                        last = error;
                        if (isFatal(error)) break;
                        sleep(700L * (attempt + 1));
                    }
                }
                if (pageFile(chapterId, index) == null) {
                    String reason = last == null ? "Gambar tidak lolos validasi." : last.getMessage();
                    return fail(chapterId, total, "Halaman " + (index + 1) + " gagal: "
                            + safeMessage(reason));
                }
                report(progress, chapterId, total);
            }
            boolean complete = isDownloaded(chapterId, total);
            ShinigamiDownloadStatus.set(chapterId,
                    complete ? ShinigamiDownloadStatus.SAVED : ShinigamiDownloadStatus.FAILED,
                    complete ? 100 : ShinigamiPageStore.percent(this, chapterId, total),
                    complete ? "" : "Sebagian halaman belum lengkap.");
            return new Result(complete, ShinigamiPageStore.savedCount(this, chapterId, total), total,
                    complete ? "Chapter tersimpan offline." : "Sebagian halaman belum lengkap.");
        } finally {
            setRunning(chapterId, false);
        }
    }

    private void fetchOnce(String url, String referer, File target) throws IOException {
        try (Stream stream = transport.open(url, referer)) {
            ShinigamiPageStore.save(stream.body, target, stream.code, stream.mime);
        }
    }

    /** Catat kegagalan tanpa menghapus halaman yang sudah lengkap (resume aman). */
    private Result fail(String chapterId, int total, String message) {
        int saved = ShinigamiPageStore.savedCount(this, chapterId, total);
        ShinigamiDownloadStatus.set(chapterId, ShinigamiDownloadStatus.FAILED,
                ShinigamiPageStore.percent(this, chapterId, total), message);
        return new Result(false, saved, total, message);
    }

    private void report(Progress progress, String chapterId, int total) {
        if (progress == null) return;
        try {
            progress.onProgress(ShinigamiPageStore.savedCount(this, chapterId, total), total);
        } catch (RuntimeException ignored) {
            // Callback UI yang gagal tidak boleh membatalkan unduhan yang sudah valid.
        }
    }

    /** 401/403/429 tidak diulang: itu pembatasan sumber, bukan gangguan jaringan. */
    private static boolean isFatal(IOException error) {
        String message = error.getMessage();
        return message != null && (message.startsWith("HTTP 401") || message.startsWith("HTTP 403")
                || message.startsWith("HTTP 429"));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
    }

    private static String safeMessage(String message) {
        if (message == null || message.isEmpty()) return "Unduhan gagal. Coba lagi.";
        String clean = message.replaceAll("(?i)(cookie|token|signature|sig|auth)[^\\s]*", "[disensor]");
        return clean.length() > 160 ? clean.substring(0, 160) : clean;
    }

    public boolean deleteChapter(String chapterId) {
        File dir = chapterDir(chapterId);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (!file.isFile() || !file.delete()) return false;
            }
        }
        ShinigamiDownloadStatus.clear(chapterId);
        return !dir.exists() || dir.delete();
    }

    public List<String> downloadedChapters() {
        List<String> ids = new ArrayList<>();
        File[] folders = new File(context.getFilesDir(), "shinigami").listFiles();
        if (folders == null) return ids;
        for (File folder : folders) {
            if (folder.isDirectory() && folder.getName().matches("[A-Za-z0-9_-]{1,128}")) {
                String id = folder.getName();
                int expected = new ShinigamiStore(context).expectedPages(id);
                if (isDownloaded(id, expected)) ids.add(id);
            }
        }
        return ids;
    }

    private static final class HttpTransport implements Transport {
        private final OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .followRedirects(false).followSslRedirects(false)
                .retryOnConnectionFailure(false).build();

        @Override public Stream open(String url, String referer) throws IOException {
            Request request = new Request.Builder().url(url)
                    .header("User-Agent", id.shuyasal.kuromaku.net.Http.USER_AGENT)
                    .header("Referer", referer)
                    .header("Accept", "image/webp,image/*;q=0.8").build();
            Response response = client.newCall(request).execute();
            int code = response.code();
            if (code < 200 || code >= 300) {
                response.close();
                ShinigamiImageRequest.validate(code, "", new byte[0]);
                throw new IOException("Gambar gagal dimuat (HTTP " + code + ").");
            }
            ResponseBody body = response.body();
            if (body == null) {
                response.close();
                throw new IOException("Respons gambar kosong.");
            }
            if (body.contentLength() > 32L * 1024 * 1024) {
                response.close();
                throw new IOException("Gambar terlalu besar.");
            }
            return new Stream(code, response.header("Content-Type"), body.byteStream(), response);
        }
    }
}
