package id.shuyasal.kuromaku.anime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;

import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.Prefs;

/**
 * Halaman home Anime Otakudesu: tema, genre, dan area atas yang menyusut.
 *
 * <p>Tanpa jaringan, daftar anime tidak bisa dimuat; pengujian karena itu
 * berfokus pada perilaku UI yang tidak bergantung pada sumber: chip genre,
 * status filter genre, pergantian tema di tempat, dan penyusutan area atas.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class OtakudesuHomeTest {

    private static OtakudesuSource nextSource;

    /** Activity asli dengan sumber offline; Robolectric butuh konstruktor tanpa argumen. */
    public static final class Host extends OtakudesuActivity {
        @Override protected OtakudesuSource createSource() {
            return nextSource;
        }
    }

    @Before public void setUp() {
        // Kembali ke tema terang supaya hasil tidak bergantung tema sistem.
        new Prefs(ApplicationProvider.getApplicationContext()).setTheme(Prefs.THEME_LIGHT);
        nextSource = new OtakudesuSource(url -> {
            if (url.contains("/genre-list/")) return fixture("genre_list.html");
            if (url.contains("/genres/action/")) return fixture("genre_action.html");
            if (url.contains("/genres/")) return fixture("genre_action.html");
            return fixture("ongoing.html");
        });
    }

    /**
     * Kembalikan tema global ke "ikut sistem".
     *
     * <p>{@code Prefs.setTheme} menulis preferensi bersama dan memanggil
     * {@code AppCompatDelegate.setDefaultNightMode} yang berlaku untuk seluruh
     * proses. Tanpa pemulihan, tes lain di suite yang sama bisa berjalan dalam
     * tema gelap dan gagal pada pemeriksaan warna.
     */
    @org.junit.After public void tearDown() {
        new Prefs(ApplicationProvider.getApplicationContext()).setTheme(Prefs.THEME_SYSTEM);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    private static String fixture(String name) {
        try (java.io.InputStream in = OtakudesuHomeTest.class.getResourceAsStream(
                "/otakudesu/" + name)) {
            if (in == null) throw new IllegalStateException("fixture: " + name);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException error) {
            throw new IllegalStateException(error);
        }
    }

    private ActivityController<Host> open() {
        return Robolectric.buildActivity(Host.class).setup();
    }

    /**
     * Tunggu sampai katalog termuat.
     *
     * <p>Pemuatan berjalan di executor activity; di suite panjang, keadaan global
     * (tema) dari tes lain bisa memperlambat satu putaran. Menunggu secara
     * terbatas lebih jujur daripada menebak bahwa satu putaran selalu cukup.
     */
    /**
     * Tunggu katalog, termasuk bila activity dibuat ulang di tengah jalan.
     *
     * <p>Perubahan tema global bisa memicu {@code recreate()}; instance lama
     * menjadi {@code finishing} dan tidak akan pernah terisi. Mengambil instance
     * terbaru dari controller membuat pengujian tidak bergantung pada urutan
     * kelas tes lain dalam suite.
     */
    private static Host settle(ActivityController<Host> controller) throws Exception {
        Host activity = controller.get();
        for (int i = 0; i < 3; i++) {
            awaitLoaded(activity);
            if (!items(activity).isEmpty()) return activity;
            ShadowLooper.idleMainLooper();
            Host terbaru = controller.get();
            if (terbaru != null && !terbaru.isFinishing()) {
                activity = terbaru;
                awaitLoaded(activity);
                if (!items(activity).isEmpty()) return activity;
            }
            Thread.sleep(120);
        }
        return activity;
    }

    private static void awaitLoaded(Host activity) throws Exception {
        // Batas dibuat longgar: di suite panjang, executor bisa sedang sibuk
        // menjalankan pekerjaan dari kelas tes lain.
        for (int i = 0; i < 200 && items(activity).isEmpty(); i++) {
            await(activity);
            Thread.sleep(15);
        }
        await(activity);
    }

    /** Tunggu pekerjaan latar belakang activity selesai, lalu jalankan main looper. */
    private static void await(OtakudesuActivity activity) throws Exception {
        java.util.concurrent.ExecutorService worker =
                (java.util.concurrent.ExecutorService) field(activity, "worker");
        worker.submit(() -> { }).get(10, java.util.concurrent.TimeUnit.SECONDS);
        ShadowLooper.idleMainLooper();
    }

    private static View tag(OtakudesuActivity activity, String tag) {
        return activity.getWindow().getDecorView().findViewWithTag(tag);
    }

    // ------------------------------------------------------------- genre

    @Test public void adaChipGenreDanMenuTitikTiga() {
        try (ActivityController<Host> controller = open()) {
            Host activity = controller.get();
            assertNotNull("chip Genre harus ada di baris filter",
                    tag(activity, "otakudesu_chip_Genre"));
            assertNotNull("menu titik tiga harus ada",
                    tag(activity, "otakudesu_overflow"));
            assertEquals("Genre", ((TextView) tag(activity, "otakudesu_chip_Genre"))
                    .getText().toString());
        }
    }

    @Test public void memilihGenreMemuatDaftarSesuaiGenre() throws Exception {
        try (ActivityController<Host> controller = open()) {
            Host activity = settle(controller);
            int sebelum = items(activity).size();
            assertTrue("katalog awal terisi", sebelum > 0);

            activity.selectGenre(new OtakudesuSource.Genre("action", "Action"));
            awaitLoaded(activity);
            await(activity);

            TextView chip = (TextView) tag(activity, "otakudesu_chip_Genre");
            assertEquals("Genre: Action", chip.getText().toString());
            assertTrue("chip genre ditandai aktif", chip.isSelected());
            assertTrue("daftar genre terisi", items(activity).size() > 0);
            // Halaman genre memakai markup berbeda dari katalog; kalau urai salah,
            // daftar akan kosong atau berisi item tanpa poster.
            OtakudesuAnime first = items(activity).get(0);
            assertFalse("judul terisi", first.title.isEmpty());
            assertFalse("poster terisi", first.posterUrl.isEmpty());
            assertFalse("Activity tidak tertutup", activity.isFinishing());
        }
    }

    @Test public void meresetGenreKembaliKeSemuaAnime() throws Exception {
        try (ActivityController<Host> controller = open()) {
            Host activity = controller.get();
            await(activity);
            activity.selectGenre(new OtakudesuSource.Genre("action", "Action"));
            await(activity);
            activity.clearGenre();
            await(activity);

            TextView chip = (TextView) tag(activity, "otakudesu_chip_Genre");
            assertEquals("Genre", chip.getText().toString());
            assertFalse("chip genre tidak aktif lagi", chip.isSelected());
        }
    }

    @Test public void genreTanpaPilihanTidakMengubahApaPun() {
        try (ActivityController<Host> controller = open()) {
            Host activity = controller.get();
            activity.selectGenre(null);
            ShadowLooper.idleMainLooper();
            assertEquals("Genre", ((TextView) tag(activity, "otakudesu_chip_Genre"))
                    .getText().toString());
        }
    }

    // ----------------------------------------------------------- toolbar

    @Test public void toolbarBerandaTanpaPanahKembali() {
        try (ActivityController<Host> controller = open()) {
            Host activity = controller.get();
            assertNull("panah kembali tidak boleh ada di beranda",
                    tag(activity, "otakudesu_back"));
            assertNotNull("hamburger tetap ada", tag(activity, "otakudesu_menu"));
            assertNotNull("tombol Unduhan tetap ada", tag(activity, "otakudesu_downloads"));
            assertNotNull("menu kanan tetap ada", tag(activity, "otakudesu_overflow"));
        }
    }

    @Test public void backDariBerandaMenujuSourceHub() {
        try (ActivityController<Host> controller = open()) {
            Host activity = controller.get();
            activity.onBackPressed();
            android.content.Intent next =
                    org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull("harus membuka layar lain", next);
            assertEquals("menuju Source Hub, bukan Novel",
                    id.shuyasal.kuromaku.SourceHubActivity.class.getName(),
                    next.getComponent().getClassName());
            assertTrue("beranda ditutup", activity.isFinishing());
        }
    }

    // -------------------------------------------------------------- tema

    @Test public void menggantiTemaTidakMemuatUlangDaftar() throws Exception {
        try (ActivityController<Host> controller = open()) {
            Host activity = controller.get();
            awaitLoaded(activity);
            List<String> sebelumId = new ArrayList<>();
            for (OtakudesuAnime anime : items(activity)) sebelumId.add(anime.animeId);
            assertTrue("katalog terisi lebih dulu", !sebelumId.isEmpty());

            Palette before = (Palette) field(activity, "palette");
            new Prefs(activity).setTheme(Prefs.THEME_DARK);
            invoke(activity, "applyThemeInPlace");
            ShadowLooper.idleMainLooper();

            Palette after = (Palette) field(activity, "palette");
            assertTrue("palet harus berubah", before.background != after.background);

            List<String> sesudahId = new ArrayList<>();
            for (OtakudesuAnime anime : items(activity)) sesudahId.add(anime.animeId);
            assertEquals("daftar anime yang sama tetap ada", sebelumId, sesudahId);
        }
    }

    @Test public void temaTersimpanDanDibacaUlang() {
        try (ActivityController<Host> controller = open()) {
            Host activity = controller.get();
            new Prefs(activity).setTheme(Prefs.THEME_LIGHT);
            assertEquals(Prefs.THEME_LIGHT, new Prefs(activity).theme());
            new Prefs(activity).setTheme(Prefs.THEME_DARK);
            assertEquals(Prefs.THEME_DARK, new Prefs(activity).theme());
            new Prefs(activity).setTheme(Prefs.THEME_SYSTEM);
            assertEquals(Prefs.THEME_SYSTEM, new Prefs(activity).theme());
        }
    }

    // ------------------------------------------------- area atas menyusut

    @Test public void areaAtasMenyusutDanMunculLagi() throws Exception {
        try (ActivityController<Host> controller = open()) {
            Host activity = controller.get();
            View top = tag(activity, "otakudesu_top_section");
            assertNotNull("wadah area atas harus ada", top);
            assertEquals("area atas tampil saat pertama dibuka", View.VISIBLE,
                    top.getVisibility());

            invoke(activity, "hideTopSection");
            ShadowLooper.idleMainLooper();
            assertTrue("area atas ditandai tersembunyi", (Boolean) field(activity, "topHidden"));

            invoke(activity, "showTopSection");
            ShadowLooper.idleMainLooper();
            assertFalse("area atas ditandai tampil lagi",
                    (Boolean) field(activity, "topHidden"));
            assertEquals("area atas benar-benar terlihat", View.VISIBLE,
                    top.getVisibility());
        }
    }

    @Test public void areaAtasBerisiPencarianDanFilter() {
        try (ActivityController<Host> controller = open()) {
            Host activity = controller.get();
            LinearLayout top = (LinearLayout) tag(activity, "otakudesu_top_section");
            assertNotNull("kolom pencarian ikut dalam area atas",
                    tag(activity, "otakudesu_search"));
            assertNotNull("filter status ikut dalam area atas",
                    tag(activity, "otakudesu_chip_Ongoing"));
            assertTrue("area atas berisi beberapa bagian", top.getChildCount() >= 2);
        }
    }

    // ------------------------------------------------------------ perkakas

    @SuppressWarnings("unchecked")
    private static List<OtakudesuAnime> items(OtakudesuActivity activity) throws Exception {
        return (List<OtakudesuAnime>) field(activity, "items");
    }

    private static Object field(Object target, String name) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field f = type.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void invoke(Object target, String name, Object... args) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(name)) continue;
                if (method.getParameterTypes().length != args.length) continue;
                method.setAccessible(true);
                method.invoke(target, args);
                return;
            }
            type = type.getSuperclass();
        }
        throw new NoSuchMethodException(name);
    }
}