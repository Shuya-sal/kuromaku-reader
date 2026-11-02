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

import id.shuyasal.kuromaku.data.Catalog;
import id.shuyasal.kuromaku.data.Chapter;
import id.shuyasal.kuromaku.data.Novel;
import id.shuyasal.kuromaku.data.Volume;

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
        row.setBackground(Ui.rounded(palette.surface, palette.divider, 10, context));
        row.setPadding(Ui.dp(context, 10), Ui.dp(context, 9),
                Ui.dp(context, 12), Ui.dp(context, 9));

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

        TextView chevron = Ui.label(context, open ? "  ▾" : "  ▸", 13, palette.accent);
        nameRow.addView(chevron);
        texts.addView(nameRow);

        String count = volume.chapterCount() == 0
                ? "belum ada chapter"
                : volume.chapterCount() + " chapter";
        texts.addView(Ui.label(context, count, 11, palette.muted));
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
        row.setPadding(Ui.dp(context, 26), Ui.dp(context, 12),
                Ui.dp(context, 4), Ui.dp(context, 12));

        TextView label = Ui.label(context, chapter.label, 14,
                finished ? palette.muted : palette.text);
        row.addView(label, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        if (finished) {
            row.addView(Ui.label(context, "selesai", 11, palette.muted));
        } else if (started) {
            row.addView(Ui.label(context, progress + "%", 11, palette.accent));
        }

        row.setOnClickListener(v -> listener.onOpenChapter(chapter));
        parent.addView(row);

        View line = new View(context);
        line.setBackgroundColor(palette.divider);
        parent.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(context, 1))));
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
