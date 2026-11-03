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

    /**
     * Absolute path of the downloaded copy of this illustration, or {@code null}
     * when the picture lives online. When it is set the reader must load the file
     * instead of the URL: that is what makes illustrations survive airplane mode.
     * {@link #imageUrl} is always kept for attribution and re-download.
     */
    public final String imagePath;

    private Block(int type, String text, String imageUrl, int level,
                  int imageWidth, int imageHeight) {
        this(type, text, imageUrl, level, imageWidth, imageHeight, null);
    }

    private Block(int type, String text, String imageUrl, int level,
                  int imageWidth, int imageHeight, String imagePath) {
        this.type = type;
        this.text = text;
        this.imageUrl = imageUrl;
        this.level = level;
        this.imageWidth = Math.max(0, imageWidth);
        this.imageHeight = Math.max(0, imageHeight);
        String path = imagePath == null ? "" : imagePath.trim();
        this.imagePath = path.isEmpty() ? null : path;
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

    /**
     * Rebuilds a block read from the offline store.
     *
     * <p>{@code localPath} is the downloaded illustration on disk; it is kept
     * alongside {@code url} so the reader can render from the file and still
     * know the original source of the picture.
     */
    public static Block restored(int type, String text, String url, int level,
                                 int width, int height, String localPath) {
        return new Block(type, text, url, level, width, height, localPath);
    }

    /** True when this illustration has a downloaded copy that can be rendered offline. */
    public boolean hasLocalImage() {
        return type == TYPE_IMAGE && imagePath != null;
    }

    /**
     * What the image loader should open: the downloaded file when it exists,
     * otherwise the remote URL. Keeps "read from disk first" in one place so no
     * call site can accidentally prefer the network.
     */
    public String imageSource() {
        return hasLocalImage() ? imagePath : imageUrl;
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
