package id.shuyasal.kuromaku.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import id.shuyasal.kuromaku.R;
import id.shuyasal.kuromaku.data.Catalog;
import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.Novel;
import id.shuyasal.kuromaku.data.Volume;
import id.shuyasal.kuromaku.offline.DownloadStatusStore;

/**
 * Novel detail page: cover, verified metadata, synopsis, continue button and the
 * volume list where every volume carries its own cover.
 *
 * <p>Kept out of the activity so the layout can be measured in a Robolectric
 * test: cover ratio, per-volume thumbnails, the accordion and the placeholder
 * behaviour are all verified against real view geometry.
 *
 * <p>Every volume row shows that volume's own artwork. When the source page has
 * no picture for a volume, a tidy placeholder is shown instead: borrowing Volume
 * 1's cover for every volume is exactly the mistake this page must not make.
 */
public final class LibraryPage {

    /** Callbacks the hosting screen wires to navigation and state changes. */
    public interface Listener {
        void onOpenChapter(Chapter chapter);

        void onToggleSynopsis();

        void onToggleVolume(String volumeKey);

        void onOpenSource();

        /** Cover tapped: open it full screen so it can be zoomed. */
        void onOpenCover(String url, String caption);
    }

    /**
     * Offline download actions.
     *
     * <p>Separate from {@link Listener} on purpose: the page renders the same
     * whether or not downloads are wired, and a screen that does not support
     * offline reading never has to implement five empty methods.
     */
    public interface DownloadListener {
        void onDownloadChapter(Chapter chapter);

        void onDeleteChapter(Chapter chapter);

        void onDownloadVolume(Volume volume);

        void onCancelVolume(Volume volume);
    }

    private static final int COVER_WIDTH_DP = 210;
    private static final int THUMB_WIDTH_DP = 62;
    private static final float FALLBACK_COVER_RATIO = 0.70f;
    private static final float THUMB_RATIO = 0.70f;

    private final Context context;
    private final Prefs prefs;
    private final Palette palette;
    private final Novel novel;
    private final Catalog catalog;
    private final boolean synopsisOpen;
    private final Listener listener;
    /** Optional: when null no download controls are drawn. */
    private DownloadListener downloadListener;
    /** Set when the activity knows which chapters are queued/saved/failed. */
    private DownloadState downloadState;

    public LibraryPage(Context context, Prefs prefs, Palette palette, Novel novel,
                       Catalog catalog, boolean synopsisOpen, Listener listener) {
        this.context = context;
        this.prefs = prefs;
        this.palette = palette;
        this.novel = novel;
        this.catalog = catalog;
        this.synopsisOpen = synopsisOpen;
        this.listener = listener;
    }

    /**
     * Read-only view of the download layer, so this page can print a status per
     * chapter without knowing anything about WorkManager or the file system.
     */
    public interface DownloadState {
        /** One of the {@code DownloadStatusStore} constants. */
        int state(Chapter chapter);

        /** Human label: "Belum diunduh", "Mengunduh…", "Tersimpan", "Gagal". */
        String statusLabel(Chapter chapter);

        /** {@code [finished, total]} for a volume, or null when nothing is known. */
        int[] volumeProgress(Volume volume);

        /** True while a volume job is running, so the page offers "Batalkan". */
        boolean isVolumeRunning(Volume volume);

        /**
         * Measured byte percentage for a chapter's current illustration, or -1.
         *
         * <p>-1 is not "zero": it means the server declared no size, so the page
         * must spin instead of printing a percentage it did not measure.
         */
        default int bytePercent(Chapter chapter) {
            return -1;
        }

        /** Fraction of a volume's chapters already stored, or -1 when unknown. */
        default int volumePercent(Volume volume) {
            int[] progress = volumeProgress(volume);
            if (progress == null || progress[1] <= 0) return -1;
            return Math.max(0, Math.min(100, (progress[0] * 100) / progress[1]));
        }
    }

    public LibraryPage setDownload(DownloadListener listener, DownloadState state) {
        this.downloadListener = listener;
        this.downloadState = state;
        return this;
    }

    /** Builds the scrollable content column for the detail screen. */
    public LinearLayout build() {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setTag("library_content");

        addCover(content);
        addTitle(content);
        addMetadata(content);
        addSynopsis(content);
        addContinue(content);
        addVolumes(content);
        addAttribution(content);
        return content;
    }

