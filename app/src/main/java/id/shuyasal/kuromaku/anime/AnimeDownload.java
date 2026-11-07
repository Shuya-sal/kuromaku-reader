package id.shuyasal.kuromaku.anime;

import org.json.JSONObject;

/**
 * Satu episode anime yang benar-benar tersimpan di perangkat.
 *
 * <p>Catatan ini adalah <b>salinan keterangan</b>, bukan sumber kebenaran.
 * Sumber kebenaran tetap berkas media di {@link AnimeDownloads}: catatan tanpa
 * berkas valid dianggap tidak ada, dan berkas tanpa catatan tetap ditampilkan
 * sebagai episode tersimpan dengan keterangan seadanya.
 */
public final class AnimeDownload {

    public final String episodeKey;
    public final String animeId;
    public final String animeTitle;
    public final String poster;
    public final String episodeLabel;
    public final String episodeUrl;
    public final String quality;
    public final String server;
    public final String url;
    public final long bytes;
    public final long savedAt;

    public AnimeDownload(String episodeKey, String animeId, String animeTitle, String poster,
                         String episodeLabel, String episodeUrl, String quality, String server,
                         String url, long bytes, long savedAt) {
        this.episodeKey = safe(episodeKey);
        this.animeId = safe(animeId);
        this.animeTitle = safe(animeTitle);
        this.poster = safe(poster);
        this.episodeLabel = safe(episodeLabel);
        this.episodeUrl = safe(episodeUrl);
        this.quality = safe(quality);
        this.server = safe(server);
        this.url = safe(url);
        this.bytes = Math.max(0L, bytes);
        this.savedAt = savedAt;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("episode_key", episodeKey);
            json.put("anime_id", animeId);
            json.put("anime_title", animeTitle);
            json.put("poster", poster);
            json.put("episode_label", episodeLabel);
            json.put("episode_url", episodeUrl);
            json.put("quality", quality);
            json.put("server", server);
            json.put("url", url);
            json.put("bytes", bytes);
            json.put("saved_at", savedAt);
        } catch (Exception ignored) {
            // JSONObject hanya melempar untuk nama kunci yang tidak sah; kunci
            // di atas tetap, jadi cabang ini tidak pernah tercapai.
        }
        return json;
    }

    public static AnimeDownload fromJson(JSONObject json) {
        if (json == null) return null;
        String key = json.optString("episode_key", "");
        if (key.isEmpty()) return null;
        return new AnimeDownload(key,
                json.optString("anime_id", ""),
                json.optString("anime_title", ""),
                json.optString("poster", ""),
                json.optString("episode_label", ""),
                json.optString("episode_url", ""),
                json.optString("quality", ""),
                json.optString("server", ""),
                json.optString("url", ""),
                json.optLong("bytes", 0L),
                json.optLong("saved_at", 0L));
    }

    /** Ukuran untuk ditampilkan, mis. "142,9 MB". */
    public static String size(long bytes) {
        if (bytes <= 0) return "—";
        double mb = bytes / (1024.0 * 1024.0);
        if (mb >= 1024) return String.format(java.util.Locale.US, "%.1f GB", mb / 1024.0);
        if (mb >= 10) return String.format(java.util.Locale.US, "%.0f MB", mb);
        return String.format(java.util.Locale.US, "%.1f MB", mb);
    }
}