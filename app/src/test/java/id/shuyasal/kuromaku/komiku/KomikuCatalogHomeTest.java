package id.shuyasal.kuromaku.komiku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;
import androidx.recyclerview.widget.RecyclerView;

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
 * Beranda memakai katalog lengkap, bukan hanya 40 judul beranda.
 *
 * <p>Yang dijaga: tab "Manga Terbaru" menampilkan seluruh katalog tersimpan,
 * pencarian menemukan judul yang hanya ada di katalog, dan beranda tetap
 * memakai beranda situs bila katalog belum pernah diambil.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w411dp-h891dp-mdpi")
public class KomikuCatalogHomeTest {

    private android.content.Context context;

    @Before public void siapkan() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(KomikuStore.FILE_NAME, android.content.Context.MODE_PRIVATE)
                .edit().clear().commit();
        new KomikuCatalog(context).clear();
    }

    private void simpanKatalog(int jumlah) throws Exception {
        List<KomikuManga> items = new ArrayList<>();
        for (int i = 1; i <= jumlah; i++) {
            String slug = "judul-ke-" + i;
            items.add(new KomikuManga(slug, "Judul Ke " + i,
                    KomikuConfig.BASE + "/manga/" + slug + "/",
                    "https://thumbnail.komiku.org/" + slug + ".jpg",
                    "Manhwa", "Chapter " + i, i + " jam lalu"));
        }
        new KomikuCatalog(context).save(items, 1);
    }

    @Test public void tabMangaTerbaruMemakaiSeluruhKatalogTersimpan() throws Exception {
        simpanKatalog(90);
        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            KomikuActivity activity = controller.get();
            tunggu(() -> activity.pilihTabUntukTes(KomikuTabNewTitles.INDEX));

            assertEquals("seluruh katalog, bukan 40 judul beranda",
                    90, activity.jumlahItemUntukTes());
            assertTrue("status menyebut katalog lengkap",
                    activity.statusUntukTes().contains("katalog lengkap"));
        }
    }

    @Test public void pencarianMenemukanJudulYangHanyaAdaDiKatalog() throws Exception {
        simpanKatalog(120);
        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            KomikuActivity activity = controller.get();
            // "Judul Ke 117" tidak ada di beranda situs mana pun.
            tunggu(() -> activity.cariUntukTes("Judul Ke 117"));

            assertEquals("satu judul ditemukan dari katalog", 1, activity.jumlahItemUntukTes());
            assertTrue("status menyebut hasil",
                    activity.statusUntukTes().contains("hasil untuk"));
        }
    }

    @Test public void tanpaKatalogTabMangaTerbaruTetapMemakaiBeranda() throws Exception {
        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            KomikuActivity activity = controller.get();
            // Tanpa katalog, jalur ini memanggil situs. Di tes, jaringan tidak
            // tersedia, jadi yang dijaga adalah: tidak ada katalog berarti tidak
            // ada klaim "katalog lengkap" pada status.
            assertTrue("tidak mengklaim katalog lengkap",
                    !activity.statusUntukTes().contains("katalog lengkap"));
        }
    }

    @Test public void katalogTersimpanTerbacaSebagaiDaftarLengkap() throws Exception {
        simpanKatalog(35);
        List<KomikuManga> loaded = new KomikuCatalog(context).load();
        assertEquals(35, loaded.size());
        assertEquals("Judul Ke 1", loaded.get(0).title);
        assertEquals(35, new KomikuCatalog(context).stats().titles);
    }

    @Test public void daftarKatalogTampilDiDaftarBeranda() throws Exception {
        simpanKatalog(50);
        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            KomikuActivity activity = controller.get();
            tunggu(() -> activity.pilihTabUntukTes(KomikuTabNewTitles.INDEX));

            RecyclerView list = activity.findViewById(
                    activity.getResources().getIdentifier("komiku_list", "id",
                            activity.getPackageName()));
            if (list == null) list = activity.cariDaftarUntukTes();
            assertNotNull("daftar beranda ada", list);
            assertNotNull("adapter terpasang", list.getAdapter());
            assertTrue("lima puluh judul terikat",
                    list.getAdapter().getItemCount() >= 50);
        }
    }

    /** Tunggu hasil pekerjaan latar, lalu jalankan tugas antrean utama. */
    private static void tunggu(Runnable aksi) {
        aksi.run();
        for (int i = 0; i < 80; i++) {
            ShadowLooper.idleMainLooper();
            try {
                Thread.sleep(25);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            ShadowLooper.idleMainLooper();
        }
    }

    /** Indeks tab "Manga Terbaru" di enum Tab internal Activity. */
    private static final class KomikuTabNewTitles {
        static final int INDEX = 2;
    }
}
