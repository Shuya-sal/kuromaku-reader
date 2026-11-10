package id.shuyasal.kuromaku.sync;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * Jembatan antara riwayat masing-masing sumber dan progres kanonis.
 *
 * <p>Alurnya satu arah per kejadian: sumber mencatat bacaan seperti biasa
 * (riwayat lokalnya tidak diubah), lalu kejadian itu diteruskan ke
 * {@link SyncStore} sebagai status kanonis. Layar sumber lain membaca status
 * kanonis itu dan menampilkannya — tidak ada baris yang disalin ke database
 * sumber lain, sehingga tidak mungkin terjadi dua penulis yang saling
 * menimpa.
 */
public final class SyncBridge {

    /** Ringkasan satu judul untuk ditampilkan di layar detail. */
    public static final class LinkState {
        public final boolean linked;
        public final String seriesId;
        public final String otherSource;
        public final String otherTitle;
        public final String lastCompletedKey;

        LinkState(boolean linked, String seriesId, String otherSource, String otherTitle,
                  String lastCompletedKey) {
            this.linked = linked;
            this.seriesId = seriesId;
            this.otherSource = otherSource;
            this.otherTitle = otherTitle;
            this.lastCompletedKey = lastCompletedKey;
        }

        static LinkState none() {
            return new LinkState(false, "", "", "", "");
        }

        /** Label sumber untuk UI. */
        public String otherSourceLabel() {
            if (SyncStore.SOURCE_KOMIKU.equals(otherSource)) return "Manga Server 3";
            if (SyncStore.SOURCE_SHINIGAMI.equals(otherSource)) return "Manga Server 2";
            return otherSource;
        }
    }

    /** Nama sumber untuk pemanggil yang tidak ingin mengimpor {@link SyncStore}. */
    public static final String SOURCE_SHINIGAMI = SyncStore.SOURCE_SHINIGAMI;
    public static final String SOURCE_KOMIKU = SyncStore.SOURCE_KOMIKU;

    private final SyncStore sync;

    public SyncBridge(Context context) {
        sync = new SyncStore(context);
    }

    public SyncStore store() {
        return sync;
    }

    /**
     * Catat bahwa sebuah chapter dibuka pada satu sumber.
     *
     * <p>Bila judulnya belum dipasangkan, tidak ada yang diteruskan — sumber
     * lain memang tidak punya padanan untuk chapter ini.
     */
    public void onChapterOpened(String sourceId, String mangaId, String chapterLabel,
                                boolean completed) {
        SyncStore.Series series = sync.forSource(sourceId, mangaId);
        if (series == null) return;
        String key = ChapterKey.of(chapterLabel);
        if (key.isEmpty()) return;
        sync.recordOpened(series.id, key, sourceId, completed, System.currentTimeMillis());
    }

    /** Status sebuah chapter menurut data kanonis; {@code null} bila tidak ada. */
    public SyncStore.Progress canonical(String sourceId, String mangaId, String chapterLabel) {
        SyncStore.Series series = sync.forSource(sourceId, mangaId);
        if (series == null) return null;
        String key = ChapterKey.of(chapterLabel);
        if (key.isEmpty()) return null;
        return sync.progress(series.id, key);
    }

    /**
     * Benar bila chapter ini sudah selesai dibaca di sumber mana pun.
     *
     * <p>Dipakai penanda "sudah dibaca" pada daftar chapter: sumber yang
     * belum pernah dibuka tetap menampilkan tanda selesai bila pasangannya
     * sudah menyelesaikan chapter yang sama.
     */
    public boolean isCompletedAnywhere(String sourceId, String mangaId, String chapterLabel) {
        SyncStore.Progress progress = canonical(sourceId, mangaId, chapterLabel);
        return progress != null && progress.completed;
    }

    /** Keadaan tautan satu judul untuk badge di layar detail. */
    public LinkState stateFor(String sourceId, String mangaId) {
        SyncStore.Series series = sync.forSource(sourceId, mangaId);
        if (series == null) return LinkState.none();
        List<String> others = series.otherSources(sourceId);
        if (others.isEmpty()) return LinkState.none();
        String other = others.get(0);
        String last = sync.lastCompleted(series.id);
        return new LinkState(true, series.id, other, series.titleFor(other),
                last == null ? "" : last);
    }

    /**
     * Pasangkan judul ini dengan judul di sumber lain.
     *
     * @return karya kanonis hasil pemasangan
     */
    public SyncStore.Series link(String sourceId, String mangaId, String title, String url,
                                 String otherSourceId, String otherMangaId, String otherTitle,
                                 String otherUrl, String method) {
        return sync.link(sourceId, mangaId, title, url,
                otherSourceId, otherMangaId, otherTitle, otherUrl, method);
    }

    /** Lepas tautan; progres kanonis dan riwayat sumber tetap utuh. */
    public void unlink(String sourceId, String mangaId) {
        sync.unlink(sourceId, mangaId);
    }

