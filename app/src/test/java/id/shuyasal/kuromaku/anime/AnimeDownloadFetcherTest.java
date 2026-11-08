package id.shuyasal.kuromaku.anime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;

/**
 * Uji mesin unduhan terhadap server HTTP sungguhan di localhost.
 *
 * <p>Yang dibuktikan di sini adalah hal yang tidak bisa dibuktikan oleh tiruan:
 * byte benar-benar ditulis ke berkas, {@code Range} benar-benar dipakai untuk
 * melanjutkan, dan berkas hanya dinyatakan tersimpan setelah ukuran serta
 * kepalanya lolos pemeriksaan.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class AnimeDownloadFetcherTest {

    private Context context;
    private TinyServer server;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build();

    private final AtomicInteger rangeRequests = new AtomicInteger();
    private final AtomicBoolean ignoreRange = new AtomicBoolean(false);
    private volatile int status = 200;
    private volatile byte[] payload;
    private volatile long declaredLength = -1;

    @Before public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        clear();
        AnimeDownloadStatus.reset();
        payload = mp4(4096);
        server = new TinyServer(this::respond);
    }

    @After public void tearDown() {
        if (server != null) server.close();
        clear();
        AnimeDownloadStatus.reset();
    }

    /** Jawaban server untuk satu permintaan; memakai Range bila diminta. */
    private TinyServer.Reply respond(String rangeHeader) {
        byte[] body = payload == null ? new byte[0] : payload;
        if (status != 200) return TinyServer.Reply.of(status, null, new byte[0]);
        if (rangeHeader != null && !ignoreRange.get()) {
            rangeRequests.incrementAndGet();
            long start;
            try {
                start = Long.parseLong(rangeHeader.replace("bytes=", "").replace("-", "").trim());
            } catch (NumberFormatException error) {
                start = 0;
            }
            start = Math.min(start, body.length);
            byte[] rest = java.util.Arrays.copyOfRange(body, (int) start, body.length);
            java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();
            headers.put("Content-Range",
                    "bytes " + start + "-" + (body.length - 1) + "/" + body.length);
            return TinyServer.Reply.of(206, headers, rest);
        }
        if (declaredLength > 0) return TinyServer.Reply.truncated(200, body, declaredLength);
        return TinyServer.Reply.of(200, null, body);
    }

    private String url() {
        return "http://127.0.0.1:" + server.port() + "/video.mp4";
    }

    private void clear() {
        File root = AnimeDownloads.root(context);
        File[] dirs = root.listFiles();
        if (dirs != null) {
            for (File dir : dirs) {
                File[] files = dir.listFiles();
                if (files != null) for (File file : files) file.delete();
                dir.delete();
            }
        }
    }

    /** Berkas palsu berformat MP4: kotak ftyp pada offset 4. */
    static byte[] mp4(int size) {
        byte[] data = new byte[Math.max(size, 16)];
        data[4] = 'f';
        data[5] = 't';
        data[6] = 'y';
        data[7] = 'p';
        for (int i = 8; i < data.length; i++) data[i] = (byte) (i % 251);
        return data;
    }

    private AnimeDownloadFetcher.Params params() {
        return paramsFor(url());
    }

    private AnimeDownloadFetcher.Params paramsFor(String mediaUrl) {
        return new AnimeDownloadFetcher.Params("fxskc", "FX Senshi Kurumi-chan", "",
                "Episode 1", "https://otakudesu.blog/episode/fxskc-episode-1-sub-indo/",
                "kunci1", "720p", "odcdn", mediaUrl, "PROGRESSIVE");
    }

    // ------------------------------------------------------------- alur utama

    @Test public void mengunduhBerkasLengkapDanMencatatUkurannya() {
        AnimeDownloadFetcher.Result result = AnimeDownloadFetcher.run(context, params(), client,
                () -> false);

        assertEquals(AnimeDownloadFetcher.Outcome.SAVED, result.outcome);
        File file = AnimeDownloads.videoFile(context, "fxskc", "kunci1");
        assertTrue("berkas harus ada di penyimpanan aplikasi", file.isFile());
        assertEquals("jumlah byte harus sama dengan yang dikirim",
                payload.length, file.length());
        assertEquals("episode harus lolos pemeriksaan", true,
                AnimeDownloads.isSaved(context, "fxskc", "kunci1"));

        AnimeDownload meta = AnimeDownloads.find(context, "fxskc", "kunci1");
        assertNotNull(meta);
        assertEquals(payload.length, meta.bytes);
        assertEquals("720p", meta.quality);
        assertEquals("odcdn", meta.server);
        assertEquals("semua byte terunduh",
                AnimeDownloadStatus.SAVED, AnimeDownloadStatus.state("kunci1"));
    }

    @Test public void meneruskanUnduhanTerputusDenganRange() throws Exception {
        // Sisa unduhan sebelumnya: 1000 byte pertama sudah ada.
        File part = AnimeDownloads.partFile(context, "fxskc", "kunci1");
        assertTrue(part.getParentFile().isDirectory() || part.getParentFile().mkdirs());
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(part)) {
            out.write(payload, 0, 1000);
        }

        AnimeDownloadFetcher.Result result = AnimeDownloadFetcher.run(context, params(), client,
                () -> false);

        assertEquals(AnimeDownloadFetcher.Outcome.SAVED, result.outcome);
        assertTrue("Range harus dipakai untuk melanjutkan", rangeRequests.get() >= 1);
        assertEquals("berkas akhir harus utuh, bukan berisi data ganda",
                payload.length, AnimeDownloads.videoFile(context, "fxskc", "kunci1").length());
    }

    @Test public void serverYangMengabaikanRangeTidakMembuatDataGanda() throws Exception {
        File part = AnimeDownloads.partFile(context, "fxskc", "kunci1");
        assertTrue(part.getParentFile().isDirectory() || part.getParentFile().mkdirs());
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(part)) {
            out.write(payload, 0, 1000);
        }
        ignoreRange.set(true);

        AnimeDownloadFetcher.Result result = AnimeDownloadFetcher.run(context, params(), client,
                () -> false);

        assertEquals(AnimeDownloadFetcher.Outcome.SAVED, result.outcome);
        assertEquals("berkas ditulis ulang dari awal, bukan ditambahkan",
                payload.length, AnimeDownloads.videoFile(context, "fxskc", "kunci1").length());
    }

    // --------------------------------------------------------------- penolakan

    @Test public void serverMenolakUnduhanTidakDisembunyikan() {
        status = 403;
        AnimeDownloadFetcher.Result result = AnimeDownloadFetcher.run(context, params(), client,
                () -> false);

        assertEquals(AnimeDownloadFetcher.Outcome.FAILED, result.outcome);
        assertTrue("alasan menyebut kode", result.message.contains("403"));
        assertFalse("tidak ada berkas tersimpan",
                AnimeDownloads.isSaved(context, "fxskc", "kunci1"));
    }

    @Test public void halamanHtmlTidakDisimpanSebagaiVideo() {
        payload = "<html><body>Bukan video</body></html>".getBytes(StandardCharsets.UTF_8);

        AnimeDownloadFetcher.Result result = AnimeDownloadFetcher.run(context, params(), client,
                () -> false);

        assertEquals(AnimeDownloadFetcher.Outcome.FAILED, result.outcome);
        assertTrue(result.message.contains("bukan video"));
        assertFalse(AnimeDownloads.isSaved(context, "fxskc", "kunci1"));
        assertFalse("berkas sementara yang gagal dibuang",
                AnimeDownloads.partFile(context, "fxskc", "kunci1").exists());
    }

    @Test public void hlsTidakPernahDisimpan() {
        AnimeDownloadFetcher.Params hls = new AnimeDownloadFetcher.Params("fxskc", "FX", "",
                "Episode 1", "https://otakudesu.blog/e/1", "kunci1", "360p", "vidhide",
                url(), "HLS");
        AnimeDownloadFetcher.Result result = AnimeDownloadFetcher.run(context, hls, client,
                () -> false);

        assertEquals(AnimeDownloadFetcher.Outcome.FAILED, result.outcome);
        assertFalse(AnimeDownloads.isSaved(context, "fxskc", "kunci1"));
    }

    @Test public void alamatKosongTidakMencobaApaPun() {
        AnimeDownloadFetcher.Params kosong = new AnimeDownloadFetcher.Params("fxskc", "FX", "",
                "Episode 1", "https://otakudesu.blog/e/1", "kunci1", "720p", "odcdn",
                "", "PROGRESSIVE");
        AnimeDownloadFetcher.Result result = AnimeDownloadFetcher.run(context, kosong, client,
                () -> false);
        assertEquals(AnimeDownloadFetcher.Outcome.FAILED, result.outcome);
    }

    // ------------------------------------------------------- belum lengkap

    @Test public void koneksiTerputusTidakPernahDianggapSeratusPersen() {
        // Server menjanjikan lebih banyak daripada yang dikirim, lalu menutup.
        declaredLength = payload.length + 5000L;

        AnimeDownloadFetcher.Result result = AnimeDownloadFetcher.run(context, params(), client,
                () -> false);

        assertEquals("belum lengkap", AnimeDownloadFetcher.Outcome.INCOMPLETE, result.outcome);
        assertFalse("tidak boleh muncul sebagai episode tersimpan",
                AnimeDownloads.isSaved(context, "fxskc", "kunci1"));
        assertTrue("berkas sementara disimpan untuk dilanjutkan",
                AnimeDownloads.partFile(context, "fxskc", "kunci1").isFile());
    }

    @Test public void pembatalanBerhentiSebelumSatuBytePun() throws Exception {
        AnimeDownloadFetcher.Result result = AnimeDownloadFetcher.run(context, params(), client,
                () -> true);

        // Pembatalan bukan "koneksi terputus": hasilnya CANCELLED, sehingga
        // worker berhenti alih-alih menjadwalkan ulang unduhan yang dibatalkan.
        assertEquals(AnimeDownloadFetcher.Outcome.CANCELLED, result.outcome);
        assertEquals(0L, AnimeDownloads.savedBytes(context, "fxskc", "kunci1"));
        assertFalse(AnimeDownloads.isSaved(context, "fxskc", "kunci1"));
    }

    @Test public void koneksiTerputusMasihBisaDilanjutkan() throws Exception {
        // Sinyal batal tidak pernah aktif: kegagalan jaringan tetap INCOMPLETE
        // (bisa dilanjutkan), bukan CANCELLED (dibuang).
        int port;
        try (TinyServer server = new TinyServer(range -> TinyServer.Reply.of(200,
                java.util.Collections.singletonMap("Content-Type", "video/mp4"),
                new byte[]{0, 0, 0, 0}))) {
            port = server.port();
        }
        OkHttpClient putus = new OkHttpClient.Builder()
                .connectTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        AnimeDownloadFetcher.Result result = AnimeDownloadFetcher.run(context,
                paramsFor("http://127.0.0.1:" + port + "/video.mp4"), putus, () -> false);
        assertEquals("kegagalan jaringan bukan pembatalan",
                AnimeDownloadFetcher.Outcome.INCOMPLETE, result.outcome);
    }

    @Test public void unduhUlangEpisodeYangSudahAdaTidakMenimpaDenganRusak() {
        AnimeDownloadFetcher.run(context, params(), client, () -> false);
        File file = AnimeDownloads.videoFile(context, "fxskc", "kunci1");
        long pertama = file.length();

        AnimeDownloadFetcher.Result result = AnimeDownloadFetcher.run(context, params(), client,
                () -> false);

        assertEquals(AnimeDownloadFetcher.Outcome.SAVED, result.outcome);
        assertEquals("berkas tetap utuh", pertama, file.length());
    }

    /**
     * Server HTTP kecil berbasis socket.
     *
     * <p>Dipakai alih-alih {@code com.sun.net.httpserver} karena modul itu tidak
     * tersedia pada classpath pengujian Android. Yang penting: permintaan benar
     * benar lewat TCP dan header {@code Range} benar-benar dibaca server.
     */
    static final class TinyServer implements java.io.Closeable {

        interface Handler {
            Reply respond(String rangeHeader);
        }

        static final class Reply {
            final int code;
            final java.util.Map<String, String> headers;
            final byte[] body;
            /** Bila > 0, header Content-Length memakai angka ini walau body lebih pendek. */
            final long declaredLength;

            private Reply(int code, java.util.Map<String, String> headers, byte[] body,
                          long declaredLength) {
                this.code = code;
                this.headers = headers == null ? new java.util.LinkedHashMap<>()
                        : new java.util.LinkedHashMap<>(headers);
                this.body = body == null ? new byte[0] : body;
                this.declaredLength = declaredLength;
            }

            static Reply of(int code, java.util.Map<String, String> headers, byte[] body) {
                return new Reply(code, headers, body, -1);
            }

            static Reply truncated(int code, byte[] body, long declared) {
                return new Reply(code, null, body, declared);
            }
        }

        private final ServerSocket socket;
        private final Handler handler;
        private final Thread thread;
        private volatile boolean running = true;

        TinyServer(Handler handler) throws IOException {
            this.handler = handler;
            this.socket = new ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1"));
            this.thread = new Thread(this::loop, "tiny-http");
            this.thread.setDaemon(true);
            this.thread.start();
        }

        int port() {
            return socket.getLocalPort();
        }

        private void loop() {
            while (running) {
                try (Socket client = socket.accept()) {
                    handle(client);
                } catch (IOException error) {
                    if (running) continue;
                }
            }
        }

        private void handle(Socket client) throws IOException {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(client.getInputStream(),
                            java.nio.charset.StandardCharsets.ISO_8859_1));
            String requestLine = reader.readLine();
            if (requestLine == null) return;
            String range = null;
            String header;
            while ((header = reader.readLine()) != null && !header.isEmpty()) {
                int colon = header.indexOf(':');
                if (colon > 0 && header.substring(0, colon).trim()
                        .equalsIgnoreCase("Range")) {
                    range = header.substring(colon + 1).trim();
                }
            }
            Reply reply = handler.respond(range);
            long length = reply.body.length;
            StringBuilder head = new StringBuilder();
            head.append("HTTP/1.1 ").append(reply.code)
                    .append(reply.code == 206 ? " Partial Content" : " OK").append("\r\n");
            head.append("Content-Length: ")
                    .append(reply.declaredLength > 0 ? reply.declaredLength : length)
                    .append("\r\n");
            head.append("Content-Type: video/mp4\r\n");
            head.append("Connection: close\r\n");
            for (java.util.Map.Entry<String, String> entry : reply.headers.entrySet()) {
                head.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
            }
            head.append("\r\n");
            java.io.OutputStream out = client.getOutputStream();
            out.write(head.toString().getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
            out.write(reply.body);
            out.flush();
        }

        @Override public void close() {
            running = false;
            try {
                socket.close();
            } catch (IOException ignored) {
                // Socket sudah tertutup.
            }
            thread.interrupt();
        }
    }
}
