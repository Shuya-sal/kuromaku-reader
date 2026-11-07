package id.shuyasal.kuromaku.anime;

/**
 * Satu permintaan unduhan episode: apa yang diunduh, dari server mana, dan
 * untuk anime/episode mana berkas itu disimpan.
 *
 * <p>{@link #downloadable()} adalah satu-satunya tempat yang memutuskan apakah
 * sebuah sumber boleh diunduh. Aturannya sengaja ketat: hanya berkas media
 * langsung ({@code PROGRESSIVE}) dengan alamat yang benar-benar diterbitkan
 * halaman episode yang bisa disimpan. HLS/DASH di sumber ini selalu berupa
 * manifest terenkripsi atau tertaut ke pemutar pihak ketiga, jadi tidak
 * ditawarkan sebagai unduhan; halaman embed tidak pernah dianggap berkas.
 */
public final class AnimeDownloadTask {

    public final String animeId;
    public final String animeTitle;
    public final String poster;
    public final String episodeLabel;
    public final String episodeUrl;
    public final String episodeKey;
    public final String quality;
    public final String server;
    public final String url;
    public final String kind;

    public AnimeDownloadTask(String animeId, String animeTitle, String poster,
                             String episodeLabel, String episodeUrl, String quality,
                             String server, String url, String kind) {
        this.animeId = safe(animeId);
        this.animeTitle = safe(animeTitle);
        this.poster = safe(poster);
        this.episodeLabel = safe(episodeLabel);
        this.episodeUrl = safe(episodeUrl);
        this.episodeKey = AnimeDownloads.episodeKey(this.episodeUrl, this.episodeLabel);
        this.quality = safe(quality);
        this.server = safe(server);
        this.url = safe(url);
        this.kind = safe(kind);
    }

    /** Susun tugas dari sumber yang sudah lolos pemeriksaan berkas. */
    public static AnimeDownloadTask of(String animeId, String animeTitle, String poster,
                                       String episodeLabel, String episodeUrl,
                                       OtakudesuPlayback.Source source, String server) {
        if (source == null) return null;
        return new AnimeDownloadTask(animeId, animeTitle, poster, episodeLabel, episodeUrl,
                source.quality, server, source.url,
                source.kind == null ? "" : source.kind.name());
    }

    /** Boleh diunduh hanya bila berupa berkas media langsung yang sah. */
    public boolean downloadable() {
        if (url.isEmpty() || episodeKey.isEmpty() || animeId.isEmpty()) return false;
        if (!"PROGRESSIVE".equals(kind)) return false;
        String lower = url.toLowerCase(java.util.Locale.US);
        // Tolak apa pun yang masih berbentuk halaman, bukan berkas.
        if (lower.startsWith("data:") || lower.startsWith("javascript:")) return false;
        return lower.endsWith(".mp4") || lower.endsWith(".webm")
                || lower.contains(".mp4?") || lower.contains(".webm?");
    }

    /** Alasan tombol unduh dinonaktifkan, untuk ditampilkan apa adanya. */
    public String reasonNotDownloadable() {
        if (url.isEmpty()) return "Server ini tidak menyebut alamat berkas media.";
        if (!"PROGRESSIVE".equals(kind)) {
            return "Server ini hanya menyediakan pemutar bertaut (HLS/DASH), bukan berkas "
                    + "yang bisa disimpan aplikasi.";
        }
        if (episodeKey.isEmpty() || animeId.isEmpty()) {
            return "Episode ini tidak punya penanda yang sah untuk disimpan.";
        }
        if (!downloadable()) {
            return "Alamat media ini bukan berkas video langsung yang boleh disimpan.";
        }
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}