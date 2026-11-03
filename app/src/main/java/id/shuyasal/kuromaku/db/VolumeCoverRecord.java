package id.shuyasal.kuromaku.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;

/**
 * One stored per-volume cover.
 *
 * <p>Kept in its own table rather than as a column on {@link NovelRecord} because
 * a novel has one row per volume, not one row in total. Storing the URLs means
 * opening a novel's detail page a second time does not have to re-parse the
 * catalogue page just to draw three thumbnails.
 *
 * <p>An empty {@code url} means "the source has no cover for this volume" — it is
 * recorded deliberately so a later refresh can tell "not looked at yet" from
 * "looked at, nothing there". That distinction is what keeps a placeholder from
 * turning into another volume's artwork.
 */
@Entity(tableName = "volume_covers", primaryKeys = {"novelId", "volume"})
public final class VolumeCoverRecord {

    @NonNull
    public String novelId = "";

    public int volume = 0;

    /** Cover URL, or empty when the source genuinely has none for this volume. */
    @NonNull
    public String url = "";

    /** Declared pixel size, 0 when the page did not state it. */
    public int width = 0;
    public int height = 0;
}