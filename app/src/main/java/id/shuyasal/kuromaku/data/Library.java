package id.shuyasal.kuromaku.data;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import id.shuyasal.kuromaku.db.NovelDao;
import id.shuyasal.kuromaku.db.NovelDatabase;
import id.shuyasal.kuromaku.db.NovelRecord;

/**
 * The user's own shelf.
 *
 * <p>Before this existed the library was a hardcoded list of two novels, which
 * meant a new novel could only arrive with a new APK. The shelf now lives in a
 * database ({@link NovelDatabase}) keyed by the normalised catalogue URL, so:
 *
 * <ul>
 *   <li>a novel added once is there after the app is closed or the phone
 *       restarts,</li>
 *   <li>entering the same page twice cannot create a second copy — the URL
 *       normaliser strips {@code ?m=1}, trailing slashes and the {@code http}/
 *       {@code https} difference before the duplicate check,</li>
 *   <li>removing a novel deletes one row and nothing else, so other novels,
 *       reading progress and offline files are untouched.</li>
 * </ul>
 *
 * <p>On first launch — including the first launch after an upgrade from a version
 * that shipped two fixed novels — the two built-in novels are written into an
 * empty table, so nobody loses the books they were reading.
 *
 * <p>Reads are synchronous against a tiny in-memory list: the shelf is on screen
 * in the first frame instead of flashing empty while a query returns, and the
 * actual network work (catalogues, covers, downloads) stays off the main thread.
 */
public final class Library {

    /** Result of trying to add a novel. */
    public enum Added {
        /** Stored; {@link Result#novel} is the new row. */
        ADDED,
        /** The same page is already on the shelf; nothing was written. */
        DUPLICATE,
        /** Nothing was written and {@link Result#message} says why. */
        REFUSED
    }

    /** Outcome of {@link #add(Novel)} together with the novel it refers to. */
    public static final class Result {
        public final Added status;
        public final Novel novel;
        public final String message;

        Result(Added status, Novel novel, String message) {
            this.status = status;
            this.novel = novel;
            this.message = message;
        }
    }

    private final NovelDao dao;
    private final List<Novel> novels = new ArrayList<>();
    private boolean seeded;

    Library(NovelDatabase database) {
        this.dao = database.novels();
    }

    /**
     * Library bound to a specific database.
     *
     * <p>Tests build one over {@link NovelDatabase#inMemory(Context)} so the whole
     * shelf — seeding, duplicate refusal, deletion — runs on the real Room stack
     * instead of a stand-in that could disagree with it.
     */
    public static Library forDatabase(NovelDatabase database) {
        return new Library(database);
    }

    /** Process-wide instance bound to the application context. */
    public static Library get(Context context) {
        return Holder.get(context);
    }

    private static final class Holder {
        private static volatile Library instance;

        static Library get(Context context) {
            if (instance == null) {
                synchronized (Holder.class) {
                    if (instance == null) {
                        instance = new Library(NovelDatabase.get(context));
                    }
                }
            }
            return instance;
        }

        static synchronized void use(Library library) {
            instance = library;
        }
    }

    /**
     * Points the app at another library. Tests use it to build an in-memory
     * database; {@code null} restores the real, on-disk one on next access.
     */
    public static void useForTests(Library library) {
        Holder.use(library);
    }

    /** Forgets the cached rows so the next read comes from disk again. */
    public void reload() {
        synchronized (novels) {
            novels.clear();
            for (NovelRecord record : dao.all()) {
                Novel novel = NovelMapper.toNovel(record);
                if (novel != null) novels.add(novel);
            }
        }
    }

    /**
     * The shelf, in library order.
     *
     * <p>Seeds the two built-in novels the first time the table is empty, which is
     * also the upgrade path from the version that shipped them hardcoded.
     */
    public List<Novel> all() {
        ensureSeeded();
        synchronized (novels) {
            return Collections.unmodifiableList(new ArrayList<>(novels));
        }
    }

    /**
     * Novel by id, or {@code null} when it is not on the shelf.
     *
     * <p>Deliberately no "first novel" fallback. Returning some other book for an
     * id that was just deleted would attach a removed novel's downloads and
     * progress to whichever row happens to be first, so a deleted novel could
     * reappear under another name. Callers that genuinely want "some novel" ask
     * {@link #first()} instead.
     */
    public Novel byId(String id) {
        ensureSeeded();
        if (id == null || id.isEmpty()) return null;
        synchronized (novels) {
            for (Novel novel : novels) {
                if (novel.id.equals(id)) return novel;
            }
            return null;
        }
    }

