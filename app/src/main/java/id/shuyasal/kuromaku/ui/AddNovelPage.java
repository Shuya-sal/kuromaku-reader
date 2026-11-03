package id.shuyasal.kuromaku.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.util.List;

import id.shuyasal.kuromaku.data.Volume;
import id.shuyasal.kuromaku.parser.NovelDiscovery;

/**
 * The "Tambah Novel" page: paste a catalogue URL, read it, check the preview,
 * then save.
 *
 * <p>Nothing is stored until the user presses "Simpan ke Perpustakaan". The page
 * shows four honest states instead of guessing:
 *
 * <ul>
 *   <li><b>input</b> — the URL field plus an explanation of which pages work;</li>
 *   <li><b>loading</b> — a spinner and the URL being read;</li>
 *   <li><b>error</b> — the reason in plain words plus "Coba Lagi";</li>
 *   <li><b>preview</b> — cover, title, the metadata the page actually printed,
 *       the synopsis, how many volumes and chapters were found, and a warning for
 *       every link that could not be mapped.</li>
 * </ul>
 *
 * <p>Metadata the source does not print is shown as "Tidak tercantum di halaman
 * sumber" rather than being filled in from the title or the URL: an invented
 * author is worse than an empty field.
 */
public final class AddNovelPage {

    /** Page actions; the activity owns the fetching and the saving. */
    public interface Listener {
        /** User pressed "Ambil Katalog" with this text. */
        void onFetch(String url);

        /** User pressed "Coba Lagi" after a failure. */
        void onRetry();

        /** User pressed "Simpan ke Perpustakaan" on a preview. */
        void onSave();

        /** User pressed "Batal" on a preview. */
        default void onCancelPreview() {
        }

        /** User confirmed a hand-picked set of chapter links. */
        default void onApplySelection(java.util.List<String> urls) {
        }

        /** User asked to see every link the parser found. */
        default void onTogglePicking() {
        }
    }

    /** Everything the page draws. Mutable holder owned by the activity. */
    public static final class State {
        public String url = "";
        public boolean loading;
        public String error = "";
        /** Set only when a catalogue was read successfully. */
        public NovelDiscovery.Result preview;
        /** True when this exact novel is already on the shelf. */
        public boolean duplicate;
        /** True while the full link list is open for hand-picking. */
        public boolean picking;
        /** URLs the user ticked; seeded with the ones the parser accepted. */
        public final java.util.LinkedHashSet<String> selected = new java.util.LinkedHashSet<>();
    }

    private static final String MISSING = "Tidak tercantum di halaman sumber";

    private final Context context;
    private final Palette palette;
    private final State state;
    private final Listener listener;
    private EditText field;

    public AddNovelPage(Context context, Palette palette, State state, Listener listener) {
        this.context = context;
        this.palette = palette;
        this.state = state == null ? new State() : state;
        this.listener = listener;
    }

    /** Text the user typed, kept for the activity when the page is rebuilt. */
    public String typedUrl() {
        if (field != null) state.url = field.getText().toString();
        return state.url == null ? "" : state.url.trim();
    }

