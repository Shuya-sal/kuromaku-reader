package id.shuyasal.kuromaku.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Groups feed entries into volumes and keeps chapters in reading order. */
public final class LibraryBuilder {

    private static final Pattern VOLUME = Pattern.compile("[Vv]olume\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHAPTER = Pattern.compile("chapter\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHORT_STORY = Pattern.compile("short\\s*story\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SIDE_STORY = Pattern.compile("side\\s*story\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private LibraryBuilder() {
    }

    public static Catalog build(List<FeedEntry> entries) {
        Map<Integer, List<Chapter>> grouped = new LinkedHashMap<>();

        for (FeedEntry entry : entries) {
            if (entry.url.isEmpty()) continue;
            int volume = extractVolume(entry.title);
            String label = label(entry.title, volume);
            Chapter chapter = new Chapter(entry.title, label, entry.url, entry.published,
                    order(entry.title), volume);
            List<Chapter> list = grouped.get(volume);
            if (list == null) {
                list = new ArrayList<>();
                grouped.put(volume, list);
            }
            list.add(chapter);
        }

        List<Volume> volumes = new ArrayList<>();
        for (Map.Entry<Integer, List<Chapter>> group : grouped.entrySet()) {
            List<Chapter> chapters = group.getValue();
            Collections.sort(chapters, new Comparator<Chapter>() {
                @Override
                public int compare(Chapter left, Chapter right) {
                    if (left.order != right.order) return left.order - right.order;
                    return left.title.compareToIgnoreCase(right.title);
                }
            });
            volumes.add(new Volume(group.getKey(), chapters));
        }

        Collections.sort(volumes, new Comparator<Volume>() {
            @Override
            public int compare(Volume left, Volume right) {
                return left.number - right.number;
            }
        });

        return new Catalog(volumes);
    }

    static int extractVolume(String title) {
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

    /** Short chapter label, e.g. "Chapter 5", "Prolog", "Epilog", "Afterword". */
    static String label(String title, int volume) {
        if (title == null) return "";
        String text = title;
        Matcher volumeMatcher = VOLUME.matcher(text);
        if (volumeMatcher.find()) {
            text = text.substring(volumeMatcher.end());
        }
        text = text.replaceAll("^[\\s\\-:–—]+", "").trim();
        text = text.replaceAll("^\\[LN\\].*?[Vv]olume\\s*\\d+", "").trim();
        text = text.replaceAll("^~.*?~\\s*", "").trim();
        if (text.isEmpty()) return volume > 0 ? "Volume " + volume : "Bab";
        if (text.length() > 48) text = text.substring(0, 47) + "…";
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /** Reading order inside a volume: front matter, chapters, extras, back matter. */
    static int order(String title) {
        if (title == null) return 5000;
        String text = title.toLowerCase();

        Matcher chapter = CHAPTER.matcher(text);
        if (chapter.find()) {
            int number = parseInt(chapter.group(1), 100);
            return 100 + number;
        }
        Matcher shortStory = SHORT_STORY.matcher(text);
        if (shortStory.find()) {
            return 400 + parseInt(shortStory.group(1), 0);
        }
        Matcher sideStory = SIDE_STORY.matcher(text);
        if (sideStory.find()) {
            return 420 + parseInt(sideStory.group(1), 0);
        }
        if (text.contains("prolog") || text.contains("prologue")) return 10;
        if (text.contains("interlude")) return 380;
        if (text.contains("bonus")) return 460;
        if (text.contains("epilog") || text.contains("epilogue")) return 500;
        if (text.contains("afterword")) return 520;
        if (text.contains("illustra")) return 600;
        return 300;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
