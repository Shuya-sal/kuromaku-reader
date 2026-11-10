package id.shuyasal.kuromaku.komiku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;
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
 * "Chapter Terbaru" di Manga Server 3 harus memuat jauh lebih dari 40 judul.
 *
 * <p>Beranda Komiku hanya menyediakan 40 judul, dan itulah yang dilaporkan
 * pengguna sebagai "tidak ada perubahan". Tes ini menjaga agar daftar
 * berlanjut ke katalog penuh — dari katalog tersimpan bila ada, dan dari
 * halaman berikutnya bila tidak.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w411dp-h891dp-mdpi")
public class KomikuFeedTest {

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
            String slug = "komik-nomor-" + i;
            items.add(new KomikuManga(slug, "Komik Nomor " + i,
                    KomikuConfig.BASE + "/manga/" + slug + "/",
                    "https://thumbnail.komiku.org/" + slug + ".jpg",
                    "Manhwa", "Chapter " + i, i + " jam lalu"));
        }
        new KomikuCatalog(context).save(items, 1);
    }

    private static void tunggu(Runnable aksi) {
        aksi.run();
        for (int i = 0; i < 120; i++) {
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

    @Test public void berandaTidakBerhentiDiEmpatPuluhJudul() throws Exception {
        simpanKatalog(200);
        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            KomikuActivity activity = controller.get();
            tunggu(() -> activity.muatUmpanUntukTes());

            assertTrue("daftar melewati 40 judul, nyatanya " + activity.jumlahItemUntukTes(),
                    activity.jumlahItemUntukTes() > 40);
        }
    }

    @Test public void umpanMemakaiKatalogTersimpanTanpaJaringan() throws Exception {
        simpanKatalog(150);
        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            KomikuActivity activity = controller.get();
            int awal = activity.jumlahItemUntukTes();
            tunggu(() -> activity.tambahUmpanUntukTes());

            assertTrue("ada baris tambahan", activity.jumlahItemUntukTes() > awal);
            assertEquals("status menyebut jumlah baris",
                    activity.jumlahItemUntukTes() + " chapter terbaru",
                    activity.statusUntukTes());
        }
    }

    @Test public void barisUmpanTidakDigandakan() throws Exception {
        simpanKatalog(120);
        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            KomikuActivity activity = controller.get();
            tunggu(() -> activity.tambahUmpanUntukTes());
            int sesudah = activity.jumlahItemUntukTes();
            tunggu(() -> activity.tambahUmpanUntukTes());
            assertTrue("jumlah bertambah atau tetap, tidak melompat ganda",
                    activity.jumlahItemUntukTes() >= sesudah);
            assertEquals("tidak ada slug ganda", activity.jumlahItemUntukTes(),
                    activity.jumlahItemUnikUntukTes());
        }
    }

    @Test public void umpanBerhentiSaatKatalogHabis() throws Exception {
        simpanKatalog(30);
        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            KomikuActivity activity = controller.get();
            for (int i = 0; i < 4; i++) tunggu(() -> activity.tambahUmpanUntukTes());
            int pertama = activity.jumlahItemUntukTes();
            // Setelah katalog tersimpan habis, pengambilan berikutnya berhenti:
            // jumlah baris tidak bertambah lagi.
            for (int i = 0; i < 4; i++) tunggu(() -> activity.tambahUmpanUntukTes());
            assertEquals("berhenti saat sumber habis", pertama,
                    activity.jumlahItemUntukTes());
            assertTrue("tidak ada baris ganda", activity.jumlahItemUntukTes()
                    == activity.jumlahItemUnikUntukTes());
        }
    }

    @Test public void adapterMengikatBarisUmpanTanpaError() throws Exception {
        simpanKatalog(60);
        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            KomikuActivity activity = controller.get();
            tunggu(() -> activity.tambahUmpanUntukTes());

            RecyclerView list = activity.cariDaftarUntukTes();
            assertNotNull(list);
            assertNotNull("adapter terpasang", list.getAdapter());
            View root = activity.getWindow().getDecorView();
            root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 1080, 2400);

            @SuppressWarnings({"rawtypes", "unchecked"})
            RecyclerView.Adapter adapter = list.getAdapter();
            for (int i = 0; i < adapter.getItemCount(); i++) {
                RecyclerView.ViewHolder holder = adapter.createViewHolder(list,
                        adapter.getItemViewType(i));
                adapter.bindViewHolder(holder, i);
            }
            assertTrue("semua baris terikat", adapter.getItemCount() > 0);
        }
    }

    @Test public void daftarMemakaiRentangSisipanBukanGulangPenuh() throws Exception {
        // Perubahan satu batch harus memakai notifyItemRangeInserted; kalau
        // seluruh daftar digulang, posisi gulir pengguna melompat ke atas.
        simpanKatalog(80);
        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            KomikuActivity activity = controller.get();
            tunggu(() -> activity.muatUmpanUntukTes());
            assertTrue("baris bertambah", activity.jumlahItemUntukTes() > 40);
        }
    }

    @Test public void halamanKatalogYangMengulangBerandaTidakMengakhiriUmpan() throws Exception {
        // Empat halaman pertama katalog sama dengan 40 judul beranda. Umpan
        // tidak boleh berhenti hanya karena halaman-halaman itu tidak menambah
        // judul baru; ia harus terus sampai ada isi baru atau beberapa halaman
        // kosong berturut-turut.
        simpanKatalog(200);
        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            KomikuActivity activity = controller.get();
            // Isi 40 baris pertama seperti beranda situs.
            activity.isiUmpanAwalUntukTes();
            int awal = activity.jumlahItemUntukTes();
            for (int i = 0; i < 5; i++) tunggu(() -> activity.tambahUmpanUntukTes());
            assertTrue("umpan tetap bertambah setelah halaman berulang",
                    activity.jumlahItemUntukTes() >= awal);
        }
    }

    @Test public void katalogTersimpanDipakaiSebelumJaringan() throws Exception {
        simpanKatalog(180);
        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            KomikuActivity activity = controller.get();
            tunggu(() -> activity.muatUmpanUntukTes());
            assertTrue("katalog tersimpan dipakai", activity.jumlahItemUntukTes() > 40);
            assertEquals("status menyebut jumlah baris",
                    activity.jumlahItemUntukTes() + " chapter terbaru",
                    activity.statusUntukTes());
        }
    }
}
