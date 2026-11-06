package id.shuyasal.kuromaku.shinigami;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Cache halaman Shinigami untuk pembacaan online.
 *
 * <p>Ini <b>bukan</b> unduhan offline: berkas disimpan di {@code cacheDir},
 * bisa dibersihkan sistem kapan saja, dan tidak pernah ditandai
 * "Tersimpan offline". Fungsinya supaya halaman yang sudah dibaca bisa
 * dirender ulang (misalnya setelah rotasi layar atau saat menggulir balik)
 * tanpa mengunduh ulang byte yang sama.
 *
 * <p>Penulisan memakai berkas sementara + ganti nama, jadi halaman setengah
 * jadi tidak pernah dibaca sebagai gambar valid.
 */
public final class ShinigamiPageCache {

    /** Cara mengambil byte gambar; dapat diganti pada pengujian. */
    public interface Transport {
        InputStream open(String url, String referer) throws IOException;
    }

    private static final long MAX_BYTES = 96L * 1024 * 1024;
    private static final int MAX_PAGE_BYTES = 32 * 1024 * 1024;
    /** Cache referensi berkas di memori untuk menghindari parsing dan I/O disk berulang. */
    private static final android.util.LruCache<String, File> MEM_CACHE =
            new android.util.LruCache<>(256);

    private final Context context;
    private final Transport transport;

    public ShinigamiPageCache(Context context) {
        this(context, new HttpTransport());
    }

    ShinigamiPageCache(Context context, Transport transport) {
        this.context = context.getApplicationContext();
        this.transport = transport;
    }

    public File dir() {
        File dir = new File(context.getCacheDir(), "shinigami-pages");
        if (!dir.isDirectory() && !dir.mkdirs()) return dir;
        return dir;
    }

    static String key(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest((url == null ? "" : url).getBytes("UTF-8"));
            StringBuilder out = new StringBuilder();
            for (byte b : hash) out.append(String.format(Locale.ROOT, "%02x", b));
            return out.toString();
        } catch (Exception error) {
            return Integer.toHexString(url == null ? 0 : url.hashCode());
        }
    }

    /** Berkas cache yang sudah ada dan masih berupa gambar utuh. */
    public File file(String url) {
        if (url == null || url.isEmpty()) return null;
        File cached = MEM_CACHE.get(url);
        if (cached != null && cached.isFile()) return cached;
        File file = new File(dir(), key(url) + ".img");
        if (!file.isFile() || file.length() < 8) return null;
        byte[] head = new byte[12];
        int read;
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            read = in.read(head);
        } catch (IOException error) {
            return null;
        }
        if (read < 8) return null;
        try {
            ShinigamiImageRequest.validate(200, "image/*", Arrays.copyOf(head, read));
        } catch (IOException error) {
            return null;
        }
        int[] size = ShinigamiPageTiles.bounds(file);
        if (size == null) return null;
        MEM_CACHE.put(url, file);
        return file;
    }

    /**
     * Ambil halaman, memakai cache bila ada. Byte yang bukan gambar, respons
     * 401/403/429, atau berkas terpotong tidak pernah menjadi berkas cache.
     */
    public File fetch(String url, String referer) throws IOException {
        File cached = file(url);
        if (cached != null) return cached;
        File target = new File(dir(), key(url) + ".img");
        File tmp = new File(dir(), key(url) + ".tmp");
        try (InputStream in = transport.open(url, referer)) {
            if (in == null) throw new IOException("Respons gambar kosong.");
            writeAtomically(in, tmp, target);
        } catch (IOException error) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw error;
        }
        if (file(url) == null) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            throw new IOException("Gambar tidak lolos validasi.");
        }
        prune();
        return target;
    }

    private static void writeAtomically(InputStream in, File tmp, File target) throws IOException {
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int step;
            while ((step = in.read(buffer)) != -1) {
                total += step;
                if (total > MAX_PAGE_BYTES) throw new IOException("Gambar terlalu besar.");
                out.write(buffer, 0, step);
            }
            out.flush();
            out.getFD().sync();
        } catch (IOException | RuntimeException error) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw error;
        }
        if (!tmp.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new IOException("Tidak dapat menyimpan cache gambar.");
        }
    }

    /** Buang berkas tertua bila cache melewati batas supaya penyimpanan tidak penuh. */
    public void prune() {
        File[] files = dir().listFiles();
        if (files == null) return;
        long total = 0;
        for (File file : files) total += file.length();
        if (total <= MAX_BYTES) return;
        Arrays.sort(files, (left, right) -> Long.compare(left.lastModified(), right.lastModified()));
        for (File file : files) {
            if (total <= MAX_BYTES) break;
            long length = file.length();
            if (file.delete()) total -= length;
        }
    }

    private static final class HttpTransport implements Transport {
        private final OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .followRedirects(false).followSslRedirects(false)
                .retryOnConnectionFailure(false).build();

        @Override public InputStream open(String url, String referer) throws IOException {
            if (!ShinigamiConfig.isValidResource(url)) {
                throw new IOException("Alamat CDN tidak valid atau bukan HTTPS.");
            }
            Request request = new Request.Builder().url(url)
                    .header("User-Agent", id.shuyasal.kuromaku.net.Http.USER_AGENT)
                    .header("Referer", referer)
                    .header("Accept", "image/webp,image/*;q=0.8").build();
            Response response = client.newCall(request).execute();
            int code = response.code();
            if (code < 200 || code >= 300) {
                response.close();
                ShinigamiImageRequest.validate(code, "", new byte[0]);
                throw new IOException("Gambar gagal dimuat (HTTP " + code + ").");
            }
            ResponseBody body = response.body();
            if (body == null) {
                response.close();
                throw new IOException("Respons gambar kosong.");
            }
            if (body.contentLength() > MAX_PAGE_BYTES) {
                response.close();
                throw new IOException("Gambar terlalu besar untuk dimuat dengan aman.");
            }
            InputStream stream = body.byteStream();
            int read;
            byte[] head = new byte[12];
            java.io.BufferedInputStream buffered = new java.io.BufferedInputStream(stream, 8192);
            buffered.mark(16);
            read = 0;
            while (read < head.length) {
                int step = buffered.read(head, read, head.length - read);
                if (step == -1) break;
                read += step;
            }
            try {
                ShinigamiImageRequest.validate(code, response.header("Content-Type"),
                        Arrays.copyOf(head, Math.max(0, read)));
            } catch (IOException error) {
                response.close();
                throw error;
            }
            buffered.reset();
            final Response held = response;
            return new java.io.FilterInputStream(buffered) {
                @Override public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        held.close();
                    }
                }
            };
        }
    }
}
