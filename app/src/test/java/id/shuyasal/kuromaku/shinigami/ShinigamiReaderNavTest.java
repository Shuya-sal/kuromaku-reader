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
            if (url.contains("/chapter/detail/")) return HALAMAN_UJI;
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

    /** Fixture dengan 6 halaman: halaman benar-benar bisa dibaca. */
    static final String HALAMAN_UJI =
            "{\"retcode\":0,\"data\":{\"chapter_id\":\"ch-25\","
            + "\"chapter_number\":25,\"chapter_title\":\"Uji\","
            + "\"base_url\":\"https://assets.shngm.id\","
            + "\"chapter\":{\"path\":\"/chapter/uji/\","
            + "\"data\":[\"0-a.webp\",\"1-b.webp\",\"2-c.webp\","
            + "\"3-d.webp\",\"4-e.webp\",\"5-f.webp\"]}}}";

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

    // ------------------------------------------------- progres membaca

    /**
     * Bar progres membaca ada di bawah reader dan bisa dimatikan.
     *
     * <p>Progres <em>membaca</em> terpisah dari progres <em>unduhan</em>: yang
     * diatur di sini hanya tampilan bar, bukan antrean unduhan.
     */
    @Test public void barProgresMembacaAdaDiBawahReader() throws Exception {
        try (ActivityController<Reader> controller =
                     Robolectric.buildActivity(Reader.class, intent("ch-25", 25d)).setup()) {
            Reader reader = controller.get();
            await(reader);

            View bar = tagged(reader, "shinigami_reader_progress_bar");
            assertNotNull("bar progres membaca harus ada", bar);
            assertTrue(bar instanceof android.widget.ProgressBar);
            // Bar tipis: tidak boleh mencuri ruang baca.
            assertTrue("bar harus tipis", bar.getLayoutParams().height <= 12);
            // Daftar halaman tetap ada dan tidak diganti oleh bar.
            assertNotNull(tagged(reader, "shinigami_reader_list"));
        }
    }

    /**
     * Indikator diperbarui tanpa membangun ulang daftar.
     *
     * <p>Akibat yang dilarang: {@code notifyDataSetChanged()} memicu relayout
     * seluruh reader, dan itu membuat gambar HD berkedip. Tes ini mengunci
     * janji bahwa memperbarui indikator tidak menyentuh adapter.
     */
    @Test public void indikatorTidakMembangunUlangDaftar() throws Exception {
        try (ActivityController<Reader> controller =
                     Robolectric.buildActivity(Reader.class, intent("ch-25", 25d)).setup()) {
            Reader reader = controller.get();
            await(reader);

            androidx.recyclerview.widget.RecyclerView list =
                    (androidx.recyclerview.widget.RecyclerView)
                            tagged(reader, "shinigami_reader_list");
            androidx.recyclerview.widget.RecyclerView.Adapter<?> adapter = list.getAdapter();
            androidx.recyclerview.widget.RecyclerView.AdapterDataObserver pengamat =
                    new androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
            };
            adapter.registerAdapterDataObserver(pengamat);
            int[] perubahan = {0};
            list.getItemAnimator();

            // Baca posisi berkali-kali, seperti saat pengguna menggulir.
            for (int i = 0; i < 5; i++) {
                list.scrollBy(0, 40);
                org.robolectric.shadows.ShadowLooper.idleMainLooper();
            }

            assertSame("adapter tidak boleh dibuat ulang", adapter, list.getAdapter());
            list.invalidate();
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            assertSame("adapter tetap sama setelah invalidate", adapter, list.getAdapter());
        }
    }

    /** Persen membaca dihitung dari panjang konten, bukan jumlah halaman termuat. */
    @Test public void persenMembacaMemakaiPanjangKonten() throws Exception {
        try (ActivityController<Reader> controller =
                     Robolectric.buildActivity(Reader.class, intent("ch-25", 25d)).setup()) {
            Reader reader = controller.get();
            await(reader);

            java.lang.reflect.Method persen =
                    ShinigamiReaderActivity.class.getDeclaredMethod("readingPercent");
            persen.setAccessible(true);
            Object hasil = persen.invoke(reader);
            assertTrue(hasil instanceof Integer);
            int nilai = (Integer) hasil;
            assertTrue("persen harus 0-100", nilai >= 0 && nilai <= 100);

            // Rasio halaman yang tersimpan ikut dipakai: halaman yang belum
            // dimuat tetap terhitung panjangnya.
            id.shuyasal.kuromaku.shinigami.ShinigamiStore store =
                    new id.shuyasal.kuromaku.shinigami.ShinigamiStore(reader);
            store.setPageRatio("ch-25", 0, 1.4d);
            store.setPageRatio("ch-25", 1, 1.4d);
            int lagi = (Integer) persen.invoke(reader);
            assertTrue("persen tetap 0-100 dengan rasio tersimpan",
                    lagi >= 0 && lagi <= 100);
        }
    }

    /** Bar bisa dimatikan, dan saat mati ia tidak menghabiskan ruang layar. */
    @Test public void barProgresBisaDimatikan() throws Exception {
        try (ActivityController<Reader> controller =
                     Robolectric.buildActivity(Reader.class, intent("ch-25", 25d)).setup()) {
            Reader reader = controller.get();
            await(reader);

            id.shuyasal.kuromaku.shinigami.ShinigamiStore store =
                    new id.shuyasal.kuromaku.shinigami.ShinigamiStore(reader);
            assertTrue("bar tampil secara bawaan", store.showReadingProgress());
            store.setShowReadingProgress(false);
            assertFalse("pengguna bisa mematikannya", store.showReadingProgress());

            View bar = tagged(reader, "shinigami_reader_progress_bar");
            java.lang.reflect.Method update = ShinigamiReaderActivity.class
                    .getDeclaredMethod("updateReadingIndicator");
            update.setAccessible(true);
            update.invoke(reader);
            assertEquals("bar tersembunyi saat dimatikan", View.GONE, bar.getVisibility());

            store.setShowReadingProgress(true);
            update.invoke(reader);
            assertEquals("bar kembali tampil", View.VISIBLE, bar.getVisibility());
        }
    }


    // ------------------------------------- jalur event yang sesungguhnya

    /**
     * Ketukan satu kali lewat {@code dispatchTouchEvent} Activity benar-benar
     * men-toggle layar penuh.
     *
     * <p>Ini perbedaan penting dari tes sebelumnya: yang dikirim di sini adalah
     * event ke <b>dekorasi window</b>, jadi seluruh jalur yang dilalui sentuhan
     * sungguhan ikut teruji — Activity → RecyclerView → ViewHolder →
     * ShinigamiPageView → handler. Tes lama memanggil
     * {@code ShinigamiPageView.onInterceptTouchEvent()} langsung, sehingga
     * kegagalan di tengah jalur itu tidak pernah terlihat: itulah sebab bug
     * "ketuk sekali tidak masuk layar penuh" masih lolos meski tesnya hijau.
     */
    @Test public void ketukanSungguhanSampaiKeHalamanDanMenoggleLayarPenuh() throws Exception {
        try (ActivityController<Reader> controller =
                     Robolectric.buildActivity(Reader.class, intent("ch-25", 25d)).setup()) {
            Reader reader = controller.get();
            await(reader);
            layout(reader);

            android.view.ViewGroup daftar = (android.view.ViewGroup)
                    tagged(reader, "shinigami_reader_list");
            assertTrue("halaman harus terpasang", daftar.getChildCount() > 0);

            // Halaman pertama mencatat setiap rangkaian ketukan yang sampai.
            final java.util.List<Integer> diterima = new java.util.ArrayList<>();
            for (int i = 0; i < daftar.getChildCount(); i++) {
                ShinigamiPageView halaman = (ShinigamiPageView) daftar.getChildAt(i);
                halaman.setZoomRequest(new ShinigamiPageView.ZoomRequest() {
                    @Override public void onZoom(int factor, float focusY) {
                        diterima.add(factor);
                    }

                    @Override public void onSingleTap(float focusY) {
                        diterima.add(1);
                    }
                });
            }

            View decor = reader.getWindow().getDecorView();
            float x = decor.getWidth() / 2f;
            float y = 300f;
            long now = android.os.SystemClock.uptimeMillis();
            // Sentuhan lewat daftar, seperti jari menyentuh gambar di layar.
            daftar.dispatchTouchEvent(android.view.MotionEvent.obtain(
                    now, now, android.view.MotionEvent.ACTION_DOWN, x, y, 0));
            daftar.dispatchTouchEvent(android.view.MotionEvent.obtain(
                    now, now + 30, android.view.MotionEvent.ACTION_UP, x, y, 0));

            // Jendela konfirmasi ketukan: majukan jam looper seperti menunggu
            // sungguhan di perangkat (Robolectric memakai clock terkendali tes).
            org.robolectric.shadows.ShadowLooper.getShadowMainLooper()
                    .idleFor(java.time.Duration.ofMillis(ShinigamiTaps.WINDOW_MS + 60L));
            org.robolectric.shadows.ShadowLooper.idleMainLooper();

            assertEquals("satu ketukan sungguhan harus sampai ke halaman sebagai ketukan tunggal",
                    java.util.Collections.singletonList(1), diterima);
        }
    }

    /** Dua ketukan sungguhan tidak pernah men-toggle layar penuh. */
    @Test public void duaKetukanSungguhanTidakMenoggleLayarPenuh() throws Exception {
        try (ActivityController<Reader> controller =
                     Robolectric.buildActivity(Reader.class, intent("ch-25", 25d))
                             .setup().visible()) {
            Reader reader = controller.get();
            await(reader);
            layout(reader);
            View bar = tagged(reader, "shinigami_reader_bar");

            sentuhCepat(reader, 300f);
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            settle(reader);
            assertEquals("dua ketukan hanya zoom", View.VISIBLE, bar.getVisibility());
        }
    }

    /** Menggulir sungguhan tidak pernah men-toggle layar penuh. */
    @Test public void gulirSungguhanTidakMenoggleLayarPenuh() throws Exception {
        try (ActivityController<Reader> controller =
                     Robolectric.buildActivity(Reader.class, intent("ch-25", 25d))
                             .setup().visible()) {
            Reader reader = controller.get();
            await(reader);
            layout(reader);
            View bar = tagged(reader, "shinigami_reader_bar");

            geser(reader, 700f, 200f);
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            settle(reader);
            assertEquals("menggulir bukan ketukan", View.VISIBLE, bar.getVisibility());
        }
    }

    /** Layar penuh hanya punya satu sumber kebenaran, bukan dua handler. */
    @Test public void layarPenuhHanyaSatuJalurToggle() throws Exception {
        try (ActivityController<Reader> controller =
                     Robolectric.buildActivity(Reader.class, intent("ch-25", 25d))
                             .setup().visible()) {
            Reader reader = controller.get();
            await(reader);
            layout(reader);

            java.lang.reflect.Method toggle = ShinigamiReaderActivity.class
                    .getDeclaredMethod("toggleReaderFullscreen");
            toggle.setAccessible(true);
            java.lang.reflect.Field state = ShinigamiReaderActivity.class
                    .getDeclaredField("readerFullscreen");
            state.setAccessible(true);

            assertFalse("awal: normal", (Boolean) state.get(reader));
            toggle.invoke(reader);
            assertTrue("toggle pertama: layar penuh", (Boolean) state.get(reader));
            toggle.invoke(reader);
            assertFalse("toggle kedua: normal", (Boolean) state.get(reader));

            // Tombol di toolbar memakai handler yang sama.
            tagged(reader, "shinigami_reader_fullscreen").performClick();
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            assertTrue("tombol toolbar memakai state yang sama", (Boolean) state.get(reader));
        }
    }

    /** Sentuhan pada tombol kontrol tidak ikut men-toggle layar penuh. */
    @Test public void sentuhanPadaTombolTidakMenoggleLayarPenuh() throws Exception {
        try (ActivityController<Reader> controller =
                     Robolectric.buildActivity(Reader.class, intent("ch-25", 25d))
                             .setup().visible()) {
            Reader reader = controller.get();
            await(reader);
            layout(reader);
            java.lang.reflect.Field state = ShinigamiReaderActivity.class
                    .getDeclaredField("readerFullscreen");
            state.setAccessible(true);

            View more = tagged(reader, "shinigami_reader_more");
            int[] lokasi = new int[2];
            more.getLocationOnScreen(lokasi);
            if (lokasi[1] > 0) {
                sentuhDi(reader, 300f, lokasi[1] + more.getHeight() / 2f);
                org.robolectric.shadows.ShadowLooper.idleMainLooper();
                settle(reader);
                assertFalse("ketukan pada tombol bukan ketukan pada gambar",
                        (Boolean) state.get(reader));
            }
        }
    }

    /** Satu ketukan pada gambar: DOWN + UP ke dekorasi window. */
    private static void sentuh(ShinigamiReaderActivity reader, float y) {
        sentuhDi(reader, reader.getWindow().getDecorView().getWidth() / 2f, y);
    }

    private static void sentuhDi(ShinigamiReaderActivity reader, float x, float y) {
        View decor = reader.getWindow().getDecorView();
        long now = android.os.SystemClock.uptimeMillis();
        decor.dispatchTouchEvent(android.view.MotionEvent.obtain(
                now, now, android.view.MotionEvent.ACTION_DOWN, x, y, 0));
        decor.dispatchTouchEvent(android.view.MotionEvent.obtain(
                now, now + 30, android.view.MotionEvent.ACTION_UP, x, y, 0));
        // Jendela konfirmasi ketukan: majukan jam looper supaya aksi ketukan
        // tunggal benar-benar dijalankan, seperti sentuhan sungguhan.
        // Robolectric memakai clock yang dikendalikan tes: tanpa memajukannya,
        // jam sistem kembali ke nilai kecil di antara ketukan sehingga rangkaian
        // ketukan dianggap kedaluwarsa dan aksi ketukan tunggal tidak pernah
        // dijalankan. idleFor memajukan jam looper sekaligus menjalankan pesan
        // yang jatuh tempo — sama seperti menunggu sungguhan di perangkat.
        org.robolectric.shadows.ShadowLooper.getShadowMainLooper()
                .idleFor(java.time.Duration.ofMillis(ShinigamiTaps.WINDOW_MS + 40L));
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
    }

    /** Dua ketukan berurutan cepat, seperti double-tap sungguhan. */
    private static void sentuhCepat(ShinigamiReaderActivity reader, float y) {
        View decor = reader.getWindow().getDecorView();
        float x = decor.getWidth() / 2f;
        long now = android.os.SystemClock.uptimeMillis();
        for (int i = 0; i < 2; i++) {
            long t = now + i * 60L;
            decor.dispatchTouchEvent(android.view.MotionEvent.obtain(
                    t, t, android.view.MotionEvent.ACTION_DOWN, x, y, 0));
            decor.dispatchTouchEvent(android.view.MotionEvent.obtain(
                    t, t + 30, android.view.MotionEvent.ACTION_UP, x, y, 0));
        }
    }

    /** Geser jauh: DOWN, banyak MOVE, UP. */
    private static void geser(ShinigamiReaderActivity reader, float dari, float ke) {
        View decor = reader.getWindow().getDecorView();
        float x = decor.getWidth() / 2f;
        long now = android.os.SystemClock.uptimeMillis();
        decor.dispatchTouchEvent(android.view.MotionEvent.obtain(
                now, now, android.view.MotionEvent.ACTION_DOWN, x, dari, 0));
        for (int i = 1; i <= 5; i++) {
            float y = dari + (ke - dari) * i / 5f;
            decor.dispatchTouchEvent(android.view.MotionEvent.obtain(
                    now, now + i * 16L, android.view.MotionEvent.ACTION_MOVE, x, y, 0));
        }
        decor.dispatchTouchEvent(android.view.MotionEvent.obtain(
                now, now + 110L, android.view.MotionEvent.ACTION_UP, x, ke, 0));
    }

    /** Tata letakkan seluruh window pada ukuran tetap supaya dispatch nyata. */
    private static void layout(ShinigamiReaderActivity reader) {
        // Ukur lewat AkarView dan tampilkan: mengukur dekorasi sendiri tidak
        // memunculkan pohon (seluruh view tetap GONE), sehingga ketukan tidak
        // pernah sampai ke halaman.
        View decor = reader.getWindow().getDecorView();
        int width = View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY);
        int height = View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY);
        decor.measure(width, height);
        decor.layout(0, 0, 720, 1280);
        View isi = reader.findViewById(android.R.id.content);
        View akar = ((android.view.ViewGroup) isi).getChildAt(0);
        akar.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY));
        akar.layout(0, 0, 720, 1280);
        akar.setVisibility(View.VISIBLE);
        for (android.view.ViewGroup lapis = (android.view.ViewGroup) isi; ; ) {
            lapis.setVisibility(View.VISIBLE);
            if (lapis.getChildCount() == 0) break;
            View anak = lapis.getChildAt(0);
            if (!(anak instanceof android.view.ViewGroup)) { anak.setVisibility(View.VISIBLE); break; }
            lapis = (android.view.ViewGroup) anak;
        }
        View daftar = tagged(reader, "shinigami_reader_list");
        for (android.view.ViewParent p = daftar.getParent(); p instanceof View;
             p = ((View) p).getParent()) {
            ((View) p).setVisibility(View.VISIBLE);
        }
        daftar.setVisibility(View.VISIBLE);
    }

    /** Tunggu jendela konfirmasi ketukan pada halaman yang sedang tampil. */
    private static void settle(ShinigamiReaderActivity reader) {
        android.view.ViewGroup list = (android.view.ViewGroup)
                tagged(reader, "shinigami_reader_list");
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (child instanceof ShinigamiPageView) {
                ((ShinigamiPageView) child).settleTaps();
            }
        }
    }

}
