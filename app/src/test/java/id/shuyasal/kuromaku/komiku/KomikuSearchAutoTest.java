package id.shuyasal.kuromaku.komiku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Menu dan pencarian Manga Server 3 tanpa campur tangan pengguna.
 *
 * <p>Yang dijaga: tidak ada lagi perintah "perbarui katalog" di menu, katalog
 * berjalan sendiri, dan mengetik di kolom pencarian langsung mencari tanpa
 * menekan apa pun.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w411dp-h891dp-mdpi")
public class KomikuSearchAutoTest {

    private Context context;

    @Before public void siapkan() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(KomikuStore.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        new KomikuCatalog(context).clear();
    }

    private void simpanKatalog(int jumlah) throws Exception {
        List<KomikuManga> items = new ArrayList<>();
        for (int i = 1; i <= jumlah; i++) {
            String slug = "judul-otomatis-" + i;
            items.add(new KomikuManga(slug, "Judul Otomatis " + i,
                    KomikuConfig.BASE + "/manga/" + slug + "/", "", "Manga",
                    "Chapter " + i, i + " jam lalu"));
        }
        new KomikuCatalog(context).save(items, 1);
    }

    private static void tunggu(Runnable aksi) {
        aksi.run();
        for (int i = 0; i < 100; i++) {
            ShadowLooper.idleMainLooper();
            try {
                Thread.sleep(20);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            ShadowLooper.idleMainLooper();
        }
    }

    @Test public void menuTidakLagiMemuatPerintahKatalog() {
        String[] opsi = KomikuActivity.opsiMenuUntukTes();
        for (String opsi1 : opsi) {
            assertFalse("menu tidak memuat perintah katalog: " + opsi1,
                    opsi1.toLowerCase(java.util.Locale.ROOT).contains("katalog"));
        }
        assertTrue("menu tetap punya Pengaturan",
                java.util.Arrays.asList(opsi).contains("Pengaturan"));
        assertTrue("menu tetap punya Muat ulang",
                java.util.Arrays.asList(opsi).contains("Muat ulang"));
    }

    @Test public void mengetikLangsungMencariTanpaMenekanTombol() throws Exception {
        simpanKatalog(60);
        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            KomikuActivity activity = controller.get();
            // Mengetik saja — tidak ada klik tombol "Cari".
            activity.ketikUntukTes("Judul Otomatis 44");
            ShadowLooper.idleMainLooper();
            for (int i = 0; i < 60; i++) {
                org.robolectric.shadows.ShadowSystemClock.advanceBy(
                        java.time.Duration.ofMillis(50));
                ShadowLooper.idleMainLooper();
            }
            tunggu(() -> { });

            assertEquals("hasil pencarian otomatis muncul",
                    1, activity.jumlahItemUntukTes());
            assertTrue("status menyebut hasil",
                    activity.statusUntukTes().contains("hasil"));
        }
    }

    @Test public void katalogDicariLokalDuluTanpaJaringan() throws Exception {
        simpanKatalog(40);
        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            KomikuActivity activity = controller.get();
            activity.ketikUntukTes("Judul Otomatis 7");
            for (int i = 0; i < 60; i++) {
                org.robolectric.shadows.ShadowSystemClock.advanceBy(
                        java.time.Duration.ofMillis(50));
                ShadowLooper.idleMainLooper();
            }
            tunggu(() -> { });
            assertTrue("judul dari katalog ditemukan",
                    activity.jumlahItemUntukTes() >= 1);
        }
    }

    @Test public void mengosongkanPencarianKembaliKeUmpan() throws Exception {
        simpanKatalog(120);
        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            KomikuActivity activity = controller.get();
            activity.ketikUntukTes("Judul Otomatis 5");
            for (int i = 0; i < 40; i++) {
                org.robolectric.shadows.ShadowSystemClock.advanceBy(
                        java.time.Duration.ofMillis(50));
                ShadowLooper.idleMainLooper();
            }
            tunggu(() -> { });
            int hasil = activity.jumlahItemUntukTes();
            assertTrue("ada hasil", hasil >= 1);

            activity.ketikUntukTes("");
            for (int i = 0; i < 40; i++) {
                org.robolectric.shadows.ShadowSystemClock.advanceBy(
                        java.time.Duration.ofMillis(50));
                ShadowLooper.idleMainLooper();
            }
            tunggu(() -> activity.muatUmpanUntukTes());
            assertTrue("kembali ke daftar umpan",
                    activity.jumlahItemUntukTes() != hasil
                            || activity.jumlahItemUntukTes() > 40);
        }
    }
}
