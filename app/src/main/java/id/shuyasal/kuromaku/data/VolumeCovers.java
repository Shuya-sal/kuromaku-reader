package id.shuyasal.kuromaku.data;


import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-volume cover artwork, read from each novel's own catalogue page.
 *
 * <p>Both source pages list volumes as a heading ("Volume 5") followed by one
 * image, so the cover for a volume is the first picture after its own heading.
 * These tables were produced from exactly that parse and each entry was then
 * downloaded and decoded to confirm the URL really serves a picture of the
 * stated pixel size. {@code VolumeCoverParserTest} re-runs the parse over the
 * saved source pages and fails if a table ever drifts from the source.
 *
 * <p>Extras per volume (illustrations inside a chapter) are deliberately not
 * used here: those must never stand in for a cover.
 */
public final class VolumeCovers {

    /** One cover: where it lives and the pixel size the source declares. */
    public static final class Cover {
        public final String url;
        public final int width;
        public final int height;

        Cover(String url, int width, int height) {
            this.url = url;
            this.width = Math.max(0, width);
            this.height = Math.max(0, height);
        }

        /** Width divided by height, or 0 when the size is unknown. */
        public float ratio() {
            if (width <= 0 || height <= 0) return 0f;
            return width / (float) height;
        }
    }

    private VolumeCovers() {
    }


    /** Monogatari no Kuromaku, volumes 1-7. */
    private static Map<Integer, Cover> monogatari() {
        Map<Integer, Cover> map = new LinkedHashMap<>();
        map.put(1, new Cover(
                "https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEjzb"
                        + "kd6ERV1F9c_vsngNOoyDmx33P2qv3BRhdh3tIMxD0SqkLWmH_NBSCUEICVgGve"
                        + "Iue9frwXQVOk08sHeUuaiYCHb0usqILfqrUjtMYYlS2E4b0pCy6pHBuBsZJthS"
                        + "l7nPMrs5LOXmyPV0ZffllnPlSxsccb8VTRsYBli55rTnfuSl7dQ-YxplEznGwI"
                        + "/s1600/m3.jpg",
                1080, 1534));
        map.put(2, new Cover(
                "https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEi7t"
                        + "4UBCLCvwCc9_XcIyNUT3E4rPgm773a5CNZUlHHBbUen6Y9wrpCNH1Bn1zdneIN"
                        + "NEK59s_Y-0tHn6Q43cLMsQh_DrnxBXFtH1Vv3hUQOSrXPy0nISYylGPZcRo_Ac"
                        + "I7drihtgK_9RJhjoLfej6wUzA8wEbE41M5CKgam6_1UuQfDI4HCw177E09bXGs"
                        + "/s1600/v21.jpg",
                1080, 1537));
        map.put(3, new Cover(
                "https://blogger.googleusercontent.com/img/a/AVvXsEhAC4HO63BYcC"
                        + "V5xXdnM08aGf8RJUAjjgBzuL-GzCHMQ9kNmaoJKs-JKJ6tpzeXFk08GAIVGB34"
                        + "z3a_3ZoPuXWuFBhrXjOmOEyUM2ORxtzyTCWhk6vCcdd9tua_wmWTGaJrrIHb-D"
                        + "Kk-NW4m_ZNniLJ3cNJaT6BebppUyxPHZwtZburB5MOntbGa_k0mAk=s1600",
                1131, 1600));
        map.put(4, new Cover(
                "https://blogger.googleusercontent.com/img/a/AVvXsEjqnyOK41XfX6"
                        + "SA82hpMdkiVO-ulHOk-ld7Jh2Gyt88kC3zR_9wG-fybZ5HKm8-uyonregskWOk"
                        + "XVHFbdRDqmGjVqeIKvC7w7dGOAi5EX8XWyviyDJBQrjcIeKVAoF73orcY6P0Mn"
                        + "1zMhXOGph4myENoN_yRpI-vAeDoZkmh33Zy0pyfUQWfYJi5uogitg=s1600",
                1125, 1600));
        map.put(5, new Cover(
                "https://blogger.googleusercontent.com/img/a/AVvXsEjS2WZbwv1H4I"
                        + "RRLgHLeBEDVZWYj-sROm6oMfJcHuZQoEFk3bN0fOqFYMAxQLGyAliK5IoTrCsu"
                        + "l3CsxJKFwplccUQfWz-m1i1NkCS71UJftF0zDCfDQEZrxCcfBO6q0pyRO3P9Gu"
                        + "cosWXkVp-jmyE4Fdn6sVU35ooqoocTV4K50SZkm-ikDGUNyujgsp8=s1600",
                1151, 1600));
        map.put(6, new Cover(
                "https://blogger.googleusercontent.com/img/a/AVvXsEiNfgTPJAv_ah"
                        + "sYMencUs5wxXeatp0Ln32iExZOdyXAlo9w1LXXVbc60kNn8PjDVuiOlo6_s6VX"
                        + "sAkAmEMRFYmPka8WZA0zNB4jA_ZSsQsRD2TlUCB6Heq05UTVpDIGhxYtOqW-24"
                        + "nkqU-DLEGANQOEdrARltGkbAZIuHYieWrRZ20nOl53GwXFUD8cmeI=s1600",
                1125, 1600));
        map.put(7, new Cover(
                "https://blogger.googleusercontent.com/img/a/AVvXsEjSxidHw0zFeG"
                        + "KTqV_JnFAw42gzB7WLyV1J8CEhLmYtMVnMDwVdkBEiuXp2TChMmjk9IkU8HcNT"
                        + "JX4GlEU4qbqambmRYHYpYDF42wztbR9X01fNT2sgNAWtdVS5mUf0f9SPWLgaFD"
                        + "w6MAV_qjUbbYIj8JJ5R4OE7tW4eUg-gTVmz39Gy1KOSU7ZHbAIDxY=s1600",
                1125, 1600));
        return Collections.unmodifiableMap(map);
    }

