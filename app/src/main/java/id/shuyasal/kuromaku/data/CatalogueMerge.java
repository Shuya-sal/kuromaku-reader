package id.shuyasal.kuromaku.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import id.shuyasal.kuromaku.parser.ChapterNaming;

/**
 * Fills the gaps in a chapter list read from a catalogue page with posts that only
 * the label feed knows about.
 *
 * <p>Why this exists: the source's own catalogue article lists every volume and
 * every chapter of Monogatari, but one of its anchors is wrong — the "Prolog" link
 * for Volume 2 points at the same post as "Chapter 1". Read alone, the article
 * therefore shows a volume whose prologue opens chapter one, and the real prologue
 * post (`volume-2_12`) appears nowhere. The label feed still carries that post, so
 * it is used here for exactly three jobs and nothing else:
 *
 * <ul>
 *   <li><b>Repair</b> a link the page printed twice: when two entries in one volume
 *       share a URL and the feed has an unused post with the same volume and label,
 *       that post's URL replaces the duplicate.</li>
 *   <li><b>Add</b> a chapter the page never mentioned, matched by volume and label,
 *       so a late chapter still shows up without a new APK.</li>
 *   <li><b>Nothing else.</b> The page decides the volumes, the reading order and
 *       the labels; the feed can only fill a hole, never reshape the list.</li>
 * </ul>
 *
 * <p>Deduplication is by normalised URL, because that URL is what the reader, the
 * offline store and the progress record are keyed on. Two entries pointing at one
 * post would make one chapter unreadable and the other downloaded twice.
 */
public final class CatalogueMerge {

    /** What the merge changed, so the UI can say it instead of hiding it. */
    public static final class Result {
        public final List<Volume> volumes;
        /** Chapters taken from the feed because the page did not list them. */
        public final int added;
        /** Links the page printed twice and the feed could resolve. */
        public final int repaired;

        Result(List<Volume> volumes, int added, int repaired) {
            this.volumes = volumes;
            this.added = added;
            this.repaired = repaired;
        }

        public boolean changed() {
            return added > 0 || repaired > 0;
        }
    }

    /** One chapter candidate read from the feed. */
    private static final class Row {
        final int volume;
        final String label;
        final String title;
        final String url;

        Row(int volume, String label, String title, String url) {
            this.volume = volume;
            this.label = label;
            this.title = title;
            this.url = url;
        }
    }

    private CatalogueMerge() {
    }

