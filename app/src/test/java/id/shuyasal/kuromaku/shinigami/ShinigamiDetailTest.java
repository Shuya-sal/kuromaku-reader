package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

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
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w320dp-h640dp-mdpi")
public class ShinigamiDetailTest {
    static final String MANGA = "14423a1b-8d7e-41e2-af00-ba0ba655d5f9";
    static ShinigamiSource.Transport transport;

    public static class Detail extends ShinigamiDetailActivity {
        @Override protected ShinigamiSource createSource() {
            return new ShinigamiSource(this, transport);
        }
    }

    Context context;

    @Before public void setup() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(ShinigamiStore.FILE_NAME, 0).edit().clear().commit();
        transport = ShinigamiDetailTest::route;
    }

    static String route(String url) throws java.io.IOException {
        if (url.contains("/manga/detail/")) return ShinigamiReaderTest.fixture("live_manga_detail.json");
        if (url.contains("/chapter/") && url.contains("/list")) return ShinigamiReaderTest.fixture("live_chapter_list.json");
        throw new java.io.IOException("unexpected " + url);
    }

    Intent intent() {
        return new Intent(context, Detail.class)
                .putExtra(ShinigamiDetailActivity.EXTRA_MANGA_ID, MANGA)
                .putExtra(ShinigamiDetailActivity.EXTRA_MANGA_TITLE, "Regressor Instruction Manual");
    }

    void await(Detail detail) throws Exception {
        Field field = ShinigamiDetailActivity.class.getDeclaredField("worker");
        field.setAccessible(true);
        ((ExecutorService) field.get(detail)).submit(() -> {}).get(5, TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    View tagged(Detail detail, String tag) {
        View view = detail.getWindow().getDecorView().findViewWithTag(tag);
        assertNotNull(tag, view);
        return view;
    }

    @Test public void realPayloadShowsSynopsisGenreStatusAndChapters() throws Exception {
        try (ActivityController<Detail> controller = Robolectric.buildActivity(Detail.class, intent()).setup()) {
            Detail detail = controller.get(); await(detail);
            String meta = ((TextView) tagged(detail, "shinigami_detail_meta")).getText().toString();
            assertTrue(meta, meta.contains("Genre:"));
            assertTrue(meta, meta.contains("Berjalan") || meta.contains("Tamat") || meta.contains("Status"));
            String synopsis = ((TextView) tagged(detail, "shinigami_detail_synopsis")).getText().toString();
            assertTrue(synopsis.length() > 20);
            assertEquals(3, ((LinearLayout) tagged(detail, "shinigami_chapter_list")).getChildCount());
        }
    }

    @Test public void failedChapterListOffersRetryAndRetryDoesNotDuplicate() throws Exception {
        AtomicBoolean failChapters = new AtomicBoolean(true);
        transport = url -> {
            if (url.contains("/list") && failChapters.get()) {
                throw new ShinigamiSource.SourceException("Sumber membatasi akses (HTTP 403).");
            }
            return route(url);
        };
        try (ActivityController<Detail> controller = Robolectric.buildActivity(Detail.class, intent()).setup()) {
            Detail detail = controller.get(); await(detail);
            assertTrue(((TextView) tagged(detail, "shinigami_detail_error")).getText().toString().contains("403"));
            assertEquals(View.VISIBLE, tagged(detail, "shinigami_detail_retry").getVisibility());

            failChapters.set(false);
            tagged(detail, "shinigami_detail_retry").performClick(); await(detail);
            assertEquals(3, ((LinearLayout) tagged(detail, "shinigami_chapter_list")).getChildCount());
            assertEquals(View.GONE, tagged(detail, "shinigami_detail_error").getVisibility());

            tagged(detail, "shinigami_detail_retry").performClick(); await(detail);
            assertEquals(3, ((LinearLayout) tagged(detail, "shinigami_chapter_list")).getChildCount());
        }
    }
}
