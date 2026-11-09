package id.shuyasal.kuromaku.komiku;

import android.content.Intent;
import android.graphics.Color;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;
import id.shuyasal.kuromaku.ui.ThemeManager;
import id.shuyasal.kuromaku.ui.Ui;

/**
 * Beranda Manga Server 3 (Komiku) — native, bukan WebView.
 *
 * <p>Empat daftar: Chapter Terbaru, Terpopuler, Manga Terbaru, dan Favorit.
 * Semua isi berasal dari halaman Komiku yang benar-benar dibaca saat itu;
 * tidak ada katalog contoh yang ditanam di aplikasi.
 */
public class KomikuActivity extends AppCompatActivity {

    private static final int TAB_LATEST = 0;
    private static final int TAB_POPULAR = 1;
    private static final int TAB_NEW = 2;
    private static final int TAB_FAVORITES = 3;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<KomikuManga> items = new ArrayList<>();

    private Palette palette;
    private KomikuSource source;
    private KomikuStore store;
    private RecyclerView grid;
    private GridAdapter adapter;
    private ProgressBar spinner;
    private TextView status;
    private EditText searchField;
    private LinearLayout chips;
    private int tab = TAB_LATEST;
    private int generation;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ThemeManager.applyTheme(this);
        palette = Palette.of(new Prefs(this).resolvedTheme());
        source = new KomikuSource();
        store = new KomikuStore(this);
        buildUi();
        load(tab);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 4));
        Button back = Ui.backButton(this, palette);
        back.setTag("komiku_back");
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        TextView title = Ui.label(this, "Manga Server 3", 18, palette.text);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setPadding(Ui.dp(this, 8), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(bar);

        searchField = new EditText(this);
        searchField.setHint("Cari judul di Komiku");
        searchField.setSingleLine(true);
        searchField.setTag("komiku_search");
        searchField.setTextColor(palette.text);
        searchField.setHintTextColor(palette.muted);
        searchField.setBackground(Ui.rounded(palette.surface, palette.divider, 16, this));
        searchField.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        searchField.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchField.setOnEditorActionListener((v, action, event) -> {
            runSearch();
            return true;
        });
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4));
        searchRow.addView(searchField, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button cari = Ui.chip(this, "Cari", palette);
        cari.setTag("komiku_search_button");
        cari.setOnClickListener(v -> runSearch());
        searchRow.addView(cari);
        root.addView(searchRow);

        chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 6));
        root.addView(chips);
        buildChips();

        status = Ui.label(this, "", 13, palette.muted);
        status.setTag("komiku_status");
        status.setPadding(Ui.dp(this, 14), Ui.dp(this, 2), Ui.dp(this, 14), Ui.dp(this, 6));
        root.addView(status);

        FrameLayout body = new FrameLayout(this);
        grid = new RecyclerView(this);
        grid.setTag("komiku_grid");
        grid.setBackgroundColor(palette.background);
        grid.setItemAnimator(null);
        adapter = new GridAdapter();
        grid.setLayoutManager(new GridLayoutManager(this, 2));
        grid.setAdapter(adapter);
        body.addView(grid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        spinner.setVisibility(View.GONE);
        body.addView(spinner, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }

    private void buildChips() {
        chips.removeAllViews();
        addChip("Chapter Terbaru", tab == TAB_LATEST, () -> select(TAB_LATEST));
        addChip("Terpopuler", tab == TAB_POPULAR, () -> select(TAB_POPULAR));
        addChip("Manga Terbaru", tab == TAB_NEW, () -> select(TAB_NEW));
        addChip("Favorit", tab == TAB_FAVORITES, () -> select(TAB_FAVORITES));
    }

    private void addChip(String text, boolean active, Runnable action) {
        Button chip = Ui.chip(this, text, palette);
        chip.setTag("komiku_chip_" + text);
        chip.setSelected(active);
        chip.setAlpha(active ? 1f : 0.7f);
        chip.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = Ui.dp(this, 8);
        chips.addView(chip, params);
    }

    private void select(int next) {
        if (tab == next) return;
        tab = next;
        buildChips();
        load(tab);
    }

    private void runSearch() {
        String query = searchField.getText().toString().trim();
        if (query.isEmpty()) {
            load(tab);
            return;
        }
        final int request = ++generation;
        spinner.setVisibility(View.VISIBLE);
        status.setText("Mencari \"" + query + "\"…");
        worker.execute(() -> {
            List<KomikuManga> found;
            String error = null;
            try {
                found = source.search(query);
            } catch (Throwable failure) {
                found = new ArrayList<>();
                error = failure.getMessage();
            }
            final List<KomikuManga> result = found;
            final String message = error;
            main.post(() -> {
                if (request != generation || isFinishing()) return;
                spinner.setVisibility(View.GONE);
                items.clear();
                items.addAll(result);
                adapter.notifyDataSetChanged();
                if (message != null) {
                    status.setText("Pencarian gagal: " + message);
                } else if (result.isEmpty()) {
                    status.setText("Tidak ada hasil untuk \"" + query + "\".");
                } else {
                    status.setText(result.size() + " hasil untuk \"" + query + "\"");
                }
            });
        });
    }

    private void load(int which) {
        final int request = ++generation;
        spinner.setVisibility(View.VISIBLE);
        status.setText("Memuat…");
        worker.execute(() -> {
            List<KomikuManga> result = new ArrayList<>();
            String error = null;
            try {
                if (which == TAB_POPULAR) result = source.popular();
                else if (which == TAB_NEW) result = source.newest();
                else if (which == TAB_FAVORITES) result = store.favorites();
                else result = source.latest();
            } catch (Throwable failure) {
                error = failure.getMessage();
            }
            final List<KomikuManga> loaded = result;
            final String message = error;
            main.post(() -> {
                if (request != generation || isFinishing()) return;
                spinner.setVisibility(View.GONE);
                items.clear();
                items.addAll(loaded);
                adapter.notifyDataSetChanged();
                if (message != null) {
                    status.setText("Gagal memuat: " + message);
                } else if (loaded.isEmpty()) {
                    status.setText(which == TAB_FAVORITES
                            ? "Belum ada judul favorit." : "Daftar kosong dari sumber.");
                } else {
                    status.setText(loaded.size() + " judul");
                }
            });
        });
    }

    private void open(KomikuManga manga) {
        Intent intent = new Intent(this, KomikuDetailActivity.class);
        intent.putExtra(KomikuDetailActivity.EXTRA_URL, manga.url);
        intent.putExtra(KomikuDetailActivity.EXTRA_TITLE, manga.title);
        intent.putExtra(KomikuDetailActivity.EXTRA_COVER, manga.coverUrl);
        startActivity(intent);
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private final class GridAdapter extends RecyclerView.Adapter<GridAdapter.Holder> {
        final class Holder extends RecyclerView.ViewHolder {
            final ImageView cover;
            final TextView title;
            final TextView meta;

            Holder(View item) {
                super(item);
                cover = item.findViewWithTag("komiku_cover");
                title = item.findViewWithTag("komiku_title");
                meta = item.findViewWithTag("komiku_meta");
            }
        }

        @Override public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout card = new LinearLayout(KomikuActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(Ui.dp(KomikuActivity.this, 6), Ui.dp(KomikuActivity.this, 6),
                    Ui.dp(KomikuActivity.this, 6), Ui.dp(KomikuActivity.this, 10));
            card.setBackground(Ui.ripple(KomikuActivity.this, palette.surface,
                    palette.divider, 14, palette.ripple));

            ImageView cover = new ImageView(KomikuActivity.this);
            cover.setTag("komiku_cover");
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setBackgroundColor(palette.background);
            card.addView(cover, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(KomikuActivity.this, 190)));

            TextView title = Ui.label(KomikuActivity.this, "", 13, palette.text);
            title.setTag("komiku_title");
            title.setMaxLines(2);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.setPadding(0, Ui.dp(KomikuActivity.this, 6), 0, 0);
            card.addView(title);

            TextView meta = Ui.label(KomikuActivity.this, "", 11, palette.muted);
            meta.setTag("komiku_meta");
            meta.setSingleLine(true);
            meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(meta);
            return new Holder(card);
        }

        @Override public void onBindViewHolder(Holder holder, int position) {
            KomikuManga manga = items.get(position);
            holder.title.setText(manga.title);
            holder.meta.setText(manga.subtitle());
            KomikuImages.load(manga.coverUrl, holder.cover);
            holder.itemView.setOnClickListener(v -> open(manga));
        }

        @Override public int getItemCount() {
            return items.size();
        }
    }
}