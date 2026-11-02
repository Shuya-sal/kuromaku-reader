package id.shuyasal.kuromaku.data;

/** Minimal feed record: everything the library needs from a Blogger entry. */
public final class FeedEntry {

    public final String title;
    public final String url;
    public final String published;

    public FeedEntry(String title, String url, String published) {
        this.title = title == null ? "" : title.trim();
        this.url = url == null ? "" : url.trim();
        this.published = published == null ? "" : published.trim();
    }
}
