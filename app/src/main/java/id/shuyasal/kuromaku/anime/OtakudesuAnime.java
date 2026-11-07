package id.shuyasal.kuromaku.anime;

import java.util.ArrayList;
import java.util.List;

/**
 * Model Anime Otakudesu.
 *
 * <p>Semua identitas diturunkan dari URL sumber: {@code animeId} adalah slug
 * halaman anime ({@code uchi-otouto-sumimasen-sub-indo}), dan episode memakai
 * URL halamannya sendiri. Tidak ada ID yang dikarang — dua halaman berbeda
 * tidak mungkin berbagi ID, dan episode tetap terurut seperti di sumber.
 */
public final class OtakudesuAnime {

    public final String animeId;
    public final String title;
    public final String url;
    public final String posterUrl;
    /** "Episode 18" untuk katalog, atau judul episode pada daftar episode. */
    public final String latestEpisode;
    /** Hari tayang bila sumber menyediakannya (mis. "Sabtu"). */
    public final String day;
    /** Tanggal update apa adanya dari sumber (mis. "07 Nov"). */
    public final String updated;
    public final List<String> genres;

    public OtakudesuAnime(String animeId, String title, String url, String posterUrl,
                          String latestEpisode, String day, String updated, List<String> genres) {
        this.animeId = animeId == null ? "" : animeId;
        this.title = title == null ? "" : title;
        this.url = url == null ? "" : url;
        this.posterUrl = posterUrl == null ? "" : posterUrl;
        this.latestEpisode = latestEpisode == null ? "" : latestEpisode.trim();
        this.day = day == null ? "" : day.trim();
        this.updated = updated == null ? "" : updated.trim();
        this.genres = genres == null ? new ArrayList<String>() : genres;
    }

    /** Satu episode pada halaman detail. */
    public static final class Episode {
        public final String episodeId;
        public final String label;
        public final String url;
        public final String date;
        /** Nomor episode bila bisa dibaca dari label; -1 bila tidak. */
        public final double number;

        public Episode(String episodeId, String label, String url, String date, double number) {
            this.episodeId = episodeId == null ? "" : episodeId;
            this.label = label == null ? "" : label;
            this.url = url == null ? "" : url;
            this.date = date == null ? "" : date;
            this.number = number;
        }
    }

    /** Detail lengkap satu anime. */
    public static final class Detail {
        public final OtakudesuAnime summary;
        public final String synopsis;
        public final String status;
        public final String studio;
        public final String totalEpisodes;
        public final String released;
        public final List<Episode> episodes;

        public Detail(OtakudesuAnime summary, String synopsis, String status, String studio,
                      String totalEpisodes, String released, List<Episode> episodes) {
            this.summary = summary;
            this.synopsis = synopsis == null ? "" : synopsis;
            this.status = status == null ? "" : status;
            this.studio = studio == null ? "" : studio;
            this.totalEpisodes = totalEpisodes == null ? "" : totalEpisodes;
            this.released = released == null ? "" : released;
            this.episodes = episodes == null ? new ArrayList<Episode>() : episodes;
        }
    }

    /** Hasil satu halaman katalog. */
    public static final class Page {
        public final List<OtakudesuAnime> items;
        public final int page;
        public final int totalPages;

        public Page(List<OtakudesuAnime> items, int page, int totalPages) {
            this.items = items == null ? new ArrayList<OtakudesuAnime>() : items;
            this.page = page;
            this.totalPages = Math.max(1, totalPages);
        }
    }
}