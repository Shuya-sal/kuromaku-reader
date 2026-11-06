package id.shuyasal.kuromaku.shinigami;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Matematika dan decoding halaman Shinigami.
 *
 * <p>Halaman Shinigami sangat panjang: lebar 800 px dengan tinggi sampai
 * 10.000 px. Satu bitmap penuh sebesar itu memakan 32 MB (ARGB_8888) dan
 * melewati batas tekstur GPU, jadi halaman dibaca sebagai potongan vertikal
 * ({@link BitmapRegionDecoder}). Dengan cara ini lebar asli sumber tetap utuh —
 * tidak ada penurunan resolusi seperti saat seluruh halaman dipaksa muat dalam
 * tinggi 4096 px.
 */
public final class ShinigamiPageTiles {

    /** Jumlah potongan yang disimpan di memori pada satu waktu. */
    public static final int CACHE_TILES = 6;
    /** Cache dimensi gambar di memori untuk menghindari disk I/O berulang pada main thread. */
    private static final android.util.LruCache<String, int[]> BOUNDS_CACHE =
            new android.util.LruCache<>(256);

    private ShinigamiPageTiles() {
    }

    /** Dimensi sumber, atau {@code null} bila berkas bukan gambar terbaca. */
    public static int[] bounds(File file) {
        if (file == null || !file.isFile() || file.length() < 8) return null;
        String path = file.getAbsolutePath();
        int[] cached = BOUNDS_CACHE.get(path);
        if (cached != null) return cached;
        // BitmapFactory pada sebagian implementasi mengembalikan ukuran palsu untuk
        // berkas non-gambar, jadi header diperiksa lebih dulu.
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            byte[] head = new byte[12];
            int read = 0;
            while (read < head.length) {
                int step = in.read(head, read, head.length - read);
                if (step == -1) break;
                read += step;
            }
            if (read < 8) return null;
            ShinigamiImageRequest.validate(200, "image/*", java.util.Arrays.copyOf(head, read));
        } catch (IOException error) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try {
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (RuntimeException error) {
            return null;
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) return null;
        int[] result = new int[]{options.outWidth, options.outHeight};
        BOUNDS_CACHE.put(path, result);
        return result;
    }

    /** Potongan vertikal {@code {top, height}} yang menutupi seluruh halaman. */
    public static List<int[]> slices(int sourceHeight, int tileHeight) {
        List<int[]> out = new ArrayList<>();
        int height = Math.max(1, tileHeight);
        int total = Math.max(1, sourceHeight);
        for (int top = 0; top < total; top += height) {
            out.add(new int[]{top, Math.min(height, total - top)});
        }
        if (out.isEmpty()) out.add(new int[]{0, total});
        return out;
    }

    /** Potongan ke berapa yang memuat piksel sumber {@code y}. */
    public static int indexOf(int y, int tileHeight) {
        return Math.max(0, y) / Math.max(1, tileHeight);
    }

    public static BitmapRegionDecoder open(File file) {
        // Header dan dimensi diperiksa lebih dulu: jangan membuka decoder untuk
        // berkas yang sebenarnya bukan gambar.
        if (bounds(file) == null) return null;
        try {
            return BitmapRegionDecoder.newInstance(file.getAbsolutePath(), false);
        } catch (IOException | RuntimeException error) {
            return null;
        }
    }

    public static Bitmap region(BitmapRegionDecoder decoder, Rect rect, boolean rgb565) {
        if (decoder == null || rect == null || rect.isEmpty()) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = rgb565 ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888;
        try {
            return decoder.decodeRegion(rect, options);
        } catch (RuntimeException | OutOfMemoryError error) {
            return null;
        }
    }

    /**
     * Bitmap penuh untuk layar zoom satu halaman.
     *
     * <p>Resolusi native dipertahankan selama masih di bawah {@code maxPixels};
     * hanya bila melewati batas, gambar diperkecil berpangkat dua supaya tidak
     * berakhir dengan {@link OutOfMemoryError}.
     */
    public static Bitmap decodeFull(File file, int maxPixels, boolean preferSharp) {
        int[] size = bounds(file);
        if (size == null) return null;
        int sample = 1;
        while ((long) (size[0] / sample) * (size[1] / sample) > Math.max(1, maxPixels)) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = preferSharp ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
        try {
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (RuntimeException | OutOfMemoryError error) {
            return null;
        }
    }
}
