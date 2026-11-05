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

    public boolean isDownloaded(String chapterId, int expectedPages) {
        if (expectedPages <= 0) return false;
        for (int index = 0; index < expectedPages; index++) {
            if (pageFile(chapterId, index) == null) return false;
        }
        return true;
    }

    public File pageFile(String chapterId, int index) {
        if (index < 0 || chapterId == null || !chapterId.matches("[A-Za-z0-9_-]{1,128}")) return null;
        File file = new File(chapterDir(chapterId), index + ".img");
        return file.isFile() && file.length() > 0 ? file : null;
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
                File[] files = folder.listFiles();
                if (files != null && files.length > 0) ids.add(folder.getName());
            }
        }
        return ids;
    }
}
