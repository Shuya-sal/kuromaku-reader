package id.shuyasal.kuromaku.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.Novel;
import id.shuyasal.kuromaku.data.Novels;
import id.shuyasal.kuromaku.offline.OfflineStore;

/**
 * The "Unduhan" page: everything this device holds for offline reading.
 *
 * <p>It answers four questions without leaving the screen: which novels have
 * offline content, which volumes are stored, how many chapters are saved, and
 * how much space they take. Deleting is offered at chapter and at volume level,
 * always confirmed by the host activity first.
 *
 * <p>The page reads {@link OfflineStore} directly instead of the session
 * catalogue, so it still works with no connection — the whole point of an
 * offline library. Reading progress and bookmarks live in {@link Prefs} and are
 * never touched by anything here.
 */
public final class DownloadsPage {

    /** Delete actions; the host screen confirms before anything is removed. */
    public interface Listener {
        void onDeleteChapter(Novel novel, Chapter chapter);

        void onDeleteVolume(Novel novel, int volume, int chapterCount);

        void onOpenStoredChapter(Novel novel, Chapter chapter);
    }

    private final Context context;
    private final Palette palette;
    private final OfflineStore store;
    private final Listener listener;
    /** Chapter URL → readable label, taken from the live catalogue when available. */
    private final Map<String, String> labels = new LinkedHashMap<>();

    public DownloadsPage(Context context, Palette palette, OfflineStore store, Listener listener) {
        this.context = context;
        this.palette = palette;
        this.store = store;
        this.listener = listener;
    }

    /** Supplies real chapter labels from the catalogue the screen already holds. */
    public DownloadsPage withLabels(Map<String, String> catalogueLabels) {
        if (catalogueLabels != null) labels.putAll(catalogueLabels);
        return this;
    }

