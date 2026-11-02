package id.shuyasal.kuromaku.data;

/**
 * Novel description shown at the top of the library screen.
 *
 * <p>Every field here was read from the source landing page
 * ({@code kaoritranslation.blogspot.com}) and must not be invented. The page
 * does not state a publication status, so {@link #STATUS} says so plainly
 * instead of guessing "Ongoing" or "Completed".
 */
public final class NovelInfo {

    /** Romanised series title, as used by the source. */
    public static final String TITLE = "Monogatari no Kuromaku ni Tensei Shite";

    /** Japanese title shown under the romanised one. */
    public static final String TITLE_JP =
            "物語の黒幕に転生して ～進化する魔剣とゲーム知識ですべてをねじ伏せる～";

    /** Full title including the subtitle, exactly as printed on the source page. */
    public static final String FULL_TITLE = TITLE
            + " ~Shinka suru Maken to Game Chishiki de Subete wo Nejifuseru~";

    public static final String AUTHOR = "Yuuki Ryou";
    public static final String ILLUSTRATOR = "Nakamura";
    public static final String GENRES =
            "Action, Adventure, Fantasy, Harem, Romance, School Life";

    /** The source page has no status field; say so rather than invent one. */
    public static final String STATUS = "Tidak dicantumkan di halaman sumber";

    public static final String TRANSLATION = "Kaori Translation (Bahasa Indonesia)";

    public static final String SOURCE_LABEL_URL =
            "https://kaoritranslation.blogspot.com/search/label/Monogatari%20no%20Kuromaku";

    /**
     * Cover artwork from the source post. Blogger serves the same image at
     * several widths; this variant is 720x1023, close to the printed 2:3 ratio.
     */
    public static final String COVER_URL =
            "https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEjzbkd6ERV1F9c_"
                    + "vsngNOoyDmx33P2qv3BRhdh3tIMxD0SqkLWmH_NBSCUEICVgGveIue9frwXQVOk08sHeU"
                    + "uaiYCHb0usqILfqrUjtMYYlS2E4b0pCy6pHBuBsZJthSl7nPMrs5LOXmyPV0ZffllnPlSx"
                    + "sccb8VTRsYBli55rTnfuSl7dQ-YxplEznGwI/w720-h1024/m3.jpg";

    /** Width divided by height of {@link #COVER_URL}; used to reserve space early. */
    public static final float COVER_RATIO = 1080f / 1534f;

    /** Synopsis paragraphs, copied verbatim from the source page. */
    public static final String[] SYNOPSIS = {
            "『The Legend of the Seven Heroes』── Sebuah game yang membanggakan popularitasnya "
                    + "di seluruh dunia.",
            "Ren, seorang mahasiswa yang menyelesaikan sekuelnya sekaligus memecahkan rekor dunia, "
                    + "bereinkarnasi sebagai bayi di dalam game tersebut.",
            "(Mungkinkah aku bereinkarnasi sebagai protagonis 『The Legend of the Seven Heroes』?)",
            "Ketika asyik memikirkan hal itu, mendengar namanya dari mulut ibunya membuat dia "
                    + "meragukan telinganya.",
            "Itu karena nama itu adalah nama teman sekelas para pahlawan yang kemudian mengkhianati "
                    + "mereka di tengah cerita dan ternyata juga merupakan musuh yang misterius dan "
                    + "kuat yang bermanuver di balik layar untuk mencoba dan menjerumuskan dunia ke "
                    + "dalam jurang keputusasaan.",
            "Karena ingin hidup damai, Ren memutuskan untuk melanjutkan hidupnya di daerah terpencil.",
            "Namun, ia akhirnya berkenalan dengan Saint yang hidupnya seharusnya diambil olehnya "
                    + "dalam game, dan membawanya ke takdir yang tak terduga dan malang.",
            "Ini adalah awal dari kisah fantasi yang penuh dengan perkembangan dramatis dan "
                    + "petualangan mendebarkan yang membuat mu tidak mungkin mengalihkan pandangan…!",
    };

    /** Paragraphs shown before the reader taps "Baca selengkapnya". */
    public static final int SYNOPSIS_PREVIEW = 3;

    private NovelInfo() {
    }

    /** Cover height in pixels for a given width, matching the real artwork. */
    public static int coverHeight(int widthPx) {
        if (widthPx <= 0) return 0;
        return Math.max(1, Math.round(widthPx / COVER_RATIO));
    }
}