    /** Magical★Explorer, volumes 1-14. */
    private static Map<Integer, Cover> magical() {
        Map<Integer, Cover> map = new LinkedHashMap<>();
        map.put(1, new Cover(
                "https://bagisharenovel.wordpress.com/wp-content/uploads/2021/1"
                        + "2/001.png?w=722",
                722, 1024));
        map.put(2, new Cover(
                "https://bagisharenovel.wordpress.com/wp-content/uploads/2021/1"
                        + "2/001.jpg?w=722",
                722, 1024));
        map.put(3, new Cover(
                "https://bagisharenovel.wordpress.com/wp-content/uploads/2022/1"
                        + "0/001.jpg?w=722",
                722, 1024));
        map.put(4, new Cover(
                "https://bagisharenovel.wordpress.com/wp-content/uploads/2022/1"
                        + "0/401.jpg?w=722",
                722, 1024));
        map.put(5, new Cover(
                "https://bagisharenovel.wordpress.com/wp-content/uploads/2022/1"
                        + "1/ln_v5_cover_-_jp.webp?w=722",
                722, 1024));
        map.put(6, new Cover(
                "https://bagisharenovel.wordpress.com/wp-content/uploads/2022/1"
                        + "1/v6illust1.jpg",
                1804, 2560));
        map.put(7, new Cover(
                "https://bagisharenovel.wordpress.com/wp-content/uploads/2023/0"
                        + "3/v70.jpg?w=722",
                722, 1023));
        map.put(8, new Cover(
                "https://bagisharenovel.wordpress.com/wp-content/uploads/2023/0"
                        + "7/v8-0.png.jpg?w=722",
                722, 1023));
        map.put(9, new Cover(
                "https://bagisharenovel.wordpress.com/wp-content/uploads/2023/1"
                        + "0/v91.jpg?w=722",
                722, 1023));
        map.put(10, new Cover(
                "https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEiWh"
                        + "gHW1gYiGh2R-4hsMMUOb1jNq8aOpFxNi5wX5vuClsAHBllhetu9WqUQhYeoXXr"
                        + "Y7DN4VqbohbX3iI2p9FYyQUCZ3Os1vRi6ZIEIOEa6edAsR_UJuYd-0ijjSQvAM"
                        + "YGArU6K-pjH46oG6aIs5iAiEulchYh453VWjOpn6VVDuH3hegvAm6mpStQSvao"
                        + "/s1600/v108.png",
                1081, 1482));
        map.put(11, new Cover(
                "https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEgT_"
                        + "stryb5W-hkQXA2f7HrezQnlNsQypankAjxrv5z8z_1llLbdAIesXH8VC8PfNPt"
                        + "jgKhxA3Ex8KMSZUyjVdZj2tb5hyLSsdxVcGxjMJW_di7SRBEKPkJJZQs5vcS1C"
                        + "IMMugvtZeoWGCxD6mavmGbzc4UkLQO6JetsbBGxfj9mQV1vkI0oDNCeBBRqSQU"
                        + "/s1600/v118.png",
                816, 1056));
        map.put(12, new Cover(
                "https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEhjJ"
                        + "je54SiFJynHZzXYzvLYJQbs7ibmMeDojDsYUEgDGgCejyqIU-e7T7tVRYbiQ7g"
                        + "KuODhbtFAkmOydtJx-qj5U7qo0stEahE-7rq4OCBBDqhYOEAiWBsDn6eQL_EsW"
                        + "T5wCWbBphf4_ZODztyVYuwDrnKOCnJuiZhT5_Ah6gIVtXuokrDQIlZaVs5jzHA"
                        + "/s1600/v1111.jpg",
                1080, 1536));
        map.put(13, new Cover(
                "https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEjO_"
                        + "EerOyhlcG_7F8Ul_oBScM05A9kIGs3LEmgMsWe4gU4zIYtAYyzp9GSqXoMYUm4"
                        + "DQVNy6OWinyzyhmiu_5GcDnF2rNiqBp1KMlmUy2Dt-8kmxPAL08tVF09-aHPyD"
                        + "JFsVJzZkOJlHW_tlhiGNd218g7KBiCVsTz5iNBdg6-_u6RxfwnrZAqL_ypr8W8"
                        + "/s1600/v131.jpg",
                1080, 1534));
        map.put(14, new Cover(
                "https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEhmn"
                        + "aZhpSfF2p_G8fv2C7I4ArRJg9aScj9oKr2a6i9VO_0cLwZvYE0414y-O0lJDTv"
                        + "hsHTKufARZKpzEPOooWfXKqzFH_NvoKRj6aTRxbKVp9jYxWvMM0CAxMbT_xspe"
                        + "wZQbdomltG01VVFeDRsSdNcMDC7GZrk95bOjjddDGq2kqG5W2LhR79yMjp3YVY"
                        + "/s1600/v143.jpg",
                1080, 1536));
        return Collections.unmodifiableMap(map);
    }

    private static final Map<Integer, Cover> MONOGATARI = monogatari();
    private static final Map<Integer, Cover> MAGICAL = magical();


    /** Cover for a volume, or null when the source page has none for it. */
    public static Cover forVolume(String novelId, int volume) {
        Map<Integer, Cover> map = "monogatari".equals(novelId) ? MONOGATARI : MAGICAL;
        return map.get(volume);
    }

    public static int count(String novelId) {
        return forVolume(novelId, 1) == null && !"monogatari".equals(novelId)
                ? MAGICAL.size() : ("monogatari".equals(novelId) ? MONOGATARI.size() : 0);
    }
}
