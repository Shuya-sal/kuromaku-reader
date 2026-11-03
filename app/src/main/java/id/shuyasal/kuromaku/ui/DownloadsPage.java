package id.shuyasal.kuromaku.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import id.shuyasal.kuromaku.R;
import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.Novel;
import id.shuyasal.kuromaku.data.Novels;
import id.shuyasal.kuromaku.offline.OfflineStore;

/**
 * The "Unduhan" page: everything this device holds for offline reading, plus the
 * jobs that are still running.
 *
 * <p>The page never renders empty. With nothing stored and nothing running it
 * shows an icon, what is missing, and a way back to the chapters. While a
 * download runs it shows the novel, the volume, how many chapters are done out
 * of how many, a progress bar, the live status and a cancel button. A failed
 * chapter is listed with its reason and a retry. Stored chapters come last, with
 * per-chapter and per-volume delete.
 *
 * <p>Everything is read from {@link OfflineStore} and {@link Jobs} — the real
 * storage and the real queue state — so the numbers on screen are the numbers on
 * disk. Reading progress and bookmarks live in {@link Prefs} and are never
 * touched here.
 */
public final class DownloadsPage {

    /** Delete and navigation actions; the host screen confirms before deleting. */
    public interface Listener {
        void onDeleteChapter(Novel novel, Chapter chapter);

        void onDeleteVolume(Novel novel, int volume, int chapterCount);

        void onOpenStoredChapter(Novel novel, Chapter chapter);

        /** Leaves this page for the chapter list; ignored when no novel is open. */
        default void onBrowseChapters() {
        }

        /** Re-queues a job that failed. */
        default void onRetryJob(Job job) {
        }

        /** Stops a running job; finished chapters stay stored. */
        default void onCancelJob(Job job) {
        }
    }

    /** One volume of work: running, queued or failed. */
    public static final class Job {
        public final Novel novel;
        public final int volume;
        public final int finished;
        public final int total;
        public final int failed;
        public final String message;

        public Job(Novel novel, int volume, int finished, int total, int failed, String message) {
            this.novel = novel;
            this.volume = volume;
            this.finished = finished;
            this.total = total;
            this.failed = failed;
            this.message = message == null ? "" : message;
        }

        public String title() {
            String name = novel == null ? "Novel" : novel.title;
            return volume > 0 ? name + " · Volume " + volume : name;
        }
    }

    /** Supplies the live queue state. Kept narrow so the page stays testable. */
    public interface Jobs {
        List<Job> active();

        List<Job> failed();
    }

    private final Context context;
    private final Palette palette;
    private final OfflineStore store;
    private final Listener listener;
    /** Chapter URL → readable label, taken from the live catalogue when available. */
    private final Map<String, String> labels = new LinkedHashMap<>();
    private Jobs jobs;

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

    /** Supplies the running and failed jobs so progress and retry can be shown. */
    public DownloadsPage withJobs(Jobs jobs) {
        this.jobs = jobs;
        return this;
    }

    /** Builds the whole page. There is always something to show. */
    public LinearLayout build() {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setTag("downloads_content");

        int chapters = 0;
        for (Novel novel : Novels.all()) chapters += store.savedCount(novel.id);
        long total = store.totalBytes();

        List<Job> active = jobs == null ? new ArrayList<Job>() : safe(jobs.active());
        List<Job> failedJobs = jobs == null ? new ArrayList<Job>() : safe(jobs.failed());

        TextView heading = Ui.label(context, "Unduhan offline", 18, palette.text);
        heading.setTag("downloads_heading");
        heading.setTypeface(null, Typeface.BOLD);
        content.addView(heading);

        content.addView(summaryLine(chapters, total, active.size()));

        // Running work first: it is the most useful thing on the screen.
        if (!active.isEmpty()) {
            content.addView(jobSection("Sedang diunduh", active, true));
        }
        if (!failedJobs.isEmpty()) {
            content.addView(jobSection("Gagal diunduh", failedJobs, false));
        }

        if (chapters == 0 && active.isEmpty()) {
            content.addView(emptyState());
            return content;
        }

        for (Novel novel : Novels.all()) {
            if (store.savedCount(novel.id) == 0) continue;
            content.addView(novelSection(novel));
        }
        return content;
    }

    private static List<Job> safe(List<Job> jobs) {
        return jobs == null ? new ArrayList<Job>() : jobs;
    }

