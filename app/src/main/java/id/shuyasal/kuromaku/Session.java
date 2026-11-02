package id.shuyasal.kuromaku;

/** Holds the catalog in memory so chapter navigation works without refetching. */
public final class Session {

    private static volatile id.shuyasal.kuromaku.data.Catalog catalog;

    private Session() {
    }

    public static void setCatalog(id.shuyasal.kuromaku.data.Catalog value) {
        catalog = value;
    }

    public static id.shuyasal.kuromaku.data.Catalog catalog() {
        return catalog;
    }
}