    /**
     * @param novelId     id stored on every chapter's catalogue
     * @param pageVolumes volumes read from the catalogue page, in reading order
     * @param feedEntries every post carrying the novel's label; may be empty
     */
    public static Result merge(String novelId, List<Volume> pageVolumes,
                               List<FeedEntry> feedEntries) {
        List<Volume> source = pageVolumes == null ? new ArrayList<Volume>() : pageVolumes;
        List<FeedEntry> feed = feedEntries == null ? new ArrayList<FeedEntry>() : feedEntries;

        // Working copy: chapters per volume, still in the page's order.
        List<Integer> numbers = new ArrayList<>();
        List<List<Chapter>> working = new ArrayList<>();
        Set<String> pageUrls = new LinkedHashSet<>();
        for (Volume volume : source) {
            List<Chapter> copy = new ArrayList<>(volume.chapters);
            numbers.add(volume.number);
            working.add(copy);
            for (Chapter chapter : copy) pageUrls.add(normalise(chapter.url));
        }

        // Feed rows the page did not already carry. Volume and label come from the
        // post title, exactly as the feed path has always derived them.
        List<Row> spare = new ArrayList<>();
        for (FeedEntry entry : feed) {
            if (entry == null) continue;
            String url = normalise(entry.url);
            if (url.isEmpty() || pageUrls.contains(url)) continue;
            String title = entry.title == null ? "" : entry.title;
            if (!LibraryBuilder.isChapterPost(title)) continue;
            int volume = LibraryBuilder.extractVolume(title);
            if (volume <= 0) continue;
            spare.add(new Row(volume, LibraryBuilder.label(title), title, url));
        }

        int repaired = 0;
        for (int v = 0; v < working.size(); v++) {
            List<Chapter> chapters = working.get(v);
            int volume = numbers.get(v);
            // Pass 1 — a URL printed twice inside one volume. Only a feed post with
            // the same volume and label can say which entry was meant to point
            // elsewhere, so an unmatched duplicate is left alone for pass 2.
            Map<String, List<Integer>> byUrl = new LinkedHashMap<>();
            for (int i = 0; i < chapters.size(); i++) {
                String url = normalise(chapters.get(i).url);
                byUrl.computeIfAbsent(url, key -> new ArrayList<>()).add(i);
            }
            for (List<Integer> indexes : byUrl.values()) {
                if (indexes.size() < 2) continue;
                for (int index : indexes) {
                    Chapter chapter = chapters.get(index);
                    Row match = take(spare, volume, chapter.label);
                    if (match == null) continue;
                    chapters.set(index, copy(chapter, match.title, match.label, match.url));
                    repaired++;
                    break;
                }
            }
            // Pass 2 — whatever still points at one post twice collapses to the
            // entry that reads later, so the chapter itself survives once.
            Set<String> seen = new LinkedHashSet<>();
            List<Chapter> kept = new ArrayList<>();
            for (Chapter chapter : sorted(chapters)) {
                if (seen.add(normalise(chapter.url))) kept.add(chapter);
            }
            working.set(v, kept);
        }

        // Pass 3 — chapters the page never listed, in the volume they belong to.
        int added = 0;
        for (Row row : spare) {
            int slot = numbers.indexOf(row.volume);
            if (slot < 0) continue;
            List<Chapter> chapters = working.get(slot);
            boolean urlTaken = false;
            boolean labelTaken = false;
            for (Chapter chapter : chapters) {
                if (normalise(chapter.url).equals(row.url)) urlTaken = true;
                if (chapter.label.equalsIgnoreCase(row.label)) labelTaken = true;
            }
            if (urlTaken || labelTaken) continue;
            chapters.add(new Chapter(row.title, row.label, row.url, "",
                    ChapterNaming.order(row.title, row.label), row.volume));
            added++;
        }

        List<Volume> merged = new ArrayList<>();
        for (int v = 0; v < working.size(); v++) {
            List<Chapter> chapters = sorted(working.get(v));
            Volume original = source.get(v);
            merged.add(new Volume(numbers.get(v), chapters, original.coverUrl,
                    original.coverWidth, original.coverHeight));
        }
        return new Result(merged, added, repaired);
    }

    /** Unused feed row for this volume and label, removed from the pool when taken. */
    private static Row take(List<Row> spare, int volume, String label) {
        for (int i = 0; i < spare.size(); i++) {
            Row row = spare.get(i);
            if (row.volume == volume && row.label.equalsIgnoreCase(label)) {
                spare.remove(i);
                return row;
            }
        }
        return null;
    }

    private static Chapter copy(Chapter chapter, String title, String label, String url) {
        return new Chapter(title, label, url, chapter.published, chapter.order, chapter.volume);
    }

    private static List<Chapter> sorted(List<Chapter> chapters) {
        List<Chapter> out = new ArrayList<>(chapters);
        out.sort((left, right) -> {
            if (left.order != right.order) return left.order - right.order;
            return left.label.compareToIgnoreCase(right.label);
        });
        return out;
    }

    /** URL compared without the mobile suffix or a trailing slash. */
    static String normalise(String url) {
        if (url == null) return "";
        String text = url.trim();
        int query = text.indexOf('?');
        if (query >= 0) text = text.substring(0, query);
        while (text.endsWith("/")) text = text.substring(0, text.length() - 1);
        return text.toLowerCase(Locale.ROOT);
    }
}