    /** Cover inside a box that always keeps the artwork's real ratio. */
    private void addCover(LinearLayout parent) {
        int available = context.getResources().getDisplayMetrics().widthPixels
                - Ui.dp(context, 28);
        int width = Math.min(Ui.dp(context, COVER_WIDTH_DP), Math.max(0, available));
        float ratio = novel.coverRatio > 0 ? novel.coverRatio : FALLBACK_COVER_RATIO;
        int height = Math.max(1, Math.round(width / ratio));

        RatioFrameLayout holder = new RatioFrameLayout(context);
        holder.setTag("cover");
        holder.setRatio(ratio);
        holder.setBackground(Ui.rounded(palette.surface, palette.divider, 10, context));

        // Drawn first, so a failed download leaves this tidy box visible
        // instead of an empty hole.
        LinearLayout placeholder = new LinearLayout(context);
        placeholder.setOrientation(LinearLayout.VERTICAL);
        placeholder.setGravity(Gravity.CENTER);
        placeholder.setTag("cover_placeholder");
        placeholder.addView(Ui.label(context, "Sampul", 13, palette.muted));
        placeholder.addView(Ui.label(context, "tidak tersedia", 11, palette.muted));
        holder.addView(placeholder, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView cover = new ImageView(context);
        cover.setTag("cover_image");
        cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
        holder.addView(cover, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // A failed download must never crash the page: the placeholder
        // behind the image stays visible instead.
        try {
            Glide.with(context)
                    .load(novel.coverUrl)
                    .centerCrop()
                    .into(cover);
        } catch (Exception ignored) {
            // Offline or no image loader available: keep the placeholder.
        }

        holder.setOnClickListener(v -> listener.onOpenCover(novel.coverUrl, novel.title));

        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_HORIZONTAL);
        row.setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 14));
        row.addView(holder, new LinearLayout.LayoutParams(width, height));
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addTitle(LinearLayout parent) {
        TextView title = Ui.label(context, novel.title, 21, palette.text);
        title.setTag("novel_title");
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        parent.addView(title);

        if (!novel.fullTitle.isEmpty() && !novel.fullTitle.equals(novel.title)) {
            TextView full = Ui.label(context, novel.fullTitle, 12, palette.text);
            full.setTag("novel_full_title");
            full.setGravity(Gravity.CENTER_HORIZONTAL);
            full.setPadding(0, Ui.dp(context, 6), 0, 0);
            parent.addView(full);
        }

        TextView japanese = Ui.label(context, novel.titleJp, 12, palette.muted);
        japanese.setTag("novel_title_jp");
        japanese.setGravity(Gravity.CENTER_HORIZONTAL);
        japanese.setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 12));
        parent.addView(japanese);
    }

    private void addMetadata(LinearLayout parent) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setTag("metadata");
        card.setBackground(Ui.rounded(palette.surface, palette.divider, 12, context));
        card.setPadding(Ui.dp(context, 14), Ui.dp(context, 6),
                Ui.dp(context, 14), Ui.dp(context, 6));

        addMetaRow(card, "Penulis", novel.author);
        addMetaRow(card, "Ilustrator", novel.illustrator);
        addMetaRow(card, "Genre", novel.genres);
        addMetaRow(card, "Status", novel.status);
        addMetaRow(card, "Terjemahan", novel.translation);

        parent.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addMetaRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Ui.dp(context, 7), 0, Ui.dp(context, 7));

        TextView name = Ui.label(context, label, 12, palette.muted);
        name.setMinWidth(Ui.dp(context, 92));
        row.addView(name);

        TextView text = Ui.label(context, value, 12, palette.text);
        row.addView(text, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        parent.addView(row);
    }

    private void addSynopsis(LinearLayout parent) {
        TextView heading = Ui.label(context, "Sinopsis", 15, palette.text);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(0, Ui.dp(context, 16), 0, Ui.dp(context, 6));
        parent.addView(heading);

        String[] paragraphs = novel.synopsis;
        int preview = Math.max(1, novel.synopsisPreview);
        int visible = synopsisOpen ? paragraphs.length : Math.min(preview, paragraphs.length);

        StringBuilder text = new StringBuilder();
        for (int i = 0; i < visible; i++) {
            if (i > 0) text.append("\n\n");
            text.append(paragraphs[i]);
            if (!synopsisOpen && visible < paragraphs.length && i == visible - 1) {
                text.append(" …");
            }
        }

        TextView body = Ui.label(context, text.toString(), 13, palette.text);
        body.setTag("synopsis");
        body.setLineSpacing(0, 1.45f);
        parent.addView(body);

        if (paragraphs.length > preview) {
            Button toggle = Ui.chip(context,
                    synopsisOpen ? "Tutup sinopsis" : "Baca selengkapnya", palette);
            toggle.setTag("synopsis_toggle");
            toggle.setTextColor(palette.accent);
            toggle.setOnClickListener(v -> listener.onToggleSynopsis());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, Ui.dp(context, 8), 0, 0);
            parent.addView(toggle, params);
        }
    }

    private void addContinue(LinearLayout parent) {
        if (catalog == null) return;
        Chapter resume = catalog.findByUrl(prefs.lastUrl());
        if (resume == null) return;

        int percent = prefs.progress(resume.url);
        String label = percent > 0
                ? "Lanjutkan Membaca · " + resume.label + " (" + percent + "%)"
                : "Lanjutkan Membaca · " + resume.label;

        Button button = Ui.chip(context, label, palette);
        button.setTag("continue");
        button.setTextSize(14);
        button.setTextColor(palette.accent);
        button.setOnClickListener(v -> listener.onOpenChapter(resume));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, Ui.dp(context, 14), 0, Ui.dp(context, 4));
        parent.addView(button, params);
    }

    private void addVolumes(LinearLayout parent) {
        int chapterTotal = catalog == null ? 0 : catalog.size();
        int volumeTotal = catalog == null ? 0 : catalog.volumes.size();

        TextView heading = Ui.label(context,
                "Volume & chapter · " + chapterTotal + " chapter dalam "
                        + volumeTotal + " volume", 15, palette.text);
        heading.setTag("volume_heading");
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(0, Ui.dp(context, 18), 0, Ui.dp(context, 6));
        parent.addView(heading);

        if (catalog == null) {
            parent.addView(Ui.label(context, "Daftar volume belum dimuat.", 12, palette.muted));
            return;
        }
        for (Volume volume : catalog.volumes) {
            addVolume(parent, volume);
        }
    }

    /**
     * One volume row: its own cover thumbnail, its number and chapter count.
     * A volume without artwork gets a placeholder with the same footprint so the
     * list never collapses or borrows another volume's picture.
     */
    private void addVolume(LinearLayout parent, Volume volume) {
        String key = String.valueOf(volume.number);
        boolean open = prefs.isExpanded(key);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setTag("volume:" + key);
        row.setBackground(Ui.ripple(context, palette.surface, palette.divider, 10,
                palette.ripple));
        row.setPadding(Ui.dp(context, 10), Ui.dp(context, 9),
                Ui.dp(context, 12), Ui.dp(context, 9));
        row.setMinimumHeight(Ui.dp(context, Ui.TOUCH_DP));

        addVolumeThumb(row, volume);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Ui.dp(context, 10), 0, Ui.dp(context, 8), 0);

        LinearLayout nameRow = new LinearLayout(context);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = Ui.label(context, volume.title(), 15, palette.text);
        name.setTypeface(null, Typeface.BOLD);
        nameRow.addView(name);

        ImageView chevron = new ImageView(context);
        chevron.setTag("chevron:" + key);
        chevron.setImageDrawable(Ui.tinted(context, R.drawable.ic_chevron_right,
                palette.accent));
        chevron.setRotation(open ? 90f : 0f);
        chevron.setContentDescription(open ? "Tutup daftar chapter" : "Buka daftar chapter");
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(
                Ui.dp(context, 18), Ui.dp(context, 18));
        chevronParams.setMargins(Ui.dp(context, 6), 0, 0, 0);
        nameRow.addView(chevron, chevronParams);
        texts.addView(nameRow);

        String count = volume.chapterCount() == 0
                ? "belum ada chapter"
                : volume.chapterCount() + " chapter";
        texts.addView(Ui.label(context, count, 11, palette.muted));

        // Volume download controls only make sense when the volume has chapters
        // and the host screen actually wired the offline layer.
        if (downloadListener != null && downloadState != null && volume.chapterCount() > 0) {
            addVolumeDownload(texts, volume);
        }
        row.addView(texts, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setTag("chapters:" + key);
        body.setVisibility(open ? View.VISIBLE : View.GONE);
        if (open) {
            if (volume.chapters.isEmpty()) {
                TextView empty = Ui.label(context,
                        "Volume ini belum punya tautan chapter di halaman sumber.",
                        12, palette.muted);
                empty.setPadding(Ui.dp(context, 14), Ui.dp(context, 10),
                        Ui.dp(context, 14), Ui.dp(context, 10));
                body.addView(empty);
            }
            for (Chapter chapter : volume.chapters) {
                addChapter(body, chapter);
            }
        }

        row.setOnClickListener(v -> listener.onToggleVolume(key));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, Ui.dp(context, 4), 0, Ui.dp(context, 2));
        parent.addView(row, params);
        parent.addView(body);
    }

    /** Cover thumbnail for one volume, with placeholder and tap-to-enlarge. */
    private void addVolumeThumb(LinearLayout row, Volume volume) {
        int width = Ui.dp(context, THUMB_WIDTH_DP);
        float ratio = volume.coverRatio() > 0 ? volume.coverRatio() : THUMB_RATIO;
        int height = Math.max(1, Math.round(width / ratio));

        RatioFrameLayout holder = new RatioFrameLayout(context);
        holder.setTag("volume_cover:" + volume.number);
        holder.setRatio(ratio);
        holder.setBackground(Ui.rounded(palette.background, palette.divider, 6, context));

        TextView placeholder = Ui.label(context, "Vol " + volume.number, 10, palette.muted);
        placeholder.setTag("volume_cover_placeholder:" + volume.number);
        placeholder.setGravity(Gravity.CENTER);
        holder.addView(placeholder, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView image = new ImageView(context);
        image.setTag("volume_cover_image:" + volume.number);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        holder.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (volume.hasCover()) {
            try {
                Glide.with(context).load(volume.coverUrl).centerCrop().into(image);
            } catch (Exception ignored) {
                // Keep the placeholder.
            }
            holder.setOnClickListener(v -> listener.onOpenCover(
                    volume.coverUrl, volume.title()));
        }

        row.addView(holder, new LinearLayout.LayoutParams(width, height));
    }

    private void addChapter(LinearLayout parent, Chapter chapter) {
        int progress = prefs.progress(chapter.url);
        boolean finished = progress > 95;
        boolean started = progress > 0 && !finished;

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setTag("chapter:" + chapter.url);
        row.setBackground(Ui.ripple(context, 0, 0, 8, palette.ripple));
        row.setPadding(Ui.dp(context, 26), Ui.dp(context, 12),
                Ui.dp(context, 4), Ui.dp(context, 12));
        row.setMinimumHeight(Ui.dp(context, Ui.TOUCH_DP));

        TextView label = Ui.label(context, chapter.label, 14,
                finished ? palette.muted : palette.text);
        row.addView(label, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        if (finished) {
            row.addView(Ui.label(context, "selesai", 11, palette.muted));
        } else if (started) {
            row.addView(Ui.label(context, progress + "%", 11, palette.accent));
        }

        if (downloadListener != null && downloadState != null) {
            addChapterDownload(row, chapter);
        }

        row.setOnClickListener(v -> listener.onOpenChapter(chapter));
        parent.addView(row);

        View line = new View(context);
        line.setBackgroundColor(palette.divider);
        parent.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(context, 1))));
    }

    /**
     * Download control for one chapter: a progress ring, a status word and one
     * action.
     *
     * <p>The action always matches the status, so the reader never has to guess:
     * not saved → download arrow, queued/downloading/verifying → a ring (and the
     * busy label, which never claims "Menunggu" while bytes are actually moving),
     * saved → a delete button, partial → "Lanjutkan", failed → "Coba lagi". All of
     * them keep the 48 dp touch target the rest of the list uses.
     *
     * <p>The percentage lives inside its own view with its own spacing: printing
     * it directly against the status word is what produced the "67%Mengunduh…"
     * run-together text in the report.
     */
    private void addChapterDownload(LinearLayout row, Chapter chapter) {
        int state = downloadState.state(chapter);
        String status = downloadState.statusLabel(chapter);

        boolean busy = state == DownloadStatusStore.QUEUED
                || state == DownloadStatusStore.DOWNLOADING
                || state == DownloadStatusStore.VERIFYING;
        if (busy) {
            row.addView(chapterRing(chapter, state));
        } else if (state == DownloadStatusStore.SAVED) {
            // A stored chapter is confirmed by a tick, not another arrow.
            ImageView tick = new ImageView(context);
            tick.setTag("download_tick:" + chapter.url);
            tick.setImageDrawable(Ui.tinted(context, R.drawable.ic_check, palette.accent));
            tick.setContentDescription("Tersimpan");
            LinearLayout.LayoutParams tickParams = new LinearLayout.LayoutParams(
                    Ui.dp(context, 16), Ui.dp(context, 16));
            tickParams.setMargins(0, 0, Ui.dp(context, 8), 0);
            row.addView(tick, tickParams);
        }

        TextView statusView = Ui.label(context, status, 10,
                state == DownloadStatusStore.SAVED ? palette.accent : palette.muted);
        statusView.setTag("download_status:" + chapter.url);
        statusView.setPadding(0, 0, Ui.dp(context, 8), 0);
        row.addView(statusView);

        View action;
        if (state == DownloadStatusStore.SAVED) {
            action = smallButton(R.drawable.ic_check, "Hapus unduhan chapter",
                    "download_delete:" + chapter.url,
                    v -> downloadListener.onDeleteChapter(chapter));
        } else if (state == DownloadStatusStore.PARTIAL) {
            action = smallTextButton("Lanjutkan", "download_resume:" + chapter.url,
                    v -> downloadListener.onDownloadChapter(chapter));
        } else if (state == DownloadStatusStore.FAILED) {
            action = smallTextButton("Coba lagi", "download_retry:" + chapter.url,
                    v -> downloadListener.onDownloadChapter(chapter));
        } else if (busy) {
            // The ring above already says what is happening; a second button
            // labelled "Menunggu" on a chapter that is downloading was the bug.
            action = smallTextButton(
                    state == DownloadStatusStore.QUEUED ? "Menunggu" : "Mengunduh",
                    "download_busy:" + chapter.url, null);
            action.setAlpha(0.6f);
        } else {
            action = smallButton(R.drawable.ic_download, "Unduh chapter ini",
                    "download_chapter:" + chapter.url,
                    v -> downloadListener.onDownloadChapter(chapter));
        }
        row.addView(action);
    }

    /**
     * The ring shown while a chapter downloads.
     *
     * <p>Its number comes from {@code bytePercent}, which the downloader fills from
     * real bytes against {@code Content-Length}. When the size is unknown that is
     * -1 and the ring spins with no text at all.
     */
    private View chapterRing(Chapter chapter, int state) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);

        RingProgress ring = new RingProgress(context);
        ring.setTag("download_ring:" + chapter.url);
        ring.colors(palette.divider, palette.accent, 2);
        int size = Ui.dp(context, 16);
        LinearLayout.LayoutParams ringParams = new LinearLayout.LayoutParams(size, size);
        ringParams.setMargins(0, 0, Ui.dp(context, 6), 0);
        box.addView(ring, ringParams);

        int percent = state == DownloadStatusStore.VERIFYING ? -1
                : downloadState.bytePercent(chapter);
        ring.setProgress(percent);

        if (percent >= 0) {
            TextView number = Ui.label(context, percent + "%", 10, palette.accent);
            number.setTag("download_percent:" + chapter.url);
            // Own padding on both sides: the number can never touch the status
            // word that follows it.
            number.setPadding(0, 0, Ui.dp(context, 8), 0);
            box.addView(number);
        }
        return box;
    }

    /**
     * Volume row controls, in the three states the design calls for.
     *
     * <p>Before: "Download Semua". While running: a progress ring, the measured
     * percentage and "x dari y chapter", plus "Batalkan". When some chapters are
     * stored but not all: the count and "Lanjutkan Download". When everything is
     * stored: a tick and "Tersimpan semua".
     *
     * <p>The percentage is computed from stored chapters over the volume's total,
     * so it is a real measurement. With no total to divide by there is no number,
     * only the ring — a percentage invented from nothing would be a lie.
     */
    private void addVolumeDownload(LinearLayout texts, Volume volume) {
        int[] progress = downloadState.volumeProgress(volume);
        boolean running = downloadState.isVolumeRunning(volume);
        int finished = progress == null ? 0 : progress[0];
        int total = progress == null ? 0 : progress[1];
        int percent = downloadState.volumePercent(volume);

        LinearLayout controls = new LinearLayout(context);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(0, Ui.dp(context, 6), 0, 0);

        if (running) {
            RingProgress ring = new RingProgress(context);
            ring.setTag("volume_ring:" + volume.number);
            ring.colors(palette.divider, palette.accent, 2);
            int size = Ui.dp(context, 16);
            LinearLayout.LayoutParams ringParams = new LinearLayout.LayoutParams(size, size);
            ringParams.setMargins(0, 0, Ui.dp(context, 6), 0);
            controls.addView(ring, ringParams);
            // A volume with an unknown total has no honest fraction to show.
            ring.setProgress(total > 0 ? percent : -1);

            TextView progressView = Ui.label(context,
                    (percent >= 0 ? percent + "% · " : "")
                            + finished + " dari " + total + " chapter",
                    11, palette.accent);
            progressView.setTag("volume_progress:" + volume.number);
            progressView.setPadding(0, 0, Ui.dp(context, 8), 0);
            controls.addView(progressView, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            controls.addView(smallTextButton("Batalkan", "volume_cancel:" + volume.number,
                    v -> downloadListener.onCancelVolume(volume)));
            texts.addView(controls);
            return;
        }

        if (total > 0) {
            TextView summary = Ui.label(context,
                    finished + " dari " + total + " tersimpan", 11,
                    finished >= total ? palette.accent : palette.muted);
            summary.setTag("volume_summary:" + volume.number);
            summary.setPadding(0, 0, Ui.dp(context, 8), 0);
            controls.addView(summary, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        } else {
            controls.addView(new android.view.View(context),
                    new LinearLayout.LayoutParams(0, 1, 1));
        }

        boolean allSaved = total > 0 && finished >= total;
        boolean partial = total > 0 && finished > 0 && !allSaved;
        if (allSaved) {
            ImageView tick = new ImageView(context);
            tick.setTag("volume_tick:" + volume.number);
            tick.setImageDrawable(Ui.tinted(context, R.drawable.ic_check, palette.accent));
            tick.setContentDescription("Tersimpan semua");
            LinearLayout.LayoutParams tickParams = new LinearLayout.LayoutParams(
                    Ui.dp(context, 16), Ui.dp(context, 16));
            tickParams.setMargins(0, 0, Ui.dp(context, 6), 0);
            controls.addView(tick, tickParams);
        }
        controls.addView(smallTextButton(
                allSaved ? "Tersimpan semua" : partial ? "Lanjutkan Download" : "Download Semua",
                "volume_download:" + volume.number,
                allSaved ? null : v -> downloadListener.onDownloadVolume(volume)));
        if (allSaved) controls.getChildAt(controls.getChildCount() - 1).setAlpha(0.7f);
        texts.addView(controls);
    }

    /** 48 dp icon action with a content description, themed ripple. */
    private View smallButton(int iconRes, String description, String tag,
                             View.OnClickListener click) {
        android.widget.ImageButton button = new android.widget.ImageButton(context);
        button.setImageDrawable(Ui.tinted(context, iconRes, palette.accent));
        button.setContentDescription(description);
        button.setTag(tag);
        button.setBackground(Ui.ripple(context, 0, 0, 10, palette.ripple));
        button.setPadding(Ui.dp(context, 12), Ui.dp(context, 12),
                Ui.dp(context, 12), Ui.dp(context, 12));
        button.setMinimumWidth(Ui.dp(context, Ui.TOUCH_DP));
        button.setMinimumHeight(Ui.dp(context, Ui.TOUCH_DP));
        button.setStateListAnimator(null);
        if (click != null) button.setOnClickListener(click);
        return button;
    }

    /** Compact 48 dp tall text action, used for retry and cancel. */
    private View smallTextButton(String text, String tag, View.OnClickListener click) {
        Button button = Ui.chip(context, text, palette);
        button.setTextSize(11);
        button.setTag(tag);
        button.setMinimumHeight(Ui.dp(context, Ui.TOUCH_DP));
        button.setPadding(Ui.dp(context, 10), 0, Ui.dp(context, 10), 0);
        if (click != null) {
            button.setOnClickListener(click);
        } else {
            button.setEnabled(false);
        }
        return button;
    }

    private void addAttribution(LinearLayout parent) {
        TextView attribution = Ui.label(context,
                "Sumber: Kaori Translation · kaoritranslation.blogspot.com\n"
                        + "Aplikasi ini hanya membuka isi publik dan tidak menyimpan salinan novel.",
                11, palette.muted);
        attribution.setTag("attribution");
        attribution.setPadding(0, Ui.dp(context, 18), 0, Ui.dp(context, 10));
        parent.addView(attribution);

        Button openSource = Ui.chip(context, "Kunjungi sumber asli", palette);
        openSource.setTag("source");
        openSource.setOnClickListener(v -> listener.onOpenSource());
        parent.addView(openSource);
    }
}
