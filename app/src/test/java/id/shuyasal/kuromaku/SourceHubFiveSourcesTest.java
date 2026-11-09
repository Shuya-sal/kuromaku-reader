package id.shuyasal.kuromaku;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.view.ViewGroup;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * Source Hub harus menampilkan lima sumber: Novel, Manga Server 1, 2, 3, dan
 * Anime Streaming. Kartu Manga Server 3 tidak boleh menghilangkan kartu lain.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SourceHubFiveSourcesTest {

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

    @Test public void limaSumberTampilDiHub() {
        // Activity ditutup lewat controller: meninggalkan ViewRoot hidup membuat
        // tes berikutnya mewarisi antrean traversal dan gagal secara acak.
        try (ActivityController<SourceHubActivity> controller =
                     Robolectric.buildActivity(SourceHubActivity.class).setup()) {
            View root = controller.get().getWindow().getDecorView();

            assertNotNull("Novel", byTag(root, "source_hub_card_novel"));
            assertNotNull("Manga Server 1", byTag(root, "source_hub_card_voratoon"));
            assertNotNull("Manga Server 2", byTag(root, "source_hub_card_shinigami"));
            assertNotNull("Manga Server 3", byTag(root, "source_hub_card_komiku"));
            assertNotNull("Anime Streaming", byTag(root, "source_hub_card_otakudesu"));
        }
    }

    @Test public void kartuKomikuAdaDiAntaraServer2DanAnime() {
        try (ActivityController<SourceHubActivity> controller =
                     Robolectric.buildActivity(SourceHubActivity.class).setup()) {
            kartuKomikuDiAntara(controller.get().getWindow().getDecorView());
        }
    }

    private void kartuKomikuDiAntara(View root) {
        View cards = byTag(root, "source_hub_card_shinigami");
        assertTrue("kartu berada dalam satu kolom",
                cards != null && cards.getParent() instanceof ViewGroup);
        ViewGroup column = (ViewGroup) cards.getParent();
        int shinigami = column.indexOfChild(cards);
        int komiku = column.indexOfChild(byTag(root, "source_hub_card_komiku"));
        int otakudesu = column.indexOfChild(byTag(root, "source_hub_card_otakudesu"));

        assertTrue("Komiku setelah Manga Server 2", komiku > shinigami);
        assertTrue("Anime Streaming setelah Komiku", otakudesu > komiku);
    }
}