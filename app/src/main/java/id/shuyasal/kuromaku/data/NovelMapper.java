package id.shuyasal.kuromaku.data;

import id.shuyasal.kuromaku.db.NovelRecord;

/**
 * Converts between the stored row and the immutable {@link Novel} the rest of the
 * app reads.
 *
 * <p>Kept in one place so a field can never be written but never read back: every
 * column that exists on {@link NovelRecord} is mapped here in both directions, and
 * the synopsis is stored as paragraphs separated by a blank line so an empty
 * synopsis stays empty instead of becoming a single empty string.
 */
public final class NovelMapper {

    private static final String PARAGRAPH = "\n\n";

    private NovelMapper() {
    }

    public static Novel toNovel(NovelRecord record) {
        if (record == null) return null;
        return new Novel(
                record.id,
                record.title,
                record.titleJp,
                record.fullTitle,
                record.author,
                record.illustrator,
                record.genres,
                record.status,
                record.translation,
                record.sourceLabelUrl,
                record.url,
                record.coverUrl,
                record.coverRatio,
                paragraphs(record.synopsis),
                record.synopsisPreview,
                record.catalogMode,
                record.slugPrefix,
                record.feedLabel,
                record.builtIn);
    }

    /** New row for a user-added novel; the id is the URL slug. */
    public static NovelRecord toRecord(Novel novel, int position) {
        NovelRecord record = new NovelRecord();
        apply(record, novel);
        record.position = position;
        record.addedAt = System.currentTimeMillis();
        return record;
    }

    /** Copies editable metadata onto an existing row, keeping identity and order. */
    public static void apply(NovelRecord record, Novel novel) {
        record.id = novel.id == null ? "" : novel.id;
        record.url = novel.landingUrl == null ? "" : novel.landingUrl;
        record.title = safe(novel.title);
        record.titleJp = safe(novel.titleJp);
        record.fullTitle = safe(novel.fullTitle);
        record.author = safe(novel.author);
        record.illustrator = safe(novel.illustrator);
        record.genres = safe(novel.genres);
        record.status = safe(novel.status);
        record.translation = safe(novel.translation);
        record.sourceLabelUrl = safe(novel.sourceLabelUrl);
        record.coverUrl = safe(novel.coverUrl);
        record.coverRatio = novel.coverRatio > 0 ? novel.coverRatio : 0.70f;
        record.synopsis = join(novel.synopsis);
        record.synopsisPreview = novel.synopsisPreview;
        record.catalogMode = novel.catalogMode;
        record.slugPrefix = safe(novel.slugPrefix);
        record.feedLabel = safe(novel.feedLabel);
        record.builtIn = novel.builtIn;
    }

    static String join(String[] paragraphs) {
        if (paragraphs == null || paragraphs.length == 0) return "";
        StringBuilder out = new StringBuilder();
        for (String paragraph : paragraphs) {
            String text = paragraph == null ? "" : paragraph.trim();
            if (text.isEmpty()) continue;
            if (out.length() > 0) out.append(PARAGRAPH);
            out.append(text);
        }
        return out.toString();
    }

    static String[] paragraphs(String stored) {
        if (stored == null || stored.trim().isEmpty()) return new String[0];
        return stored.split("\n\n+");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