    /** The scrollable column for this page. */
    public LinearLayout build() {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setTag("add_novel_page");

        TextView heading = Ui.label(context, "Tambah Novel dari URL", 18, palette.text);
        heading.setTag("add_novel_heading");
        heading.setTypeface(null, Typeface.BOLD);
        content.addView(heading);

        TextView hint = Ui.label(context,
                "Buka halaman katalog novel di kaoritranslation.blogspot.com, salin URL-nya, "
                        + "lalu tempel di bawah. URL dengan ?m=1 juga diterima.",
                12, palette.muted);
        hint.setTag("add_novel_hint");
        hint.setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 14));
        content.addView(hint);

        content.addView(urlField());

        if (state.loading) {
            content.addView(loadingBox());
            return content;
        }
        if (state.error != null && !state.error.isEmpty()) {
            content.addView(errorBox());
            return content;
        }
        if (state.preview != null) {
            content.addView(previewBox());
            return content;
        }
        content.addView(fetchButton());
        return content;
    }

    // ------------------------------------------------------------------- input

    private View urlField() {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setTag("add_novel_form");

        TextView label = Ui.label(context, "URL katalog", 12, palette.muted);
        label.setPadding(0, 0, 0, Ui.dp(context, 6));
        box.addView(label);

        field = new EditText(context);
        field.setTag("add_novel_url");
        field.setText(state.url == null ? "" : state.url);
        field.setHint("https://kaoritranslation.blogspot.com/2025/09/judul-novel.html");
        field.setHintTextColor(palette.muted);
        field.setTextColor(palette.text);
        field.setTextSize(13);
        field.setSingleLine(false);
        field.setMaxLines(3);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        field.setMinHeight(Ui.dp(context, Ui.TOUCH_DP));
        field.setBackground(Ui.rounded(palette.background, palette.divider, 10, context));
        field.setPadding(Ui.dp(context, 12), Ui.dp(context, 10),
                Ui.dp(context, 12), Ui.dp(context, 10));
        field.setEnabled(!state.loading);
        box.addView(field, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return box;
    }

    private View fetchButton() {
        Button button = Ui.chip(context, "Ambil Katalog", palette);
        button.setTag("add_novel_fetch");
        button.setMinHeight(Ui.dp(context, Ui.TOUCH_DP));
        button.setMinimumHeight(Ui.dp(context, Ui.TOUCH_DP));
        button.setTypeface(null, Typeface.BOLD);
        button.setOnClickListener(v -> listener.onFetch(typedUrl()));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, Ui.dp(context, 12), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private View loadingBox() {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setTag("add_novel_loading");
        box.setPadding(0, Ui.dp(context, 28), 0, Ui.dp(context, 12));

        ProgressBar spinner = new ProgressBar(context);
        spinner.setIndeterminate(true);
        box.addView(spinner);

        TextView label = Ui.label(context, "Membaca halaman katalog…", 13, palette.text);
        label.setTag("add_novel_loading_label");
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, Ui.dp(context, 10), 0, 0);
        box.addView(label);

        TextView url = Ui.label(context, state.url == null ? "" : state.url, 11, palette.muted);
        url.setGravity(Gravity.CENTER);
        url.setPadding(Ui.dp(context, 10), Ui.dp(context, 4), Ui.dp(context, 10), 0);
        box.addView(url);
        return box;
    }

    /**
     * Failure state: what went wrong, in the words the user needs, plus a retry.
     * The URL field stays on screen so the text can be corrected without retyping.
     */
    private View errorBox() {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setTag("add_novel_error");
        box.setBackground(Ui.rounded(palette.surface, palette.divider, 12, context));
        box.setPadding(Ui.dp(context, 14), Ui.dp(context, 14),
                Ui.dp(context, 14), Ui.dp(context, 14));

        TextView title = Ui.label(context, "Katalog gagal diambil", 14, palette.text);
        title.setTag("add_novel_error_title");
        title.setTypeface(null, Typeface.BOLD);
        box.addView(title);

        TextView reason = Ui.label(context, state.error, 12, palette.muted);
        reason.setTag("add_novel_error_reason");
        reason.setPadding(0, Ui.dp(context, 6), 0, Ui.dp(context, 10));
        box.addView(reason);

        Button retry = Ui.chip(context, "Coba Lagi", palette);
        retry.setTag("add_novel_retry");
        retry.setMinHeight(Ui.dp(context, Ui.TOUCH_DP));
        retry.setOnClickListener(v -> listener.onRetry());
        box.addView(retry);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, Ui.dp(context, 12), 0, 0);
        box.setLayoutParams(params);
        return box;
    }

    // ----------------------------------------------------------------- preview

    /** What was actually read, before anything is stored. */
    private View previewBox() {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setTag("add_novel_preview");
        box.setBackground(Ui.rounded(palette.surface, palette.divider, 12, context));
        box.setPadding(Ui.dp(context, 14), Ui.dp(context, 14),
                Ui.dp(context, 14), Ui.dp(context, 14));

        NovelDiscovery.Result preview = state.preview;

        LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.addView(cover(preview));
        top.addView(texts(preview), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        box.addView(top);

        box.addView(counts(preview));
        box.addView(diagnostics(preview));
        if (state.picking || preview.needsConfirmation) box.addView(picker(preview));
        box.addView(metadata(preview));
        box.addView(synopsis(preview));
        box.addView(warnings(preview));

        if (state.duplicate) {
            TextView already = Ui.label(context,
                    "Novel ini sudah ada di perpustakaan Tuan. Menyimpan lagi tidak akan "
                            + "membuat duplikat.", 12, palette.accent);
            already.setTag("add_novel_duplicate");
            already.setPadding(0, Ui.dp(context, 10), 0, Ui.dp(context, 8));
            box.addView(already);
        }

        box.addView(actions());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, Ui.dp(context, 4), 0, 0);
        box.setLayoutParams(params);
        return box;
    }

    private View cover(NovelDiscovery.Result preview) {
        float ratio = preview.coverRatio > 0 ? preview.coverRatio : 0.70f;
        int width = Ui.dp(context, 92);
        int height = Math.max(1, Math.round(width / ratio));

        RatioFrameLayout holder = new RatioFrameLayout(context);
        holder.setTag("add_novel_cover");
        holder.setRatio(ratio);
        holder.setBackground(Ui.rounded(palette.background, palette.divider, 8, context));

        TextView placeholder = Ui.label(context,
                preview.coverUrl.isEmpty() ? "Tanpa cover" : "Sampul", 9, palette.muted);
        placeholder.setGravity(Gravity.CENTER);
        holder.addView(placeholder, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (!preview.coverUrl.isEmpty()) {
            ImageView image = new ImageView(context);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            holder.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            try {
                Glide.with(context).load(preview.coverUrl).centerCrop().into(image);
            } catch (Exception ignored) {
                // The placeholder stays visible.
            }
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(0, 0, Ui.dp(context, 12), 0);
        holder.setLayoutParams(params);
        return holder;
    }

    private View texts(NovelDiscovery.Result preview) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);

        TextView title = Ui.label(context, preview.title, 16, palette.text);
        title.setTag("add_novel_title");
        title.setTypeface(null, Typeface.BOLD);
        column.addView(title);

        if (!preview.titleJp.isEmpty()) {
            TextView jp = Ui.label(context, preview.titleJp, 11, palette.muted);
            jp.setTag("add_novel_title_jp");
            jp.setMaxLines(3);
            jp.setPadding(0, Ui.dp(context, 3), 0, 0);
            column.addView(jp);
        }

        TextView source = Ui.label(context, sourceLabel(preview), 10, palette.muted);
        source.setTag("add_novel_source");
        source.setPadding(0, Ui.dp(context, 6), 0, 0);
        column.addView(source);
        return column;
    }

    private String sourceLabel(NovelDiscovery.Result preview) {
        String url = preview.sourceUrl == null ? "" : preview.sourceUrl;
        url = url.replace("https://", "").replace("http://", "");
        return url.length() > 56 ? url.substring(0, 55) + "…" : url;
    }

    /** "3 volume berisi chapter · 42 chapter" — the numbers actually found. */
    private View counts(NovelDiscovery.Result preview) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Ui.dp(context, 12), 0, Ui.dp(context, 4));

        TextView volumes = Ui.label(context,
                preview.filledVolumes() + " volume", 12, palette.text);
        volumes.setTag("add_novel_volume_count");
        volumes.setTypeface(null, Typeface.BOLD);
        row.addView(volumes);

        TextView chapters = Ui.label(context,
                " · " + preview.chapterCount() + " chapter ditemukan", 12, palette.muted);
        chapters.setTag("add_novel_chapter_count");
        row.addView(chapters);
        return row;
    }

    /** Only the fields the page printed; missing ones say so explicitly. */
    private View metadata(NovelDiscovery.Result preview) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setTag("add_novel_metadata");
        column.setPadding(0, Ui.dp(context, 8), 0, Ui.dp(context, 6));

        column.addView(row("Penulis", preview.author));
        column.addView(row("Ilustrator", preview.illustrator));
        column.addView(row("Genre", preview.genres));
        column.addView(row("Status", preview.status));
        return column;
    }

    private View row(String name, String value) {
        boolean missing = value == null || value.trim().isEmpty();
        TextView line = Ui.label(context,
                name + ": " + (missing ? MISSING : value), 11, missing ? palette.muted : palette.text);
        line.setTag("add_novel_meta_" + name.toLowerCase());
        line.setPadding(0, Ui.dp(context, 2), 0, Ui.dp(context, 2));
        return line;
    }

    private View synopsis(NovelDiscovery.Result preview) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setTag("add_novel_synopsis_box");
        column.setPadding(0, Ui.dp(context, 6), 0, 0);

        TextView heading = Ui.label(context, "Sinopsis", 12, palette.text);
        heading.setTypeface(null, Typeface.BOLD);
        column.addView(heading);

        if (preview.synopsis.length == 0) {
            TextView missing = Ui.label(context, MISSING, 11, palette.muted);
            missing.setTag("add_novel_synopsis");
            missing.setPadding(0, Ui.dp(context, 4), 0, 0);
            column.addView(missing);
            return column;
        }
        for (String paragraph : preview.synopsis) {
            TextView line = Ui.label(context, paragraph, 11, palette.muted);
            line.setTag("add_novel_synopsis");
            line.setPadding(0, Ui.dp(context, 4), 0, 0);
            column.addView(line);
        }
        return column;
    }

    /** Every unmapped link and every assumption, stated rather than hidden. */
    private View warnings(NovelDiscovery.Result preview) {
        List<String> warnings = preview.warnings;
        if (warnings.isEmpty()) return new LinearLayout(context);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setTag("add_novel_warnings");
        column.setPadding(0, Ui.dp(context, 10), 0, 0);

        TextView heading = Ui.label(context, "Perlu diketahui", 11, palette.accent);
        heading.setTypeface(null, Typeface.BOLD);
        column.addView(heading);

        for (String warning : warnings) {
            TextView line = Ui.label(context, "• " + warning, 11, palette.muted);
            line.setTag("add_novel_warning");
            line.setPadding(0, Ui.dp(context, 3), 0, 0);
            column.addView(line);
        }

        List<String> missing = preview.missingMetadata();
        if (!missing.isEmpty()) {
            TextView line = Ui.label(context,
                    "• Tidak tertulis di halaman: " + android.text.TextUtils.join(", ", missing),
                    11, palette.muted);
            line.setTag("add_novel_warning");
            line.setPadding(0, Ui.dp(context, 3), 0, 0);
            column.addView(line);
        }
        return column;
    }

    /**
     * What the parser saw, in numbers.
     *
     * <p>Three lines, because "no chapters found" alone tells the user nothing:
     * how many links the article held, how many were recognised, and why the rest
     * were dropped. The rejected list is capped and says so.
     */
    private View diagnostics(NovelDiscovery.Result preview) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setTag("add_novel_diagnostics");
        column.setPadding(0, Ui.dp(context, 10), 0, 0);

        TextView heading = Ui.label(context, "Diagnostik tautan", 11, palette.text);
        heading.setTypeface(null, Typeface.BOLD);
        column.addView(heading);

        // Where the links were found depends on the address: an article carries them
        // inside the post body, a label carries them in its feed. Saying "di artikel"
        // for a label address describes a page the app never read.
        boolean label = id.shuyasal.kuromaku.net.NovelUrl.isLabel(preview.sourceUrl);
        TextView found = Ui.label(context,
                "Tautan ditemukan " + (label ? "di feed label: " : "di artikel: ")
                        + preview.linksFound, 11, palette.muted);
        found.setTag("add_novel_links_found");
        found.setPadding(0, Ui.dp(context, 3), 0, 0);
        column.addView(found);

        TextView accepted = Ui.label(context,
                "Dikenali sebagai chapter: " + preview.linksAccepted, 11,
                preview.linksAccepted > 0 ? palette.text : palette.accent);
        accepted.setTag("add_novel_links_accepted");
        accepted.setPadding(0, Ui.dp(context, 2), 0, 0);
        column.addView(accepted);

        java.util.List<NovelDiscovery.Candidate> rejected = preview.rejected();
        if (!rejected.isEmpty()) {
            TextView why = Ui.label(context,
                    "Ditolak: " + rejected.size() + " tautan", 11, palette.muted);
            why.setTag("add_novel_links_rejected");
            why.setPadding(0, Ui.dp(context, 2), 0, 0);
            column.addView(why);

            int shown = 0;
            for (NovelDiscovery.Candidate candidate : rejected) {
                if (shown++ >= 6) break;
                String text = candidate.text.isEmpty() ? "(tanpa teks)" : candidate.text;
                TextView line = Ui.label(context,
                        "• " + trim(text, 40) + " — " + candidate.reason, 10, palette.muted);
                line.setTag("add_novel_rejection");
                line.setPadding(Ui.dp(context, 6), Ui.dp(context, 2), 0, 0);
                column.addView(line);
            }
            if (rejected.size() > shown) {
                TextView more = Ui.label(context,
                        "… dan " + (rejected.size() - shown) + " tautan lain", 10, palette.muted);
                more.setPadding(Ui.dp(context, 6), Ui.dp(context, 2), 0, 0);
                column.addView(more);
            }
        }

        if (preview.needsConfirmation) {
            TextView ask = Ui.label(context,
                    "Belum ada yang bisa dipastikan sebagai chapter. Centang tautan yang benar "
                            + "di bawah, lalu tekan “Pakai pilihan ini”.", 11, palette.accent);
            ask.setTag("add_novel_needs_confirmation");
            ask.setPadding(0, Ui.dp(context, 8), 0, 0);
            column.addView(ask);
        }

        Button toggle = Ui.chip(context,
                state.picking ? "Sembunyikan daftar tautan" : "Pilih tautan manual", palette);
        toggle.setTag("add_novel_pick_toggle");
        toggle.setMinHeight(Ui.dp(context, Ui.TOUCH_DP));
        toggle.setOnClickListener(v -> listener.onTogglePicking());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, Ui.dp(context, 8), 0, 0);
        toggle.setLayoutParams(params);
        column.addView(toggle);
        return column;
    }

    /**
     * The links the parser found, each one a checkbox.
     *
     * <p>This exists because the alternative — guessing — is worse. When no link
     * matches a known chapter pattern, the user sees the actual list and decides;
     * nothing is saved from this list until "Pakai pilihan ini" is pressed, and a
     * link that is not on the page can never be selected.
     */
    private View picker(NovelDiscovery.Result preview) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setTag("add_novel_picker");
        column.setPadding(0, Ui.dp(context, 8), 0, 0);

        java.util.List<NovelDiscovery.Candidate> selectable = preview.selectableCandidates();
        TextView heading = Ui.label(context,
                "Tautan yang bisa dipakai (" + selectable.size() + " dari "
                        + preview.candidates.size() + ")", 11, palette.text);
        heading.setTypeface(null, Typeface.BOLD);
        column.addView(heading);

        if (selectable.isEmpty()) {
            TextView none = Ui.label(context,
                    "Tidak ada tautan yang bisa dipilih di halaman ini.", 11, palette.muted);
            none.setPadding(0, Ui.dp(context, 4), 0, 0);
            column.addView(none);
            return column;
        }

        int shown = 0;
        for (NovelDiscovery.Candidate candidate : selectable) {
            if (shown++ >= 80) break;
            android.widget.CheckBox box = new android.widget.CheckBox(context);
            box.setTag("add_novel_candidate");
            String text = candidate.text.isEmpty()
                    ? trim(tail(candidate.url), 46) : trim(candidate.text, 46);
            box.setText(text + (candidate.volume > 0 ? " · Vol " + candidate.volume : ""));
            box.setTextSize(11);
            box.setTextColor(palette.text);
            box.setChecked(state.selected.contains(candidate.url));
            box.setMinHeight(Ui.dp(context, Ui.TOUCH_DP));
            final String url = candidate.url;
            box.setOnCheckedChangeListener((button, checked) -> {
                if (checked) state.selected.add(url);
                else state.selected.remove(url);
            });
            column.addView(box);
        }

        Button apply = Ui.chip(context, "Pakai pilihan ini", palette);
        apply.setTag("add_novel_apply_selection");
        apply.setMinHeight(Ui.dp(context, Ui.TOUCH_DP));
        apply.setTypeface(null, Typeface.BOLD);
        apply.setOnClickListener(v -> listener.onApplySelection(
                new java.util.ArrayList<>(state.selected)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, Ui.dp(context, 8), 0, 0);
        apply.setLayoutParams(params);
        column.addView(apply);

        TextView note = Ui.label(context,
                "Tautan navigasi, iklan, dan postingan novel lain tidak ditampilkan di sini. "
                        + "Hanya tautan yang tercentang yang dipakai, dan novel tidak disimpan "
                        + "sebelum Tuan menekan “Simpan ke Perpustakaan”.", 10, palette.muted);
        note.setPadding(0, Ui.dp(context, 6), 0, 0);
        column.addView(note);
        return column;
    }

    private static String trim(String text, int limit) {
        if (text == null) return "";
        String value = text.trim();
        return value.length() > limit ? value.substring(0, limit - 1) + "…" : value;
    }

    private static String tail(String url) {
        if (url == null) return "";
        String path = url;
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private View actions() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Ui.dp(context, 14), 0, 0);

        Button cancel = Ui.chip(context, "Batal", palette);
        cancel.setTag("add_novel_cancel");
        cancel.setMinHeight(Ui.dp(context, Ui.TOUCH_DP));
        cancel.setOnClickListener(v -> listener.onCancelPreview());
        row.addView(cancel);

        Button save = Ui.chip(context, "Simpan ke Perpustakaan", palette);
        save.setTag("add_novel_save");
        save.setMinHeight(Ui.dp(context, Ui.TOUCH_DP));
        save.setTypeface(null, Typeface.BOLD);
        // Nothing is saved without a verified chapter list: an empty novel on the
        // shelf is worse than a refusal, so the only button that writes is disabled
        // while the catalogue is empty or the picker has not been applied.
        boolean blocked = state.duplicate
                || state.preview == null || state.preview.chapterCount() == 0;
        save.setEnabled(!blocked);
        save.setAlpha(blocked ? 0.5f : 1f);
        save.setOnClickListener(v -> listener.onSave());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(Ui.dp(context, 8), 0, 0, 0);
        save.setLayoutParams(params);
        row.addView(save);
        return row;
    }
}
