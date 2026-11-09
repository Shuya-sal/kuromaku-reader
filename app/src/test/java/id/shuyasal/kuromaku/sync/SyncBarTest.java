package id.shuyasal.kuromaku.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import id.shuyasal.kuromaku.ui.Palette;

/**
 * Bar sinkron harus menyatakan keadaan sebenarnya: belum dipasangkan tetap
 * tertulis demikian, dan setelah dipasangkan menyebut sumber pasangannya.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SyncBarTest {

    private Context context;
    private Palette palette;
    private SyncStore sync;

    @Before public void siapkan() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(SyncStore.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        palette = Palette.of(0);
        sync = new SyncStore(context);
    }

    private SyncBar bar(String sourceId) {
        return new SyncBar(context, palette, new SyncBar.Self(sourceId, "manga-1",
                "Black Clover", "", "https://komiku.org/manga/black-clover-indonesia/"));
    }

    @Test public void tanpaPasanganMenulisBelumDipasangkan() {
        View view = bar(SyncBar.SOURCE_KOMIKU).build();
        TextView text = view.findViewWithTag("sync_bar_text");
        Button action = view.findViewWithTag("sync_bar_action");

        assertTrue("jujur soal keadaan: " + text.getText(),
                text.getText().toString().startsWith("Belum dipasangkan"));
        assertEquals("Cari pasangan di Manga Server 2", action.getText().toString());
    }

    @Test public void setelahDipasangkanMenyebutSumberLain() {
        sync.link(SyncStore.SOURCE_KOMIKU, "manga-1", "Black Clover",
                "https://komiku.org/manga/black-clover-indonesia/",
                SyncStore.SOURCE_SHINIGAMI, "shini-1", "Black Clover (Shinigami)", "", "manual");

        View view = bar(SyncBar.SOURCE_KOMIKU).build();
        TextView text = view.findViewWithTag("sync_bar_text");
        Button action = view.findViewWithTag("sync_bar_action");

        assertTrue("menyebut Manga Server 2: " + text.getText(),
                text.getText().toString().contains("Tersinkron dengan Manga Server 2"));
        assertEquals("Ubah pasangan", action.getText().toString());
    }

    @Test public void sisiServer2MenunjukServer3() {
        sync.link(SyncStore.SOURCE_SHINIGAMI, "manga-1", "Black Clover", "",
                SyncStore.SOURCE_KOMIKU, "black-clover-indonesia", "Black Clover", "", "manual");

        View view = bar(SyncBar.SOURCE_SHINIGAMI).build();
        TextView text = view.findViewWithTag("sync_bar_text");
        assertTrue("dari sisi Server 2: " + text.getText(),
                text.getText().toString().contains("Tersinkron dengan Manga Server 3"));
    }

    @Test public void lepasTautanMengembalikanTeksAwal() {
        sync.link(SyncStore.SOURCE_KOMIKU, "manga-1", "Black Clover", "",
                SyncStore.SOURCE_SHINIGAMI, "shini-1", "Black Clover", "", "manual");
        new SyncBridge(context).unlink(SyncStore.SOURCE_KOMIKU, "manga-1");

        View view = bar(SyncBar.SOURCE_KOMIKU).build();
        TextView text = view.findViewWithTag("sync_bar_text");
        assertTrue(text.getText().toString().startsWith("Belum dipasangkan"));
    }

    @Test public void barAdaDiDalamSatuBarisSendiri() {
        View view = bar(SyncBar.SOURCE_KOMIKU).build();
        assertTrue("view berupa wadah", view instanceof ViewGroup);
        assertEquals("sync_bar", view.getTag());
    }
}