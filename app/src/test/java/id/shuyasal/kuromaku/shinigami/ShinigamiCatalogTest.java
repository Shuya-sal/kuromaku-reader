package id.shuyasal.kuromaku.shinigami;

import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
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
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w320dp-h640dp-mdpi")
public class ShinigamiCatalogTest {
    static ShinigamiSource.Transport transport;
    final List<String> requests = java.util.Collections.synchronizedList(new ArrayList<>());
    public static class Catalog extends ShinigamiActivity {
        @Override protected ShinigamiSource createSource() { return new ShinigamiSource(this, transport); }
    }
    @Before public void setup() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(ShinigamiStore.FILE_NAME, 0).edit().clear().commit();
        context.getSharedPreferences(ShinigamiConfig.FILE_NAME, 0).edit().clear().commit();
        transport = url -> {
            requests.add(url);
            if (url.contains("/genre/")) return "{\"retcode\":0,\"data\":[]}";
            return ShinigamiReaderTest.fixture("live_manga_list.json");
        };
    }
    void await(Catalog host) throws Exception {
        Field field = ShinigamiActivity.class.getDeclaredField("worker"); field.setAccessible(true);
        ((ExecutorService) field.get(host)).submit(() -> {}).get(5, TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    View tagged(Catalog host, String tag) {
        View found = host.getWindow().getDecorView().findViewWithTag(tag);
        assertNotNull(tag, found); return found;
    }
    void invoke(Catalog host, String method) throws Exception {
        java.lang.reflect.Method m = ShinigamiActivity.class.getDeclaredMethod(method);
        m.setAccessible(true); m.invoke(host);
    }
    String lastList() {
        String last = "";
        for (String request : requests) if (request.contains("/manga/list")) last = request;
        return last;
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    View bindFirst(Catalog host) {
        RecyclerView list = (RecyclerView) tagged(host, "shinigami_grid");
        RecyclerView.Adapter adapter = list.getAdapter();
        assertNotNull(adapter); assertTrue(adapter.getItemCount() > 0);
        RecyclerView.ViewHolder holder = adapter.createViewHolder(list, 0);
        adapter.bindViewHolder(holder, 0); return holder.itemView;
    }
    @Test public void restoredSortSearchAndFiltersReachTheSource() throws Exception {
        Bundle state = new Bundle();
        state.putString("shinigami_sort", "rank"); state.putString("shinigami_query", "solo leveling");
        state.putString("shinigami_genre", "action"); state.putString("shinigami_status", "ongoing");
        try (ActivityController<Catalog> controller = Robolectric.buildActivity(Catalog.class).create(state).start().resume().visible()) {
            Catalog host = controller.get(); await(host);
            String request = lastList();
            assertTrue(request, request.contains("sort=rank"));
            assertTrue(request, request.contains("q=solo%20leveling"));
            assertTrue(request, request.contains("genre=action"));
            assertTrue(request, request.contains("status=ongoing"));
            assertNotNull(tagged(host, "shinigami_chip_Terpopuler"));
            assertEquals("solo leveling", ((TextView) tagged(host, "shinigami_search")).getText().toString());
        }
    }
    @Test public void catalogueCardShowsLastChapterUpdateInUserTimezone() throws Exception {
        java.util.TimeZone previous = java.util.TimeZone.getDefault();
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Jakarta"));
        try (ActivityController<Catalog> controller = Robolectric.buildActivity(Catalog.class).setup()) {
            Catalog host = controller.get(); await(host);
            assertNotNull(tagged(host, "shinigami_chip_Manga Terbaru"));
            TextView info = bindFirst(host).findViewWithTag("shinigami_subtitle");
            assertTrue(info.getText().toString(), info.getText().toString().contains("Chapter terbaru: 210"));
            // Baris tanggal kini ringkas dan berdiri sendiri agar tidak terpotong
            // pada kartu dua kolom: "Update 5 Nov 2026".
            assertTrue(info.getText().toString(),
                    info.getText().toString().contains("Update 5 Nov 2026"));
        } finally { java.util.TimeZone.setDefault(previous); }
    }
    @Test public void latestChaptersUseLatestSortWithoutLosingFilters() throws Exception {
        try (ActivityController<Catalog> controller = Robolectric.buildActivity(Catalog.class).setup()) {
            Catalog host = controller.get(); await(host);
            tagged(host, "shinigami_chip_Berjalan").performClick(); await(host);
            tagged(host, "shinigami_chip_Chapter Terbaru").performClick(); await(host);
            assertTrue(lastList(), lastList().contains("sort=latest"));
            assertTrue(lastList(), lastList().contains("status=ongoing"));
            TextView info = bindFirst(host).findViewWithTag("shinigami_row_subtitle");
            assertTrue(info.getText().toString(), info.getText().toString().contains("Rilis:"));
        }
    }
    @Test public void lateCatalogueResponseDoesNotReplaceHistory() throws Exception {
        try (ActivityController<Catalog> controller = Robolectric.buildActivity(Catalog.class).setup()) {
            Catalog host = controller.get();
            tagged(host, "shinigami_chip_Riwayat").performClick(); await(host);
            RecyclerView list = (RecyclerView) tagged(host, "shinigami_grid");
            assertEquals(0, list.getAdapter().getItemCount());
        }
    }
    @Test public void repeatedIdsDuringPaginationDoNotDuplicateCards() throws Exception {
        try (ActivityController<Catalog> controller = Robolectric.buildActivity(Catalog.class).setup()) {
            Catalog host = controller.get(); await(host);
            RecyclerView list = (RecyclerView) tagged(host, "shinigami_grid");
            int count = list.getAdapter().getItemCount();
            invoke(host, "loadMore"); await(host);
            assertEquals(count, list.getAdapter().getItemCount());
            assertTrue(lastList(), lastList().contains("page=2"));
        }
    }
    @Test public void failedRefreshPreservesWorkingCards() throws Exception {
        try (ActivityController<Catalog> controller = Robolectric.buildActivity(Catalog.class).setup()) {
            Catalog host = controller.get(); await(host);
            RecyclerView list = (RecyclerView) tagged(host, "shinigami_grid");
            int count = list.getAdapter().getItemCount();
            transport = url -> { throw new java.io.IOException("network down"); };
            // Source captures transport at construction, so use a new failing source.
            Field field = ShinigamiActivity.class.getDeclaredField("source"); field.setAccessible(true);
            field.set(host, new ShinigamiSource(host, transport));
            invoke(host, "reload"); await(host);
            assertEquals(count, list.getAdapter().getItemCount());
        }
    }

    @Test public void downloadsTabStartsEmptyAndExplainsHowToSave() throws Exception {
        try (ActivityController<Catalog> controller = Robolectric.buildActivity(Catalog.class).setup()) {
            Catalog host = controller.get(); await(host);
            tagged(host, "shinigami_chip_Unduhan").performClick(); await(host);
            assertEquals(View.VISIBLE, tagged(host, "shinigami_downloads_empty").getVisibility());
            RecyclerView list = (RecyclerView) tagged(host, "shinigami_grid");
            assertEquals(0, list.getAdapter().getItemCount());
            assertTrue(((TextView) tagged(host, "shinigami_status")).getText().toString()
                    .contains("Belum ada chapter tersimpan"));
        }
    }

    @Test public void toolbarHasDownloadButtonBeforeSettings() throws Exception {
        try (ActivityController<Catalog> controller = Robolectric.buildActivity(Catalog.class).setup()) {
            Catalog host = controller.get(); await(host);
            View downloads = tagged(host, "shinigami_downloads_button");
            assertNotNull(downloads);
            android.view.ViewGroup header = (android.view.ViewGroup) downloads.getParent();
            int downloadIndex = header.indexOfChild(downloads);
            int settingsIndex = -1;
            for (int i = 0; i < header.getChildCount(); i++) {
                CharSequence description = header.getChildAt(i).getContentDescription();
                if (description != null && description.toString().contains("Pengaturan")) settingsIndex = i;
            }
            assertTrue("ikon settings harus ada", settingsIndex >= 0);
            assertTrue("unduhan harus di kiri settings", downloadIndex < settingsIndex);
            int minTouch = id.shuyasal.kuromaku.ui.Ui.dp(host, id.shuyasal.kuromaku.ui.Ui.TOUCH_DP);
            assertTrue("touch target minimal 48dp",
                    downloads.getLayoutParams().width >= minTouch);

            downloads.performClick();
            android.content.Intent started = Shadows.shadowOf(host).getNextStartedActivity();
            assertNotNull(started);
            assertEquals(ShinigamiDownloadsActivity.class.getName(),
                    started.getComponent().getClassName());
        }
    }
}
