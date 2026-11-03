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

    /**
     * Live progress of one chapter, reported from the worker's own thread.
     *
     * <p>{@code percent} is the byte progress of the picture being fetched, or
     * -1 when the server declared no {@code Content-Length}. The UI must show a
     * spinner rather than a number in that case.
     */
    public interface Progress {
        void onImage(int percent, int imageIndex, int imageCount);

        void onStage(int stage);
    }

    /** Stages a chapter passes through, in order. */
    public static final int STAGE_TEXT = 0;
    public static final int STAGE_IMAGES = 1;
    public static final int STAGE_VERIFY = 2;

    private static final Progress NONE = new Progress() {
        @Override
        public void onImage(int percent, int imageIndex, int imageCount) {
        }

        @Override
        public void onStage(int stage) {
        }
    };

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
        /** Illustrations the chapter needed but could not store. */
        public final int imagesMissing;

        Result(OfflineStore.Saved saved, String title, int imageCount) {
            this(saved, title, imageCount, 0);
        }

        Result(OfflineStore.Saved saved, String title, int imageCount, int imagesMissing) {
            this.saved = saved;
            this.title = title;
            this.imageCount = imageCount;
            this.imagesMissing = Math.max(0, imagesMissing);
        }

        /** Text is on disk but at least one illustration is not. */
        public boolean partial() {
            return imagesMissing > 0;
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
        return download(context, novelId, url, volume, titleHint, cancellation, NONE);
    }

    /**
     * Same, reporting real progress.
     *
     * <p>Nothing here invents a percentage: the text stage is a spinner, each
     * illustration reports the bytes that actually arrived against the
     * {@code Content-Length} the server sent, and the verify stage is a spinner
     * because reading the file back has no measurable fraction.
     *
     * <p>Text that arrived is kept even when a picture did not. The chapter is
     * then stored as <em>partial</em>: the entry remembers how many illustrations
     * were owed, so the UI can say so honestly and offer to continue instead of
     * losing the text or claiming the chapter is complete. A resume re-fetches the
     * chapter and overwrites the partial copy, so a later run can only improve it.
     */
    public static Result download(Context context, String novelId, String url, int volume,
                                  String titleHint, Cancellation cancellation,
                                  Progress progress) throws Exception {
        Progress report = progress == null ? NONE : progress;
        checkCancelled(cancellation);
        SLOTS.acquire();
        try {
            OfflineStore store = OfflineStore.get(context);
            if (store.isComplete(novelId, url)) {
                // Already complete: never refetch the same chapter for no reason.
                OfflineStore.Entry existing = store.entry(novelId, url);
                return new Result(new OfflineStore.Saved(existing, true),
                        existing == null ? titleHint : existing.title,
                        existing == null ? 0 : existing.imageCount);
            }

            report.onStage(STAGE_TEXT);
            String html = Fetcher.get().page(url);
            checkCancelled(cancellation);

            ChapterContent parsed = ChapterParser.parse(html, url);
            validateText(parsed, url);

            List<String> wanted = imageUrls(parsed);
            Images images = fetchImagesTolerant(parsed, cancellation, report);
            checkCancelled(cancellation);

            String title = parsed.title() == null || parsed.title().trim().isEmpty()
                    ? (titleHint == null ? "" : titleHint) : parsed.title().trim();

            // Verification is its own visible stage: the fetch succeeding is not
            // the same thing as the bytes reaching the disk.
            report.onStage(STAGE_VERIFY);
            OfflineStore.Saved saved = store.save(novelId, url, title, volume, parsed,
                    images.data, wanted.size());
            if (!store.isSaved(novelId, url)) {
                throw new IOException("chapter tidak tersimpan setelah ditulis: " + url);
            }
            int missing = Math.max(images.missing, wanted.size() - images.data.size());
            return new Result(saved, title, images.data.size(), missing);
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
        return fetchImages(parsed, cancellation, NONE);
    }

    /**
     * Same, reporting the bytes that actually arrive for each illustration.
     *
     * <p>When the server sends no {@code Content-Length} the reported percentage
     * is -1 and the UI shows a spinner. That is deliberate: a number nobody
     * measured would look like real progress and be wrong.
     */
    static Map<String, byte[]> fetchImages(ChapterContent parsed, Cancellation cancellation,
                                           Progress progress) throws Exception {
        Progress report = progress == null ? NONE : progress;
        Set<String> urls = new LinkedHashSet<>();
        for (Block block : parsed.blocks()) {
            if (block.isImage() && block.imageUrl != null && !block.imageUrl.isEmpty()) {
                urls.add(block.imageUrl);
            }
        }

        Map<String, byte[]> downloaded = new LinkedHashMap<>();
        int index = 0;
        int count = urls.size();
        report.onImage(-1, 0, count);
        for (String url : urls) {
            checkCancelled(cancellation);
            final int position = index;
            byte[] data = Fetcher.get().image(url, (read, total) -> {
                int percent = total <= 0 ? -1
                        : (int) Math.max(0, Math.min(100, (read * 100) / total));
                report.onImage(percent, position, count);
            });
            index++;
            if (!ImageValidator.isImage(data)) {
                // A missing picture means the chapter would be incomplete offline,
                // so this chapter fails as a whole instead of silently losing art.
                throw new IOException(ImageValidator.rejectionReason(data, url));
            }
            downloaded.put(url, data);
        }
        report.onImage(100, count, count);
        return downloaded;
    }

    /**
     * Downloaded illustrations plus how many could not be fetched.
     *
     * <p>A picture that is missing is counted, never invented: the store is told
     * how many the chapter needed, so a partial chapter stays partial after a
     * restart instead of looking complete.
     */
    static final class Images {
        final Map<String, byte[]> data = new LinkedHashMap<>();
        int missing;

        int total() {
            return data.size() + missing;
        }
    }

    /**
     * Downloads every illustration it can, counting the ones that fail.
     *
     * <p>This is what makes point 8 of the download spec real: text that arrived
     * is never thrown away because one picture was rate-limited. The caller stores
     * the chapter as partial and offers to continue; a cancellation is different
     * and still aborts the whole chapter, because the user asked to stop.
     */
    static Images fetchImagesTolerant(ChapterContent parsed, Cancellation cancellation,
                                      Progress progress) throws Exception {
        Progress report = progress == null ? NONE : progress;
        Set<String> urls = new LinkedHashSet<>();
        for (Block block : parsed.blocks()) {
            if (block.isImage() && block.imageUrl != null && !block.imageUrl.isEmpty()) {
                urls.add(block.imageUrl);
            }
        }

        Images images = new Images();
        int index = 0;
        int count = urls.size();
        report.onImage(-1, 0, count);
        for (String url : urls) {
            checkCancelled(cancellation);
            final int position = index;
            index++;
            byte[] data;
            try {
                data = Fetcher.get().image(url, (read, total) -> {
                    int percent = total <= 0 ? -1
                            : (int) Math.max(0, Math.min(100, (read * 100) / total));
                    report.onImage(percent, position, count);
                });
            } catch (CancelledException stopped) {
                throw stopped;
            } catch (Exception error) {
                // A rate-limited or missing picture: the text is still worth
                // keeping, so this is recorded rather than aborting the chapter.
                images.missing++;
                continue;
            }
            if (!ImageValidator.isImage(data)) {
                // An HTML error page saved under a .jpg name would render as a
                // broken picture forever, so it counts as missing instead.
                images.missing++;
                continue;
            }
            images.data.put(url, data);
        }
        report.onImage(100, count, count);
        return images;
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
