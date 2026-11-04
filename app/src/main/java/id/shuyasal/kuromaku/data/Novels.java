package id.shuyasal.kuromaku.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The novels this reader knows about.
 *
 * <p>Two source shapes exist:
 * <ul>
 *   <li>Monogatari no Kuromaku publishes one Blogger label, so its catalogue comes
 *       from the label JSON feed and chapters are grouped by the volume number in
 *       the post title.</li>
 *   <li>Magical★Explorer has no single label; its landing page lists every volume
 *       heading followed by the chapter links for that volume. That page is the
 *       catalogue, so the links there are used verbatim and each chapter inherits
 *       the volume heading above it.</li>
 * </ul>
 *
 * <p>All text below was copied from the source pages. Nothing is invented.
 */
public final class Novels {

    public static final String MONOGATARI = "monogatari";
    public static final String MAGICAL = "magical-explorer";

    private static final List<Novel> ALL = build();

    private Novels() {
    }

    /**
     * The novels that ship with the app, in their original order.
     *
     * <p>These are <em>seeds</em>, not the library: on first launch they are
     * written into the database, and from then on the database is the source of
     * truth so the user can add and remove novels. Their metadata is verified from
     * the source pages, so they never depend on the network to appear.
     */
    public static List<Novel> builtIns() {
        return ALL;
    }

    /** Alias kept for older callers; returns the built-in seeds. */
    public static List<Novel> all() {
        return ALL;
    }

    /** Novel by id, or the first novel when the id is unknown. */
    public static Novel byId(String id) {
        for (Novel novel : ALL) {
            if (novel.id.equals(id)) return novel;
        }
        return ALL.isEmpty() ? null : ALL.get(0);
    }

    private static List<Novel> build() {
        List<Novel> novels = new ArrayList<>();
        novels.add(monogatari());
        novels.add(magicalExplorer());
        return Collections.unmodifiableList(novels);
    }

    private static Novel monogatari() {
        String[] synopsis = {
                "『The Legend of the Seven Heroes』── Sebuah game yang membanggakan "
                    + "popularitasnya di seluruh dunia.",
                "Ren, seorang mahasiswa yang menyelesaikan sekuelnya sekaligus memecahkan rekor "
                    + "dunia, bereinkarnasi sebagai bayi di dalam game tersebut.",
                "(Mungkinkah aku bereinkarnasi sebagai protagonis 『The Legend of the Seven Heroes』?)",
                "Ketika asyik memikirkan hal itu, mendengar namanya dari mulut ibunya membuat "
                    + "dia meragukan telinganya.",
                "Itu karena nama itu adalah nama teman sekelas para pahlawan yang kemudian "
                    + "mengkhianati mereka di tengah cerita dan ternyata juga merupakan musuh yang "
                    + "misterius dan kuat yang bermanuver di balik layar untuk mencoba dan "
                    + "menjerumuskan dunia ke dalam jurang keputusasaan.",
                "Karena ingin hidup damai, Ren memutuskan untuk melanjutkan hidupnya di daerah terpencil.",
                "Namun, ia akhirnya berkenalan dengan Saint yang hidupnya seharusnya diambil "
                    + "olehnya dalam game, dan membawanya ke takdir yang tak terduga dan malang.",
                "Ini adalah awal dari kisah fantasi yang penuh dengan perkembangan dramatis dan "
                    + "petualangan mendebarkan yang membuat mu tidak mungkin mengalihkan pandangan…!",
        };
        return new Novel(
                MONOGATARI,
                "Monogatari no Kuromaku ni Tensei Shite",
                "物語の黒幕に転生して ～進化する魔剣とゲーム知識ですべてをねじ伏せる～",
                "Monogatari no Kuromaku ni Tensei Shite"
                        + " ~Shinka suru Maken to Game Chishiki de Subete wo Nejifuseru~",
                "Yuuki Ryou",
                "Nakamura",
                "Action, Adventure, Fantasy, Harem, Romance, School Life",
                Novel.STATUS_NOT_STATED,
                "Kaori Translation (Bahasa Indonesia)",
                "https://kaoritranslation.blogspot.com/search/label/Monogatari%20no%20Kuromaku",
                "https://kaoritranslation.blogspot.com/2025/09/ln-monogatari-no-kuromaku-ni-tensei.html",
                "https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEjzbkd6ERV1F9c_vsngNOoyDmx33P2qv3BRhdh3tIMxD0SqkLWmH_NBSCUEICVgGveIue9frwXQVOk08sHeUuaiYCHb0usqILfqrUjtMYYlS2E4b0pCy6pHBuBsZJthSl7nPMrs5LOXmyPV0ZffllnPlSxsccb8VTRsYBli55rTnfuSl7dQ-YxplEznGwI/s1600/m3.jpg",
                0.704042f,
                synopsis,
                3,
                Novel.CATALOG_ARTICLE_FEED,
                "monogatari",
                "Monogatari no Kuromaku",
                true);
    }

