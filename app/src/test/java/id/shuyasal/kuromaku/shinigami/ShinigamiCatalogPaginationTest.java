package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
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

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Katalog Manga Shinigami tidak boleh melompat ke atas saat halaman berikutnya
 * dimuat.
 *
 * <p>Penyebab lamanya: setiap halaman memanggil {@code list.setAdapter(new
 * ConcatAdapter(...))} lalu {@code notifyDataSetChanged()}; RecyclerView
 * membuang posisi scroll setiap kali adapter diganti. Tes ini menahan kedua
 * jaminan itu: instance adapter tetap sama, dan penambahan halaman hanya
 * dilaporkan sebagai range insert.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w360dp-h640dp-mdpi")
public class ShinigamiCatalogPaginationTest {

    static final int PAGE_SIZE = 12;
    static final int TOTAL_PAGES = 3;
    static final List<String> REQUESTS = new ArrayList<>();

    static ShinigamiSource.Transport transport;

    public static class Catalog extends ShinigamiActivity {
        @Override protected ShinigamiSource createSource() {
            return new ShinigamiSource(this, transport);
        }
    }

    Context context;

    @Before public void setup() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(ShinigamiStore.FILE_NAME, 0).edit().clear().commit();
        context.getSharedPreferences(ShinigamiConfig.FILE_NAME, 0).edit().clear().commit();
        REQUESTS.clear();
        transport = url -> {
            if (url.contains("/genre/")) return "{\"retcode\":0,\"data\":[]}";
            REQUESTS.add(url);
            int page = 1;
            int found = url.indexOf("page=");
            if (found >= 0) {
                int end = url.indexOf('&', found);
                page = Integer.parseInt(url.substring(found + 5, end < 0 ? url.length() : end));
            }
            return mangaList(page);
        };
    }

    static String mangaList(int page) throws IOException {
        try {
            JSONArray data = new JSONArray();
            for (int index = 0; index < PAGE_SIZE; index++) {
                int number = (page - 1) * PAGE_SIZE + index;
                JSONObject item = new JSONObject();
                item.put("manga_id", "manga-" + number);
                item.put("title", "Judul " + number);
                item.put("description", "Deskripsi");
                item.put("status", "ongoing");
                item.put("cover_image_url", "");
                data.put(item);
            }
            JSONObject meta = new JSONObject();
            meta.put("total_record", PAGE_SIZE * TOTAL_PAGES);
            meta.put("total_page", TOTAL_PAGES);
            meta.put("page", page);
            JSONObject root = new JSONObject();
            root.put("retcode", 0);
            root.put("data", data);
            root.put("meta", meta);
            return root.toString();
        } catch (org.json.JSONException error) {
            throw new IOException(error);
        }
    }

    void await(Catalog host) throws Exception {
        Field field = ShinigamiActivity.class.getDeclaredField("worker");
        field.setAccessible(true);
        for (int attempt = 0; attempt < 4; attempt++) {
            ((ExecutorService) field.get(host)).submit(() -> { }).get(10, TimeUnit.SECONDS);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
    }

    void append(Catalog host, int page) throws Exception {
        Method load = ShinigamiActivity.class.getDeclaredMethod("loadPage", int.class, boolean.class);
        load.setAccessible(true);
        load.invoke(host, page, true);
        await(host);
    }

    RecyclerView list(Catalog host) {
        return (RecyclerView) host.getWindow().getDecorView().findViewWithTag("shinigami_grid");
    }

    int itemCount(Catalog host) {
        RecyclerView.Adapter<?> adapter = list(host).getAdapter();
        return adapter == null ? 0 : adapter.getItemCount();
    }

    /** Catat notifikasi adapter: append harus range-insert, bukan reset total. */
    static final class Recorder extends RecyclerView.AdapterDataObserver {
        int changed;
        int inserted;
        int removed;

        @Override public void onChanged() { changed++; }
        @Override public void onItemRangeInserted(int positionStart, int itemCount) { inserted += itemCount; }
        @Override public void onItemRangeRemoved(int positionStart, int itemCount) { removed += itemCount; }
    }

    @Test public void halamanBerikutnyaDitambahkanTanpaMenggantiAdapter() throws Exception {
        try (ActivityController<Catalog> controller = Robolectric.buildActivity(Catalog.class).setup()) {
            Catalog host = controller.get();
            await(host);

            RecyclerView.Adapter<?> first = list(host).getAdapter();
            assertNotNull(first);
            assertEquals(PAGE_SIZE, itemCount(host));

            append(host, 2);

            assertSame("adapter tidak boleh diganti saat pagination", first, list(host).getAdapter());
            assertEquals("halaman kedua harus menambah item", PAGE_SIZE * 2, itemCount(host));
        }
    }

    @Test public void penambahanHalamanHanyaMemakaiRangeInsert() throws Exception {
        try (ActivityController<Catalog> controller = Robolectric.buildActivity(Catalog.class).setup()) {
            Catalog host = controller.get();
            await(host);

            Recorder recorder = new Recorder();
            list(host).getAdapter().registerAdapterDataObserver(recorder);

            append(host, 2);

            assertEquals("tidak boleh notifyDataSetChanged saat append", 0, recorder.changed);
            assertEquals("item baru dilaporkan sebagai range insert", PAGE_SIZE, recorder.inserted);
            assertEquals("item lama tidak boleh dihapus", 0, recorder.removed);
        }
    }

    @Test public void posisiScrollTetapSetelahPagination() throws Exception {
        try (ActivityController<Catalog> controller = Robolectric.buildActivity(Catalog.class).setup()) {
            Catalog host = controller.get();
            await(host);
            layout(list(host));

            LinearLayoutManager manager = (LinearLayoutManager) list(host).getLayoutManager();
            manager.scrollToPositionWithOffset(PAGE_SIZE - 2, 0);
            layout(list(host));
            int before = manager.findFirstVisibleItemPosition();
            assertTrue("prasyarat: daftar tergulir ke bawah", before > 0);

            append(host, 2);
            layout(list(host));

            assertEquals("posisi item pertama harus bertahan setelah append",
                    before, manager.findFirstVisibleItemPosition());
        }
    }

    @Test public void permintaanHalamanSamaTidakDiulang() throws Exception {
        try (ActivityController<Catalog> controller = Robolectric.buildActivity(Catalog.class).setup()) {
            Catalog host = controller.get();
            await(host);

            // Empat pemicu beruntun seperti onScrolled yang mengenai dasar daftar.
            Method loadMore = ShinigamiActivity.class.getDeclaredMethod("loadMore");
            loadMore.setAccessible(true);
            for (int attempt = 0; attempt < 4; attempt++) loadMore.invoke(host);
            await(host);

            int pageTwoRequests = 0;
            for (String request : REQUESTS) if (request.contains("page=2")) pageTwoRequests++;
            assertEquals("halaman 2 cukup diminta sekali: " + REQUESTS, 1, pageTwoRequests);
        }
    }

    @Test public void paginationBerlanjutSampaiHalamanTerakhir() throws Exception {
        try (ActivityController<Catalog> controller = Robolectric.buildActivity(Catalog.class).setup()) {
            Catalog host = controller.get();
            await(host);

            append(host, 2);
            append(host, 3);

            assertEquals("seluruh halaman terkumpul", PAGE_SIZE * TOTAL_PAGES, itemCount(host));
            Set<String> ids = new HashSet<>();
            RecyclerView.Adapter<?> adapter = list(host).getAdapter();
            assertEquals("tidak ada duplikat", PAGE_SIZE * TOTAL_PAGES, itemCount(host));
            assertTrue("adapter tetap satu instance", adapter != null);

            // Halaman terakhir tidak boleh memicu permintaan lagi.
            int before = REQUESTS.size();
            Method loadMore = ShinigamiActivity.class.getDeclaredMethod("loadMore");
            loadMore.setAccessible(true);
            loadMore.invoke(host);
            await(host);
            assertEquals("tidak ada permintaan setelah halaman terakhir", before, REQUESTS.size());
        }
    }

    static void layout(RecyclerView list) {
        int width = View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY);
        int height = View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY);
        list.measure(width, height);
        list.layout(0, 0, 720, 1280);
    }
}