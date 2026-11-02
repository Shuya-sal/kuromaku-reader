package id.shuyasal.kuromaku.data;

/** A single chapter entry taken from the source label feed. */
public final class Chapter {

    /** Original post title as published on the source blog. */
    public final String title;
    /** Short label shown in the library, e.g. "Chapter 5" or "Epilog". */
    public final String label;
    /** Canonical post URL. */
    public final String url;
    /** Publication date, yyyy-MM-dd. */
    public final String published;
    /** Sort key inside a volume. */
    public final int order;
    /** Volume number parsed from the title, 0 when unknown. */
    public final int volume;

    public Chapter(String title, String label, String url, String published, int order, int volume) {
        this.title = title == null ? "" : title;
        this.label = label == null || label.isEmpty() ? this.title : label;
        this.url = url == null ? "" : url;
        this.published = published == null ? "" : published;
        this.order = order;
        this.volume = volume;
    }
}
