package id.shuyasal.kuromaku.shinigami;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public final class ShinigamiPageView extends FrameLayout {
    final ImageView image;
    private final LinearLayout loading;
    private final LinearLayout errorBox;
    private final TextView error;
    private double ratio;
    private Runnable retryAction;

    public ShinigamiPageView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        image = new ImageView(context);
        image.setTag("shinigami_page_image");
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(image, new LayoutParams(-1, -1));
        loading = new LinearLayout(context);
        loading.setOrientation(LinearLayout.VERTICAL);
        loading.setGravity(Gravity.CENTER);
        loading.setTag("shinigami_page_loading");
        loading.addView(new ProgressBar(context));
        TextView message = new TextView(context);
        message.setText("Memuat gambar...");
        message.setTextColor(Color.WHITE);
        loading.addView(message);
        addView(loading, new LayoutParams(-1, -2, Gravity.CENTER));
        errorBox = new LinearLayout(context);
        errorBox.setOrientation(LinearLayout.VERTICAL);
        errorBox.setGravity(Gravity.CENTER);
        errorBox.setTag("shinigami_page_error");
        errorBox.setPadding(16, 16, 16, 16);
        error = new TextView(context);
        error.setTextColor(0xFFFF8080);
        error.setGravity(Gravity.CENTER);
        error.setTag("shinigami_page_error_detail");
        errorBox.addView(error);
        Button retry = new Button(context);
        retry.setText("Coba Lagi");
        retry.setTag("shinigami_page_retry");
        retry.setOnClickListener(v -> { if (retryAction != null) retryAction.run(); });
        errorBox.addView(retry);
        addView(errorBox, new LayoutParams(-1, -2, Gravity.CENTER));
        showLoading();
    }

    public void setRetryAction(Runnable action) { retryAction = action; }

    public void showLoading() {
        ratio = 0;
        image.setImageDrawable(null);
        image.setVisibility(INVISIBLE);
        loading.setVisibility(VISIBLE);
        errorBox.setVisibility(GONE);
        requestLayout();
    }

    public void showReady(Bitmap bitmap) {
        if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            showFailure("Gambar tidak dapat didekode.");
            return;
        }
        ratio = (double) bitmap.getHeight() / bitmap.getWidth();
        image.setImageBitmap(bitmap);
        image.setVisibility(VISIBLE);
        loading.setVisibility(GONE);
        errorBox.setVisibility(GONE);
        requestLayout();
    }

    public void showFailure(String reason) {
        image.setVisibility(INVISIBLE);
        loading.setVisibility(GONE);
        error.setText(reason);
        errorBox.setVisibility(VISIBLE);
        requestLayout();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        if (width <= 0) width = getResources().getDisplayMetrics().widthPixels;
        int height = ratio > 0 ? Math.max(1, (int) Math.ceil(width * ratio))
                : Math.max(320, getResources().getDisplayMetrics().heightPixels);
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
}