    /**
     * Magical★Explorer. The source landing page carries the volume headings and
     * the chapter links, so its catalogue is parsed from there rather than from a
     * label feed. The page states no publication status either.
     */
    private static Novel magicalExplorer() {
        String[] synopsis = {
                "Ada Game Bishoujo legendaris yang menjadi populer di kalangan pria "
                    + "Jepang.Namanya adalah “MagicalExplorer” atau singkatnya Magiex.Protagonis "
                    + "permainan memiliki kekuatan seperti cheat dengan 12 pahlawan wanita cantik "
                    + "yang berbeda untuk digoda, termasuk 12 pahlawan wanita tambahan dari DLC, "
                    + "harem dari 24 wanita cantik.",
                "Dan aku telah menjadi protagonis itu!………………Tidak. Sebaliknya, aku telah "
                    + "menjadi karakter roda tiga yang selalu tertawa seperti orang idiot di "
                    + "sebelahnya.Tentu saja aku putus asa pada awalnya.Karena Jika aku protagonis, "
                    + "aku bisa memiliki semua 24 pahlawan untuk diriku sendiri bersama dengan "
                    + "kekuatan cheat yang tak tertandingi.",
                "Namun, karena aku adalah roda ketiga, tidak ada pahlawan wanita yang akan "
                    + "memperhatikan ku.Tapi sekarang aku tidak peduli tentang protagonis dan "
                    + "pahlawan wanitanya. Mengapa? Karena aku menemukan sihir. Ketika aku "
                    + "menggunakannya, aku menjadi tertarik padanya. aku ingin menguasainya. aku "
                    + "ingin menjadi yang terkuat.",
                "Dengan demikian aku telah memutuskan.aku akan meninggalkan setengah peran ku "
                    + "sebagai teman protagonis untuk melatih sihir dan kemudian aku akan mengalahkan "
                    + "cheat yang bisa mengalahkan Raja Iblis sendirian.………..Aku sedang berlatih "
                    + "dengan pemikiran itu, tapi entah bagaimana aku semakin dekat dengan para "
                    + "pahlawan wanita dan lebih jauh lagi, aku tidak tahu mengapa aku terus "
                    + "menghadapi peristiwa seperti aku adalah protagonis Eroge.",
        };
        return new Novel(
                MAGICAL,
                "Magical★Explorer",
                "マジカル★エクスプローラー　エロゲの友人キャラに転生したけど、"
                        + "ゲーム知識使って自由に生きる",
                "Magical★Explorer Eroge no Yuujin Kyara ni Tensei Shitakedo,"
                        + " Geemu Chishiki Tsukatte Jiyuu ni Ikiru",
                "Irisu",
                "Kannatsuki Noboru",
                "Action, Adventure, Comedy, Ecchi, Fantasy, Harem, Martial Arts,"
                        + " Romance, School Life",
                Novel.STATUS_NOT_STATED,
                "Kaori Translation (Bahasa Indonesia)",
                "https://kaoritranslation.blogspot.com/2025/04/ln-magicalexplorer-eroge-no-yuujin.html",
                "https://kaoritranslation.blogspot.com/2025/04/ln-magicalexplorer-eroge-no-yuujin.html",
                "https://bagisharenovel.wordpress.com/wp-content/uploads/2021/12/001.png?w=722",
                0.705078f,
                synopsis,
                2,
                Novel.CATALOG_LANDING,
                "ln-magical-explorer-",
                "",
                true);
    }
}