    /** Builds the whole page, or a friendly empty state when nothing is stored. */
    public LinearLayout build() {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setTag("downloads_content");

        int chapters = 0;
        for (Novel novel : Novels.all()) chapters += store.savedCount(novel.id);
        long total = store.totalBytes();

        TextView heading = Ui.label(context, "Unduhan offline", 18, palette.text);
        heading.setTag("downloads_heading");
        heading.setTypeface(null, Typeface.BOLD);
        content.addView(heading);

        TextView summary = Ui.label(context,
                chapters == 0
                        ? "Belum ada chapter yang tersimpan di perangkat ini."
                        : chapters + " chapter tersimpan · " + OfflineStore.formatSize(total)
                        + " terpakai",
                12, palette.muted);
        summary.setTag("downloads_summary");
        summary.setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 10));
        content.addView(summary);

        if (chapters == 0) {
            TextView empty = Ui.label(context,
                    "Unduh chapter atau seluruh volume dari halaman detail novel. Setelah "
                            + "tersimpan, chapter bisa dibaca tanpa internet, termasuk seluruh "
                            + "ilustrasinya. Menghapus unduhan tidak menghapus progres membaca.",
                    13, palette.muted);
            empty.setTag("downloads_empty");
            empty.setPadding(0, Ui.dp(context, 6), 0, Ui.dp(context, 12));
            content.addView(empty);
            return content;
        }

        for (Novel novel : Novels.all()) {
            if (store.savedCount(novel.id) == 0) continue;
            content.addView(novelSection(novel));
        }
        return content;
    }

    /** One novel block: name, totals, then each volume that holds something. */
    private LinearLayout novelSection(Novel novel) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setTag("downloads_novel:" + novel.id);
        box.setBackground(Ui.rounded(palette.surface, palette.divider, 12, context));
        box.setPadding(Ui.dp(context, 12), Ui.dp(context, 12),
                Ui.dp(context, 12), Ui.dp(context, 12));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, Ui.dp(context, 8), 0, Ui.dp(context, 4));

        TextView title = Ui.label(context, novel.title, 15, palette.text);
        title.setTypeface(null, Typeface.BOLD);
        box.addView(title);

        TextView meta = Ui.label(context,
                store.savedCount(novel.id) + " chapter · "
                        + OfflineStore.formatSize(store.bytesOf(novel.id)),
                11, palette.muted);
        meta.setTag("downloads_novel_meta:" + novel.id);
        meta.setPadding(0, 0, 0, Ui.dp(context, 2));
        box.addView(meta);

        for (Map.Entry<Integer, List<OfflineStore.Entry>> group
                : store.byVolume(novel.id).entrySet()) {
            box.addView(volumeSection(novel, group.getKey(), group.getValue()));
        }

        box.setLayoutParams(params);
        return box;
    }

    /** One volume: chapter count, size, volume delete, then its stored chapters. */
    private LinearLayout volumeSection(Novel novel, int volume,
                                       List<OfflineStore.Entry> entries) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setTag("downloads_volume:" + novel.id + ":" + volume);
        box.setPadding(0, Ui.dp(context, 10), 0, 0);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView name = Ui.label(context,
                volume > 0 ? "Volume " + volume : "Volume lain", 13, palette.text);
        name.setTypeface(null, Typeface.BOLD);
        texts.addView(name);

        long bytes = 0;
        for (OfflineStore.Entry entry : entries) bytes += entry.bytes;
        TextView meta = Ui.label(context,
                entries.size() + " chapter · " + OfflineStore.formatSize(bytes), 11, palette.muted);
        meta.setTag("downloads_volume_meta:" + novel.id + ":" + volume);
        texts.addView(meta);

        row.addView(texts, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button deleteVolume = Ui.chip(context, "Hapus volume", palette);
        deleteVolume.setTextSize(11);
        deleteVolume.setTag("delete_volume:" + novel.id + ":" + volume);
        deleteVolume.setMinimumHeight(Ui.dp(context, Ui.TOUCH_DP));
        final int count = entries.size();
        deleteVolume.setOnClickListener(v -> listener.onDeleteVolume(novel, volume, count));
        row.addView(deleteVolume);

        box.addView(row);

        for (OfflineStore.Entry entry : entries) {
            box.addView(chapterRow(novel, volume, entry));
        }
        return box;
    }

    private LinearLayout chapterRow(Novel novel, int volume, OfflineStore.Entry entry) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setTag("downloads_chapter:" + entry.url);
        row.setMinimumHeight(Ui.dp(context, Ui.TOUCH_DP));
        row.setPadding(Ui.dp(context, 8), 0, 0, 0);

        String label = labels.get(entry.url);
        if (label == null || label.isEmpty()) label = entry.title;
        if (label == null || label.isEmpty()) label = "Chapter tersimpan";

        TextView text = Ui.label(context, "• " + label, 12, palette.text);
        text.setPadding(0, 0, Ui.dp(context, 6), 0);
        row.addView(text, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView size = Ui.label(context, OfflineStore.formatSize(entry.bytes), 10, palette.muted);
        size.setPadding(0, 0, Ui.dp(context, 8), 0);
        row.addView(size);

        Button delete = Ui.chip(context, "Hapus", palette);
        delete.setTextSize(10);
        delete.setTag("delete_chapter:" + entry.url);
        delete.setMinimumHeight(Ui.dp(context, Ui.TOUCH_DP));
        delete.setPadding(Ui.dp(context, 10), 0, Ui.dp(context, 10), 0);
        final Chapter chapter = chapterOf(novel, entry, volume);
        delete.setOnClickListener(v -> listener.onDeleteChapter(novel, chapter));

        // Tapping the row opens the stored chapter, which also works offline.
        row.setOnClickListener(v -> listener.onOpenStoredChapter(novel, chapter));
        row.addView(delete);
        return row;
    }

    /**
     * Rebuilds enough of a {@link Chapter} for the store to find its folder. The
     * catalogue may be unavailable offline, so the entry's own URL and title are
     * used rather than looked up.
     */
    private Chapter chapterOf(Novel novel, OfflineStore.Entry entry, int volume) {
        return new Chapter(entry.title, entry.title, entry.url, "", 0,
                volume > 0 ? volume : entry.volume);
    }

    /** Total chapters stored for one novel, used by the library card summary. */
    public static int storedCount(OfflineStore store, Novel novel) {
        return store.savedCount(novel.id);
    }
}
