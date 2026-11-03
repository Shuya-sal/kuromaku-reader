package id.shuyasal.kuromaku.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.util.List;

import id.shuyasal.kuromaku.R;
import id.shuyasal.kuromaku.data.Catalog;
import id.shuyasal.kuromaku.data.Novel;

/**
 * Library home: one card per novel, each with its cover, title and a one-line
 * summary of what has been loaded.
 *
 * <p>Tapping a card opens that novel's detail page. The cards are plain views
 * with stable tags so a Robolectric test can measure them and tap them without a
 * network fetch.
 */
public final class NovelListPage {

    /** Tapping a card opens the novel. */
    public interface Listener {
        void onOpenNovel(Novel novel);
    }

    /**
     * Per-novel actions behind the three-dot button.
     *
     * <p>Kept separate from {@link Listener} so a screen that only needs tapping
     * still works, and so the menu is drawn by the page while the decisions
     * (confirm dialogs, database writes) stay with the activity.
     */
    public interface MenuListener {
        void onOpenNovel(Novel novel);

        void onRefreshNovel(Novel novel);

        void onDeleteNovel(Novel novel);
    }

    private static final int CARD_WIDTH_DP = 108;

    /** Ids for the card menu items. */
    private static final int MENU_OPEN = 1;
    private static final int MENU_REFRESH = 2;
    private static final int MENU_DELETE = 3;

    private final Context context;
    private final Prefs prefs;
    private final Palette palette;
    private final List<Novel> novels;
    private final Listener listener;
    /** Null hides the three-dot button; set by screens that can act on a novel. */
    private MenuListener menuListener;
    /** Null hides the "Tambah Novel" button. */
    private Runnable addNovel;

    public NovelListPage(Context context, Prefs prefs, Palette palette, List<Novel> novels,
                         Listener listener) {
        this.context = context;
        this.prefs = prefs;
        this.palette = palette;
        this.novels = novels;
        this.listener = listener;
    }

    /** Supplies the per-novel menu actions. */
    public NovelListPage withMenu(MenuListener listener) {
        this.menuListener = listener;
        return this;
    }

    /** Supplies the "Tambah Novel" action; without it the button is not drawn. */
    public NovelListPage withAddNovel(Runnable action) {
        this.addNovel = action;
        return this;
    }

    /** The scrollable column shown on the home screen. */
    public LinearLayout build() {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setTag("novel_list");

        // The toolbar already says "Perpustakaan", so the app name is printed
        // here exactly once instead of repeating the page name in the body.
        TextView heading = Ui.label(context, "Kuromaku Reader", 20, palette.text);
        heading.setTag("library_heading");
        heading.setTypeface(null, Typeface.BOLD);
        content.addView(heading);

        TextView sub = Ui.label(context,
                novels.size() + " novel · sumber Kaori Translation", 12, palette.muted);
        sub.setPadding(0, Ui.dp(context, 2), 0, Ui.dp(context, 12));
        content.addView(sub);

        // Adding a novel is the primary action on this screen, so it sits at the
        // top where it is found without scrolling, at full touch-target height.
        if (addNovel != null) content.addView(addButton());

        if (novels.isEmpty()) {
            content.addView(emptyState());
        }
        for (Novel novel : novels) {
            content.addView(card(novel));
        }
        return content;
    }

    /** "+ Tambah Novel": opens the URL input. Always at least 48 dp tall. */
    private View addButton() {
        Button button = new Button(context);
        button.setText("+ Tambah Novel");
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTextColor(palette.text);
        button.setTypeface(null, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(Ui.dp(context, Ui.TOUCH_DP));
        button.setMinimumHeight(Ui.dp(context, Ui.TOUCH_DP));
        button.setPadding(Ui.dp(context, 16), Ui.dp(context, 8),
                Ui.dp(context, 16), Ui.dp(context, 8));
        button.setBackground(Ui.ripple(context, palette.surface, palette.divider, 12,
                palette.ripple));
        button.setStateListAnimator(null);
        button.setTag("add_novel");
        button.setContentDescription("Tambah novel dari URL");
        button.setOnClickListener(v -> addNovel.run());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, Ui.dp(context, 12));
        button.setLayoutParams(params);
        return button;
    }

    /**
     * Shown when the shelf has no novels at all.
     *
     * <p>A delete-everything state must still be usable: it explains what the
     * screen is for and points back at the add button instead of showing a blank
     * list.
     */
    private View emptyState() {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setTag("library_empty");
        box.setPadding(0, Ui.dp(context, 28), 0, Ui.dp(context, 24));

        TextView title = Ui.label(context, "Perpustakaan masih kosong", 15, palette.text);
        title.setTag("library_empty_title");
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        box.addView(title);

        TextView hint = Ui.label(context,
                "Tambahkan novel dengan menempel URL halaman katalog dari "
                        + "kaoritranslation.blogspot.com.", 12, palette.muted);
        hint.setTag("library_empty_hint");
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(Ui.dp(context, 12), Ui.dp(context, 6), Ui.dp(context, 12), 0);
        box.addView(hint);
        return box;
    }