    /** Novel whose catalogue URL matches, or null. Duplicate checks go through here. */
    public Novel byUrl(String normalisedUrl) {
        if (normalisedUrl == null || normalisedUrl.isEmpty()) return null;
        NovelRecord record = dao.byUrl(normalisedUrl);
        return NovelMapper.toNovel(record);
    }

    /** First novel on the shelf, or null when it is empty. */
    public Novel first() {
        List<Novel> all = all();
        return all.isEmpty() ? null : all.get(0);
    }

    public boolean isEmpty() {
        return all().isEmpty();
    }

    /**
     * Adds a novel, refusing a page that is already on the shelf.
     *
     * <p>The duplicate test uses the normalised URL and the slug id, so the same
     * page typed with different spellings is recognised as one novel.
     */
    public Result add(Novel novel) {
        if (novel == null) {
            return new Result(Added.REFUSED, null, "Novel tidak terbaca dari halaman itu.");
        }
        ensureSeeded();
        String url = novel.landingUrl == null ? "" : novel.landingUrl;
        if (url.isEmpty()) {
            return new Result(Added.REFUSED, null, "URL katalog kosong, novel tidak disimpan.");
        }

        Novel existing = byUrl(url);
        if (existing == null) {
            String id = novel.id;
            for (Novel other : all()) {
                if (other.id.equals(id)) {
                    existing = other;
                    break;
                }
            }
        }
        if (existing != null) {
            return new Result(Added.DUPLICATE, existing,
                    "Novel ini sudah ada di perpustakaan Tuan.");
        }

        int position = dao.maxPosition() + 1;
        NovelRecord record = NovelMapper.toRecord(novel, position);
        record.lastRefresh = System.currentTimeMillis();
        dao.insert(record);
        reload();
        return new Result(Added.ADDED, NovelMapper.toNovel(record), "");
    }

    /**
     * Updates a novel's metadata after a refresh.
     *
     * <p>Deliberately conservative: an empty field from the page never erases a
     * value already stored, and the row's id, position and URL are left alone, so
     * refreshing cannot detach a novel from its reading progress or its offline
     * folders.
     */
    public Novel updateMetadata(Novel refreshed) {
        if (refreshed == null) return null;
        NovelRecord current = dao.byId(refreshed.id);
        if (current == null) return null;

        String title = pick(refreshed.title, current.title);
        String titleJp = pick(refreshed.titleJp, current.titleJp);
        String fullTitle = pick(refreshed.fullTitle, current.fullTitle);
        String author = pick(refreshed.author, current.author);
        String illustrator = pick(refreshed.illustrator, current.illustrator);
        String genres = pick(refreshed.genres, current.genres);
        String status = pick(refreshed.status, current.status);
        String cover = pick(refreshed.coverUrl, current.coverUrl);
        float ratio = refreshed.coverRatio > 0 ? refreshed.coverRatio : current.coverRatio;
        String synopsis = NovelMapper.join(refreshed.synopsis);
        if (synopsis.isEmpty()) synopsis = current.synopsis;
        int preview = refreshed.synopsisPreview > 0
                ? refreshed.synopsisPreview : current.synopsisPreview;
        String slugPrefix = pick(refreshed.slugPrefix, current.slugPrefix);

        dao.updateMetadata(current.id, title, titleJp, fullTitle, author, illustrator, genres,
                status, cover, ratio, synopsis, preview, slugPrefix, System.currentTimeMillis());
        reload();
        return byId(current.id);
    }

    /** Removes the shelf entry only; downloads and progress are left on disk. */
    public boolean remove(String id) {
        if (id == null || id.isEmpty()) return false;
        NovelRecord record = dao.byId(id);
        if (record == null) return false;
        dao.delete(id);
        reload();
        return true;
    }

    /** Number of novels on the shelf. */
    public int count() {
        return all().size();
    }

    /** Writes the built-in novels once, when the table is still empty. */
    private void ensureSeeded() {
        if (seeded) return;
        synchronized (novels) {
            if (seeded) return;
            if (dao.count() == 0) {
                int position = 0;
                List<NovelRecord> seeds = new ArrayList<>();
                for (Novel novel : Novels.builtIns()) {
                    NovelRecord record = NovelMapper.toRecord(novel, position++);
                    seeds.add(record);
                }
                dao.insertAll(seeds);
            }
            novels.clear();
            for (NovelRecord record : dao.all()) {
                Novel novel = NovelMapper.toNovel(record);
                if (novel != null) novels.add(novel);
            }
            seeded = true;
        }
    }

    private static String pick(String preferred, String fallback) {
        if (preferred != null && !preferred.trim().isEmpty()) return preferred.trim();
        return fallback == null ? "" : fallback;
    }
}