    /**
     * Chapter kanonis terakhir yang sudah selesai, dipetakan kembali ke label
     * chapter pada sumber tertentu.
     *
     * <p>Hanya mengembalikan chapter yang benar-benar ada di daftar sumber
     * itu — tidak pernah mengarang alamat.
     */
    public String lastCompletedLabel(String sourceId, String mangaId,
                                     List<String> chapterLabels) {
        if (chapterLabels == null || chapterLabels.isEmpty()) return "";
        SyncStore.Series series = sync.forSource(sourceId, mangaId);
        if (series == null) return "";

        String best = "";
        double bestNumber = -1;
        for (String label : chapterLabels) {
            String key = ChapterKey.of(label);
            if (key.isEmpty()) continue;
            SyncStore.Progress progress = sync.progress(series.id, key);
            if (progress == null || !progress.completed) continue;
            double number = ChapterKey.numberValue(label);
            if (number > bestNumber) {
                bestNumber = number;
                best = label;
            }
        }
        return best;
    }

    /**
     * Label chapter untuk "Lanjutkan Membaca": chapter setelah yang terakhir
     * selesai, bila ada di daftar sumber ini. Mengembalikan string kosong bila
     * tidak ada padanan — pemanggil tidak boleh mengarang alamat.
     */
    public String nextAfterLastCompleted(String sourceId, String mangaId,
                                         List<String> chapterLabelsOldestFirst) {
        if (chapterLabelsOldestFirst == null || chapterLabelsOldestFirst.isEmpty()) return "";
        String lastCompleted = lastCompletedLabel(sourceId, mangaId, chapterLabelsOldestFirst);
        if (lastCompleted.isEmpty()) return "";
        int index = chapterLabelsOldestFirst.indexOf(lastCompleted);
        if (index < 0 || index + 1 >= chapterLabelsOldestFirst.size()) return "";
        return chapterLabelsOldestFirst.get(index + 1);
    }


    /** Satu judul yang sudah tersinkron, untuk ditawarkan di beranda. */
    public static final class PartnerEntry {
        public final String mangaId;
        public final String title;
        public final String chapterKey;
        public final long lastOpenedAt;

        PartnerEntry(String mangaId, String title, String chapterKey, long lastOpenedAt) {
            this.mangaId = mangaId == null ? "" : mangaId;
            this.title = title == null ? "" : title;
            this.chapterKey = chapterKey == null ? "" : chapterKey;
            this.lastOpenedAt = lastOpenedAt;
        }

        /** "Chapter 10" dari kunci kanonis; kosong bila belum ada. */
        public String chapterLabel() {
            return ChapterKey.displayLabel(chapterKey);
        }
    }

    /**
     * Judul yang dibaca di sumber pasangan tetapi belum ada riwayatnya di
     * sumber ini.
     *
     * <p>Ini yang membuat daftar "Lanjutkan Membaca" di Manga Server 3 tetap
     * berguna walau judul itu baru dibaca di Manga Server 2: judulnya muncul
     * dengan chapter kanonis terakhir. Yang dikembalikan hanya judul yang
     * benar-benar punya tautan dan punya chapter tercatat — tidak ada entri
     * karangan, dan tidak ada judul yang sudah punya riwayat lokal sendiri
     * (supaya tidak tampil dua kali).
     */
    public List<PartnerEntry> pendingFrom(String sourceId, String partnerSourceId,
                                         java.util.Set<String> localMangaIds) {
        List<PartnerEntry> out = new ArrayList<>();
        if (sourceId == null || partnerSourceId == null) return out;
        for (SyncStore.Series series : sync.series()) {
            String mine = series.mangaIdFor(sourceId);
            String partner = series.mangaIdFor(partnerSourceId);
            // Butuh tautan ke sumber ini: tanpa alamat judul di sini, tidak ada
            // yang bisa ditawarkan. Bila judul sudah ada di sini, alamat itu
            // yang dipakai; bila belum, alamat pasangan yang dipakai untuk
            // membuka halaman detailnya.
            String target = !mine.isEmpty() ? mine : partner;
            if (target.isEmpty()) continue;
            // Yang sudah punya riwayat lokal sudah muncul dari riwayat, jangan
            // ditampilkan dua kali.
            if (!mine.isEmpty() && localMangaIds != null && localMangaIds.contains(mine)) {
                continue;
            }
            SyncStore.Progress progress = sync.lastOpened(series.id);
            if (progress == null || progress.chapterKey.isEmpty()) continue;
            String title = series.titleFor(sourceId);
            if (title.isEmpty()) title = series.titleFor(partnerSourceId);
            if (title.isEmpty()) title = series.preferredTitle;
            out.add(new PartnerEntry(target, title, progress.chapterKey,
                    progress.lastOpenedAt));
        }
        out.sort((a, b) -> Long.compare(b.lastOpenedAt, a.lastOpenedAt));
        return out;
    }

    /** Daftar sumber yang ikut sinkron, untuk keperluan UI. */
    public static List<String> sources() {
        List<String> out = new ArrayList<>();
        out.add(SyncStore.SOURCE_SHINIGAMI);
        out.add(SyncStore.SOURCE_KOMIKU);
        return out;
    }
}