package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Looper;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
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

import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Beranda memakai satu alur scroll: rail riwayat adalah item pertama pada
 * RecyclerView katalog, jadi ikut keluar layar saat digulir dan kembali di
 * posisi atas. Katalog tetap bisa mencapai item terakhir.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w360dp-h640dp-mdpi")
public class ShinigamiHomeScrollTest {
    private static ShinigamiSource.Transport transport;

    public static class Catalog extends ShinigamiActivity {
        @Override protected ShinigamiSource createSource() {
            return new ShinigamiSource(this, transport);
        }
    }

    private Context context;
    private ShinigamiStore store;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(ShinigamiStore.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.getSharedPreferences(ShinigamiConfig.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        store = new ShinigamiStore(context);
        transport = url -> {
            if (url.contains("/genre/")) return "{\"retcode\":0,\"data\":[]}";
            return ShinigamiReaderTest.fixture("live_manga_list_big.json");
        };
    }

    private ActivityController<Catalog> controller;

    private Catalog launch() throws Exception {
        controller = Robolectric.buildActivity(Catalog.class).setup();
        Catalog host = controller.get();
        Field field = ShinigamiActivity.class.getDeclaredField("worker");
        field.setAccessible(true);
        ((ExecutorService) field.get(host)).submit(() -> {}).get(5, TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        layout(host);
        return host;
    }

    /** Ukur dan letakkan layar (viewport pendek) supaya isi melebihi layar. */
    private void layout(Catalog host) {
        View decor = host.getWindow().getDecorView();
        int width = host.getResources().getDisplayMetrics().widthPixels;
        int height = 300;
        decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, width, height);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private View tagged(Catalog host, String tag) {
        View view = host.getWindow().getDecorView().findViewWithTag(tag);
        assertNotNull(tag, view);
        return view;
    }

    @Test
    public void railScrollsOutWithTheCatalogueAndComesBack() throws Exception {
        store.recordRead("manga-a", "Judul A", "", "chapter-a", 7);
        Catalog host = launch();
        RecyclerView list = (RecyclerView) tagged(host, "shinigami_grid");
        View rail = tagged(host, "shinigami_continue_rail");
        LinearLayoutManager manager = (LinearLayoutManager) list.getLayoutManager();

        assertEquals("rail adalah item pertama", 0, list.getChildAdapterPosition(rail));
        assertTrue("katalog harus bisa digulir", list.canScrollVertically(1));

        manager.scrollToPosition(list.getAdapter().getItemCount() - 1);
        layout(host);

        // Rail (item 0) sudah jauh di atas viewport: tidak menempel di layar.
        assertTrue("item pertama yang terlihat harus katalog: "
                        + manager.findFirstVisibleItemPosition(),
                manager.findFirstVisibleItemPosition() >= 1);
        // Posisi rail berada di atas item pertama yang terlihat => tidak menempel.
        int railPosition = list.getChildAdapterPosition(rail);
        assertTrue("rail tidak boleh lagi menempel di atas",
                railPosition == RecyclerView.NO_POSITION
                        || railPosition < manager.findFirstVisibleItemPosition());

        manager.scrollToPosition(0);
        layout(host);
        assertEquals("rail kembali di posisi paling atas", 0,
                list.getChildAdapterPosition(tagged(host, "shinigami_continue_rail")));
        assertEquals(0, manager.findFirstVisibleItemPosition());
    }

    @Test
    public void catalogueCanReachItsLastItem() throws Exception {
        store.recordRead("manga-a", "Judul A", "", "chapter-a", 7);
        Catalog host = launch();
        RecyclerView list = (RecyclerView) tagged(host, "shinigami_grid");
        LinearLayoutManager manager = (LinearLayoutManager) list.getLayoutManager();

        int last = list.getAdapter().getItemCount() - 1;
        manager.scrollToPosition(last);
        layout(host);

        assertFalse("katalog harus bisa digulir sampai bawah", list.canScrollVertically(1));
        assertEquals("item terakhir harus terlihat", last,
                manager.findLastVisibleItemPosition());
        View lastChild = list.getChildAt(list.getChildCount() - 1);
        assertNotNull(lastChild);
        assertTrue("item terakhir harus utuh, tidak terpotong",
                lastChild.getBottom() <= list.getHeight() + 1);
    }

    @Test
    public void railLivesInsideTheCatalogueList() throws Exception {
        store.recordRead("manga-a", "Judul A", "", "chapter-a", 7);
        Catalog host = launch();
        RecyclerView list = (RecyclerView) tagged(host, "shinigami_grid");
        View rail = tagged(host, "shinigami_continue_rail");

        // Satu RecyclerView: tidak ada nested scrolling yang memotong katalog.
        assertTrue("rail harus menjadi item pada daftar katalog", rail.getParent() == list);
        assertEquals("rail adalah satu item header", 1,
                list.getAdapter().getItemCount() - 12);
    }
}
