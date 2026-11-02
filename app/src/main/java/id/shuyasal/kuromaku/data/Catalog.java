package id.shuyasal.kuromaku.data;

import java.util.ArrayList;
import java.util.List;

/** Whole library: volumes in reading order, plus chapter navigation helpers. */
public final class Catalog {

    public final List<Volume> volumes;

    public Catalog(List<Volume> volumes) {
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
}
