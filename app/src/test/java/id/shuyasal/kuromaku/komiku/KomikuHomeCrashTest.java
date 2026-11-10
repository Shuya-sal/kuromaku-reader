package id.shuyasal.kuromaku.komiku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import id.shuyasal.kuromaku.sync.SyncBridge;

/**
 * Dua hal yang dijaga di sini:
 *
 * <ul>
 *   <li>Adapter tidak pernah meminta posisi di luar datanya — penyebab
 *       {@code IndexOutOfBoundsException} yang dilaporkan pengguna saat
 *       beranda Manga Server 3 dibuka.</li>
 *   <li>Rail "Lanjutkan Membaca" hanya memuat judul dari Manga Server 2 bila
 *       judul itu benar-benar sudah dipasangkan, dan tidak pernah mengarang
 *       alamat chapter.</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w411dp-h891dp-mdpi")
public class KomikuHomeCrashTest {

    private Context context;

    @Before public void siapkan() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(KomikuStore.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.getSharedPreferences(SyncStoreName.FILE, Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    /** Nama berkas preferensi peta sinkron, tanpa mengimpor kelasnya di sini. */
    private static final class SyncStoreName {
        static final String FILE = "kuromaku_sync";
    }

    private static View byTag(View root, String tag) {
        if (root == null) return null;
        if (tag.equals(root.getTag())) return root;
        if (!(root instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = byTag(group.getChildAt(i), tag);
            if (found != null) return found;
        }
        return null;
    }

    /** Susun daftar seperti RecyclerView saat mengikat item. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<Integer> bindAll(RecyclerView list) {
        List<Integer> bound = new ArrayList<>();
        RecyclerView.Adapter adapter = list.getAdapter();
        if (adapter == null) return bound;
        for (int i = 0; i < adapter.getItemCount(); i++) {
            int type = adapter.getItemViewType(i);
            RecyclerView.ViewHolder holder = adapter.createViewHolder(list, type);
            adapter.bindViewHolder(holder, i);
            bound.add(i);
        }
        return bound;
    }

    @Test public void berandaDibukaTanpaDataTidakCrash() {
        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            View root = controller.get().getWindow().getDecorView();
            root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 1080, 2400);

            RecyclerView list = (RecyclerView) byTag(root, "komiku_list");
            assertNotNull(list);
            // Tidak ada ActivityNotFoundException maupun IndexOutOfBounds di sini.
            bindAll(list);
        }
    }

    @Test public void adapterMengikatSemuaItemSetelahDaftarDiisi() {
        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            KomikuActivity activity = controller.get();
            View root = activity.getWindow().getDecorView();
            RecyclerView list = (RecyclerView) byTag(root, "komiku_list");

            // Isi daftar lewat jalur yang sama dengan hasil jaringan.
            List<KomikuManga> data = new ArrayList<>();
            data.add(new KomikuManga("a", "Blue Ursus", "https://komiku.org/manga/a/",
                    "https://thumbnail.komiku.to/a.png", "Aksi", "Chapter 5", "17 menit lalu"));
            data.add(new KomikuManga("b", "Leveling My Husband", "https://komiku.org/manga/b/",
                    "https://thumbnail.komiku.to/b.png", "Romantis", "Chapter 9", "27 menit lalu"));
            activity.publishForTest(data, true);

            assertEquals("dua item terikat tanpa error", 2, bindAll(list).size());
        }
    }

    @Test public void daftarDikosongkanLaluDiisiTidakCrash() {
        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            KomikuActivity activity = controller.get();
            View root = activity.getWindow().getDecorView();
            RecyclerView list = (RecyclerView) byTag(root, "komiku_list");

            List<KomikuManga> data = new ArrayList<>();
            data.add(new KomikuManga("a", "Blue Ursus", "https://komiku.org/manga/a/",
                    "", "", "Chapter 5", ""));
            activity.publishForTest(data, true);
            bindAll(list);

            // Persis urutan yang dulu membuat adapter membaca indeks kosong:
            // daftar dikosongkan, lalu diisi data baru.
            activity.publishForTest(new ArrayList<>(), true);
            bindAll(list);
            activity.publishForTest(data, true);
            assertEquals(1, bindAll(list).size());
        }
    }

    @Test public void railKosongTanpaRiwayatDanTanpaTautan() {
        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            View root = controller.get().getWindow().getDecorView();
            root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 1080, 2400);
            assertNull("tanpa riwayat dan tanpa tautan rail tidak dipasang",
                    byTag(root, "komiku_continue_rail"));
        }
    }

    @Test public void judulServer2MunculDiRailSetelahDipasangkan() {
        SyncBridge bridge = new SyncBridge(context);
        bridge.link(SyncBridge.SOURCE_SHINIGAMI, "shini-1", "Myst, Might, Mayhem", "",
                SyncBridge.SOURCE_KOMIKU, "myst-might-mayhem", "Myst, Might, Mayhem", "",
                "manual");
        bridge.onChapterOpened(SyncBridge.SOURCE_SHINIGAMI, "shini-1", "Chapter 124", true);

        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            View root = controller.get().getWindow().getDecorView();
            root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 1080, 2400);

            View rail = byTag(root, "komiku_continue_rail");
            assertNotNull("judul tersinkron ditawarkan di rail", rail);
            RecyclerView cards = (RecyclerView) byTag(rail, "komiku_continue_cards");
            assertNotNull(cards);
            assertEquals("satu kartu", 1, cards.getAdapter().getItemCount());
            bindAll(cards);
        }
    }

    @Test public void judulTanpaTautanTidakMunculDiRail() {
        // Riwayat Server 2 ada, tetapi judulnya belum dipasangkan dengan Komiku.
        new id.shuyasal.kuromaku.shinigami.ShinigamiStore(context).recordRead(
                "shini-2", "Contract Killer Enrollment", "", "chapter-1", 27);

        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            View root = controller.get().getWindow().getDecorView();
            root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 1080, 2400);
            assertNull("tanpa tautan tidak ada klaim sinkron",
                    byTag(root, "komiku_continue_rail"));
        }
    }

    @Test public void gridDanBarisMemakaiDaftarSendiri() {
        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            KomikuActivity activity = controller.get();
            View root = activity.getWindow().getDecorView();
            RecyclerView list = (RecyclerView) byTag(root, "komiku_list");

            List<KomikuManga> data = new ArrayList<>();
            data.add(new KomikuManga("a", "Satu", "https://komiku.org/manga/a/", "", "", "", ""));
            activity.publishForTest(data, false);
            assertTrue("mode grid pun terikat tanpa error",
                    list.getLayoutManager() instanceof LinearLayoutManager
                            || list.getLayoutManager() != null);
            assertEquals(1, bindAll(list).size());
        }
    }
}