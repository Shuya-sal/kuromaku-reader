package id.shuyasal.kuromaku.shinigami;

public final class ShinigamiChapter {
    public final String id;
    public final String mangaId;
    public final double number;
    public final String title;
    public final String releaseDate;
    public final long releaseDateMillis;
    public final long updatedAtMillis;
    public final String thumbnailUrl;

    public ShinigamiChapter(String id, String mangaId, double number, String title,
                            String releaseDate, String thumbnailUrl) {
        this(id, mangaId, number, title, releaseDate, thumbnailUrl, "");
    }

    public ShinigamiChapter(String id, String mangaId, double number, String title,
                            String releaseDate, String thumbnailUrl, String updatedAt) {
        this.id = id == null ? "" : id;
        this.mangaId = mangaId == null ? "" : mangaId;
        this.number = Double.isNaN(number) || Double.isInfinite(number) ? 0d : number;
        this.title = title == null ? "" : title;
        this.releaseDate = releaseDate == null ? "" : releaseDate;
        this.releaseDateMillis = ShinigamiDates.parse(releaseDate);
        this.updatedAtMillis = ShinigamiDates.parse(updatedAt);
        this.thumbnailUrl = thumbnailUrl == null ? "" : thumbnailUrl;
    }

    public String dateLabel() {
        if (releaseDateMillis > 0) return "Rilis: " + ShinigamiDates.format(releaseDateMillis);
        if (updatedAtMillis > 0) return "Diperbarui: " + ShinigamiDates.format(updatedAtMillis);
        return "Tanggal belum tersedia";
    }

    public String label() {
        return ShinigamiManga.chapterLabel(number, title);
    }
}
