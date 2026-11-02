package id.shuyasal.kuromaku.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import id.shuyasal.kuromaku.data.Block;
import id.shuyasal.kuromaku.parser.ChapterContent;

/**
 * Renders parsed chapter blocks. Text and illustrations are inflated as real
 * native views, so no web engine and no advertising is involved.
 */
public final class BlockAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_TEXT = 1;
    private static final int TYPE_IMAGE = 2;
    private static final int TYPE_DIVIDER = 3;
    private static final int TYPE_FOOTER = 4;

    private final Context context;
    private final Prefs prefs;
    private Palette palette;
    private List<Block> blocks = new ArrayList<>();
    private String title = "";
    private String sourceUrl = "";
    private Runnable onOpenSource;
    private ImageTapListener onImageTap;
    private int bottomPadding;

    /** Called when the reader taps an illustration. */
    public interface ImageTapListener {
        void onImageTap(Block block);
    }

    public BlockAdapter(Context context, Prefs prefs, Palette palette) {
        this.context = context;
        this.prefs = prefs;
        this.palette = palette;
    }

    public void setContent(ChapterContent content) {
        this.blocks = content == null ? new ArrayList<Block>() : new ArrayList<>(content.blocks());
        this.title = content == null ? "" : content.title();
        this.sourceUrl = content == null ? "" : content.sourceUrl();
        notifyDataSetChanged();
    }

    public void setPalette(Palette palette) {
        this.palette = palette;
        notifyDataSetChanged();
    }

    public void setOnOpenSource(Runnable listener) {
        this.onOpenSource = listener;
    }

    public void setOnImageTap(ImageTapListener listener) {
        this.onImageTap = listener;
    }

    /** Extra space under the last block so the final paragraph clears the bars. */
    public void setBottomPadding(int paddingPx) {
        this.bottomPadding = Math.max(0, paddingPx);
    }

    public int blockCount() {
        return blocks.size();
    }

    @Override
    public int getItemCount() {
        return blocks.size() + 1;
    }

    @Override
    public int getItemViewType(int position) {
        if (position >= blocks.size()) return TYPE_FOOTER;
        Block block = blocks.get(position);
        if (block.type == Block.TYPE_IMAGE) return TYPE_IMAGE;
        if (block.type == Block.TYPE_DIVIDER) return TYPE_DIVIDER;
        return TYPE_TEXT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int side = Ui.dp(context, 20);
        if (viewType == TYPE_IMAGE) {
            LinearLayout wrap = new LinearLayout(context);
            wrap.setPadding(side, 0, side, 0);
            ImageCell cell = new ImageCell(context);
            wrap.addView(cell, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new ImageHolder(wrap, cell);
        }
        if (viewType == TYPE_DIVIDER) {
            View line = new View(context);
            line.setBackgroundColor(palette.divider);
            LinearLayout wrap = new LinearLayout(context);
            wrap.setPadding(side, Ui.dp(context, 14), side, Ui.dp(context, 14));
            wrap.addView(line, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(context, 1))));
            return new SimpleHolder(wrap);
        }
        if (viewType == TYPE_FOOTER) return new FooterHolder(buildFooter());
        TextView text = new TextView(context);
        text.setTextColor(palette.text);
        text.setTextIsSelectable(true);
        text.setPadding(side, Ui.dp(context, 7), side, Ui.dp(context, 7));
        return new TextHolder(text);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof FooterHolder) {
            bindFooter((FooterHolder) holder);
            return;
        }
        if (position >= blocks.size()) return;

        Block block = blocks.get(position);

        if (holder instanceof ImageHolder) {
            ImageHolder imageHolder = (ImageHolder) holder;
            // A real reserved box, sized from the source's own pixel dimensions,
            // so the illustration is laid out whether or not the bitmap arrived.
            imageHolder.cell.setRatio(block.hasImageSize() ? block.imageRatio() : 0f);
            imageHolder.cell.bind(block.imageUrl, palette);
            imageHolder.cell.setOnClickListener(v -> {
                if (onImageTap != null) onImageTap.onImageTap(block);
            });
            return;
        }
        if (holder instanceof SimpleHolder) return;

        TextHolder textHolder = (TextHolder) holder;
        TextView view = textHolder.text;
        int size = prefs.fontSize();
        float spacing = prefs.lineSpacing();

        view.setTypeface(Typeface.DEFAULT);
        view.setGravity(Gravity.START);
        view.setTextColor(palette.text);
        view.setTextSize(size * 0.78f);
        view.setLineSpacing(0, spacing);

        switch (block.type) {
            case Block.TYPE_HEADING:
                view.setTextSize(size * (block.level <= 2 ? 1.35f : 1.15f));
                view.setTypeface(Typeface.DEFAULT_BOLD);
                view.setPadding(Ui.dp(context, 20), Ui.dp(context, 18),
                        Ui.dp(context, 20), Ui.dp(context, 10));
                break;
            case Block.TYPE_QUOTE:
                view.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
                view.setTextColor(palette.muted);
                view.setPadding(Ui.dp(context, 34), Ui.dp(context, 10),
                        Ui.dp(context, 24), Ui.dp(context, 10));
                break;
            case Block.TYPE_LIST_ITEM:
                view.setText("•  " + block.text);
                break;
            default:
                break;
        }

        if (prefs.justify() && block.type != Block.TYPE_LIST_ITEM) {
            view.setJustificationMode(android.text.Layout.JUSTIFICATION_MODE_INTER_WORD);
        } else {
            view.setJustificationMode(0);
        }

        view.setText(block.text);
    }

    private LinearLayout buildFooter() {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(context, 20), Ui.dp(context, 28),
                Ui.dp(context, 20), Ui.dp(context, 40));
        return box;
    }

    private void bindFooter(FooterHolder holder) {
        LinearLayout box = (LinearLayout) holder.itemView;
        box.removeAllViews();
        // Guarantees the last paragraph can scroll clear of the navigation panel
        // and the system gesture bar (BUG 2).
        box.setPadding(Ui.dp(context, 20), Ui.dp(context, 28),
                Ui.dp(context, 20), Ui.dp(context, 40) + bottomPadding);

        TextView titleView = Ui.label(context, title, 15, palette.text);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(titleView);

        TextView note = Ui.label(context,
                "Sumber asli: Kaori Translation (kaoritranslation.blogspot.com).\n"
                        + "Seluruh teks dan ilustrasi tetap milik penerbit dan penerjemah aslinya.",
                12, palette.muted);
        note.setPadding(0, Ui.dp(context, 6), 0, Ui.dp(context, 10));
        box.addView(note);

        Button open = Ui.chip(context, "Buka halaman sumber", palette);
        open.setOnClickListener(v -> {
            if (onOpenSource != null) onOpenSource.run();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        box.addView(open, params);

        if (!TextUtils.isEmpty(sourceUrl)) {
            TextView url = Ui.label(context, sourceUrl, 10, palette.muted);
            url.setPadding(0, Ui.dp(context, 10), 0, 0);
            box.addView(url);
        }
    }

    static final class TextHolder extends RecyclerView.ViewHolder {
        final TextView text;

        TextHolder(TextView view) {
            super(view);
            this.text = view;
        }
    }

    static final class ImageHolder extends RecyclerView.ViewHolder {
        final ImageCell cell;

        ImageHolder(View view, ImageCell cell) {
            super(view);
            this.cell = cell;
        }
    }

    static final class SimpleHolder extends RecyclerView.ViewHolder {
        SimpleHolder(View view) {
            super(view);
        }
    }

    static final class FooterHolder extends RecyclerView.ViewHolder {
        FooterHolder(View view) {
            super(view);
        }
    }
}
