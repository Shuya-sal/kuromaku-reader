package id.shuyasal.kuromaku.net;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Small HTTP helper. Only text documents are fetched; no JavaScript is executed. */
public final class Http {

    public static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0 Mobile Safari/537.36 KuromakuReader/2.0";

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build();

    private Http() {
    }

    /** Downloads a binary resource (an illustration) with the same client. */
    public static byte[] getBytes(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "image/*,*/*;q=0.8")
                .build();

        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " untuk " + url);
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Respons kosong dari " + url);
            }
            return body.bytes();
        }
    }

    public static String get(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "id,en;q=0.8")
                .build();

        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " untuk " + url);
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Respons kosong dari " + url);
            }
            return body.string();
        }
    }
}
