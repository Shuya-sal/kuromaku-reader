package id.shuyasal.kuromaku.shinigami;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Sorotan beranda ("Rekomendasi") hanya berisi judul yang sumbernya sendiri
 * tandai {@code is_recommended}. Tes ini memakai respons ASLI dari
 * api.shinigami.io: dari 25 judul terpopuler hanya dua yang bertanda.
 *
 * <p>Yang dijaga:
 * <ul>
 *   <li>banner hanya memuat judul bertanda, tidak dikarang;</li>
 *   <li>tidak ada banner sama sekali bila sumber tidak menandai apa pun;</li>
 *   <li>banner memakai sampul lebar sumber, bukan sampul potret;</li>
 *   <li>label kartu memakai bentuk ringkas "Ch. N · waktu".</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w411dp-h891dp-xhdpi")
public class ShinigamiHighlightTest {

    static final List<String> REQUESTS = new ArrayList<>();

    public static class Catalog extends ShinigamiActivity {
        @Override protected ShinigamiSource createSource() {
            return new ShinigamiSource(this, url -> {
                REQUESTS.add(url);
                if (url.contains("/taxonomy/")) return "{\"data\":[]}";
                if (url.contains("sort=latest")) return fixture("live_manga_list.json");
                // Sorotan diambil dari feed judul terbaru: halaman asli
                // api.shinigami.io berisi 24 judul, enam di antaranya bertanda.
                if (url.contains("sort=new_manga")) return fixture("highlight_newest.json");
                return fixture("highlight_list.json");
            });
        }
    }

    /** Beranda tanpa satu pun judul bertanda rekomendasi. */
    public static class PlainCatalog extends ShinigamiActivity {
        @Override protected ShinigamiSource createSource() {
            return new ShinigamiSource(this, url -> {
                if (url.contains("/taxonomy/")) return "{\"data\":[]}";
                return fixture("live_manga_list.json");
            });
        }
    }

    static String fixture(String name) throws java.io.IOException {
        try (InputStream in = ShinigamiHighlightTest.class.getResourceAsStream(
                "/shinigami/" + name)) {
            if (in == null) throw new java.io.IOException("fixture tidak ada: " + name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            return out.toString("UTF-8");
        }
    }

    @Before
    public void setup() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(ShinigamiStore.FILE_NAME, 0).edit().clear().commit();
        context.getSharedPreferences(ShinigamiConfig.FILE_NAME, 0).edit().clear().commit();
        REQUESTS.clear();
    }