    private View summaryLine(int chapters, long total, int activeJobs) {
        String text;
        if (chapters == 0 && activeJobs == 0) {
            text = "Belum ada chapter yang diunduh.";
        } else if (chapters == 0) {
            text = activeJobs + " volume sedang diunduh · 0 chapter tersimpan";
        } else {
            text = chapters + " chapter tersimpan · " + OfflineStore.formatSize(total) + " terpakai";
        }
        TextView summary = Ui.label(context, text, 12, palette.muted);
        summary.setTag("downloads_summary");
        summary.setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 10));
        return summary;
    }

    // ------------------------------------------------------------- empty state

    /** Nothing stored and nothing running: say so, and offer the way back. */
    private LinearLayout emptyState() {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setTag("downloads_empty");
        box.setBackground(Ui.rounded(palette.surface, palette.divider, 12, context));
        box.setPadding(Ui.dp(context, 16), Ui.dp(context, 20),
                Ui.dp(context, 16), Ui.dp(context, 18));

        ImageView icon = new ImageView(context);
        icon.setImageDrawable(Ui.tinted(context, R.drawable.ic_download, palette.muted));
        icon.setTag("downloads_empty_icon");
        icon.setContentDescription("Belum ada unduhan");
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(Ui.dp(context, 40), Ui.dp(context, 40));
        iconParams.setMargins(0, 0, 0, Ui.dp(context, 10));
        box.addView(icon, iconParams);

        TextView title = Ui.label(context, "Belum ada chapter yang diunduh", 15, palette.text);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, Typeface.BOLD);
        box.addView(title);

        TextView hint = Ui.label(context,
                "Pilih chapter atau volume untuk dibaca secara offline", 12, palette.muted);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, Ui.dp(context, 6), 0, Ui.dp(context, 14));
        box.addView(hint);

        Button browse = Ui.chip(context, "Lihat Chapter", palette);
        browse.setTag("downloads_browse");
        browse.setMinimumHeight(Ui.dp(context, Ui.TOUCH_DP));
        browse.setOnClickListener(v -> listener.onBrowseChapters());
        box.addView(browse);

        TextView note = Ui.label(context,
                "Chapter yang tersimpan bisa dibaca tanpa internet, termasuk ilustrasinya. "
                        + "Menghapus unduhan tidak menghapus progres membaca.", 11, palette.muted);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, Ui.dp(context, 12), 0, 0);
        box.addView(note);
        return box;
    }

    // ----------------------------------------------------------- running / failed

    private LinearLayout jobSection(String heading, List<Job> jobs, boolean running) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setTag(running ? "downloads_running" : "downloads_failed");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, Ui.dp(context, 6));
        box.setLayoutParams(params);

        TextView title = Ui.label(context, heading, 13, palette.text);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 6));
        box.addView(title);

        for (Job job : jobs) box.addView(jobCard(job, running));
        return box;
    }

    private LinearLayout jobCard(Job job, boolean running) {
        String id = job.novel.id + ":" + job.volume;
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setTag("downloads_job:" + id);
        card.setBackground(Ui.rounded(palette.surface, palette.divider, 12, context));
        card.setPadding(Ui.dp(context, 12), Ui.dp(context, 10),
                Ui.dp(context, 12), Ui.dp(context, 10));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, Ui.dp(context, 8));
        card.setLayoutParams(cardParams);

        TextView name = Ui.label(context, job.title(), 13, palette.text);
        name.setTypeface(null, Typeface.BOLD);
        card.addView(name);

        TextView counts = Ui.label(context,
                job.finished + " dari " + job.total + " chapter selesai", 11, palette.muted);
        counts.setTag("downloads_job_counts:" + id);
        counts.setPadding(0, Ui.dp(context, 2), 0, Ui.dp(context, 6));
        card.addView(counts);

        ProgressBar bar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        bar.setTag("downloads_job_bar:" + id);
        bar.setMax(Math.max(1, job.total));
        bar.setProgress(Math.min(job.finished, Math.max(1, job.total)));
        bar.setIndeterminate(false);
        card.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView status = Ui.label(context, statusText(job, running), 11,
                running ? palette.accent : palette.muted);
        status.setTag("downloads_job_status:" + id);
        status.setPadding(0, Ui.dp(context, 6), 0, Ui.dp(context, 8));
        card.addView(status);

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        if (running) {
            Button cancel = Ui.chip(context, "Batalkan", palette);
            cancel.setTextSize(11);
            cancel.setTag("downloads_job_cancel:" + id);
            cancel.setMinimumHeight(Ui.dp(context, Ui.TOUCH_DP));
            cancel.setOnClickListener(v -> listener.onCancelJob(job));
            actions.addView(cancel);
        } else {
            Button retry = Ui.chip(context, "Coba Lagi", palette);
            retry.setTextSize(11);
            retry.setTag("downloads_retry:" + id);
            retry.setMinimumHeight(Ui.dp(context, Ui.TOUCH_DP));
            retry.setOnClickListener(v -> listener.onRetryJob(job));
            actions.addView(retry);
        }
        card.addView(actions);
        return card;
    }

    private String statusText(Job job, boolean running) {
        if (!running) {
            return job.message.isEmpty()
                    ? job.failed + " chapter gagal diunduh." : job.message;
        }
        if (job.total <= 0) return "Menyiapkan…";
        if (job.finished >= job.total) return "Menyelesaikan…";
        return "Mengunduh chapter " + (job.finished + 1) + " dari " + job.total;
    }

    // ----------------------------------------------------------- stored content

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
        box.setLayoutParams(params);

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
