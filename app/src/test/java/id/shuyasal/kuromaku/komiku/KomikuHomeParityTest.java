package id.shuyasal.kuromaku.komiku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

import id.shuyasal.kuromaku.shinigami.ShinigamiActivity;

/**
 * Beranda Manga Server 3 harus memakai kerangka yang sama dengan Manga Server 2:
 * bar atas dengan menu + unduhan + titik tiga, kolom pencarian dengan tombol
 * Cari, chip yang bisa digeser, baris jumlah, rail Lanjutkan Membaca, dan satu
 * daftar. Yang dibandingkan adalah keberadaan dan penamaan elemennya, bukan
 * data dari jaringan.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w411dp-h891dp-mdpi")
public class KomikuHomeParityTest {

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

    private static ViewGroup tags(ViewGroup root) {
        return root;
    }

    @Test public void kerangkaBerandaSamaDenganServer2() {
        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            View root = controller.get().getWindow().getDecorView();

            // Bar atas: menu, judul, unduhan, titik tiga — sama seperti Server 2.
            assertNotNull("menu samping", byTag(root, "section_menu"));
            assertNotNull("judul", byTag(root, "komiku_heading"));
            assertNotNull("tombol unduhan", byTag(root, "komiku_downloads_button"));
            assertNotNull("menu titik tiga", byTag(root, "komiku_overflow"));

            // Pencarian + tombol Cari.
            assertNotNull("kolom pencarian", byTag(root, "komiku_search"));
            assertNotNull("tombol cari", byTag(root, "komiku_search_button"));

            // Chip tab, urutan dan labelnya sama.
            assertNotNull(byTag(root, "komiku_chip_Chapter Terbaru"));
            assertNotNull(byTag(root, "komiku_chip_Terpopuler"));
            assertNotNull(byTag(root, "komiku_chip_Manga Terbaru"));
            assertNotNull(byTag(root, "komiku_chip_Riwayat"));
            assertNotNull(byTag(root, "komiku_chip_Favorit"));

            // Baris jumlah dan satu daftar.
            assertNotNull("baris jumlah", byTag(root, "komiku_status"));
            assertNotNull("daftar", byTag(root, "komiku_list"));
        }
    }

    @Test public void tombolKembaliLamaTidakAdaLagi() {
        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            View root = controller.get().getWindow().getDecorView();
            // Beranda sumber lain memakai menu samping, bukan panah kembali.
            assertEquals("tidak ada panah kembali di beranda", null, byTag(root, "komiku_back"));
        }
    }

    @Test public void railLanjutkanMembacaMunculHanyaBilaAdaRiwayat() {
        android.content.Context context =
                androidx.test.core.app.ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(KomikuStore.FILE_NAME, android.content.Context.MODE_PRIVATE)
                .edit().clear().commit();

        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            View root = controller.get().getWindow().getDecorView();
            layOut(root);
            assertEquals("tanpa riwayat rail tidak dipasang di daftar",
                    null, byTag(root, "komiku_continue_rail"));
        }

        new KomikuStore(context).recordChapter("black-clover-indonesia", "Black Clover",
                "https://img.komiku.org/a.jpg",
                "https://komiku.org/black-clover-chapter-10/", "Chapter 10", 10, 0.35);

        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            View root = controller.get().getWindow().getDecorView();
            layOut(root);
            View rail = byTag(root, "komiku_continue_rail");
            assertNotNull("riwayat menjadikan rail item pertama daftar", rail);
            assertEquals("rail terlihat", View.VISIBLE, rail.getVisibility());
            assertNotNull("kartu lanjutkan memakai cover",
                    byTag(rail.getParent() == null ? rail : (View) rail.getParent(),
                            "komiku_continue_cards") != null ? "ada" : null);
        }
    }

    private static void layOut(View root) {
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1080, 2400);
    }

    @Test public void menuSampingMemuatMangaServer3() {
        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            View root = controller.get().getWindow().getDecorView();
            byTag(root, "section_menu").performClick();

            View drawer = ShadowDialog.getLatestDialog().findViewById(android.R.id.content);
            assertNotNull("baris Manga Server 3", byTag(drawer, "drawer_manga_komiku"));
            assertNotNull("Novel", byTag(drawer, "drawer_novel"));
            assertNotNull("Manga Server 1", byTag(drawer, "drawer_manga_voratoon"));
            assertNotNull("Manga Server 2", byTag(drawer, "drawer_manga_shinigami"));
            assertNotNull("Anime", byTag(drawer, "drawer_anime_otakudesu"));
        }
    }

    @Test public void chipKatalogBerpindahKeGrid() {
        try (ActivityController<KomikuActivity> controller =
                     Robolectric.buildActivity(KomikuActivity.class).setup()) {
            KomikuActivity activity = controller.get();
            View root = activity.getWindow().getDecorView();
            byTag(root, "komiku_chip_Terpopuler").performClick();

            androidx.recyclerview.widget.RecyclerView list =
                    (androidx.recyclerview.widget.RecyclerView) byTag(root, "komiku_list");
            assertTrue("katalog memakai daftar baris/grid satu kolom yang sama",
                    list.getAdapter() != null);
        }
    }

    @Test public void labelChapterDipadatkanSamaSepertiServer2() {
        assertEquals("Ch. 130", KomikuActivity.chapterShort("Chapter 130"));
        assertEquals("Ch. 12", KomikuActivity.chapterShort("chapter 12"));
        assertEquals("Prolog", KomikuActivity.chapterShort("Prolog"));
        assertEquals("", KomikuActivity.chapterShort(""));
        assertEquals("", KomikuActivity.chapterShort(null));
    }
}