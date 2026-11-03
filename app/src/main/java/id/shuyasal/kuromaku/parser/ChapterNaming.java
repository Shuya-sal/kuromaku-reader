package id.shuyasal.kuromaku.parser;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How a chapter link is read: its short label and its place in the volume.
 *
 * <p>Shared by the generic discovery parser and the per-novel landing parser so a
 * new novel gets exactly the same treatment as the two built in. Sources are not
 * consistent with each other — one writes "Chapter 12", another "Bab 3", another
 * "Ch. 4", another "Vol. 2 Chapter 1", another "Interlude III" — and one blog even
 * misspells its own label ("Cahpter 2"), so every spelling is understood here, in
 * one place only.
 *
 * <p>Order is only a tie-breaker in the catalogue parser: the document order of
 * the links is what the translator laid out, so a page that prints "Illustrasi"
 * before "Prolog" keeps that order instead of being re-sorted by a rule the page
 * never agreed to.
 */
public final class ChapterNaming {

    public static final int ORDER_ILLUSTRATION = 5;
    public static final int ORDER_PROLOG = 10;
    public static final int ORDER_CHAPTER_BASE = 100;
    public static final int ORDER_INTERLUDE = 380;
    public static final int ORDER_EPILOG = 500;
    public static final int ORDER_AFTERWORD = 520;
    public static final int ORDER_UNKNOWN = 300;

