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

    @Test public void chapterReleaseDateUsesTheUserTimezone() throws Exception {
        java.util.TimeZone previous = java.util.TimeZone.getDefault();
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Jakarta"));
        try (ActivityController<Detail> controller = Robolectric.buildActivity(Detail.class, intent()).setup()) {
            Detail detail = controller.get(); await(detail);
            LinearLayout list = (LinearLayout) tagged(detail, "shinigami_chapter_list");
            TextView label = list.getChildAt(0).findViewWithTag("shinigami_chapter_label");
            assertTrue(label.getText().toString(), label.getText().toString().contains("Rilis: 2026-11-05 10:24"));
        } finally {
            java.util.TimeZone.setDefault(previous);
        }
    }

    @Test public void mangaUpdateDateIsShownSeparatelyFromChapterRelease() throws Exception {
        java.util.TimeZone previous = java.util.TimeZone.getDefault();
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Jakarta"));
        try (ActivityController<Detail> controller = Robolectric.buildActivity(Detail.class, intent()).setup()) {
            Detail detail = controller.get(); await(detail);
            String meta = ((TextView) tagged(detail, "shinigami_detail_meta")).getText().toString();
            assertTrue(meta, meta.contains("Rilis chapter: 2026-11-05 10:24"));
            assertTrue(meta, meta.contains("Diperbarui: 2026-11-05 10:39"));
        } finally {
            java.util.TimeZone.setDefault(previous);
        }
    }

    @Test public void chapterMissingReleaseFallsBackToLabeledUpdate() throws Exception {
        org.json.JSONObject payload = new org.json.JSONObject(ShinigamiReaderTest.fixture("live_chapter_list.json"));
        org.json.JSONObject first = payload.getJSONArray("data").getJSONObject(0);
        first.put("release_date", org.json.JSONObject.NULL);
        first.put("updated_at", "2026-11-05T03:39:16Z");
        String body = payload.toString();
        transport = url -> url.contains("/list") ? body : route(url);
        java.util.TimeZone previous = java.util.TimeZone.getDefault();
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        try (ActivityController<Detail> controller = Robolectric.buildActivity(Detail.class, intent()).setup()) {
            Detail detail = controller.get(); await(detail);
            LinearLayout list = (LinearLayout) tagged(detail, "shinigami_chapter_list");
            TextView label = list.getChildAt(0).findViewWithTag("shinigami_chapter_label");
            assertTrue(label.getText().toString(), label.getText().toString().contains("Diperbarui: 2026-11-05 03:39"));
        } finally { java.util.TimeZone.setDefault(previous); }
    }

    @Test public void chapterRowsShowActualStorageStateSeparateFromReadProgress() throws Exception {
        String id = new org.json.JSONObject(ShinigamiReaderTest.fixture("live_chapter_list.json"))
                .getJSONArray("data").getJSONObject(0).getString("chapter_id");
        ShinigamiStore store = new ShinigamiStore(context);
        store.setProgress(id, 100);
        store.setChapterPages(id, java.util.Collections.singletonList("https://assets.shngm.id/chapter/0.png"));
        ShinigamiDownloader downloader = new ShinigamiDownloader(context);
        downloader.deleteChapter(id);
        try (ActivityController<Detail> controller = Robolectric.buildActivity(Detail.class, intent()).setup()) {
            Detail detail = controller.get(); await(detail);
            View row = tagged(detail, "shinigami_chapter_" + id);
            assertEquals("100%", ((TextView) row.findViewWithTag("shinigami_chapter_progress")).getText().toString());
            assertEquals("Belum diunduh", ((TextView) row.findViewWithTag("shinigami_chapter_download")).getText().toString());
            downloader.setRunning(id, true);
            controller.pause().resume();
            row = tagged(detail, "shinigami_chapter_" + id);
            assertEquals("Mengunduh...", ((TextView) row.findViewWithTag("shinigami_chapter_download")).getText().toString());
            downloader.setRunning(id, false);
            downloader.chapterDir(id).mkdirs();
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(new java.io.File(downloader.chapterDir(id), "0.img"))) {
                android.graphics.Bitmap.createBitmap(50, 50, android.graphics.Bitmap.Config.ARGB_8888)
                        .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
            }
            controller.pause().resume();
            row = tagged(detail, "shinigami_chapter_" + id);
            assertEquals("Tersimpan offline", ((TextView) row.findViewWithTag("shinigami_chapter_download")).getText().toString());
        } finally { downloader.setRunning(id, false); downloader.deleteChapter(id); }
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
