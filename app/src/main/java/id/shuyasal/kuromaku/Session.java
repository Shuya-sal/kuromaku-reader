package id.shuyasal.kuromaku;

import java.util.LinkedHashMap;
import java.util.Map;

import id.shuyasal.kuromaku.data.Catalog;

/**
 * Holds each novel's catalogue in memory so chapter navigation works without
 * refetching, and so a reader opened for one novel can never walk into the
 * chapters of another.
 */
public final class Session {

    private static final Map<String, Catalog> CATALOGS = new LinkedHashMap<>();
    private static volatile String active;

    private Session() {
    }

    /** Keeps the catalogue for the novel it belongs to. */
    public static void setCatalog(Catalog value) {
        if (value == null) return;
        synchronized (CATALOGS) {
            CATALOGS.put(value.novelId, value);
            active = value.novelId;
        }
    }

    /** Catalogue of the novel most recently opened, kept for old callers. */
    public static Catalog catalog() {
        return catalog(active);
    }

    /** Catalogue of one novel, or null when it has not been loaded yet. */
    public static Catalog catalog(String novelId) {
        synchronized (CATALOGS) {
            return CATALOGS.get(novelId == null ? "" : novelId);
        }
    }

    public static void clear() {
        synchronized (CATALOGS) {
            CATALOGS.clear();
            active = null;
        }
    }
}
