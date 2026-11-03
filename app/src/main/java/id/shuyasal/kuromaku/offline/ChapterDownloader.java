package id.shuyasal.kuromaku.offline;

import android.content.Context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;

import id.shuyasal.kuromaku.data.Block;
import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.net.Fetcher;
import id.shuyasal.kuromaku.parser.ChapterContent;
import id.shuyasal.kuromaku.parser.ChapterParser;

/**
 * Downloads one chapter — text <em>and</em> illustrations — into the offline store.
 *
 * <p>Order matters here. The chapter page is fetched and parsed first, every
 * illustration is downloaded and sniffed as a real picture second, and only then
 * is anything written to disk. A half-finished download therefore never becomes
 * a "saved" chapter: the store writes atomically and the index entry is added
 * last, so a reader that opens the app mid-download sees the previous state
 * instead of a broken chapter.
 *
 * <p>Concurrency is capped globally so a volume download cannot hammer the
 * source blog with more than a couple of simultaneous requests.
 */
public final class ChapterDownloader {

    /** At most this many chapters are fetched at once, across all jobs. */
    private static final int MAX_PARALLEL = 2;
    private static final Semaphore SLOTS = new Semaphore(MAX_PARALLEL, true);

    private ChapterDownloader() {
    }

    /** Told to stop when the user cancels or WorkManager stops the job. */
    public interface Cancellation {
        boolean isCancelled();
    }

    /** Raised when the job was stopped on purpose; not an error to report. */
    public static final class CancelledException extends Exception {
        public CancelledException() {
            super("unduhan dibatalkan");
        }
    }

    /** What one successful download produced. */
    public static final class Result {
        public final OfflineStore.Saved saved;
        public final String title;
        public final int imageCount;

        Result(OfflineStore.Saved saved, String title, int imageCount) {
            this.saved = saved;
            this.title = title;
            this.imageCount = imageCount;
        }
    }

    /**
     * Fetches, validates and stores one chapter.
     *
     * @param titleHint label to store when the parsed article has no usable title
     * @throws CancelledException when {@code cancellation} asked to stop
     * @throws IOException        when the page, the text or any illustration is unusable
     */
    public static Result download(Context context, String novelId, String url, int volume,
                                  String titleHint, Cancellation cancellation) throws Exception {
        checkCancelled(cancellation);
        SLOTS.acquire();
        try {
            OfflineStore store = OfflineStore.get(context);
            if (store.isSaved(novelId, url)) {
                // Already complete: never refetch the same chapter for no reason.
                OfflineStore.Entry existing = store.entry(novelId, url);
                return new Result(new OfflineStore.Saved(existing, true),
                        existing == null ? titleHint : existing.title,
                        existing == null ? 0 : existing.imageCount);
            }

            String html = Fetcher.get().page(url);
            checkCancelled(cancellation);

            ChapterContent parsed = ChapterParser.parse(html, url);
            validateText(parsed, url);

            Map<String, byte[]> images = fetchImages(parsed, cancellation);
            checkCancelled(cancellation);

            String title = parsed.title() == null || parsed.title().trim().isEmpty()
                    ? (titleHint == null ? "" : titleHint) : parsed.title().trim();

            OfflineStore.Saved saved = store.save(novelId, url, title, volume, parsed, images);
            return new Result(saved, title, images.size());
        } finally {
            SLOTS.release();
        }
    }

    /**
     * A chapter is only usable when it has real rendered content. An error page
     * parses to zero blocks, and a JavaScript shell parses to one empty one;
     * both must fail instead of being stored as a blank chapter.
     */
    static void validateText(ChapterContent parsed, String url) throws IOException {
        if (parsed == null) throw new IOException("halaman chapter kosong: " + url);
        List<Block> blocks = parsed.blocks();
        if (blocks == null || blocks.isEmpty()) {
            throw new IOException("isi chapter tidak ditemukan di halaman sumber: " + url);
        }
        int usable = 0;
        for (Block block : blocks) {
            if (block.isImage()) {
                if (block.imageUrl != null && !block.imageUrl.isEmpty()) usable++;
            } else if (block.text != null && block.text.trim().length() > 1) {
                usable++;
            }
        }
        if (usable == 0) {
            throw new IOException("chapter tidak berisi teks maupun ilustrasi: " + url);
        }
    }

    /**
     * Downloads every distinct illustration of the chapter once. A duplicate URL
     * is fetched a single time even if the article shows it twice.
     */
    static Map<String, byte[]> fetchImages(ChapterContent parsed, Cancellation cancellation)
            throws Exception {
        Set<String> urls = new LinkedHashSet<>();
        for (Block block : parsed.blocks()) {
            if (block.isImage() && block.imageUrl != null && !block.imageUrl.isEmpty()) {
                urls.add(block.imageUrl);
            }
        }

        Map<String, byte[]> downloaded = new LinkedHashMap<>();
        for (String url : urls) {
            checkCancelled(cancellation);
            byte[] data = Fetcher.get().image(url);
            if (!ImageValidator.isImage(data)) {
                // A missing picture means the chapter would be incomplete offline,
                // so this chapter fails as a whole instead of silently losing art.
                throw new IOException(ImageValidator.rejectionReason(data, url));
            }
            downloaded.put(url, data);
        }
        return downloaded;
    }

    private static void checkCancelled(Cancellation cancellation) throws CancelledException {
        if (cancellation != null && cancellation.isCancelled()) throw new CancelledException();
    }

    /** Image URLs of a chapter, used by tests and by the UI to show what is stored. */
    public static List<String> imageUrls(ChapterContent content) {
        List<String> urls = new ArrayList<>();
        if (content == null) return urls;
        for (Block block : content.blocks()) {
            if (block.isImage() && block.imageUrl != null) urls.add(block.imageUrl);
        }
        return urls;
    }

    /** Convenience for callers holding a catalogue entry. */
    public static Result download(Context context, String novelId, Chapter chapter,
                                  Cancellation cancellation) throws Exception {
        return download(context, novelId, chapter.url, chapter.volume, chapter.label, cancellation);
    }
}
