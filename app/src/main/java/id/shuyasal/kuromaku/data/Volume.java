package id.shuyasal.kuromaku.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Volume {

    public final int number;
    public final List<Chapter> chapters;

    public Volume(int number, List<Chapter> chapters) {
        this.number = number;
        this.chapters = Collections.unmodifiableList(new ArrayList<>(chapters));
    }

    public String title() {
        return number > 0 ? "Volume " + number : "Volume lain";
    }
}
