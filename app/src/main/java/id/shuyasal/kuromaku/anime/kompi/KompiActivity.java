package id.shuyasal.kuromaku.anime.kompi;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.ThemeManager;
import id.shuyasal.kuromaku.ui.TvFocus;
import id.shuyasal.kuromaku.ui.TvMode;
import id.shuyasal.kuromaku.ui.Ui;

/**
 * Beranda Animekompi: daftar per bagian dengan pencarian.
 *
 * <p>Data datang dari API JSON sumbernya, jadi tidak ada halaman HTML yang
 * diurai. Bila sumber tidak bisa dihubungi, layar menampilkan alasannya dan
 * tombol coba lagi — tidak pernah daftar kosong yang tampak seperti hasil
 * pencarian sah.
 */
public class KompiActivity extends id.shuyasal.kuromaku.ui.TvActivity {

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private Palette palette;
    private KompiSource source;
    private LinearLayout content;
    private ProgressBar spinner;
    private TextView errorText;
    private Button retry;
    private EditText search;
    private String pendingQuery = "";

    /** Sumber dibuat ulang per layar supaya pengujian bisa menggantinya. */
    protected KompiSource createSource() {
        return new KompiSource();
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ThemeManager.applyTheme(this);
        palette = Palette.of(new Prefs(this).resolvedTheme());
        source = createSource();
        buildUi();
        load("");
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    // ------------------------------------------------------------------- UI

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), 0);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        Button back = Ui.backButton(this, palette);
        back.setOnClickListener(view -> finish());
        header.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, Ui.TOUCH_DP)));

        TextView title = Ui.label(this, "Anime Kompi", 20, palette.text);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMargins(Ui.dp(this, 12), 0, 0, 0);
        header.addView(title, titleParams);
        root.addView(header);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams searchRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchRowParams.topMargin = Ui.dp(this, 12);

        search = new EditText(this);
        search.setHint("Cari anime");
        search.setSingleLine(true);
        search.setTextColor(palette.text);
        search.setHintTextColor(palette.muted);
        search.setTextSize(15);
        search.setBackground(Ui.ripple(this, palette.surface, palette.divider, 12, palette.ripple));
        search.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        searchRow.addView(search, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button cari = Ui.chip(this, "Cari", palette);
        cari.setOnClickListener(view -> runSearch());
        LinearLayout.LayoutParams cariParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, Ui.TOUCH_DP));
        cariParams.setMargins(Ui.dp(this, 10), 0, 0, 0);
        searchRow.addView(cari, cariParams);
        root.addView(searchRow, searchRowParams);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable text) {
                String value = text.toString().trim();
                if (value.isEmpty() && !pendingQuery.isEmpty()) {
                    pendingQuery = "";
                    load("");
                }
            }
        });
        search.setOnEditorActionListener((view, action, event) -> {
            runSearch();
            return true;
        });

        spinner = new ProgressBar(this);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        spinnerParams.gravity = Gravity.CENTER_HORIZONTAL;
        spinnerParams.topMargin = Ui.dp(this, 24);
        root.addView(spinner, spinnerParams);

        errorText = Ui.label(this, "", 14, palette.muted);
        errorText.setVisibility(View.GONE);
        errorText.setPadding(Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 4), 0);
        root.addView(errorText);

        retry = Ui.chip(this, "Coba lagi", palette);
        retry.setVisibility(View.GONE);
        retry.setOnClickListener(view -> load(pendingQuery));
        root.addView(retry, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, Ui.TOUCH_DP)));

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 28));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private void runSearch() {
        String query = search == null ? "" : search.getText().toString().trim();
        if (query.length() < 2) {
            pendingQuery = "";
            load("");
            return;
        }
        pendingQuery = query;
        load(query);
    }

    // ----------------------------------------------------------------- data

    private void load(String query) {
        spinner.setVisibility(View.VISIBLE);
        errorText.setVisibility(View.GONE);
        retry.setVisibility(View.GONE);
        if (content.getChildCount() == 0) content.removeAllViews();

        worker.execute(() -> {
            try {
                if (query.isEmpty()) {
                    List<KompiSource.Section> sections = source.home();
                    main.post(() -> showSections(sections));
                } else {
                    List<KompiSource.Item> items = source.search(query);
                    main.post(() -> showSearch(query, items));
                }
            } catch (KompiSource.SourceException error) {
                String message = error.getMessage() == null
                        ? "Sumber tidak dapat dihubungi." : error.getMessage();
                main.post(() -> showError(message));
            }
        });
    }

    private void showSections(List<KompiSource.Section> sections) {
        if (isFinishing()) return;
        spinner.setVisibility(View.GONE);
        content.removeAllViews();
        for (KompiSource.Section section : sections) {
            content.addView(sectionTitle(section.name));
            content.addView(rail(section.items));
        }
        TvFocus.prepare(content, palette);
        if (TvMode.isTelevision(this)) {
            View first = TvFocus.firstFocusable(content);
            if (first != null) first.requestFocus();
        }
    }

    private void showSearch(String query, List<KompiSource.Item> items) {
        if (isFinishing()) return;
        spinner.setVisibility(View.GONE);
        content.removeAllViews();
        if (items.isEmpty()) {
            errorText.setText("Tidak ada anime yang cocok dengan \"" + query + "\".");
            errorText.setVisibility(View.VISIBLE);
            return;
        }
        content.addView(sectionTitle("Hasil pencarian: " + query));
        content.addView(grid(items));
        TvFocus.prepare(content, palette);
        if (TvMode.isTelevision(this)) {
            View first = TvFocus.firstFocusable(content);
            if (first != null) first.requestFocus();
        }
    }

    private void showError(String message) {
        if (isFinishing()) return;
        spinner.setVisibility(View.GONE);
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
        retry.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------------------- tampilan

    private TextView sectionTitle(String text) {
        TextView view = Ui.label(this, text, 17, palette.text);
        view.setTypeface(null, Typeface.BOLD);
        view.setPadding(Ui.dp(this, 4), Ui.dp(this, 18), Ui.dp(this, 4), Ui.dp(this, 10));
        return view;
    }

    /** Satu baris mendatar — nyaman digulir dengan remote maupun sentuhan. */
    private View rail(List<KompiSource.Item> items) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        int width = Ui.dp(this, TvMode.isTelevision(this) ? 150 : 128);
        for (KompiSource.Item item : items) {
            row.addView(card(item, width));
        }
        scroll.addView(row);
        return scroll;
    }

    /** Hasil pencarian memakai kisi, bukan baris mendatar. */
    private View grid(List<KompiSource.Item> items) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int kolom = TvMode.isTelevision(this) ? 5 : 3;
        int lebar = Ui.dp(this, 118);
        LinearLayout baris = null;
        for (int i = 0; i < items.size(); i++) {
            if (i % kolom == 0) {
                baris = new LinearLayout(this);
                baris.setOrientation(LinearLayout.HORIZONTAL);
                box.addView(baris);
            }
            baris.addView(card(items.get(i), lebar));
        }
        return box;
    }

    private View card(KompiSource.Item item, int width) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 10));
        card.setFocusable(true);
        card.setClickable(true);
        card.setContentDescription(item.title);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(Ui.ripple(this, palette.surface, 0, 12, palette.ripple));
        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frame.addView(poster, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        card.addView(frame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 178)));
        if (!item.cover.isEmpty()) {
            Glide.with(this).load(item.cover).centerCrop().into(poster);
        }

        TextView title = Ui.label(this, item.title, 13, palette.text);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(Ui.dp(this, 2), Ui.dp(this, 6), Ui.dp(this, 2), 0);
        card.addView(title);

        if (!item.note.isEmpty()) {
            TextView note = Ui.label(this, item.note, 11, palette.muted);
            note.setMaxLines(1);
            note.setPadding(Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2), 0);
            card.addView(note);
        }

        card.setOnClickListener(view -> open(item));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                width, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, Ui.dp(this, 8), 0);
        card.setLayoutParams(params);
        TvFocus.focusable(card, palette);
        return card;
    }

    private void open(KompiSource.Item item) {
        Intent intent = new Intent(this, KompiDetailActivity.class);
        intent.putExtra(KompiDetailActivity.EXTRA_SLUG, item.slug);
        intent.putExtra(KompiDetailActivity.EXTRA_TITLE, item.title);
        intent.putExtra(KompiDetailActivity.EXTRA_COVER, item.cover);
        startActivity(intent);
    }
}
