package id.shuyasal.kuromaku.komiku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Pencarian judul Manga Server 3.
 *
 * <p>Dua hal yang pernah membuat pencarian tampak kosong:
 *
 * <ul>
 *   <li>Halaman {@code komiku.org/?s=...} tidak memuat hasil apa pun — hasilnya
 *       dimuat dari alamat lain. Parser yang membaca halaman itu selalu
 *       menemukan nol judul.</li>
 *   <li>Tanpa {@code post_type=manga}, alamat hasil mengembalikan daftar
 *       CHAPTER (judul "Untitled", tautan ke halaman chapter), bukan judul
 *       manga.</li>
 * </ul>
 *
 * <p>Tes ini memakai fragmen asli dari situs supaya bentuk kartunya benar-benar
 * yang diurai.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w411dp-h891dp-mdpi")
public class KomikuSearchSourceTest {

    private static String fixture(String name) throws Exception {
        try (InputStream stream = KomikuSearchSourceTest.class.getResourceAsStream(
                "/komiku/" + name)) {
            assertNotNull("fixture " + name, stream);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) > 0) out.write(buffer, 0, read);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    @Test public void alamatPencarianMemakaiPostTypeManga() {
        KomikuSource source = new KomikuSource(url -> {
            assertTrue("alamat menuju api.komiku.org", url.startsWith(
                    "https://api.komiku.org/?s="));
            assertTrue("alamat memakai post_type=manga", url.contains("post_type=manga"));
            assertEquals("kata kunci terkirim", true, url.contains("solo"));
            return "";
        });
        try {
            source.search("solo");
        } catch (KomikuSource.SourceException ignored) {
            // Yang diperiksa hanya bentuk alamatnya.
        }
    }

    @Test public void hasilPencarianMembacaSepuluhJudulAsli() throws Exception {
        final String fragment = fixture("api_search_solo.html");
        KomikuSource source = new KomikuSource(url -> fragment);
        List<KomikuManga> found = source.search("solo");

        assertEquals("sepuluh judul", 10, found.size());
        KomikuManga first = found.get(0);
        assertEquals("Solo Swordmaster", first.title);
        assertEquals("solo-swordmaster", first.id);
        assertTrue("alamat manga, bukan chapter",
                first.url.startsWith("https://komiku.org/manga/"));
        assertFalse("judul tidak 'Untitled'", first.title.equalsIgnoreCase("Untitled"));
    }

    @Test public void setiapHasilPunyaJudulDanCover() throws Exception {
        final String fragment = fixture("api_search_solo.html");
        KomikuSource source = new KomikuSource(url -> fragment);
        for (KomikuManga manga : source.search("solo")) {
            assertFalse("judul terisi: " + manga.id, manga.title.isEmpty());
            assertFalse("judul bukan Untitled: " + manga.id,
                    manga.title.equalsIgnoreCase("Untitled"));
            assertTrue("cover HTTPS: " + manga.id, manga.coverUrl.startsWith("https://"));
            assertFalse("tipe terbaca: " + manga.id, manga.type.isEmpty());
            assertFalse("waktu update terbaca: " + manga.id, manga.updated.isEmpty());
        }
    }

    @Test public void kataKunciTidakAdaMenghasilkanDaftarKosong() throws Exception {
        // Situs mengembalikan fragmen kosong untuk kata kunci yang tidak ada.
        KomikuSource source = new KomikuSource(url -> "<div class=\"bge\"></div>");
        assertTrue(source.search("zzzzzkatakuncianeh").isEmpty());
    }

    @Test public void judulHasilPencarianBukanBadgeTipe() throws Exception {
        // Tautan cover (div.bgei) tidak memuat judul, hanya badge tipe seperti
        // "Manhwa Aksi". Parser harus membaca heading div.kan, bukan isi tautan
        // cover — kalau tertukar, daftar hasil menampilkan "Manhwa Aksi"
        // sebagai judul.
        final String fragment = fixture("api_search_solo.html");
        KomikuSource source = new KomikuSource(url -> fragment);
        for (KomikuManga manga : source.search("solo")) {
            assertFalse("judul bukan badge tipe: " + manga.title,
                    manga.title.equals("Manhwa Aksi") || manga.title.startsWith("Manhwa "));
        }
    }

    @Test public void waktuUpdateDiambilDariParagraf() {
        assertEquals("6 jam lalu", KomikuSource.updateFrom("Update 6 jam lalu."));
        assertEquals("32 menit lalu", KomikuSource.updateFrom("Update 32 menit lalu."));
        assertEquals("", KomikuSource.updateFrom("Tidak ada keterangan."));
    }

