package id.shuyasal.kuromaku.sync;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import id.shuyasal.kuromaku.R;
import id.shuyasal.kuromaku.komiku.KomikuManga;
import id.shuyasal.kuromaku.komiku.KomikuSource;
import id.shuyasal.kuromaku.komiku.KomikuStore;
import id.shuyasal.kuromaku.shinigami.ShinigamiManga;
import id.shuyasal.kuromaku.shinigami.ShinigamiSource;
import id.shuyasal.kuromaku.shinigami.ShinigamiStore;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Ui;

/**
 * Bar kecil di layar detail untuk menautkan judul dengan sumber lain.
 *
 * <p>Menampilkan keadaan sebenarnya: bila belum ada pasangan, tertulis
 * "Hubungkan…" — bukan label "tersinkron" palsu. Bila sudah, tertulis sumber
 * pasangannya beserta tombol ubah/lepas.
 */
public final class SyncBar {

    public static final String SOURCE_SHINIGAMI = SyncStore.SOURCE_SHINIGAMI;
    public static final String SOURCE_KOMIKU = SyncStore.SOURCE_KOMIKU;

    /** Data judul pada satu sumber, dipakai untuk mencari pasangan. */
    public static final class Self {
        public final String sourceId;
        public final String mangaId;
        public final String title;
        public final String alternative;
        public final String url;

        public Self(String sourceId, String mangaId, String title, String alternative,
                    String url) {
            this.sourceId = sourceId;
            this.mangaId = mangaId == null ? "" : mangaId;
            this.title = title == null ? "" : title;
            this.alternative = alternative == null ? "" : alternative;
            this.url = url == null ? "" : url;
        }
    }

    private final Context context;
    private final Palette palette;
    private final Self self;
    private final SyncBridge bridge;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Runnable onChanged;

    public SyncBar(Context context, Palette palette, Self self) {
        this.context = context;
        this.palette = palette;
        this.self = self;
        this.bridge = new SyncBridge(context);
    }

    public SyncBar onChanged(Runnable callback) {
        this.onChanged = callback;
        return this;
    }

    /** Bangun view; panggil {@link #refresh(View)} setelah data berubah. */
    public View build() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(Ui.dp(context, 12), Ui.dp(context, 8), Ui.dp(context, 12), Ui.dp(context, 8));
        row.setBackground(Ui.rounded(palette.surface, palette.divider, 12, context));
        row.setTag("sync_bar");

        TextView text = Ui.label(context, "", 12, palette.muted);
        text.setTag("sync_bar_text");
        row.addView(text);

