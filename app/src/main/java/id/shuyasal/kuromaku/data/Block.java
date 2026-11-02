package id.shuyasal.kuromaku.data;

/**
 * One renderable unit of a chapter, kept in document order so text and
 * illustrations stay interleaved exactly like the original article.
 *
 * <p>Image blocks carry the dimensions declared by the source
 * ({@code data-original-width}/{@code data-original-height}). Knowing the ratio
 * up front lets the reader reserve the right box before the download finishes,
 * which is what keeps illustrations visible and stops the layout from
 * collapsing them to zero width.
 */
public final class Block {

    public static final int TYPE_PARAGRAPH = 0;
    public static final int TYPE_HEADING = 1;
    public static final int TYPE_QUOTE = 2;
    public static final int TYPE_LIST_ITEM = 3;
    public static final int TYPE_IMAGE = 4;
    public static final int TYPE_DIVIDER = 5;

    public final int type;
    public final String text;
    public final String imageUrl;
    public final int level;

    /** Intrinsic width of the illustration in px, 0 when the source omits it. */
    public final int imageWidth;
    /** Intrinsic height of the illustration in px, 0 when the source omits it. */
    public final int imageHeight;

    private Block(int type, String text, String imageUrl, int level,
                  int imageWidth, int imageHeight) {
        this.type = type;
        this.text = text;
        this.imageUrl = imageUrl;
        this.level = level;
        this.imageWidth = Math.max(0, imageWidth);
        this.imageHeight = Math.max(0, imageHeight);
    }

    public static Block paragraph(String text) {
        return new Block(TYPE_PARAGRAPH, text, null, 0, 0, 0);
    }

    public static Block heading(String text, int level) {
        return new Block(TYPE_HEADING, text, null, level, 0, 0);
    }

    public static Block quote(String text) {
        return new Block(TYPE_QUOTE, text, null, 0, 0, 0);
    }

    public static Block listItem(String text) {
        return new Block(TYPE_LIST_ITEM, text, null, 0, 0, 0);
    }

    public static Block image(String url, String alt) {
        return new Block(TYPE_IMAGE, alt, url, 0, 0, 0);
    }

    public static Block image(String url, String alt, int width, int height) {
        return new Block(TYPE_IMAGE, alt, url, 0, width, height);
    }

    public static Block divider() {
        return new Block(TYPE_DIVIDER, null, null, 0, 0, 0);
    }

    public boolean isImage() {
        return type == TYPE_IMAGE;
    }

    /** True when the source declared usable pixel dimensions for this image. */
    public boolean hasImageSize() {
        return type == TYPE_IMAGE && imageWidth > 0 && imageHeight > 0;
    }

    /** Width divided by height, or 0 when unknown. */
    public float imageRatio() {
        if (!hasImageSize()) return 0f;
        return imageWidth / (float) imageHeight;
    }

    @Override
    public String toString() {
        if (type == TYPE_IMAGE) return "IMAGE(" + imageUrl + ")";
        if (type == TYPE_DIVIDER) return "DIVIDER";
        return "TEXT[" + type + "](" + text + ")";
    }
}
