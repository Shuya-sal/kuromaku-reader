package id.shuyasal.kuromaku.data;

/**
 * One renderable unit of a chapter, kept in document order so text and
 * illustrations stay interleaved exactly like the original article.
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

    private Block(int type, String text, String imageUrl, int level) {
        this.type = type;
        this.text = text;
        this.imageUrl = imageUrl;
        this.level = level;
    }

    public static Block paragraph(String text) {
        return new Block(TYPE_PARAGRAPH, text, null, 0);
    }

    public static Block heading(String text, int level) {
        return new Block(TYPE_HEADING, text, null, level);
    }

    public static Block quote(String text) {
        return new Block(TYPE_QUOTE, text, null, 0);
    }

    public static Block listItem(String text) {
        return new Block(TYPE_LIST_ITEM, text, null, 0);
    }

    public static Block image(String url, String alt) {
        return new Block(TYPE_IMAGE, alt, url, 0);
    }

    public static Block divider() {
        return new Block(TYPE_DIVIDER, null, null, 0);
    }

    public boolean isImage() {
        return type == TYPE_IMAGE;
    }

    @Override
    public String toString() {
        if (type == TYPE_IMAGE) return "IMAGE(" + imageUrl + ")";
        if (type == TYPE_DIVIDER) return "DIVIDER";
        return "TEXT[" + type + "](" + text + ")";
    }
}
