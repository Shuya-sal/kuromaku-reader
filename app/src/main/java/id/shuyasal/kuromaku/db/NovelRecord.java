package id.shuyasal.kuromaku.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * One row of the library table.
 *
 * <p>The row is keyed by the novel's normalised catalogue URL, not by its title:
 * the same page entered twice — with or without {@code ?m=1}, with or without a
 * trailing slash, as {@code http} or {@code https} — must resolve to one row, so
 * the user can never end up with two copies of one novel.
 *
 * <p>{@code id} is the URL's slug and is what the rest of the app uses for
 * preferences, offline folders and reader navigation. It is therefore written
 * once, when the novel is added, and never recomputed from a refreshed title:
 * renaming a novel on the source must not orphan the reading progress stored
 * under the old id.
 */
@Entity(tableName = "novels")
public final class NovelRecord {

    /** Slug of {@link #url}; stable for the life of the row. */
    @PrimaryKey
    @NonNull
    public String id = "";

    /** Normalised catalogue URL. Unique by construction because it makes the id. */
    @NonNull
    public String url = "";

    public String title = "";
    public String titleJp = "";
    public String fullTitle = "";
    public String author = "";
    public String illustrator = "";
    public String genres = "";
    public String status = "";
    public String translation = "";

    /** Label page used for attribution, may equal {@link #url}. */
    public String sourceLabelUrl = "";
    public String coverUrl = "";
    public float coverRatio = 0.70f;

    /** Synopsis paragraphs, joined with a blank line. */
    public String synopsis = "";
    public int synopsisPreview = 2;

    /** {@code Novel.CATALOG_FEED}, {@code CATALOG_LANDING} or {@code CATALOG_DISCOVERED}. */
    public int catalogMode = 2;
    public String slugPrefix = "";
    public String feedLabel = "";

    /** True for the two novels the app shipped with; false for user-added ones. */
    public boolean builtIn = false;

    /** Library order, ascending. New novels are appended. */
    public int position = 0;

    public long addedAt = 0L;
    /** When the catalogue was last fetched successfully, 0 when never. */
    public long lastRefresh = 0L;
}
