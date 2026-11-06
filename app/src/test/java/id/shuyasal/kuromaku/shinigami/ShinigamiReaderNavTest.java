package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;

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

    @Test public void layarPenuhMenyembunyikanPanahDanKetukanBawahMemunculkannya() throws Exception {
        try (ActivityController<Reader> controller =
                     Robolectric.buildActivity(Reader.class, intent("ch-25", 25d)).setup()) {
            Reader reader = controller.get();
            await(reader);

            View nav = tagged(reader, "shinigami_reader_nav");
            View strip = tagged(reader, "shinigami_reader_tap_strip");
            assertEquals("strip layar penuh tidak aktif di mode normal", View.GONE, strip.getVisibility());

            tagged(reader, "shinigami_reader_fullscreen").performClick();
            assertEquals("panah ikut tersembunyi saat layar penuh", View.GONE, nav.getVisibility());
            assertEquals(View.VISIBLE, strip.getVisibility());

            strip.performClick();
            assertEquals("ketukan bawah memunculkan kontrol lagi", View.VISIBLE, nav.getVisibility());
            assertEquals(View.GONE, strip.getVisibility());
        }
    }
}
