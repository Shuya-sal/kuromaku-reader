package id.shuyasal.kuromaku.shinigami;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class ShinigamiDownloader {
    private final Context context;

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
        this.context = context.getApplicationContext();
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
            ShinigamiImageRequest.validate(200, "image/*", java.util.Arrays.copyOf(head, read));
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

    public Result download(String chapterId, List<String> urls, Progress progress) throws IOException {
        throw new IOException("Unduhan offline dinonaktifkan: izin reader pihak ketiga dari sumber belum terverifikasi.");
    }

    public boolean deleteChapter(String chapterId) {
        File dir = chapterDir(chapterId);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (!file.isFile() || !file.delete()) return false;
            }
        }
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
}
