package id.shuyasal.kuromaku.anime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hasil pemeriksaan satu halaman episode: sumber media yang boleh diputar
 * aplikasi, dan mirror yang hanya berupa pemutar pihak ketiga.
 *
 * <p>Pemisahan ini disengaja. {@link Source} hanyalah alamat media yang
 * diterbitkan apa adanya oleh halaman sumber (mp4/m3u8/mpd). {@link Mirror}
 * hanyalah ID server milik penyedia lain; aplikasi tidak pernah menebak alamat
 * medianya, karena itu berarti melewati pemutar dan monetisasi penyedia.
 */
public final class OtakudesuPlayback {

    /** Jenis media yang bisa diberikan ke Media3. */
    public enum Kind {
        HLS("HLS"),
        DASH("DASH"),
        PROGRESSIVE("MP4");

        public final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    /** Satu alamat media langsung yang dipublikasikan halaman episode. */
    public static final class Source {
        public final String url;
        public final Kind kind;
        /** Label kualitas bila halaman menyebutkannya, mis. "720p". */
        public final String quality;

        public Source(String url, Kind kind, String quality) {
            this.url = url == null ? "" : url;
            this.kind = kind == null ? Kind.PROGRESSIVE : kind;
            this.quality = quality == null ? "" : quality;
        }
    }

    /** Satu mirror penyedia pihak ketiga; tidak pernah dibuka aplikasi. */
    public static final class Mirror {
        public final String quality;
        public final String provider;
        public final String serverId;
        /** Urutan tombol di halaman episode; dikirim balik apa adanya saat memilih. */
        public final int index;

        public Mirror(String quality, String provider, String serverId) {
            this(quality, provider, serverId, 0);
        }

        public Mirror(String quality, String provider, String serverId, int index) {
            this.quality = quality == null ? "" : quality;
            this.provider = provider == null ? "" : provider;
            this.serverId = serverId == null ? "" : serverId;
            this.index = index;
        }
    }

    /**
     * Satu kumpulan pilihan yang ditawarkan ke pengguna di pemilih server:
     * kualitas, nama tampilan, dan sumber media bila sudah diperiksa.
     */
    public static final class Choice {
        public final String quality;
        public final String label;
        public final Source source;
        public final int mirrorIndex;
        public final OtakudesuServerPicker.Status status;

        public Choice(String quality, String label, Source source, int mirrorIndex,
                      OtakudesuServerPicker.Status status) {
            this.quality = quality == null ? "" : quality;
            this.label = label == null ? "" : label;
            this.source = source;
            this.mirrorIndex = mirrorIndex;
            this.status = status == null ? OtakudesuServerPicker.Status.UNTESTED : status;
        }

        public boolean playable() {
            return source != null;
        }
    }

    public final List<Source> sources;
    public final List<Mirror> mirrors;

    /**
     * Kandidat cadangan yang berasal dari pemilih mirror halaman episode.
     *
     * <p>Kandidat ini <em>tidak</em> bisa diputar sebelum pemilih mirror
     * dijalankan, dan dijalankannya berarti halaman penyedia dimuat. Karena itu
     * daftarnya dipisahkan: pemutar native hanya memakainya kalau tidak ada satu
     * pun sumber terbuka, dan pemakaiannya disebutkan di layar supaya pengguna
     * tahu konsekuensinya.
     */
    public final List<Fallback> fallbacks;

    /** Satu pilihan mirror beriklan: kualitas, provider, dan indeksnya. */
    public static final class Fallback {
        public final String quality;
        public final String provider;
        public final int index;

        public Fallback(String quality, String provider, int index) {
            this.quality = quality == null ? "" : quality;
            this.provider = provider == null ? "" : provider;
            this.index = index;
        }
    }

    public OtakudesuPlayback(List<Source> sources, List<Mirror> mirrors) {
        this(sources, mirrors, null);
    }

    public OtakudesuPlayback(List<Source> sources, List<Mirror> mirrors,
                             List<Fallback> fallbacks) {
        this.sources = sources == null
                ? Collections.<Source>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(sources));
        this.mirrors = mirrors == null
                ? Collections.<Mirror>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(mirrors));
        this.fallbacks = fallbacks == null
                ? Collections.<Fallback>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(fallbacks));
    }

    /** True bila ada media yang benar-benar bisa dibuka Media3. */
    public boolean playable() {
        return !sources.isEmpty();
    }

    /** Daftar kualitas berisi satu label; dipakai saat satu server sudah dipilih. */
    public static List<String> singletonQuality(Source source) {
        List<String> qualities = new ArrayList<>();
        if (source != null && source.quality != null && !source.quality.isEmpty()) {
            qualities.add(source.quality);
        }
        return qualities;
    }

    /** Kualitas yang benar-benar tersedia dari sumber langsung. */
    public List<String> qualities() {
        List<String> found = new ArrayList<>();
        for (Source source : sources) {
            if (!source.quality.isEmpty() && !found.contains(source.quality)) {
                found.add(source.quality);
            }
        }
        return found;
    }
}