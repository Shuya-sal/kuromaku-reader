package id.shuyasal.kuromaku.shinigami;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class ShinigamiManga {
    public final String id;
    public final String title;
    public final String alternativeTitle;
    public final String coverUrl;
    public final String description;
    public final int status;
    public final String releaseYear;
    public final double rating;
    public final long viewCount;
    public final double latestChapterNumber;
    public final String latestChapterTime;
    public final long latestChapterTimeMillis;
    public final long updatedAtMillis;
    public final List<String> genres;
    public final List<String> authors;
    public final String format;

    public ShinigamiManga(String id, String title, String alternativeTitle, String coverUrl,
                          String description, int status, String releaseYear, double rating,
                          long viewCount, double latestChapterNumber, String latestChapterTime,
                          List<String> genres, List<String> authors, String format) {
        this(id, title, alternativeTitle, coverUrl, description, status, releaseYear, rating,
                viewCount, latestChapterNumber, latestChapterTime, genres, authors, format, "");
    }

    public ShinigamiManga(String id, String title, String alternativeTitle, String coverUrl,
                          String description, int status, String releaseYear, double rating,
                          long viewCount, double latestChapterNumber, String latestChapterTime,
                          List<String> genres, List<String> authors, String format, String updatedAt) {
        this.id = id == null ? "" : id;
        this.title = title == null ? "" : title;
        this.alternativeTitle = alternativeTitle == null ? "" : alternativeTitle;
        this.coverUrl = coverUrl == null ? "" : coverUrl;
        this.description = description == null ? "" : description;
        this.status = status;
        this.releaseYear = releaseYear == null ? "" : releaseYear;
        this.rating = rating;
        this.viewCount = viewCount;
        this.latestChapterNumber = Double.isNaN(latestChapterNumber)
                || Double.isInfinite(latestChapterNumber) ? 0d : latestChapterNumber;
        this.latestChapterTime = latestChapterTime == null ? "" : latestChapterTime;
        this.latestChapterTimeMillis = ShinigamiDates.parse(latestChapterTime);
        this.updatedAtMillis = ShinigamiDates.parse(updatedAt);
        this.genres = genres == null ? new ArrayList<String>() : genres;
        this.authors = authors == null ? new ArrayList<String>() : authors;
        this.format = format == null ? "" : format;
    }

    public String statusLabel() {
        switch (status) {
            case 1:
                return "Berjalan";
            case 2:
                return "Tamat";
            case 3:
                return "Hiatus";
            default:
                return "Tidak dinyatakan sumber";
        }
    }

    public static String formatNumber(double number) {
        if (Double.isNaN(number) || Double.isInfinite(number)) return "0";
        if (number == Math.rint(number)) return String.valueOf((long) number);
        return BigDecimal.valueOf(number).stripTrailingZeros().toPlainString();
    }

    public static String chapterLabel(double number, String title) {
        String base = "Chapter " + formatNumber(number);
        if (title != null && !title.trim().isEmpty()) return base + " - " + title.trim();
        return base;
    }
}
