package id.shuyasal.kuromaku.data;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Groups feed entries into volumes and keeps chapters in reading order.
 *
 * <p>Every title on the source blog follows the shape
 * {@code Monogatari no Kuromaku Volume N <part>}, so the volume is taken from the
 * title rather than from the position in the feed. That keeps Volume 1 in
 * Volume 1 even when a later post mentions another volume number.
 */
public final class LibraryBuilder {

    private static final Pattern VOLUME = Pattern.compile("[Vv]olume\\s*(\\d+)");
    private static final Pattern CHAPTER = Pattern.compile("chapter\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    /** Reading order inside one volume. Lower sorts first. */
    static final int ORDER_PROLOG = 10;
    static final int ORDER_CHAPTER_BASE = 100;
    static final int ORDER_INTERLUDE = 380;
    static final int ORDER_SHORT_STORY = 400;
    static final int ORDER_SIDE_STORY = 420;
    static final int ORDER_BONUS = 460;
    static final int ORDER_EPILOG = 500;
    static final int ORDER_AFTERWORD = 520;
    static final int ORDER_ILLUSTRATION = 600;
    static final int ORDER_UNKNOWN = 300;

    private LibraryBuilder() {
    }

    /** Builds the catalogue for one novel. */
    public static Catalog build(String novelId, List<FeedEntry> entries) {
        return build(entries).withNovel(novelId);
    }

    public static Catalog build(List<FeedEntry> entries) {
        Map<Integer, List<Chapter>> grouped = new LinkedHashMap<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        Set<String> seenSlots = new LinkedHashSet<>();

        for (FeedEntry entry : entries) {
            if (entry == null || entry.url.isEmpty()) continue;
            if (!seenUrls.add(entry.url)) continue;

            int volume = extractVolume(entry.title);
            String label = label(entry.title);

            // Same volume + same label means the same chapter posted twice.
            if (!seenSlots.add(volume + "|" + label.toLowerCase(Locale.ROOT))) continue;

            Chapter chapter = new Chapter(entry.title, label, entry.url, entry.published,
                    order(entry.title), volume);
            List<Chapter> list = grouped.get(volume);
            if (list == null) {
                list = new ArrayList<>();
                grouped.put(volume, list);
            }
            list.add(chapter);
        }

        final Collator collator = Collator.getInstance(new Locale("id", "ID"));
        List<Volume> volumes = new ArrayList<>();
        for (Map.Entry<Integer, List<Chapter>> group : grouped.entrySet()) {
            List<Chapter> chapters = group.getValue();
            Collections.sort(chapters, new Comparator<Chapter>() {
                @Override
                public int compare(Chapter left, Chapter right) {
                    if (left.order != right.order) return left.order - right.order;
                    return collator.compare(left.label, right.label);
                }
            });
            volumes.add(new Volume(group.getKey(), chapters));
        }

        // Volumes ascending by number; the volume-less landing page goes last.
        Collections.sort(volumes, new Comparator<Volume>() {
            @Override
            public int compare(Volume left, Volume right) {
                boolean leftUnknown = left.number <= 0;
                boolean rightUnknown = right.number <= 0;
                if (leftUnknown != rightUnknown) return leftUnknown ? 1 : -1;
                return left.number - right.number;
            }
        });

        return new Catalog(volumes);
    }

    /** Volume number from the title, 0 when the title has none. */
    public static int extractVolume(String title) {
        if (title == null) return 0;
        Matcher matcher = VOLUME.matcher(title);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * Short chapter label such as "Chapter 5", "Prolog", "Short Story 2" or
     * "Illustrasi". Descriptor keywords win over the leftover text, because a
     * title like "... Illustrasi Volume 3" leaves nothing after the volume match.
     */
    public static String label(String title) {
        if (title == null || title.trim().isEmpty()) return "Bab";
        String text = title.toLowerCase(Locale.ROOT);

        Matcher chapter = CHAPTER.matcher(text);
        if (chapter.find()) return "Chapter " + chapter.group(1);

        String numbered = numberedDescriptor(text);
        if (numbered != null) return numbered;

        if (text.contains("prolog") || text.contains("prologue")) return "Prolog";
        if (text.contains("interlude")) return "Interlude";
        if (text.contains("bonus")) return "Bonus Story";
        if (text.contains("epilog") || text.contains("epilogue")) return "Epilog";
        if (text.contains("afterword") || text.contains("kata penutup")) return "Afterword";
        if (text.contains("illustra")) return "Illustrasi";
        if (text.contains("short story")) return "Short Story";
        if (text.contains("side story")) return "Side Story";

        // Fallback: text after the volume marker, trimmed of decoration.
        String remainder = title;
        Matcher volumeMatcher = VOLUME.matcher(remainder);
        if (volumeMatcher.find()) remainder = remainder.substring(volumeMatcher.end());
        remainder = remainder.replaceAll("^[\\s\\-:–—~]+", "")
                .replaceAll("[\\s\\-:–—~]+$", "")
                .trim();
        if (remainder.isEmpty()) return "Bab";
        if (remainder.length() > 48) remainder = remainder.substring(0, 47) + "…";
        return Character.toUpperCase(remainder.charAt(0)) + remainder.substring(1);
    }

    /** Matches "Short Story 2", "Side Story 1", "Illustrasi Volume 3" style extras. */
    private static String numberedDescriptor(String lower) {
        Matcher shortStory = Pattern.compile("short\\s*story\\s*(\\d+)").matcher(lower);
        if (shortStory.find()) return "Short Story " + shortStory.group(1);

        Matcher sideStory = Pattern.compile("side\\s*story\\s*(\\d+)").matcher(lower);
        if (sideStory.find()) return "Side Story " + sideStory.group(1);
        return null;
    }

    /** Reading order inside a volume: front matter, chapters, extras, back matter. */
    public static int order(String title) {
        if (title == null) return ORDER_UNKNOWN;
        String text = title.toLowerCase(Locale.ROOT);

        Matcher shortStory = Pattern.compile("short\\s*story\\s*(\\d+)").matcher(text);
        if (shortStory.find()) return ORDER_SHORT_STORY + parseInt(shortStory.group(1), 0);

        Matcher sideStory = Pattern.compile("side\\s*story\\s*(\\d+)").matcher(text);
        if (sideStory.find()) return ORDER_SIDE_STORY + parseInt(sideStory.group(1), 0);

        Matcher chapter = CHAPTER.matcher(text);
        if (chapter.find()) return ORDER_CHAPTER_BASE + parseInt(chapter.group(1), 100);

        if (text.contains("prolog") || text.contains("prologue")) return ORDER_PROLOG;
        if (text.contains("interlude")) return ORDER_INTERLUDE;
        if (text.contains("bonus")) return ORDER_BONUS;
        if (text.contains("epilog") || text.contains("epilogue")) return ORDER_EPILOG;
        if (text.contains("afterword") || text.contains("kata penutup")) return ORDER_AFTERWORD;
        if (text.contains("illustra")) return ORDER_ILLUSTRATION;
        if (text.contains("short story")) return ORDER_SHORT_STORY;
        if (text.contains("side story")) return ORDER_SIDE_STORY;
        return ORDER_UNKNOWN;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