    /** One novel card: cover on the left, text on the right. */
    private View card(Novel novel) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setTag("novel_card:" + novel.id);
        card.setBackground(Ui.ripple(context, palette.surface, palette.divider, 12,
                palette.ripple));
        card.setPadding(Ui.dp(context, 10), Ui.dp(context, 10),
                Ui.dp(context, 12), Ui.dp(context, 10));
        card.setMinimumHeight(Ui.dp(context, 112));

        addCover(card, novel);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Ui.dp(context, 12), 0, 0, 0);

        TextView title = Ui.label(context, novel.title, 16, palette.text);
        title.setTag("novel_card_title:" + novel.id);
        title.setTypeface(null, Typeface.BOLD);
        texts.addView(title);

        TextView japanese = Ui.label(context, novel.titleJp, 11, palette.muted);
        japanese.setMaxLines(2);
        japanese.setPadding(0, Ui.dp(context, 3), 0, Ui.dp(context, 6));
        texts.addView(japanese);

        Catalog catalog = id.shuyasal.kuromaku.Session.catalog(novel.id);
        String summary;
        if (catalog == null) {
            summary = "Belum dimuat";
        } else if (catalog.size() == 0) {
            summary = "Katalog kosong";
        } else {
            summary = catalog.volumes.size() + " volume · " + catalog.size() + " chapter";
        }
        TextView meta = Ui.label(context, summary, 11, palette.muted);
        meta.setTag("novel_card_meta:" + novel.id);
        texts.addView(meta);

        // Progress and continue-reading stay per novel: this reads only this
        // novel's own bookmark.
        String last = prefs.lastUrl();
        if (catalog != null && !last.isEmpty() && catalog.indexOf(last) >= 0) {
            id.shuyasal.kuromaku.data.Chapter resume = catalog.findByUrl(last);
            if (resume != null) {
                int percent = prefs.progress(resume.url);
                String label = percent > 0
                        ? "Lanjutkan: " + resume.label + " (" + percent + "%)"
                        : "Terakhir: " + resume.label;
                TextView progress = Ui.label(context, label, 11, palette.accent);
                progress.setTag("novel_card_progress:" + novel.id);
                progress.setPadding(0, Ui.dp(context, 6), 0, 0);
                texts.addView(progress);
            }
        }

        card.addView(texts, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        if (menuListener != null) card.addView(menuButton(novel));

        card.setOnClickListener(v -> listener.onOpenNovel(novel));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, Ui.dp(context, 10));
        card.setLayoutParams(params);
        return card;
    }

    /**
     * Three-dot button on a card: Open, Refresh, Delete.
     *
     * <p>Its own click listener stops the tap from bubbling to the card, so
     * opening the menu never opens the novel as a side effect.
     */
    private View menuButton(Novel novel) {
        ImageButton button = Ui.iconButton(context, R.drawable.ic_more,
                "Menu " + novel.title, palette);
        button.setTag("novel_menu:" + novel.id);
        button.setOnClickListener(v -> {
            v.setPressed(false);
            showMenu(novel, v);
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                Ui.dp(context, Ui.TOUCH_DP), Ui.dp(context, Ui.TOUCH_DP));
        button.setLayoutParams(params);
        return button;
    }

    /** Popup with the three documented actions. */
    private void showMenu(Novel novel, View anchor) {
        androidx.appcompat.widget.PopupMenu menu = new androidx.appcompat.widget.PopupMenu(
                context, anchor);
        menu.getMenu().add(0, MENU_OPEN, 0, "Buka Novel");
        menu.getMenu().add(0, MENU_REFRESH, 1, "Perbarui Novel");
        menu.getMenu().add(0, MENU_DELETE, 2, "Hapus dari Perpustakaan");
        menu.setOnMenuItemClickListener(item -> {
            if (menuListener == null) return false;
            switch (item.getItemId()) {
                case MENU_OPEN:
                    menuListener.onOpenNovel(novel);
                    return true;
                case MENU_REFRESH:
                    menuListener.onRefreshNovel(novel);
                    return true;
                case MENU_DELETE:
                    menuListener.onDeleteNovel(novel);
                    return true;
                default:
                    return false;
            }
        });
        menu.show();
    }

    private void addCover(LinearLayout card, Novel novel) {
        int width = Ui.dp(context, CARD_WIDTH_DP);
        float ratio = novel.coverRatio > 0 ? novel.coverRatio : 0.70f;
        int height = Math.max(1, Math.round(width / ratio));

        RatioFrameLayout holder = new RatioFrameLayout(context);
        holder.setTag("novel_card_cover:" + novel.id);
        holder.setRatio(ratio);
        holder.setBackground(Ui.rounded(palette.background, palette.divider, 8, context));

        TextView placeholder = Ui.label(context, "Sampul", 10, palette.muted);
        placeholder.setTag("novel_card_placeholder:" + novel.id);
        placeholder.setGravity(Gravity.CENTER);
        holder.addView(placeholder, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView image = new ImageView(context);
        image.setTag("novel_card_image:" + novel.id);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        holder.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        try {
            Glide.with(context).load(novel.coverUrl).centerCrop().into(image);
        } catch (Exception ignored) {
            // Placeholder stays.
        }

        card.addView(holder, new LinearLayout.LayoutParams(width, height));
    }
}
