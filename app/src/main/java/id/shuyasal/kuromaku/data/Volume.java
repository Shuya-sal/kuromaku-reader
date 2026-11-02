package id.shuyasal.kuromaku.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One volume of a novel: its number, its chapters, and its own cover.
 *
 * <p>The cover is per volume on purpose. Using the series artwork for every
 * volume is wrong: each volume has its own printed cover on the source page, so
 * {@link #coverUrl} is filled from that volume's own entry. A volume whose
 * source entry has no picture keeps {@code null} and the library shows a
 * placeholder instead of borrowing another volume's artwork.
 */
public final class Volume {

    public final int number;
    public final List<Chapter> chapters;

    /** Cover artwork for this volume, {@code null} when the source has none. */
    public final String coverUrl;
    /** Declared pixel size of {@link #coverUrl}, 0 when unknown. */
    public final int coverWidth;
    public final int coverHeight;

    public Volume(int number, List<Chapter> chapters) {
        this(number, chapters, null, 0, 0);
    }

    public Volume(int number, List<Chapter> chapters, String coverUrl,
                  int coverWidth, int coverHeight) {
        this.number = number;
        this.chapters = Collections.unmodifiableList(new ArrayList<>(chapters));
        String url = coverUrl == null ? "" : coverUrl.trim();
        this.coverUrl = url.isEmpty() ? null : url;
        this.coverWidth = Math.max(0, coverWidth);
        this.coverHeight = Math.max(0, coverHeight);
    }

    public String title() {
        return number > 0 ? "Volume " + number : "Volume lain";
    }

    public boolean hasCover() {
        return coverUrl != null;
    }

    /** Width divided by height of the real artwork, 0 when unknown. */
    public float coverRatio() {
        if (coverWidth <= 0 || coverHeight <= 0) return 0f;
        return coverWidth / (float) coverHeight;
    }

    /** A copy of this volume with cover artwork attached. */
    public Volume withCover(String url, int width, int height) {
        return new Volume(number, chapters, url, width, height);
    }

    public int chapterCount() {
        return chapters.size();
    }
}
