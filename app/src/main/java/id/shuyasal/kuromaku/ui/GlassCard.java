package id.shuyasal.kuromaku.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Outline;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Kartu kaca (frosted glass) untuk halaman pemilih sumber.
 *
 * <p>Latar kartu memakai salinan gambar ilustrasi yang diposisikan tepat di
 * bawah kartu lalu diburamkan, sehingga ilustrasi tetap terlihat samar tanpa
 * kehilangan detail di area lain. Blur hanya menyentuh latar kartu — teks, ikon,
 * dan logo adalah view terpisah di atasnya. Pada Android di bawah 12 blur tidak
 * tersedia, jadi dipakai isian semi-transparan yang tetap terbaca.
 */
public class GlassCard extends FrameLayout {

    private static final float BLUR_RADIUS = 18f;

    private final int radiusDp;
    private final String backdropTag;
    private View backgroundView;
    private final ImageView backdrop;
    private final View tint;
    private final View border;
    private boolean blurApplied;

    public GlassCard(Context context, Palette palette, String backdropTag) {
        this(context, palette, backdropTag, 22);
    }

    public GlassCard(Context context, Palette palette, String backdropTag, int radiusDp) {
        super(context);
        this.radiusDp = radiusDp;
        this.backdropTag = backdropTag;

        // Outline + bayangan lembut. Isian dasar sengaja hampir transparan agar
        // salinan ilustrasi di dalamnya tetap terlihat.
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(Ui.dp(context, radiusDp));
        shape.setColor(0x14FFFFFF);
        setBackground(shape);
        setClipToOutline(true);
        setElevation(Ui.dp(context, 6));

        boolean blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
        backdrop = blurSupported ? new ImageView(context) : null;
        if (backdrop != null) {
            backdrop.setTag("glass_backdrop");
            backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
            backdrop.setAlpha(0.92f);
            backdrop.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(backdrop, new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            backdrop.setRenderEffect(RenderEffect.createBlurEffect(
                    BLUR_RADIUS, BLUR_RADIUS, Shader.TileMode.CLAMP));
            blurApplied = true;
        }

        // Warna kaca: lebih terang di mode terang, lebih gelap di mode gelap.
        tint = new View(context);
        tint.setTag("glass_tint");
        // Kaca gelap: ilustrasi di belakang tetap samar, teks putih tetap kontras.
        // Mode gelap sedikit lebih pekat daripada mode terang.
        tint.setBackgroundColor(palette.darkIcons ? 0x5E0E1116 : 0x3D0E1116);
        addView(tint, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Border tipis putih transparan di atas isi kartu.
        border = new View(context);
        border.setTag("glass_border");
        GradientDrawable stroke = new GradientDrawable();
        stroke.setShape(GradientDrawable.RECTANGLE);
        stroke.setCornerRadius(Ui.dp(context, radiusDp));
        stroke.setColor(0x00000000);
        stroke.setStroke(Math.max(1, Ui.dp(context, 1)), 0x5AFFFFFF);
        border.setBackground(stroke);
        border.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(border, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.RECTANGLE);
        mask.setCornerRadius(Ui.dp(context, radiusDp));
        mask.setColor(android.graphics.Color.WHITE);
        setForeground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, mask));
    }

    /** Latar bisa diuji: true bila kartu memakai salinan ilustrasi yang diburamkan. */
    public boolean hasBlurredBackdrop() {
        return backdrop != null && blurApplied;
    }

    /** Salinan ilustrasi (null pada Android &lt; 12 yang tidak mendukung blur). */
    public ImageView backdropView() {
        return backdrop;
    }

    /** Lapisan warna kaca; dipakai tes untuk memastikan kartu tidak putih solid. */
    public View tintView() {
        return tint;
    }

    /** Garis tepi putih transparan. */
    public View borderView() {
        return border;
    }

    public int radiusDp() {
        return radiusDp;
    }

    /** Gambar latar yang menjadi acuan posisi salinan ilustrasi. */
    public void setBackgroundView(View background) {
        backgroundView = background;
        requestLayout();
    }

    /** Salinan ilustrasi digeser agar tepat berada di belakang kartu. */
    public void alignBackdrop(View background) {
        if (backdrop == null || background == null) return;
        backgroundView = background;
        if (background.getWidth() <= 0 || background.getHeight() <= 0) return;
        int[] cardLocation = new int[2];
        int[] backgroundLocation = new int[2];
        getLocationInWindow(cardLocation);
        background.getLocationInWindow(backgroundLocation);

        if (backdrop.getWidth() != background.getWidth()
                || backdrop.getHeight() != background.getHeight()) {
            requestLayout();
        }
        float translateX = backgroundLocation[0] - cardLocation[0];
        float translateY = backgroundLocation[1] - cardLocation[1];
        if (backdrop.getTranslationX() != translateX) backdrop.setTranslationX(translateX);
        if (backdrop.getTranslationY() != translateY) backdrop.setTranslationY(translateY);
    }

    private boolean isOverlay(View child) {
        return child == backdrop || child == tint || child == border;
    }

    /**
     * Kartu setinggi isinya. Lapisan kaca (backdrop/tint/border) memakai
     * MATCH_PARENT sehingga tidak boleh ikut menentukan ukuran kartu.
     */
    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = 0;
        int height = 0;
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (isOverlay(child) || child.getVisibility() == GONE) continue;
            measureChildWithMargins(child, widthSpec, 0, heightSpec, 0);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            width = Math.max(width, child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin);
            height = Math.max(height, child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);
        }
        int resolvedWidth = resolveSize(width, widthSpec);
        int resolvedHeight = resolveSize(height, heightSpec);
        setMeasuredDimension(resolvedWidth, resolvedHeight);
        // Salinan ilustrasi harus seukuran gambar latar, bukan seukuran kartu:
        // dengan begitu centerCrop-nya identik dan posisinya bisa sejajar persis.
        int backdropWidth = resolvedWidth;
        int backdropHeight = resolvedHeight;
        if (backgroundView != null && backgroundView.getWidth() > 0
                && backgroundView.getHeight() > 0) {
            backdropWidth = backgroundView.getWidth();
            backdropHeight = backgroundView.getHeight();
        }
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (!isOverlay(child)) continue;
            boolean isBackdrop = child == backdrop;
            child.measure(MeasureSpec.makeMeasureSpec(
                            isBackdrop ? backdropWidth : resolvedWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(
                            isBackdrop ? backdropHeight : resolvedHeight, MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        // FrameLayout memaksa child MATCH_PARENT selebar/se tinggi kartu. Salinan
        // ilustrasi harus tetap seukuran gambar latar supaya centerCrop-nya sama,
        // jadi lapisan kaca ditata ulang memakai ukuran hasil onMeasure.
        int count = getChildCount();
        View bg = backgroundView;
        if (bg == null) {
            View root = getRootView();
            if (root != null) bg = root.findViewWithTag(backdropTag);
        }
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (!isOverlay(child) || child.getVisibility() == GONE) continue;
            if (child == backdrop && bg != null && bg.getWidth() > 0 && bg.getHeight() > 0) {
                // Ukuran diambil langsung dari latar pada saat layout supaya
                // centerCrop salinan ilustrasi identik dengan aslinya.
                child.layout(0, 0, bg.getWidth(), bg.getHeight());
            } else {
                child.layout(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
            }
        }
        if (changed) {
            View root = getRootView();
            if (root != null) alignBackdrop(root.findViewWithTag(backdropTag));
        }
    }
}