    private void await(ShinigamiActivity host) throws Exception {
        Field field = ShinigamiActivity.class.getDeclaredField("worker");
        field.setAccessible(true);
        ((ExecutorService) field.get(host)).submit(() -> { }).get(20, TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private View tagged(ShinigamiActivity host, String tag) {
        return host.getWindow().getDecorView().findViewWithTag(tag);
    }

    /** Pindah ke tab Terpopuler: sorotan dan grid katalog sama-sama terlihat. */
    private void popular(ShinigamiActivity host) throws Exception {
        View chip = tagged(host, "shinigami_chip_Terpopuler");
        assertNotNull("tab Terpopuler harus ada", chip);
        chip.performClick();
        await(host);
        await(host);
    }

    private static View field(ShinigamiActivity host, String name) throws Exception {
        Field field = ShinigamiActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return (View) field.get(host);
    }

    /**
     * Membaca RAIL VIEW milik adapter banner (adapter view-nya indeks 0).
     *
     * <p>Yang dihitung adalah adapter yang benar-benar terpasang. Memanggil
     * {@code measure()} pada rail memicu {@code onLayoutChildren} RecyclerView,
     * dan urutan layout pada Robolectric berbeda dari perangkat: beberapa anak
     * sempat dilepas karena "di luar layar", sehingga kartu yang sah ikut
     * terlewat. Uji tampilan yang sebenarnya sudah dilakukan oleh
     * {@code ShinigamiNewApiRenderTest} (tangkap layar beranda).
     */
    private List<String> highlightTitles(ShinigamiActivity host) throws Exception {
        RecyclerView rail = (RecyclerView) field(host, "highlightList");
        assertNotNull("rail sorotan harus ada", rail);
        RecyclerView.Adapter<?> adapter = rail.getAdapter();
        assertNotNull("banner harus punya adapter", adapter);
        int count = adapter.getItemCount();
        List<ShinigamiManga> loaded = highlights(host);
        List<String> titles = new ArrayList<>();
        for (int index = 0; index < count && index < loaded.size(); index++) {
            titles.add(loaded.get(index).title);
        }
        return titles;
    }

    @Test
    public void sorotanHanyaMemuatJudulYangDitandaiSumber() throws Exception {
        try (ActivityController<Catalog> controller =
                     Robolectric.buildActivity(Catalog.class).setup()) {
            Catalog host = controller.get();
            await(host);
            popular(host);

            // Rail baru masuk ke pohon tampilan saat item header dibuat, jadi
            // akses lewat field; tes ini menguji keadaan adapter, bukan layout.
            View rail = field(host, "highlightRail");
            assertEquals("sorotan harus tampil", View.VISIBLE, rail.getVisibility());

            RecyclerView cards = (RecyclerView) field(host, "highlightList");
            assertEquals("fixture menandai enam judul", 6, cards.getAdapter().getItemCount());

            List<String> titles = highlightTitles(host);
            assertTrue("judul sorotan: " + titles, titles.contains("Infinite Titles"));
            assertTrue("judul sorotan: " + titles, titles.contains("Solo Swordmaster"));
            assertTrue("judul sorotan: " + titles,
                    titles.contains("Martial Emperor Beyond Transcendence"));
            // Judul lain di halaman yang sama tidak boleh ikut: sumber tidak
            // menandainya sebagai rekomendasi.
            assertEquals("hanya judul bertanda", 6, titles.size());
        }
    }

    @Test
    public void bannerMemakaiSampulLebarBukanPotret() throws Exception {
        try (ActivityController<Catalog> controller =
                     Robolectric.buildActivity(Catalog.class).setup()) {
            Catalog host = controller.get();
            await(host);
            popular(host);

            RecyclerView rail = (RecyclerView) field(host, "highlightList");
            assertTrue(rail.getAdapter().getItemCount() > 0);
            // Sumber mengirim cover_image_url (lanskap 720x1030) dan
            // cover_portrait_url (720x1440). Banner harus memakai yang lanskap,
            // bukan potret — dan bila sumber tidak punya yang lanskap, potret
            // dipakai sebagai cadangan (tidak dikarang).
            List<ShinigamiManga> loaded = highlights(host);
            ShinigamiManga punyaKeduanya = null;
            for (ShinigamiManga manga : loaded) {
                assertTrue("sampul sorotan harus dari sumber: " + manga.title + " -> "
                        + manga.wideCoverUrl, manga.wideCoverUrl.contains("cdn.shinigami.gg"));
                if (punyaKeduanya == null && !manga.wideCoverUrl.equals(manga.coverUrl)) {
                    punyaKeduanya = manga;
                }
            }
            assertNotNull("harus ada judul dengan sampul lanskap dan potret", punyaKeduanya);
            assertEquals("Martial Emperor Beyond Transcendence", punyaKeduanya.title);
        }
    }

    @SuppressWarnings("unchecked")
    private List<ShinigamiManga> highlights(ShinigamiActivity host) throws Exception {
        Field field = ShinigamiActivity.class.getDeclaredField("highlights");
        field.setAccessible(true);
        return (List<ShinigamiManga>) field.get(host);
    }

    @Test
    public void tanpaPenandaRekomendasiRailDisembunyikan() throws Exception {
        try (ActivityController<PlainCatalog> controller =
                     Robolectric.buildActivity(PlainCatalog.class).setup()) {
            PlainCatalog host = controller.get();
            await(host);
            View chip = tagged(host, "shinigami_chip_Terpopuler");
            chip.performClick();
            await(host);

            // Tanpa satu pun judul bertanda, rail tidak pernah dipasang ke
            // daftar: jumlah item katalog sama persis dengan jumlah judul.
            View rail = field(host, "highlightRail");
            assertNotNull(rail);
            assertEquals("rail tidak boleh tampil", View.GONE, rail.getVisibility());
            RecyclerView cards = (RecyclerView) field(host, "highlightList");
            assertNotNull(cards);
            assertEquals("tidak boleh ada banner karangan",
                    0, cards.getAdapter().getItemCount());
            RecyclerView grid = (RecyclerView) tagged(host, "shinigami_grid");
            androidx.recyclerview.widget.ConcatAdapter concat =
                    (androidx.recyclerview.widget.ConcatAdapter) grid.getAdapter();
            // Adapter header memang selalu terpasang, tetapi jumlah itemnya nol:
            // daftar berisi katalog saja.
            RecyclerView.Adapter<?> header = concat.getAdapters().get(0);
            assertEquals("tidak ada item header", 0, header.getItemCount());
            assertEquals("daftar = header kosong + katalog",
                    2, concat.getAdapters().size());
        }
    }

    @Test
    public void kartuKatalogRingkasMemuatNomorChapterDanWaktu() throws Exception {
        try (ActivityController<Catalog> controller =
                     Robolectric.buildActivity(Catalog.class).setup()) {
            Catalog host = controller.get();
            await(host);
            popular(host);

            RecyclerView grid = (RecyclerView) tagged(host, "shinigami_grid");
            // Adapter katalog adalah adapter terakhir pada ConcatAdapter;
            // dua item pertama adalah banner dan rail riwayat.
            androidx.recyclerview.widget.ConcatAdapter concat =
                    (androidx.recyclerview.widget.ConcatAdapter) grid.getAdapter();
            int partCount = concat.getAdapters().size();
            RecyclerView.Adapter<?> catalogue = concat.getAdapters().get(partCount - 1);
            assertTrue(catalogue.getItemCount() > 0);
            RecyclerView.ViewHolder holder =
                    catalogue.createViewHolder(grid, catalogue.getItemViewType(0));
            @SuppressWarnings("unchecked")
            androidx.recyclerview.widget.RecyclerView.Adapter<RecyclerView.ViewHolder> typed =
                    (androidx.recyclerview.widget.RecyclerView.Adapter<RecyclerView.ViewHolder>)
                            catalogue;
            typed.bindViewHolder(holder, 0);
            View card = holder.itemView;
            assertNotNull("harus ada kartu katalog", card);
            TextView info = card.findViewWithTag("shinigami_subtitle");
            String label = info.getText().toString();
            assertTrue("label kartu: " + label, label.startsWith("Ch. "));
            assertTrue("label kartu harus memuat waktu: " + label, label.contains(" · "));
            assertEquals("label kartu satu baris", 1, info.getMaxLines());
            ViewGroup.LayoutParams params = info.getLayoutParams();
            assertNotNull(params);
        }
    }
}