    /** One numeral per index, 1..20; index 0 is unused. */
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII",
            "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII",
            "XIX", "XX"};

    /** "Volume 7" / "Vol. 7" heading → 7; anything else → 0. */
    private static final Pattern VOLUME_HEADING = Pattern.compile(
            "^\\s*(?:volume|vol)\\.?\\s*(\\d{1,2})\\s*$", Pattern.CASE_INSENSITIVE);

    /**
     * A numbered chapter in any spelling these sources use: Chapter, Chapt, Chap,
     * Ch, Bab — plus the source's own misspelling "Cahpter".
     */
    private static final Pattern CHAPTER = Pattern.compile(
            "\\b(?:chapter|chapt|cahpter|chap|ch|bab)\\s*[.:\\-]?\\s*(\\d{1,3})\\b",
            Pattern.CASE_INSENSITIVE);

    /** "Chapter 8+Afterword", "Epilog+Afterword". */
    private static final Pattern EXTRA = Pattern.compile(
            "(afterword|epilog\\w*|ilustrasi|illustrasi|illustration|prolog\\w*)",
            Pattern.CASE_INSENSITIVE);

    /** "Interlude II", "Interlude 2", "Interlude" — Roman numerals are common. */
    private static final Pattern INTERLUDE = Pattern.compile(
            "\\binterlude\\s*([ivxlcdm]{1,6}|\\d{1,3})?\\b", Pattern.CASE_INSENSITIVE);

    /** Any mention of a volume inside link text: "Volume 3", "Vol. 2". */
    private static final Pattern VOLUME_IN_TEXT = Pattern.compile(
            "\\b(?:volume|vol)\\.?\\s*(\\d{1,2})\\b", Pattern.CASE_INSENSITIVE);

    /** Volume in a slug: ...-volume-6-chapter-3, ...vol-2... */
    private static final Pattern VOLUME_IN_URL = Pattern.compile(
            "\\bvol(?:ume)?[-_. ]?(\\d{1,2})\\b", Pattern.CASE_INSENSITIVE);

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
        return number(matcher.group(1));
    }

    /** True while the accumulated text could still become a volume heading. */
    public static boolean looksLikeVolumeStart(String text) {
        String normalised = tidy(text).toLowerCase(Locale.ROOT);
        if (normalised.isEmpty()) return true;
        if (normalised.length() > 20) return false;
        return "volume".startsWith(normalised) || "vol".startsWith(normalised)
                || normalised.startsWith("volume") || normalised.startsWith("vol");
    }

    /** Volume number taken from a slug such as {@code ...-volume-6-chapter-3}. */
    public static int volumeFromUrl(String href) {
        if (href == null) return 0;
        Matcher matcher = VOLUME_IN_URL.matcher(href);
        if (!matcher.find()) return 0;
        return number(matcher.group(1));
    }

    /** Volume number written in the link text ("Volume 3 Chapter 2"), 0 when absent. */
    public static int volumeIn(String text) {
        if (text == null) return 0;
        Matcher matcher = VOLUME_IN_TEXT.matcher(text);
        if (!matcher.find()) return 0;
        return number(matcher.group(1));
    }

    /** True when the text names a volume at all ("Volume 3", "Vol. 2"). */
    public static boolean namesVolume(String text) {
        return text != null && VOLUME_IN_TEXT.matcher(text).find();
    }

    /** Chapter number written in the text, 0 when the text has none. */
    public static int chapterNumber(String text) {
        if (text == null) return 0;
        Matcher matcher = CHAPTER.matcher(text);
        if (!matcher.find()) return number(matcher.group(1));
        return 0;
    }

    /**
     * Short label shown in the library; every spelling is normalised here so the
     * list never shows "CH. 3" next to "Bab 4" next to "Chapter 5".
     */
    /** 1–20 written as a Roman numeral; anything else returned as typed, upper case. */
    private static String romanLabel(String numeral) {
        String upper = numeral.toUpperCase(Locale.ROOT);
        int value;
        try {
            value = Integer.parseInt(upper);
        } catch (NumberFormatException alreadyRoman) {
            return upper;
        }
        if (value < 1 || value > 20) return String.valueOf(value);
        return ROMAN[value];
    }

    public static String label(String raw) {
        if (raw == null) return "Bab";
        String text = tidy(raw);
        if (text.isEmpty()) return "Bab";

        Matcher chapter = CHAPTER.matcher(text);
        if (chapter.find()) {
            String base = "Chapter " + number(chapter.group(1));
            Matcher extra = EXTRA.matcher(text);
            if (extra.find()) {
                String tail = extra.group(1).toLowerCase(Locale.ROOT);
                if (tail.startsWith("afterword")) base += " + Afterword";
                else if (tail.startsWith("epilog")) base += " + Epilog";
            }
            return base;
        }

        String lower = text.toLowerCase(Locale.ROOT);
        Matcher interlude = INTERLUDE.matcher(text);
        if (interlude.find()) {
            String numeral = tidy(interlude.group(1));
            if (numeral.isEmpty()) return "Interlude";
            // The source writes "Interlude II" in one volume and splits it into
            // "Interlude" + "2" in another; both are the same chapter, so the label is
            // normalised to Roman numerals instead of depending on how it was typed.
            return "Interlude " + romanLabel(numeral);
        }
        if (lower.contains("prolog")) return "Prolog";
        if (lower.contains("ilustrasi") || lower.contains("illustrasi")
                || lower.contains("illustration")) return "Illustrasi";
        boolean epilog = lower.contains("epilog");
        boolean afterword = lower.contains("afterword") || lower.contains("kata penutup");
        if (epilog && afterword) return "Epilog + Afterword";
        if (epilog) return "Epilog";
        if (afterword) return "Afterword";
        if (lower.contains("short story")) return "Short Story";
        if (lower.contains("side story")) return "Side Story";
        if (text.length() > 48) return text.substring(0, 47) + "…";
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /** Reading order hint inside a volume; the document order always wins. */
    public static int order(String raw, String label) {
        if (raw == null) return ORDER_UNKNOWN;
        String lower = raw.toLowerCase(Locale.ROOT);

        Matcher chapter = CHAPTER.matcher(raw);
        if (chapter.find()) return ORDER_CHAPTER_BASE + number(chapter.group(1));

        Matcher interlude = INTERLUDE.matcher(raw);
        if (interlude.find()) return ORDER_INTERLUDE + roman(interlude.group(1));
        if (lower.contains("prolog")) return ORDER_PROLOG;
        if (lower.contains("ilustrasi") || lower.contains("illustrasi")
                || lower.contains("illustration")) return ORDER_ILLUSTRATION;
        if (lower.contains("epilog")) return ORDER_EPILOG;
        if (lower.contains("afterword")) return ORDER_AFTERWORD;
        return ORDER_UNKNOWN;
    }

    /** True when the label was recognised as a known part of a volume. */
    public static boolean isRecognised(String raw) {
        if (raw == null) return false;
        String lower = raw.toLowerCase(Locale.ROOT);
        return CHAPTER.matcher(raw).find()
                || INTERLUDE.matcher(raw).find()
                || lower.contains("prolog") || lower.contains("ilustrasi")
                || lower.contains("illustrasi") || lower.contains("illustration")
                || lower.contains("epilog") || lower.contains("afterword")
                || lower.contains("kata penutup");
    }

    /**
     * Roman numeral → value, 0 when the text is not one. Used by "Interlude III";
     * plain digits pass through so both spellings sort identically.
     */
    public static int roman(String text) {
        String value = text == null ? "" : text.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) return 0;
        if (value.matches("\\d{1,3}")) return number(value);

        int total = 0;
        int previous = 0;
        for (int i = value.length() - 1; i >= 0; i--) {
            int current;
            switch (value.charAt(i)) {
                case 'I': current = 1; break;
                case 'V': current = 5; break;
                case 'X': current = 10; break;
                case 'L': current = 50; break;
                case 'C': current = 100; break;
                case 'D': current = 500; break;
                case 'M': current = 1000; break;
                default: return 0;
            }
            if (current < previous) total -= current;
            else total += current;
            previous = current;
        }
        return total;
    }

    private static int number(String digits) {
        if (digits == null) return 0;
        try {
            return Integer.parseInt(digits.trim());
        } catch (NumberFormatException ignored) {
            return roman(digits);
        }
    }
}
