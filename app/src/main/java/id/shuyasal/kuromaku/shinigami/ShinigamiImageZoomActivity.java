package id.shuyasal.kuromaku.shinigami;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import id.shuyasal.kuromaku.ui.ZoomableImageView;
import java.io.File;

/** Shinigami-specific transport; the established novel image viewer is unchanged. */
public class ShinigamiImageZoomActivity extends AppCompatActivity {
    private ZoomableImageView image;
    private ProgressBar loading;
    private LinearLayout error;
    private TextView message;
    private CustomTarget<Bitmap> target;
    private String url;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ShinigamiImageRequest.register(this);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root=new FrameLayout(this); root.setBackgroundColor(Color.BLACK);
        image=new ZoomableImageView(this); image.setTag("zoom_image");
        root.addView(image,new FrameLayout.LayoutParams(-1,-1));
        loading=new ProgressBar(this);
        root.addView(loading,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));
        error=new LinearLayout(this); error.setOrientation(LinearLayout.VERTICAL);
        error.setGravity(Gravity.CENTER); error.setTag("zoom_error");
        message=new TextView(this); message.setTextColor(Color.WHITE); message.setGravity(Gravity.CENTER);
        error.addView(message);
        Button retry=new Button(this); retry.setText("Coba Lagi"); retry.setTag("zoom_retry");
        retry.setOnClickListener(v->load()); error.addView(retry);
        root.addView(error,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
        Button close=new Button(this); close.setText("Kembali"); close.setOnClickListener(v->finish());
        root.addView(close,new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.START));
        setContentView(root);
        url=getIntent().getStringExtra("url"); load();
    }
    private void failure(String reason) {
        loading.setVisibility(View.GONE); image.setVisibility(View.INVISIBLE);
        message.setText(reason); error.setVisibility(View.VISIBLE);
    }
    private void load() {
        if(target!=null) Glide.with(this).clear(target);
        String chapter=getIntent().getStringExtra("chapter_id");
        File local=new ShinigamiDownloader(this).pageFile(chapter,getIntent().getIntExtra("page",-1));
        if(local==null && !ShinigamiConfig.isValidResource(url)) {
            failure("Alamat gambar tidak valid atau bukan HTTPS."); return;
        }
        image.setVisibility(View.INVISIBLE); loading.setVisibility(View.VISIBLE); error.setVisibility(View.GONE);
        Object model=local!=null ? local : new ShinigamiImageRequest(url,new ShinigamiConfig(this).siteBase());
        target=new CustomTarget<Bitmap>(1080,8192) {
            @Override public void onResourceReady(Bitmap bitmap,Transition<? super Bitmap> transition) {
                image.setImageBitmap(bitmap); image.reset(); image.setVisibility(View.VISIBLE);
                loading.setVisibility(View.GONE); error.setVisibility(View.GONE);
            }
            @Override public void onLoadCleared(Drawable drawable) { image.setImageDrawable(null); }
            @Override public void onLoadFailed(Drawable drawable) { failure("Gambar gagal dimuat. Coba lagi."); }
        };
        Glide.with(this).asBitmap().load(model).disallowHardwareConfig()
                .format(com.bumptech.glide.load.DecodeFormat.PREFER_RGB_565)
                .downsample(com.bumptech.glide.load.resource.bitmap.DownsampleStrategy.AT_MOST)
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.DATA)
                .listener(new RequestListener<Bitmap>() {
                    @Override public boolean onLoadFailed(GlideException reason,Object ignored,Target<Bitmap> target,boolean first) {
                        failure(ShinigamiReaderActivity.safeFailure(reason)); return true;
                    }
                    @Override public boolean onResourceReady(Bitmap bitmap,Object ignored,Target<Bitmap> target,DataSource data,boolean first) { return false; }
                }).into(target);
    }
    @Override public void onDestroy() {
        if(target!=null) Glide.with(this).clear(target);
        super.onDestroy();
    }
}