        Button action = Ui.chip(context, "", palette);
        action.setTag("sync_bar_action");
        action.setOnClickListener(v -> {
            if (bridge.stateFor(self.sourceId, self.mangaId).linked) {
                showManageDialog();
            } else {
                showPickerDialog();
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(context, 6);
        row.addView(action, params);

        refresh(row);
        return row;
    }

    /** Perbarui teks/tombol sesuai keadaan sekarang. */
    public void refresh(View row) {
        if (row == null) return;
        TextView text = row.findViewWithTag("sync_bar_text");
        Button action = row.findViewWithTag("sync_bar_action");
        SyncBridge.LinkState state = bridge.stateFor(self.sourceId, self.mangaId);

        if (state.linked) {
            String other = state.otherSourceLabel();
            String detail = state.otherTitle == null || state.otherTitle.isEmpty()
                    ? "" : " · " + state.otherTitle;
            text.setText("Tersinkron dengan " + other + detail);
            action.setText("Ubah pasangan");
        } else {
            text.setText("Belum dipasangkan dengan sumber lain. "
                    + "Progres hanya tersimpan di " + sourceLabel(self.sourceId) + ".");
            action.setText("Cari pasangan di " + otherLabel(self.sourceId));
        }
    }

    private static String sourceLabel(String sourceId) {
        if (SyncStore.SOURCE_KOMIKU.equals(sourceId)) return "Manga Server 3";
        if (SyncStore.SOURCE_SHINIGAMI.equals(sourceId)) return "Manga Server 2";
        return sourceId;
    }

    private static String otherLabel(String sourceId) {
        return SyncStore.SOURCE_KOMIKU.equals(sourceId) ? "Manga Server 2" : "Manga Server 3";
    }

    private static String otherSource(String sourceId) {
        return SyncStore.SOURCE_KOMIKU.equals(sourceId)
                ? SyncStore.SOURCE_SHINIGAMI : SyncStore.SOURCE_KOMIKU;
    }

    // ------------------------------------------------------------- dialog

    private void showPickerDialog() {
        final String otherSource = otherSource(self.sourceId);
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(context, 16), Ui.dp(context, 12), Ui.dp(context, 16), Ui.dp(context, 4));

        TextView hint = Ui.label(context, "Saran dari riwayat dan favorit "
                + otherLabel(self.sourceId) + ".", 12, palette.muted);
        box.addView(hint);

        EditText search = new EditText(context);
        search.setHint("Cari judul di " + otherLabel(self.sourceId));
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setTextColor(palette.text);
        search.setHintTextColor(palette.muted);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchParams.topMargin = Ui.dp(context, 8);
        box.addView(search, searchParams);

        ProgressBar spinner = new ProgressBar(context);
        spinner.setVisibility(View.GONE);
        box.addView(spinner);

        LinearLayout results = new LinearLayout(context);
        results.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(results);
        box.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 220)));

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Hubungkan dengan " + otherLabel(self.sourceId))
                .setView(box)
                .setNegativeButton("Batal", null)
                .create();

        renderLocalSuggestions(results, otherSource, dialog);

        search.setOnEditorActionListener((v, action, event) -> {
            String query = search.getText().toString().trim();
            if (query.isEmpty()) return true;
            spinner.setVisibility(View.VISIBLE);
            results.removeAllViews();
            worker.execute(() -> {
                List<MangaMatcher.Candidate> found = new ArrayList<>();
                String error = null;
                try {
                    found = searchOther(otherSource, query);
                } catch (Throwable failure) {
                    error = failure.getMessage();
                }
                final List<MangaMatcher.Candidate> candidates = found;
                final String message = error;
                results.post(() -> {
                    spinner.setVisibility(View.GONE);
                    if (message != null) {
                        results.addView(Ui.label(context, "Pencarian gagal: " + message,
                                12, palette.muted));
                        return;
                    }
                    if (candidates.isEmpty()) {
                        results.addView(Ui.label(context,
                                "Tidak ada judul yang cocok di " + otherLabel(self.sourceId) + ".",
                                12, palette.muted));
                        return;
                    }
                    for (MangaMatcher.Candidate candidate : candidates) {
                        results.addView(candidateRow(candidate, otherSource, dialog, "cari"));
                    }
                });
            });
            return true;
        });

        dialog.show();
    }

    private void renderLocalSuggestions(LinearLayout results, String otherSource,
                                        AlertDialog dialog) {
        List<MangaMatcher.Candidate> pool = localCandidates(otherSource);
        List<MangaMatcher.Suggestion> suggestions =
                MangaMatcher.suggest(self.title, self.alternative, pool);
        if (suggestions.isEmpty()) {
            results.addView(Ui.label(context, "Belum ada judul di riwayat/favorit "
                    + otherLabel(self.sourceId) + " yang mirip. Gunakan kolom pencarian di atas.",
                    12, palette.muted));
            return;
        }
        for (MangaMatcher.Suggestion suggestion : suggestions) {
            results.addView(suggestionRow(suggestion, otherSource, dialog));
        }
    }

    /** Calon dari riwayat + favorit sumber lain (tanpa jaringan). */
    private List<MangaMatcher.Candidate> localCandidates(String otherSource) {
        List<MangaMatcher.Candidate> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (SyncStore.SOURCE_KOMIKU.equals(otherSource)) {
            KomikuStore store = new KomikuStore(context);
            for (KomikuStore.Entry entry : store.history()) {
                if (entry.mangaId.isEmpty() || !seen.add(entry.mangaId)) continue;
                out.add(new MangaMatcher.Candidate(entry.mangaId, entry.title, ""));
            }
            for (KomikuManga manga : store.favorites()) {
                if (manga.id.isEmpty() || !seen.add(manga.id)) continue;
                out.add(new MangaMatcher.Candidate(manga.id, manga.title, ""));
            }
        } else {
            ShinigamiStore store = new ShinigamiStore(context);
            for (ShinigamiStore.HistoryEntry entry : store.history()) {
                if (entry.mangaId.isEmpty() || !seen.add(entry.mangaId)) continue;
                out.add(new MangaMatcher.Candidate(entry.mangaId, entry.title, ""));
            }
            for (String mangaId : store.favorites()) {
                if (!seen.add(mangaId)) continue;
                out.add(new MangaMatcher.Candidate(mangaId, store.mangaTitle(mangaId), ""));
            }
        }
        return out;
    }

    private List<MangaMatcher.Candidate> searchOther(String otherSource, String query)
            throws Exception {
        List<MangaMatcher.Candidate> out = new ArrayList<>();
        if (SyncStore.SOURCE_KOMIKU.equals(otherSource)) {
            for (KomikuManga manga : new KomikuSource().search(query)) {
                out.add(new MangaMatcher.Candidate(manga.id, manga.title, ""));
            }
        } else {
            ShinigamiSource.Page page = new ShinigamiSource(context).search(query, 1);
            for (ShinigamiManga manga : page.items) {
                out.add(new MangaMatcher.Candidate(manga.id, manga.title,
                        manga.alternativeTitle));
            }
        }
        return out;
    }

    private View suggestionRow(MangaMatcher.Suggestion suggestion, String otherSource,
                               AlertDialog dialog) {
        return row(suggestion.candidateTitle, describe(suggestion.confidence, suggestion.reason),
                () -> confirmLink(suggestion.candidateTitle, suggestion.candidateId, otherSource,
                        dialog, suggestion.confidence >= 0.85 ? "auto" : "manual"));
    }

    private View candidateRow(MangaMatcher.Candidate candidate, String otherSource,
                              AlertDialog dialog, String method) {
        return row(candidate.title, candidate.alternative.isEmpty()
                        ? "hasil pencarian" : "alternatif: " + candidate.alternative,
                () -> confirmLink(candidate.title, candidate.id, otherSource, dialog, method));
    }

    private String describe(double confidence, String reason) {
        String level = confidence >= 0.85 ? "sangat mirip"
                : confidence >= 0.55 ? "mungkin sama" : "kemiripan lemah";
        return level + " · " + reason;
    }

    private View row(String title, String subtitle, Runnable action) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(Ui.dp(context, 10), Ui.dp(context, 10), Ui.dp(context, 10), Ui.dp(context, 10));
        row.setBackground(Ui.ripple(context, palette.background, palette.divider, 10,
                palette.ripple));
        TextView name = Ui.label(context, title, 14, palette.text);
        row.addView(name);
        TextView meta = Ui.label(context, subtitle, 11, palette.muted);
        row.addView(meta);
        row.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.dp(context, 4);
        row.setLayoutParams(params);
        return row;
    }

    private void confirmLink(String otherTitle, String otherMangaId, String otherSource,
                             AlertDialog dialog, String method) {
        if (otherMangaId == null || otherMangaId.isEmpty()) return;
        // Judul dari sumber lain disimpan bersama alamat detailnya agar
        // pengguna bisa melanjutkan ke sana tanpa menebak alamat.
        String otherUrl = SyncStore.SOURCE_KOMIKU.equals(otherSource)
                ? "https://komiku.org/manga/" + otherMangaId + "/" : "";
        bridge.link(self.sourceId, self.mangaId, self.title, self.url,
                otherSource, otherMangaId, otherTitle, otherUrl, method);
        dialog.dismiss();
        Toast.makeText(context, "Dipasangkan dengan " + otherTitle, Toast.LENGTH_SHORT).show();
        if (onChanged != null) onChanged.run();
    }

    private void showManageDialog() {
        SyncBridge.LinkState state = bridge.stateFor(self.sourceId, self.mangaId);
        new AlertDialog.Builder(context)
                .setTitle("Pasangan " + sourceLabel(self.sourceId))
                .setMessage("Tersinkron dengan " + state.otherSourceLabel()
                        + (state.otherTitle.isEmpty() ? "" : " · " + state.otherTitle)
                        + "\n\nMelepas tautan tidak menghapus riwayat maupun progres di kedua sumber. "
                        + "Kamu bisa memasangkannya lagi kapan saja.")
                .setPositiveButton("Lepas tautan", (d, w) -> {
                    bridge.unlink(self.sourceId, self.mangaId);
                    Toast.makeText(context, "Tautan dilepas. Riwayat tetap aman.",
                            Toast.LENGTH_SHORT).show();
                    if (onChanged != null) onChanged.run();
                })
                .setNeutralButton("Ganti pasangan", (d, w) -> showPickerDialog())
                .setNegativeButton("Tutup", null)
                .show();
    }
}