    // ------------------------------------------------------------- beranda

    private Context context;

    @Before public void siapkan() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(KomikuStore.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        new KomikuCatalog(context).clear();
    }

    /**
     * Jalankan aksi lalu tunggu sampai daftar terisi.
     *
     * <p>Pencarian berjalan di thread latar, sedangkan memutar jam maya saja
     * tidak memberi kesempatan thread itu menyelesaikan pekerjaannya. Karena
     * itu ditunggu dengan waktu nyata, sambil tetap memutar looper agar hasil
     * yang diposkan ke thread utama ikut dijalankan.
     */
    /**
     * Jalankan aksi lalu tunggu sampai PENCARIAN selesai.
     *
     * <p>Umpan beranda terbit lebih dulu (40 judul), jadi berhenti begitu daftar
     * terisi akan menangkap umpan itu, bukan hasil pencarian. Yang ditunggu
     * adalah baris status milik pencarian itu sendiri.
     *
     * <p>Pencarian berjalan di thread latar, sedangkan memutar jam maya saja
     * tidak memberi kesempatan thread itu menyelesaikan pekerjaannya. Karena
     * itu ditunggu dengan waktu nyata, sambil tetap memutar looper agar hasil
     * yang diposkan ke thread utama ikut dijalankan.
     */
    private static void tungguPencarian(KomikuActivity activity, String query) {
        long deadline = System.currentTimeMillis() + 25000;
        while (System.currentTimeMillis() < deadline) {
            // Ketikan memakai debounce: tanpa memajukan jam maya, pencarian
            // yang tertunda tidak pernah menyala.
            org.robolectric.shadows.ShadowSystemClock.advanceBy(
                    java.time.Duration.ofMillis(100));
            ShadowLooper.idleMainLooper();
            String status = activity.statusUntukTes();
            if (status.contains("hasil untuk") || status.startsWith("Tidak ada hasil")
                    || status.startsWith("Pencarian gagal")) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Test public void hasilPencarianTampilSebagaiBarisDenganChapterDanWaktu() throws Exception {
        // Katalog tersimpan berisi judul "Solo ..." dengan chapter dan waktu.
        List<KomikuManga> items = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            String slug = "solo-judul-" + i;
            items.add(new KomikuManga(slug, "Solo Judul " + i,
                    KomikuConfig.BASE + "/manga/" + slug + "/",
                    "https://thumbnail.komiku.org/" + slug + ".jpg",
                    "Manhwa", "Chapter " + (10 + i), i + " jam lalu"));
        }
        new KomikuCatalog(context).save(items, 1);

        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            KomikuActivity activity = controller.get();
            activity.ketikUntukTes("Solo Judul");
            tungguPencarian(activity, "Solo Judul");

            assertEquals("status=" + activity.statusUntukTes(), 8,
                    activity.jumlahItemUntukTes());
            assertTrue("memakai daftar baris", activity.modeBarisUntukTes());
            assertEquals("status menyebut jumlah hasil", true,
                    activity.statusUntukTes().contains("hasil"));
        }
    }

    /**
     * Uji jaringan sungguhan (opt-in): memastikan alamat pencarian yang dipakai
     * benar-benar hidup dan mengembalikan judul manga, bukan daftar chapter.
     */
    @Test public void jaringanSungguhanMencariSolo() throws Exception {
        org.junit.Assume.assumeTrue("jalankan dengan KUROMAKU_NETWORK_TEST=1",
                "1".equals(System.getenv("KUROMAKU_NETWORK_TEST")));
        java.util.List<KomikuManga> found = new KomikuSource().search("solo");
        assertTrue("ada hasil dari situs", found.size() >= 5);
        boolean adaSoloLeveling = false;
        for (KomikuManga manga : found) {
            assertFalse("judul terisi: " + manga.id, manga.title.isEmpty());
            assertFalse("judul bukan Untitled: " + manga.id,
                    manga.title.equalsIgnoreCase("Untitled"));
            assertFalse("judul bukan badge tipe: " + manga.title,
                    manga.title.startsWith("Manhwa ") || manga.title.startsWith("Manga "));
            assertTrue("tautan manga, bukan chapter: " + manga.url,
                    manga.url.startsWith("https://komiku.org/manga/"));
            if (manga.title.toLowerCase(java.util.Locale.ROOT).contains("solo leveling")) {
                adaSoloLeveling = true;
            }
        }
        assertTrue("Solo Leveling ditemukan", adaSoloLeveling);
    }
}
