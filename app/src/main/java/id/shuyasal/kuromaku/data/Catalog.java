package id.shuyasal.kuromaku.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Whole catalogue of one novel: volumes in reading order, plus navigation
 * helpers.
 *
 * <p>{@link #novelId} ties the catalogue to its novel so the reader can never
 * walk into a chapter that belongs to a different book.
 */
public final class Catalog {

    public final List<Volume> volumes;
    /** Novel this catalogue belongs to; empty for legacy single-novel callers. */
    public final String novelId;

    public Catalog(List<Volume> volumes) {
        this("", volumes);
    }

    public Catalog(String novelId, List<Volume> volumes) {
        this.novelId = novelId == null ? "" : novelId;
        this.volumes = volumes == null ? new ArrayList<Volume>() : volumes;
    }

    public List<Chapter> flat() {
        List<Chapter> out = new ArrayList<>();
        for (Volume volume : volumes) out.addAll(volume.chapters);
        return out;
    }

    public int size() {
        int total = 0;
        for (Volume volume : volumes) total += volume.chapters.size();
        return total;
    }

    public int indexOf(String url) {
        if (url == null) return -1;
        List<Chapter> all = flat();
        for (int i = 0; i < all.size(); i++) {
            if (url.equals(all.get(i).url)) return i;
        }
        return -1;
    }

    public Chapter findByUrl(String url) {
        int index = indexOf(url);
        return index < 0 ? null : flat().get(index);
    }

    public Chapter chapterAt(int index) {
        List<Chapter> all = flat();
        if (index < 0 || index >= all.size()) return null;
        return all.get(index);
    }

    /** Volume holding the given chapter, or null. */
    public Volume volumeOf(String url) {
        for (Volume volume : volumes) {
            for (Chapter chapter : volume.chapters) {
                if (chapter.url.equals(url)) return volume;
            }
        }
        return null;
    }

    /** Volume with this number, or null when the catalogue has no such volume. */
    public Volume volume(int number) {
        for (Volume volume : volumes) {
            if (volume.number == number) return volume;
        }
        return null;
    }

    /**
     * Attaches verified per-volume covers. A volume with no entry in the cover
     * table is left uncovered on purpose: borrowing another volume's artwork
     * would be worse than showing a placeholder.
     *
     * <p>A cover already found on the novel's own catalogue page wins: the table
     * only knows the built-in novels, so letting it overwrite a parsed cover would
     * undo the very thing that gives a URL-added novel real artwork. The table is
     * therefore a fallback for volumes the page did not describe.
     */
    public Catalog withCovers(String novel) {
        List<Volume> out = new ArrayList<>();
        for (Volume volume : volumes) {
            if (volume.hasCover()) {
                out.add(volume);
                continue;
            }
            VolumeCovers.Cover cover = VolumeCovers.forVolume(novel, volume.number);
            out.add(cover == null
                    ? volume
                    : volume.withCover(cover.url, cover.width, cover.height));
        }
        return new Catalog(this.novelId.isEmpty() ? novel : this.novelId, out);
    }

    /**
     * Same volumes with covers filled from the stored table where the catalogue has
     * none. Used when rendering a catalogue that came from cache or from the stored
     * database, so covers found earlier still show without re-parsing the page.
     */
    public Catalog withStoredCovers(java.util.Map<Integer, VolumeCovers.Cover> stored) {
        if (stored == null || stored.isEmpty()) return this;
        List<Volume> out = new ArrayList<>();
        for (Volume volume : volumes) {
            if (volume.hasCover()) {
                out.add(volume);
                continue;
            }
            VolumeCovers.Cover cover = stored.get(volume.number);
            out.add(cover == null
                    ? volume
                    : volume.withCover(cover.url, cover.width, cover.height));
        }
        return new Catalog(novelId, out);
    }

    /** Same volumes, tagged with the novel they belong to. */
    public Catalog withNovel(String id) {
        return new Catalog(id, volumes);
    }
}
