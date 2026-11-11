package id.shuyasal.kuromaku.komiku;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Pemuat gambar Komiku.
 *
 * <p>Satu cache bitmap berukuran terbatas dipakai bersama semua layar, dan
 * unduhan berjalan di satu thread latar — cukup untuk cover katalog dan
 * halaman chapter tanpa membuka banyak koneksi sekaligus.
 */
public final class KomikuImages {

    private static final ExecutorService WORKER = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .followRedirects(true)
            .build();

    /** Perangkat dengan anggaran memori sempit (banyak kotak televisi). */
    public static boolean isLowMemory() {
        return Runtime.getRuntime().maxMemory() <= 192L * 1024 * 1024;
    }

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(
            (int) (Runtime.getRuntime().maxMemory() / (isLowMemory() ? 16 : 8))) {
        @Override protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private KomikuImages() {
    }

    /** Muat gambar ke view; gambar lama tidak menimpa gambar baru. */
    public static void load(String url, ImageView view) {
        if (url == null || url.isEmpty()) return;
        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageDrawable(null);
        final Object token = new Object();
        view.setTag(token);
        WORKER.execute(() -> {
            Bitmap bitmap = download(url, 0);
            if (bitmap == null) return;
            CACHE.put(url, bitmap);
            MAIN.post(() -> {
                if (view.getTag() == token) view.setImageBitmap(bitmap);
            });
        });
    }

    /** Ambil bitmap langsung. */
    public static void fetch(String url, Callback callback) {
        fetch(url, 0, callback);
    }

    /**
     * Ambil bitmap dengan lebar maksimum.
     *
     * <p>Halaman chapter berukuran besar; men-decode pada lebar layar menjaga
     * memori tetap wajar saat satu chapter berisi puluhan gambar.
     */
    public static void fetch(String url, int maxWidth, Callback callback) {
        final String key = url + "#" + maxWidth;
        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            MAIN.post(() -> callback.onBitmap(cached));
            return;
        }
        WORKER.execute(() -> {
            Bitmap bitmap = download(url, maxWidth);
            if (bitmap != null) CACHE.put(key, bitmap);
            MAIN.post(() -> {
                if (bitmap == null) callback.onFailed();
                else callback.onBitmap(bitmap);
            });
        });
    }

    public interface Callback {
        void onBitmap(Bitmap bitmap);

        void onFailed();
    }

    private static Bitmap download(String url, int maxWidth) {
        if (!KomikuConfig.isAllowed(url)) return null;
        AtomicBoolean failed = new AtomicBoolean(false);
        try {
            Request request = new Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) Kuromaku/1.0")
                    .header("Referer", KomikuConfig.BASE + "/")
                    .build();
            try (Response response = CLIENT.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return null;
                byte[] bytes = response.body().bytes();
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                BitmapFactory.Options options = new BitmapFactory.Options();
                // Perangkat beram kecil (banyak kotak televisi 32 inci) memakai
                // setengah memori per gambar. Selisih warnanya tidak terlihat
                // pada komik, sedangkan kehabisan memori membuat layar mati.
                if (isLowMemory()) options.inPreferredConfig = Bitmap.Config.RGB_565;
                if (maxWidth > 0 && bounds.outWidth > maxWidth) {
                    int sample = 1;
                    while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2;
                    options.inSampleSize = sample;
                }
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                if (bitmap == null) {
                    failed.set(true);
                    return null;
                }
                // Penyusutan hanya boleh dua pangkat, jadi gambar bisa masih
                // jauh lebih lebar daripada layar: satu halaman 1600 px menjadi
                // 15 MB, dan tiga puluh halaman menghabiskan seluruh anggaran
                // memori aplikasi. Dipotong ke lebar layar; sisanya dibuang.
                int[] ukuran = scaledSize(bitmap.getWidth(), bitmap.getHeight(), maxWidth);
                if (ukuran != null) {
                    Bitmap scaled = Bitmap.createScaledBitmap(bitmap,
                            ukuran[0], ukuran[1], true);
                    if (scaled != bitmap) {
                        bitmap.recycle();
                        bitmap = scaled;
                    }
                }
                return bitmap;
            }
        } catch (Exception error) {
            failed.set(true);
            return null;
        }
    }


    /**
     * Ukuran gambar setelah penyusutan.
     *
     * <p>Dipisah sebagai fungsi murni supaya bisa diuji tanpa bitmap sungguhan:
     * memuat bitmap di pengujian unit butuh memori dan lambat.
     *
     * @return {@code {lebar, tinggi}} hasil susut, atau {@code null} bila tidak
     *         perlu disusutkan.
     */
    public static int[] scaledSize(int lebar, int tinggi, int maxWidth) {
        if (lebar <= 0 || tinggi <= 0) return null;
        if (maxWidth <= 0) return null;
        // Toleransi 25%: menyusut sedikit hanya menghabiskan waktu GPU tanpa
        // perbedaan yang terlihat.
        if (lebar <= maxWidth * 1.25f) return null;
        int baru = maxWidth;
        int tinggiBaru = Math.max(1, Math.round(tinggi * (float) maxWidth / lebar));
        return new int[]{baru, tinggiBaru};
    }

    public static void clear() {
        CACHE.evictAll();
    }
}