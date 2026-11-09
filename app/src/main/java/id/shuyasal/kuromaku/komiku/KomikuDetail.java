package id.shuyasal.kuromaku.komiku;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Detail satu judul: metadata dari halaman Komiku + daftar chapter. */
public final class KomikuDetail {

    public final String id;
    public final String title;
    public final String alternative;
    public final String status;
    public final String type;
    public final List<String> genres;
    public final String synopsis;
    public final String coverUrl;
    public final List<KomikuChapter> chapters;

    public KomikuDetail(String id, String title, String alternative, String status, String type,
                        List<String> genres, String synopsis, String coverUrl,
                        List<KomikuChapter> chapters) {
        this.id = id == null ? "" : id;
        this.title = title == null ? "" : title;
        this.alternative = alternative == null ? "" : alternative;
        this.status = status == null ? "" : status;
        this.type = type == null ? "" : type;
        this.genres = genres == null ? Collections.emptyList() : new ArrayList<>(genres);
        this.synopsis = synopsis == null ? "" : synopsis;
        this.coverUrl = coverUrl == null ? "" : coverUrl;
        this.chapters = chapters == null ? Collections.emptyList() : new ArrayList<>(chapters);
    }

    /** Baris ringkas: "Manga · Ongoing · 392 chapter". */
    public String meta() {
        List<String> parts = new ArrayList<>();
        if (!type.isEmpty()) parts.add(type);
        if (!status.isEmpty()) parts.add(status);
        if (!chapters.isEmpty()) parts.add(chapters.size() + " chapter");
        return KomikuManga.join(parts);
    }

    /** Chapter untuk dibaca berikutnya: paling awal bila belum ada riwayat. */
    public KomikuChapter firstChapter() {
        List<KomikuChapter> ordered = KomikuChapter.oldestFirst(chapters);
        return ordered.isEmpty() ? null : ordered.get(0);
    }
}