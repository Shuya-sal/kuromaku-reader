package id.shuyasal.kuromaku.shinigami;

import android.util.Log;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.signature.ObjectKey;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class ShinigamiImageRequest {
    final String url;
    final String referer;
    final OkHttpClient client;
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS).followRedirects(false)
            .followSslRedirects(false).retryOnConnectionFailure(false).build();

    public ShinigamiImageRequest(String url, String referer) {
        this(url, referer, CLIENT);
    }
    ShinigamiImageRequest(String url, String referer, OkHttpClient client) {
        this.url = url;
        this.client = client;
        this.referer = ShinigamiConfig.isValidBase(referer) ? referer.replaceAll("/+$", "") + "/" : "https://shinigami.asia/";
    }

    private static boolean registered;
    public static synchronized void register(android.content.Context context) {
        if (registered) return;
        com.bumptech.glide.Glide.get(context).getRegistry()
                .prepend(ShinigamiImageRequest.class, InputStream.class, new Factory());
        registered = true;
    }

    static void validate(int code, String mime, byte[] head) throws IOException {
        if (code == 401 || code == 403) throw new IOException("HTTP " + code + ": akses gambar dibatasi. Aplikasi tidak melewati autentikasi/CAPTCHA.");
        if (code == 429) throw new IOException("HTTP 429: batas permintaan tercapai. Tunggu sebelum mencoba lagi.");
        if (code < 200 || code >= 300) throw new IOException("Gambar gagal dimuat (HTTP " + code + ").");
        if (mime == null || !mime.toLowerCase(Locale.ROOT).startsWith("image/")) throw new IOException("Respons server bukan gambar (MIME tidak sesuai).");
        boolean jpeg = head.length >= 3 && (head[0] & 255) == 255 && (head[1] & 255) == 216 && (head[2] & 255) == 255;
        boolean png = head.length >= 8 && (head[0] & 255) == 137 && head[1] == 80 && head[2] == 78 && head[3] == 71;
        boolean gif = head.length >= 6 && head[0] == 'G' && head[1] == 'I' && head[2] == 'F';
        boolean webp = head.length >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
        if (!jpeg && !png && !gif && !webp) throw new IOException("Respons bukan format gambar yang didukung.");
    }

    @Override public boolean equals(Object other) {
        return other instanceof ShinigamiImageRequest && Objects.equals(url, ((ShinigamiImageRequest) other).url)
                && Objects.equals(referer, ((ShinigamiImageRequest) other).referer);
    }
    @Override public int hashCode() { return Objects.hash(url, referer); }
    @Override public String toString() { return "ShinigamiImageRequest"; }

    public static final class Factory implements ModelLoaderFactory<ShinigamiImageRequest, InputStream> {
        @Override public ModelLoader<ShinigamiImageRequest, InputStream> build(MultiModelLoaderFactory multi) {
            return new ModelLoader<ShinigamiImageRequest, InputStream>() {
                @Override public boolean handles(ShinigamiImageRequest model) { return true; }
                @Override public LoadData<InputStream> buildLoadData(ShinigamiImageRequest model, int w, int h, Options options) {
                    return new LoadData<>(new ObjectKey(model.url + "|" + model.referer), new Fetcher(model));
                }
            };
        }
        @Override public void teardown() { }
    }

    private static final class Fetcher implements DataFetcher<InputStream> {
        final ShinigamiImageRequest model;
        volatile Call call;
        volatile boolean cancelled;
        Response response;
        Fetcher(ShinigamiImageRequest model) { this.model = model; }
        @Override public void loadData(Priority priority, DataCallback<? super InputStream> callback) {
            try {
                String current = model.url;
                for (int redirects = 0; redirects <= 3; redirects++) {
                    if (cancelled) return;
                    if (!ShinigamiConfig.isValidResource(current)) throw new IOException("Alamat CDN tidak valid atau bukan HTTPS.");
                    Request request = new Request.Builder().url(current)
                            .header("User-Agent", id.shuyasal.kuromaku.net.Http.USER_AGENT)
                            .header("Referer", model.referer).header("Accept", "image/webp,image/*;q=0.8").build();
                    call = model.client.newCall(request);
                    response = call.execute();
                    int code = response.code();
                    Log.i("ShinigamiImage", "HTTP " + code + " host=" + request.url().host());
                    if (code >= 300 && code < 400) {
                        String location = response.header("Location");
                        okhttp3.HttpUrl target = location == null ? null : request.url().resolve(location);
                        response.close(); response = null;
                        if (redirects == 3 || target == null || !ShinigamiConfig.isValidResource(target.toString()))
                            throw new IOException("Redirect gambar ditolak: HTTPS/domain CDN tidak valid.");
                        current = target.toString();
                        continue;
                    }
                    ResponseBody body = response.body();
                    if (body == null) throw new IOException("Respons gambar kosong.");
                    if (body.contentLength() > 32L * 1024 * 1024) throw new IOException("Gambar terlalu besar untuk dimuat dengan aman.");
                    if (code < 200 || code >= 300) validate(code, "", new byte[0]);
                    body.source().request(16);
                    byte[] head = body.source().peek().readByteArray(Math.min(16, body.source().buffer().size()));
                    validate(code, response.header("Content-Type"), head);
                    final InputStream stream = body.byteStream();
                    callback.onDataReady(new java.io.FilterInputStream(stream) {
                        long count;
                        @Override public int read() throws IOException {
                            int value = super.read(); if (value != -1 && ++count > 32L * 1024 * 1024) throw new IOException("Gambar terlalu besar."); return value;
                        }
                        @Override public int read(byte[] b, int off, int len) throws IOException {
                            int n = in.read(b, off, len); if (n > 0 && (count += n) > 32L * 1024 * 1024) throw new IOException("Gambar terlalu besar."); return n;
                        }
                    });
                    return;
                }
            } catch (Exception error) {
                cleanup();
                if (!cancelled) callback.onLoadFailed(error instanceof IOException ? (IOException) error : new IOException("Pemuat gambar gagal.", error));
            }
        }
        @Override public void cleanup() { if (response != null) { response.close(); response = null; } }
        @Override public void cancel() { cancelled = true; if (call != null) call.cancel(); }
        @Override public Class<InputStream> getDataClass() { return InputStream.class; }
        @Override public DataSource getDataSource() { return DataSource.REMOTE; }
    }
}
