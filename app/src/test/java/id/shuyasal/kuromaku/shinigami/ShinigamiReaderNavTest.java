package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tombol chapter sebelumnya/berikutnya pada reader.
 *
 * <p>Panah harus mengikuti alur cerita (nomor chapter), bukan urutan tampilan
 * daftar, dan harus tetap benar walau chapter tujuan berada di halaman
 * pagination lain — diuji dengan daftar 500+ chapter.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w360dp-h640dp-mdpi")
public class ShinigamiReaderNavTest {

    static final String MANGA = "manga-nav-1";
    static ShinigamiSource.Transport transport;
    static boolean online = true;

    public static class Reader extends ShinigamiReaderActivity {
        @Override protected ShinigamiSource createSource() {
            return new ShinigamiSource(this, transport);
        }

        @Override protected boolean hasInternet() {
            return online;
        }
    }

    Context context;

    @Before public void setup() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(ShinigamiStore.FILE_NAME, 0).edit().clear().commit();
        ShinigamiSource.clearChapterIndex(null);
        online = true;
        transport = url -> {
            if (url.contains("/chapter/detail/")) return fixture("live_chapter_detail.json");
            if (url.contains("/chapter/") && url.contains("/list")) {
                return chapterList(url.contains("page=2"));
            }
            throw new java.io.IOException("unexpected " + url);
        };
    }

    static String fixture(String name) throws java.io.IOException {
        try (InputStream in = ShinigamiReaderNavTest.class.getResourceAsStream("/shinigami/" + name)) {
            if (in == null) throw new java.io.IOException(name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toString("UTF-8");
        }
    }

    /**
     * 501 chapter: prolog (0) lalu 1…500. Sumber membatasi 500 per halaman,
     * jadi chapter 500 hanya muncul di halaman kedua.
     */
    static String chapterList(boolean secondPage) throws java.io.IOException {
        try {
            JSONArray data = new JSONArray();
            for (int number = secondPage ? 500 : 0; secondPage ? number <= 500 : number <= 499;
                 number++) {
                JSONObject item = new JSONObject();
                item.put("chapter_id", "ch-" + number);
                item.put("manga_id", MANGA);
                item.put("chapter_number", number);
                item.put("chapter_title", number == 0 ? "Prolog" : "");
                item.put("release_date", "2026-01-01T00:00:00Z");
                data.put(item);
            }
            JSONObject meta = new JSONObject();
            meta.put("total_record", 501);
            meta.put("total_page", 2);
            JSONObject root = new JSONObject();
            root.put("retcode", 0);
            root.put("data", data);
            root.put("meta", meta);
            return root.toString();
        } catch (org.json.JSONException error) {
            throw new java.io.IOException(error);
        }
    }

    Intent intent(String chapterId, double number) {
        return new Intent(context, Reader.class)
                .putExtra(ShinigamiReaderActivity.EXTRA_MANGA_ID, MANGA)
                .putExtra(ShinigamiReaderActivity.EXTRA_MANGA_TITLE, "Uji Navigasi")
                .putExtra(ShinigamiReaderActivity.EXTRA_CHAPTER_ID, chapterId)
                .putExtra(ShinigamiReaderActivity.EXTRA_CHAPTER_NUMBER, number);
    }

    static void await(ShinigamiReaderActivity reader) throws Exception {
        Field field = ShinigamiReaderActivity.class.getDeclaredField("worker");
        field.setAccessible(true);
        ExecutorService worker = (ExecutorService) field.get(reader);
        for (int attempt = 0; attempt < 6; attempt++) {
            worker.submit(() -> { }).get(10, TimeUnit.SECONDS);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
    }

    static View tagged(ShinigamiReaderActivity reader, String tag) {
        View view = reader.getWindow().getDecorView().findViewWithTag(tag);
        assertNotNull(tag, view);
        return view;
    }

    /** Sama seperti {@link #tagged} tetapi mengembalikan {@code null} bila tidak ada. */
    static View viewOrNull(ShinigamiReaderActivity reader, String tag) {
        return reader.getWindow().getDecorView().findViewWithTag(tag);
    }

    /** ShinigamiPageView pertama di pohon view reader, apa pun posisinya. */
    static ShinigamiPageView firstPageView(ShinigamiReaderActivity reader) {
        return firstPageView((ViewGroup) reader.getWindow().getDecorView());
    }

    private static ShinigamiPageView firstPageView(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof ShinigamiPageView) return (ShinigamiPageView) child;
            if (child instanceof ViewGroup) {
                ShinigamiPageView found = firstPageView((ViewGroup) child);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test public void panahMenunjukChapterSebelumDanSesudahnya() throws Exception {
        try (ActivityController<Reader> controller =
                     Robolectric.buildActivity(Reader.class, intent("ch-25", 25d)).setup()) {
            Reader reader = controller.get();
            await(reader);

            View prev = tagged(reader, "shinigami_reader_prev");
            View next = tagged(reader, "shinigami_reader_next");
            assertTrue("panah kiri aktif", prev.isEnabled());
            assertTrue("panah kanan aktif", next.isEnabled());
            assertEquals("Chapter 24", prev.getContentDescription());
            assertEquals("Chapter 26", next.getContentDescription());
        }
    }

    @Test public void panahKananMembukaChapterBerikutnya() throws Exception {
        try (ActivityController<Reader> controller =
                     Robolectric.buildActivity(Reader.class, intent("ch-25", 25d)).setup()) {
            Reader reader = controller.get();
            await(reader);

            tagged(reader, "shinigami_reader_next").performClick();
            await(reader);

            ShadowActivity shadow = Shadows.shadowOf(reader);
            Intent started = shadow.getNextStartedActivity();
            assertNotNull("chapter berikutnya harus dibuka", started);
            assertEquals("ch-26", started.getStringExtra(ShinigamiReaderActivity.EXTRA_CHAPTER_ID));
            assertEquals(26d,
                    started.getDoubleExtra(ShinigamiReaderActivity.EXTRA_CHAPTER_NUMBER, 0d), 0.0001);
        }
    }

    @Test public void panahKiriMembukaChapterSebelumnya() throws Exception {
        try (ActivityController<Reader> controller =
                     Robolectric.buildActivity(Reader.class, intent("ch-25", 25d)).setup()) {
            Reader reader = controller.get();
            await(reader);

            tagged(reader, "shinigami_reader_prev").performClick();
            await(reader);

            Intent started = Shadows.shadowOf(reader).getNextStartedActivity();
            assertNotNull(started);
            assertEquals("ch-24", started.getStringExtra(ShinigamiReaderActivity.EXTRA_CHAPTER_ID));
        }
    }

    @Test public void chapterPalingAwalTidakPunyaPanahKiri() throws Exception {
        try (ActivityController<Reader> controller =
                     Robolectric.buildActivity(Reader.class, intent("ch-0", 0d)).setup()) {
            Reader reader = controller.get();
            await(reader);

            View prev = tagged(reader, "shinigami_reader_prev");
            assertFalse("prolog adalah chapter pertama", prev.isEnabled());
            assertEquals("Tidak ada chapter sebelumnya", prev.getContentDescription());
            assertTrue("masih ada chapter berikutnya", tagged(reader, "shinigami_reader_next").isEnabled());
        }
    }

    @Test public void chapterTerakhirTidakPunyaPanahKanan() throws Exception {
        try (ActivityController<Reader> controller =
                     Robolectric.buildActivity(Reader.class, intent("ch-500", 500d)).setup()) {
            Reader reader = controller.get();
            await(reader);

            View next = tagged(reader, "shinigami_reader_next");
            assertFalse("chapter terakhir tidak punya lanjutan", next.isEnabled());
            assertEquals("Tidak ada chapter berikutnya", next.getContentDescription());
        }
    }

    /** Chapter 499 → 500 hanya mungkin bila halaman pagination kedua ikut dibaca. */
    @Test public void panahMenyeberangHalamanPagination() throws Exception {
        try (ActivityController<Reader> controller =
                     Robolectric.buildActivity(Reader.class, intent("ch-499", 499d)).setup()) {
            Reader reader = controller.get();
            await(reader);

            View next = tagged(reader, "shinigami_reader_next");
            assertEquals("Chapter 500", next.getContentDescription());

            tagged(reader, "shinigami_reader_next").performClick();
            await(reader);
            Intent started = Shadows.shadowOf(reader).getNextStartedActivity();
            assertNotNull(started);
            assertEquals("ch-500", started.getStringExtra(ShinigamiReaderActivity.EXTRA_CHAPTER_ID));
        }
    }

    @Test public void ketukanBerulangCepatHanyaMembukaSatuChapter() throws Exception {
        try (ActivityController<Reader> controller =
                     Robolectric.buildActivity(Reader.class, intent("ch-25", 25d)).setup()) {
            Reader reader = controller.get();
            await(reader);

            View next = tagged(reader, "shinigami_reader_next");
            next.performClick();
            next.performClick();
            next.performClick();
            await(reader);

            // Tiga ketukan, satu perpindahan: ketukan kedua dan ketiga diabaikan.
            ShadowActivity shadow = Shadows.shadowOf(reader);
            assertNotNull(shadow.getNextStartedActivity());
            assertNull("tidak boleh ada perpindahan kedua", shadow.getNextStartedActivity());
        }
    }

    @Test public void offlineTanpaUnduhanPanahDinonaktifkan() throws Exception {
        online = false;
        try (ActivityController<Reader> controller =
                     Robolectric.buildActivity(Reader.class, intent("ch-25", 25d)).setup()) {
            Reader reader = controller.get();
            await(reader);

            assertFalse("tidak tahu chapter mana yang tersedia offline",
                    tagged(reader, "shinigami_reader_prev").isEnabled());
            assertFalse(tagged(reader, "shinigami_reader_next").isEnabled());
        }
    }

    @Test public void navigasiTersediaDiLayarSempitDenganAreaSentuh48dp() throws Exception {
        try (ActivityController<Reader> controller =
                     Robolectric.buildActivity(Reader.class, intent("ch-25", 25d)).setup()) {
            Reader reader = controller.get();
            await(reader);
            View nav = tagged(reader, "shinigami_reader_nav");
            int minimum = (int) (48 * reader.getResources().getDisplayMetrics().density);
            assertTrue("area sentuh kiri >= 48dp",
                    tagged(reader, "shinigami_reader_prev").getLayoutParams().width >= minimum);
            assertTrue("area sentuh kanan >= 48dp",
                    tagged(reader, "shinigami_reader_next").getLayoutParams().width >= minimum);
            assertEquals(View.VISIBLE, nav.getVisibility());
        }
    }

    /**
     * Satu ketukan pada gambar menyalakan dan mematikan layar penuh, dan
     * mengembalikan bilah aplikasi saat keluar.
     *
     * <p>Ketukan dikirim sebagai {@link android.view.MotionEvent} sungguhan
     * lewat {@code onInterceptTouchEvent} — jalur yang sama dengan sentuhan
     * pengguna — lalu jendela konfirmasi dilewati dengan
     * {@code settleTaps()}, padanan {@code onSingleTapConfirmed()}.
     */
    @Test public void satuKetukanPadaGambarMasukDanKeluarLayarPenuh() throws Exception {
        try (ActivityController<Reader> controller =
                     Robolectric.buildActivity(Reader.class, intent("ch-25", 25d)).setup()) {
            Reader reader = controller.get();
            await(reader);

            View nav = tagged(reader, "shinigami_reader_nav");
            View bar = (View) tagged(reader, "shinigami_reader_fullscreen").getParent();
            assertEquals("mulai dari keadaan normal", View.VISIBLE, bar.getVisibility());

            ShinigamiPageView page = new ShinigamiPageView(reader);
            page.setZoomRequest(new ShinigamiPageView.ZoomRequest() {
                @Override public void onZoom(int factor, float focusY) {
                    throw new AssertionError("zoom tidak boleh jalan dari satu ketukan");
                }

                @Override public void onSingleTap(float focusY) {
                    tagged(reader, "shinigami_reader_fullscreen").performClick();
                }
            });

            tapAt(page, 40f, 400f);
            page.settleTaps();
            assertEquals("satu ketukan menyembunyikan bilah", View.GONE, bar.getVisibility());
            assertEquals("satu ketukan menyembunyikan panah chapter", View.GONE, nav.getVisibility());

            tapAt(page, 40f, 400f);
            page.settleTaps();
            assertEquals("ketukan berikutnya memunculkan bilah lagi",
                    View.VISIBLE, bar.getVisibility());
            assertEquals(View.VISIBLE, nav.getVisibility());
        }
    }

    /**
     * Rangkaian dua ketukan <b>tidak</b> boleh mengubah layar penuh.
     *
     * <p>Inilah permintaan "jangan langsung dijalankan pada tap pertama": aksi
     * ketukan tunggal menunggu jendela konfirmasi, jadi ketukan kedua
     * membatalkannya dan hanya membuka zoom.
     */
    @Test public void duaKetukanTidakMengubahLayarPenuh() throws Exception {
        try (ActivityController<Reader> controller =
                     Robolectric.buildActivity(Reader.class, intent("ch-25", 25d)).setup()) {
            Reader reader = controller.get();
            await(reader);
            View bar = (View) tagged(reader, "shinigami_reader_fullscreen").getParent();

            ShinigamiPageView page = new ShinigamiPageView(reader);
            final int[] zoom = {0};
            final int[] toggled = {0};
            page.setZoomRequest(new ShinigamiPageView.ZoomRequest() {
                @Override public void onZoom(int factor, float focusY) {
                    zoom[0] = factor;
                }

                @Override public void onSingleTap(float focusY) {
                    toggled[0]++;
                }
            });

            tapAt(page, 40f, 400f);
            tapAt(page, 40f, 400f);
            page.settleTaps();

            assertEquals("dua ketukan membuka zoom 2x", 2, zoom[0]);
            assertEquals("dua ketukan tidak mengubah layar penuh", 0, toggled[0]);
            assertEquals("bilah tetap seperti semula", View.VISIBLE, bar.getVisibility());
        }
    }

    /** Tiga ketukan: zoom 3x, layar penuh tidak tersentuh. */
    @Test public void tigaKetukanTidakMengubahLayarPenuh() throws Exception {
        try (ActivityController<Reader> controller =
                     Robolectric.buildActivity(Reader.class, intent("ch-25", 25d)).setup()) {
            Reader reader = controller.get();
            await(reader);

            ShinigamiPageView page = new ShinigamiPageView(reader);
            final int[] zoom = {0};
            final int[] toggled = {0};
            page.setZoomRequest(new ShinigamiPageView.ZoomRequest() {
                @Override public void onZoom(int factor, float focusY) {
                    zoom[0] = factor;
                }

                @Override public void onSingleTap(float focusY) {
                    toggled[0]++;
                }
            });

            tapAt(page, 40f, 400f);
            tapAt(page, 40f, 400f);
            tapAt(page, 40f, 400f);
            page.settleTaps();

            assertEquals("tiga ketukan membuka zoom 3x", 3, zoom[0]);
            assertEquals("tiga ketukan tidak mengubah layar penuh", 0, toggled[0]);
        }
    }

    /**
     * Menggulir atau menggeser gambar tidak pernah mengubah layar penuh,
     * walaupun ada ketukan di ujungnya.
     */
    @Test public void geserTidakMengubahLayarPenuh() throws Exception {
        try (ActivityController<Reader> controller =
                     Robolectric.buildActivity(Reader.class, intent("ch-25", 25d)).setup()) {
            Reader reader = controller.get();
            await(reader);

            ShinigamiPageView page = new ShinigamiPageView(reader);
            final int[] toggled = {0};
            page.setZoomRequest(new ShinigamiPageView.ZoomRequest() {
                @Override public void onZoom(int factor, float focusY) {
                }

                @Override public void onSingleTap(float focusY) {
                    toggled[0]++;
                }
            });

            // Jari turun, bergeser jauh (menggulir), lalu lepas.
            long now = android.os.SystemClock.uptimeMillis();
            page.onInterceptTouchEvent(android.view.MotionEvent.obtain(
                    now, now, android.view.MotionEvent.ACTION_DOWN, 40f, 600f, 0));
            page.onInterceptTouchEvent(android.view.MotionEvent.obtain(
                    now, now + 40, android.view.MotionEvent.ACTION_MOVE, 40f, 300f, 0));
            page.onInterceptTouchEvent(android.view.MotionEvent.obtain(
                    now, now + 80, android.view.MotionEvent.ACTION_UP, 40f, 300f, 0));
            page.settleTaps();

            assertEquals("geser bukan ketukan", 0, toggled[0]);
        }
    }

    /** Ketukan pada tombol toolbar tidak boleh ikut mengubah layar penuh. */
    @Test public void tombolToolbarTidakMemicuKetukanGambar() throws Exception {
        try (ActivityController<Reader> controller =
                     Robolectric.buildActivity(Reader.class, intent("ch-25", 25d)).setup()) {
            Reader reader = controller.get();
            await(reader);

            // Tombol lain di toolbar ditekan: mode tidak berubah.
            View bar = (View) tagged(reader, "shinigami_reader_fullscreen").getParent();
            tagged(reader, "shinigami_reader_more").performClick();
            assertEquals("menekan tombol toolbar tidak mengubah mode",
                    View.VISIBLE, bar.getVisibility());
        }
    }

    /**
     * Beralih mode tidak boleh menggeser posisi baca.
     *
     * <p>Bilah atas dan bar navigasi memakai ruang tata letaknya sendiri di
     * luar daftar halaman, jadi menyembunyikannya tidak memindahkan satu pun
     * halaman. Tes ini mengunci sifat itu: daftar tetap pada posisi yang sama
     * sebelum dan sesudah mode berubah, dan tidak ada adapter yang dibuat ulang.
     */
    @Test public void beralihModeTidakMenggeserPosisiBaca() throws Exception {
        try (ActivityController<Reader> controller =
                     Robolectric.buildActivity(Reader.class, intent("ch-25", 25d)).setup()) {
            Reader reader = controller.get();
            await(reader);

            androidx.recyclerview.widget.RecyclerView list =
                    (androidx.recyclerview.widget.RecyclerView)
                            tagged(reader, "shinigami_reader_list");
            androidx.recyclerview.widget.RecyclerView.Adapter<?> before = list.getAdapter();
            androidx.recyclerview.widget.LinearLayoutManager manager =
                    (androidx.recyclerview.widget.LinearLayoutManager)
                            list.getLayoutManager();
            int firstBefore = manager.findFirstVisibleItemPosition();
            int heightBefore = list.getHeight();

            tagged(reader, "shinigami_reader_fullscreen").performClick();
            org.robolectric.shadows.ShadowLooper.idleMainLooper();

            assertSame("daftar tidak boleh dibuat ulang", before, list.getAdapter());
            assertSame("LayoutManager tidak boleh dibuat ulang", manager, list.getLayoutManager());
            assertEquals("halaman pertama yang terlihat tidak berpindah",
                    firstBefore, manager.findFirstVisibleItemPosition());
            assertTrue("area baca bertambah tinggi saat bilah disembunyikan",
                    list.getHeight() > heightBefore);

            tagged(reader, "shinigami_reader_fullscreen").performClick();
            org.robolectric.shadows.ShadowLooper.idleMainLooper();

            assertSame("keluar mode juga tidak membuat ulang daftar", before, list.getAdapter());
            assertSame(manager, list.getLayoutManager());
            assertEquals("kembali normal pada halaman yang sama",
                    firstBefore, manager.findFirstVisibleItemPosition());
            assertEquals("tinggi kembali seperti semula", heightBefore, list.getHeight());
        }
    }

    /** Ketukan pada gambar dalam satu titik, seperti sentuhan sungguhan. */
    private static void tapAt(ShinigamiPageView page, float x, float y) {
        long now = android.os.SystemClock.uptimeMillis();
        page.onInterceptTouchEvent(android.view.MotionEvent.obtain(
                now, now, android.view.MotionEvent.ACTION_DOWN, x, y, 0));
        page.onInterceptTouchEvent(android.view.MotionEvent.obtain(
                now, now + 20, android.view.MotionEvent.ACTION_UP, x, y, 0));
    }

    @Test public void areaKetukLayarPenuhTidakLagiAdaKarenaGambarSendiriYangJadiTombol() throws Exception {
        try (ActivityController<Reader> controller =
                     Robolectric.buildActivity(Reader.class, intent("ch-25", 25d)).setup()) {
            Reader reader = controller.get();
            await(reader);
            assertEquals("tidak ada lagi area 56dp yang merebut ketukan",
                    null, viewOrNull(reader, "shinigami_reader_tap_strip"));
        }
    }
}
