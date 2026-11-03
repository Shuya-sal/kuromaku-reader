package id.shuyasal.kuromaku.parser;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How a chapter link is read: its short label and its place in the volume.
 *
 * <p>Shared by the generic discovery parser and the per-novel landing parser so a
 * new novel gets exactly the same treatment as the two built in: the source's own
 * typos ("Cahpter 2"), the special entries (Prolog, Interlude, Epilog, Afterword,
 * Illustrasi) and the volume number written inside the slug are all understood in
 * one place only.
 */
public final class ChapterNaming {

    public static final int ORDER_PROLOG = 10;
    public static final int ORDER_CHAPTER_BASE = 100;
    public static final int ORDER_INTERLUDE = 380;
    public static final int ORDER_EPILOG = 500;
    public static final int ORDER_AFTERWORD = 520;
    public static final int ORDER_ILLUSTRATION = 600;
    public static final int ORDER_UNKNOWN = 300;

    /** "Volume 7" heading → 7; anything else → 0. */
    private static final Pattern VOLUME_HEADING =
            Pattern.compile("^\\s*volume\\s*(\\d{1,2})\\s*$", Pattern.CASE_INSENSITIVE);

    /**
     * "Chapter 7" plus the source's own misspellings. The page spells one entry as
     * "Cahpter 2", so the letters are allowed to swap.
     */
    private static final Pattern CHAPTER =
            Pattern.compile("(?:ch(?:ap|pa)ter|cahpter|cap?hter)\\s*(\\d{1,3})",
                    Pattern.CASE_INSENSITIVE);

    /** "Chapter 8+Afterword", "Epilog+Afterword". */
    private static final Pattern EXTRA =
            Pattern.compile("(afterword|epilog\\w*|ilustrasi|illustrasi|illustration|prolog\\w*)",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern VOLUME_IN_URL =
            Pattern.compile("volume[-_ ]?(\\d{1,2})", Pattern.CASE_INSENSITIVE);

    private ChapterNaming() {
    }

    /** Collapses non-breaking spaces and runs of whitespace. */
    public static String tidy(String text) {
        if (text == null) return "";
        return text.replace('\u00a0', ' ').replace('\u200b', ' ')
                .replaceAll("\\s+", " ").trim();
    }

    /** "Volume 7" heading → 7; anything else → 0. */
    public static int volumeHeading(String text) {
        String normalised = tidy(text);
        if (normalised.isEmpty()) return 0;
        Matcher matcher = VOLUME_HEADING.matcher(normalised);
        if (!matcher.find()) return 0;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /** True while the accumulated text could still become a volume heading. */
    public static boolean looksLikeVolumeStart(String text) {
        String normalised = tidy(text).toLowerCase(Locale.ROOT);
        if (normalised.isEmpty()) return true;
        if (normalised.length() > 20) return false;
        return "volume".startsWith(normalised) || normalised.startsWith("volume");
    }

    /** Volume number taken from a slug such as {@code ...-volume-6-chapter-3}. */
    public static int volumeFromUrl(String href) {
        if (href == null) return 0;
        Matcher matcher = VOLUME_IN_URL.matcher(href);
        if (!matcher.find()) return 0;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /** Short label shown in the library; misspellings are corrected here. */
    public static String label(String raw) {
        if (raw == null) return "Bab";
        String text = tidy(raw);

        Matcher chapter = CHAPTER.matcher(text);
        if (chapter.find()) {
            int number = number(chapter.group(1));
            String base = "Chapter " + number;
            Matcher extra = EXTRA.matcher(text);
            if (extra.find()) {
                String tail = extra.group(1).toLowerCase(Locale.ROOT);
                if (tail.startsWith("afterword")) base += " + Afterword";
                else if (tail.startsWith("epilog")) base += " + Epilog";
            }
            return base;
        }

        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("prolog")) return "Prolog";
        if (lower.contains("interlude")) return "Interlude";
        if (lower.contains("ilustrasi") || lower.contains("illustrasi")
                || lower.contains("illustration")) return "Illustrasi";
        boolean epilog = lower.contains("epilog");
        boolean afterword = lower.contains("afterword") || lower.contains("kata penutup");
        if (epilog && afterword) return "Epilog + Afterword";
        if (epilog) return "Epilog";
        if (afterword) return "Afterword";
        if (text.length() > 48) return text.substring(0, 47) + "…";
        return text;
    }

    /** Reading order inside a volume. Chapter 0 sorts before Chapter 1. */
    public static int order(String raw, String label) {
        if (raw == null) return ORDER_UNKNOWN;
        String lower = raw.toLowerCase(Locale.ROOT);

        Matcher chapter = CHAPTER.matcher(raw);
        if (chapter.find()) {
            return ORDER_CHAPTER_BASE + number(chapter.group(1));
        }
        if (lower.contains("prolog")) return ORDER_PROLOG;
        if (lower.contains("interlude")) return ORDER_INTERLUDE;
        if (lower.contains("ilustrasi") || lower.contains("illustrasi")
                || lower.contains("illustration")) return ORDER_ILLUSTRATION;
        if (lower.contains("epilog")) return ORDER_EPILOG;
        if (lower.contains("afterword")) return ORDER_AFTERWORD;
        return ORDER_UNKNOWN;
    }

    /** True when the label was recognised as a known part of a volume. */
    public static boolean isRecognised(String raw, String label) {
        if (raw == null) return false;
        String lower = raw.toLowerCase(Locale.ROOT);
        return CHAPTER.matcher(raw).find()
                || lower.contains("prolog") || lower.contains("interlude")
                || lower.contains("ilustrasi") || lower.contains("illustrasi")
                || lower.contains("illustration") || lower.contains("epilog")
                || lower.contains("afterword") || lower.contains("kata penutup");
    }

    private static int number(String digits) {
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
