package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
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
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tab beranda: label permanen, kursor pagination terpisah per kategori, dan
 * header yang menyusut saat scroll turun lalu kembali saat scroll naik.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w360dp-h640dp-mdpi")
public class ShinigamiTabsTest {

    static ShinigamiSource.Transport transport;
    final List<String> requests = java.util.Collections.synchronizedList(new ArrayList<>());

    public static class Catalog extends ShinigamiActivity {
        @Override protected ShinigamiSource createSource() { return new ShinigamiSource(this, transport); }
    }

    @Before public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(ShinigamiStore.FILE_NAME, 0).edit().clear().commit();
        context.getSharedPreferences(ShinigamiConfig.FILE_NAME, 0).edit().clear().commit();
        requests.clear();
        transport = url -> {
            requests.add(url);
            if (url.contains("/genre/")) return "{\"retcode\":0,\"data\":[]}";
            return ShinigamiReaderTest.fixture("live_manga_list.json");
        };
    }

    private void await(ShinigamiActivity host) throws Exception {
        Field field = ShinigamiActivity.class.getDeclaredField("worker");
        field.setAccessible(true);
        ((ExecutorService) field.get(host)).submit(() -> {}).get(5, TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private View tagged(ShinigamiActivity host, String tag) {
        View found = host.getWindow().getDecorView().findViewWithTag(tag);
        assertNotNull(tag, found);
        return found;
    }

    private int listRequestCount() {
        int count = 0;
        for (String request : requests) if (request.contains("/manga/list")) count++;
        return count;
    }

    private String lastListRequest() {
        String last = "";
        for (String request : requests) if (request.contains("/manga/list")) last = request;
        return last;
    }

    @Test public void tabLabelsNeverChangeWhenPressed() throws Exception {
        try (ActivityController<Catalog> controller =
                     Robolectric.buildActivity(Catalog.class).setup()) {
            Catalog host = controller.get();
            await(host);

            String[] wanted = {"Terpopuler", "Manga Terbaru", "Chapter Terbaru", "Riwayat", "Favorit"};
            for (String label : wanted) {
                View chip = tagged(host, "shinigami_chip_" + label);
                assertEquals(label, ((TextView) chip).getText().toString());
            }

            tagged(host, "shinigami_chip_Manga Terbaru").performClick();
            await(host);
            assertEquals("Manga Terbaru",
                    ((TextView) tagged(host, "shinigami_chip_Manga Terbaru")).getText().toString());
            assertEquals("Terpopuler",
                    ((TextView) tagged(host, "shinigami_chip_Terpopuler")).getText().toString());

            tagged(host, "shinigami_chip_Terpopuler").performClick();
            await(host);
            assertEquals("Terpopuler",
                    ((TextView) tagged(host, "shinigami_chip_Terpopuler")).getText().toString());
        }
    }

    @Test public void eachCategoryAsksTheSourceForItsOwnMix() throws Exception {
        try (ActivityController<Catalog> controller =
                     Robolectric.buildActivity(Catalog.class).setup()) {
            Catalog host = controller.get();
            await(host);

            String popular = lastListRequest();
            assertTrue(popular, popular.contains("sort=rank"));
            assertTrue(popular, popular.contains("sort_order=desc"));

            tagged(host, "shinigami_chip_Manga Terbaru").performClick();
            await(host);
            String newestTitles = lastListRequest();
            assertNotSame(popular, newestTitles);
            assertTrue(newestTitles, newestTitles.contains("sort=rank"));
            assertTrue(newestTitles, newestTitles.contains("sort_order=asc"));

            tagged(host, "shinigami_chip_Chapter Terbaru").performClick();
            await(host);
            String newestChapters = lastListRequest();
            assertTrue(newestChapters, newestChapters.contains("sort=latest"));
            assertTrue(newestChapters, newestChapters.contains("sort_order=desc"));
        }
    }

    @Test public void returningToATabReusesItsDataAndCursor() throws Exception {
        try (ActivityController<Catalog> controller =
                     Robolectric.buildActivity(Catalog.class).setup()) {
            Catalog host = controller.get();
            await(host);

            RecyclerView list = (RecyclerView) tagged(host, "shinigami_grid");
            int popularCount = list.getAdapter().getItemCount();
            assertTrue(popularCount > 0);
            int requestsAfterPopular = listRequestCount();

            tagged(host, "shinigami_chip_Manga Terbaru").performClick();
            await(host);
            assertTrue("tab kedua harus meminta data sendiri",
                    listRequestCount() > requestsAfterPopular);

            int requestsBeforeReturn = listRequestCount();
            tagged(host, "shinigami_chip_Terpopuler").performClick();
            await(host);
            // Kembali ke tab yang sudah punya data tidak boleh memicu fetch baru.
            assertEquals(requestsBeforeReturn, listRequestCount());
            assertEquals(popularCount, list.getAdapter().getItemCount());
        }
    }

    @Test public void chromeCollapsesOnScrollDownAndReturnsOnScrollUp() throws Exception {
        try (ActivityController<Catalog> controller =
                     Robolectric.buildActivity(Catalog.class).setup()) {
            Catalog host = controller.get();
            await(host);

            View chrome = tagged(host, "shinigami_chrome");
            assertEquals(View.VISIBLE, chrome.getVisibility());

            Method update = ShinigamiActivity.class.getDeclaredMethod("updateChrome", int.class);
            update.setAccessible(true);
            update.invoke(host, 40);
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
            assertEquals("header harus tersembunyi saat scroll turun",
                    View.GONE, chrome.getVisibility());

            update.invoke(host, -40);
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
            assertEquals("header harus kembali saat scroll naik",
                    View.VISIBLE, chrome.getVisibility());
        }
    }

    @Test public void smallScrollDoesNotFlickerTheChrome() throws Exception {
        try (ActivityController<Catalog> controller =
                     Robolectric.buildActivity(Catalog.class).setup()) {
            Catalog host = controller.get();
            await(host);

            View chrome = tagged(host, "shinigami_chrome");
            Method update = ShinigamiActivity.class.getDeclaredMethod("updateChrome", int.class);
            update.setAccessible(true);
            // Gerakan kecil berulang ke arah sama belum melewati ambang 24 dp.
            for (int i = 0; i < 3; i++) {
                update.invoke(host, 4);
                update.invoke(host, -4);
            }
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
            assertEquals(View.VISIBLE, chrome.getVisibility());
        }
    }
}
