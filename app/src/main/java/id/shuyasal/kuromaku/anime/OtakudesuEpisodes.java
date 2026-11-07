package id.shuyasal.kuromaku.anime;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Daftar episode yang dibawa antar-activity.
 *
 * <p>Halaman detail sudah memegang seluruh daftar episode; meneruskannya lewat
 * Intent membuat pemutar bisa berpindah episode tanpa memuat ulang halaman
 * detail dan tanpa permintaan jaringan tambahan. Urutannya selalu urutan
 * cerita (nomor menaik); tampilan detail yang membalik urutan.
 */
public final class OtakudesuEpisodes {

    private static final int MAX_EPISODES = 3000;

    private OtakudesuEpisodes() {
    }

    public static String toJson(List<OtakudesuAnime.Episode> episodes) {
        JSONArray array = new JSONArray();
        if (episodes != null) {
            int limit = Math.min(episodes.size(), MAX_EPISODES);
            for (int i = 0; i < limit; i++) {
                OtakudesuAnime.Episode episode = episodes.get(i);
                if (episode == null || episode.url.isEmpty()) continue;
                JSONObject item = new JSONObject();
                try {
                    item.put("id", episode.episodeId);
                    item.put("label", episode.label);
                    item.put("url", episode.url);
                    item.put("date", episode.date);
                    item.put("number", episode.number);
                } catch (Exception ignored) {
                    continue;
                }
                array.put(item);
            }
        }
        return array.toString();
    }

    public static List<OtakudesuAnime.Episode> fromJson(String json) {
        List<OtakudesuAnime.Episode> episodes = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return episodes;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String url = item.optString("url", "");
                if (url.isEmpty()) continue;
                episodes.add(new OtakudesuAnime.Episode(
                        item.optString("id", url),
                        item.optString("label", ""),
                        url,
                        item.optString("date", ""),
                        item.optDouble("number", -1d)));
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        return episodes;
    }

    /** Episode sebelum/berikutnya dalam urutan cerita; null bila di ujung. */
    public static OtakudesuAnime.Episode neighbor(List<OtakudesuAnime.Episode> episodes,
                                                  String currentUrl, int delta) {
        if (episodes == null || episodes.isEmpty() || currentUrl == null) return null;
        // Urutan cerita tidak boleh bergantung pada urutan halaman sumber.
        // Halaman episode Otakudesu menulis episode terbaru lebih dulu, jadi
        // indeks daftar menurun sedangkan cerita menaik. Memakai indeks apa
        // adanya membuat tombol Berikutnya justru mundur ke episode sebelumnya
        // (dan pada Episode 1, Berikutnya mati sama sekali).
        List<OtakudesuAnime.Episode> ordered = inStoryOrder(episodes);
        int index = indexOf(ordered, currentUrl);
        if (index < 0) return null;
        int target = index + delta;
        if (target < 0 || target >= ordered.size()) return null;
        return ordered.get(target);
    }

    /**
     * Daftar episode dalam urutan cerita (nomor menaik).
     *
     * <p>Dipisah supaya arah tombol dapat diuji tanpa Activity. Episode yang
     * nomornya tidak terbaca diletakkan di akhir dengan urutan aslinya, tidak
     * dibuang dan tidak dibuat-buat nomornya.
     */
    public static List<OtakudesuAnime.Episode> inStoryOrder(
            List<OtakudesuAnime.Episode> episodes) {
        List<OtakudesuAnime.Episode> ordered = new ArrayList<>();
        if (episodes == null) return ordered;
        for (OtakudesuAnime.Episode episode : episodes) {
            if (episode != null) ordered.add(episode);
        }
        boolean allNumbered = !ordered.isEmpty();
        for (OtakudesuAnime.Episode episode : ordered) {
            if (episode.number < 0) {
                allNumbered = false;
                break;
            }
        }
        if (!allNumbered) return ordered;
        Collections.sort(ordered, new Comparator<OtakudesuAnime.Episode>() {
            @Override public int compare(OtakudesuAnime.Episode a, OtakudesuAnime.Episode b) {
                return Double.compare(a.number, b.number);
            }
        });
        return ordered;
    }

    /** Episode pertama dan terakhir menurut urutan cerita. */
    public static OtakudesuAnime.Episode first(List<OtakudesuAnime.Episode> episodes) {
        List<OtakudesuAnime.Episode> ordered = inStoryOrder(episodes);
        return ordered.isEmpty() ? null : ordered.get(0);
    }

    public static OtakudesuAnime.Episode last(List<OtakudesuAnime.Episode> episodes) {
        List<OtakudesuAnime.Episode> ordered = inStoryOrder(episodes);
        return ordered.isEmpty() ? null : ordered.get(ordered.size() - 1);
    }

    public static int indexOf(List<OtakudesuAnime.Episode> episodes, String url) {
        if (episodes == null || url == null) return -1;
        for (int i = 0; i < episodes.size(); i++) {
            OtakudesuAnime.Episode episode = episodes.get(i);
            if (episode != null && url.equals(episode.url)) return i;
        }
        return -1;
    }
}