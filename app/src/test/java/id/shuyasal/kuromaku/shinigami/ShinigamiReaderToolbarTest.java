package id.shuyasal.kuromaku.shinigami;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
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
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowPopupMenu;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Toolbar reader responsif pada layar kecil dan notifikasi offline yang muncul
 * sekali lalu hilang sendiri setelah 3 detik.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w320dp-h480dp-mdpi")
public class ShinigamiReaderToolbarTest {
    private static final String MANGA = "14423a1b-8d7e-41e2-af00-ba0ba655d5f9";
    private static final String CHAPTER = "d6cd3f14-a528-4752-a546-0d14873e9a4a";
    private static final String PAGE_URL = "https://storage.shngm.id/page.webp";

    private static ShinigamiSource.Transport transport;

    public static class Reader extends ShinigamiReaderActivity {
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
        ShinigamiQuality.AUTO.save(context);
        store = new ShinigamiStore(context);
        transport = url -> url.contains("/list")
                ? ShinigamiReaderTest.fixture("live_chapter_list.json")
                : ShinigamiReaderTest.fixture("live_chapter_detail.json");
    }

    private Intent intent() {
        return new Intent(context, Reader.class)
                .putExtra(ShinigamiReaderActivity.EXTRA_MANGA_ID, MANGA)
                .putExtra(ShinigamiReaderActivity.EXTRA_MANGA_TITLE, "Regressor Instruction Manual")
                .putExtra(ShinigamiReaderActivity.EXTRA_CHAPTER_ID, CHAPTER)
                .putExtra(ShinigamiReaderActivity.EXTRA_CHAPTER_NUMBER, 210d);
    }

    private void await(Reader reader) throws Exception {
        Field field = ShinigamiReaderActivity.class.getDeclaredField("worker");
        field.setAccessible(true);
        ((ExecutorService) field.get(reader)).submit(() -> {}).get(5, TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private View tagged(Reader reader, String tag) {
        View view = reader.getWindow().getDecorView().findViewWithTag(tag);
        assertNotNull(tag, view);
        return view;
    }

    private ActivityController<Reader> launch() throws Exception {
        ActivityController<Reader> controller =
                Robolectric.buildActivity(Reader.class, intent()).setup();
        await(controller.get());
        return controller;
    }

    /** Tulis halaman PNG valid supaya chapter terbaca sebagai offline. */
    private void storePages(int pages) throws Exception {
        List<String> urls = new ArrayList<>();
        for (int index = 0; index < pages; index++) urls.add(PAGE_URL + "?p=" + index);
        store.setChapterPages(CHAPTER, urls);
        File dir = new ShinigamiDownloader(context).chapterDir(CHAPTER);
        assertTrue(dir.isDirectory() || dir.mkdirs());
        for (int index = 0; index < pages; index++) {
            try (FileOutputStream out = new FileOutputStream(new File(dir, index + ".img"))) {
                Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888)
                        .compress(Bitmap.CompressFormat.PNG, 100, out);
            }
        }
    }

    @Test
    public void toolbarFitsOnSmallScreenWithoutHorizontalScrolling() throws Exception {
        try (ActivityController<Reader> controller = launch()) {
            Reader reader = controller.get();
            View bar = tagged(reader, "shinigami_reader_bar");
            int screen = reader.getResources().getDisplayMetrics().widthPixels;
            bar.measure(View.MeasureSpec.makeMeasureSpec(screen, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            bar.layout(0, 0, bar.getMeasuredWidth(), bar.getMeasuredHeight());

            assertTrue("toolbar tidak boleh melebihi layar: " + bar.getMeasuredWidth(),
                    bar.getMeasuredWidth() <= screen);

            int minTouch = Math.round(48 * reader.getResources().getDisplayMetrics().density);
            int total = 0;
            ViewGroup group = (ViewGroup) bar;
            for (int index = 0; index < group.getChildCount(); index++) {
                View child = group.getChildAt(index);
                total += child.getMeasuredWidth();
                if (child.getTag() != null && child.getTag().toString()
                        .startsWith("shinigami_reader_")
                        && !"shinigami_reader_title".equals(child.getTag())) {
                    assertTrue(child.getTag() + " minimal 48 dp",
                            child.getMeasuredWidth() >= minTouch);
                    assertTrue(child.getTag() + " minimal 48 dp",
                            child.getMeasuredHeight() >= minTouch);
                }
            }
            assertTrue("total lebar tombol + judul harus muat: " + total, total <= screen);

            TextView title = (TextView) tagged(reader, "shinigami_reader_title");
            assertEquals("Ch. 210", title.getText().toString());
            assertEquals(1, title.getMaxLines());
            assertNotNull("judul harus ellipsize", title.getEllipsize());
        }
    }

    @Test
    public void everyToolbarButtonIsReachable() throws Exception {
        try (ActivityController<Reader> controller = launch()) {
            Reader reader = controller.get();
            for (String tag : new String[]{"shinigami_reader_back", "shinigami_reader_chapters",
                    "shinigami_reader_fullscreen", "shinigami_reader_more"}) {
                View button = tagged(reader, tag);
                assertTrue(tag + " harus terlihat", button.getVisibility() == View.VISIBLE);
                assertTrue(tag + " harus clickable", button.isClickable());
                int[] location = new int[2];
                button.getLocationInWindow(location);
                assertTrue(tag + " keluar layar", location[0] >= 0
                        && location[0] + button.getWidth()
                        <= reader.getResources().getDisplayMetrics().widthPixels);
            }
        }
    }

    @Test
    public void fullscreenKeepsWorkingAndBackRestoresTheBar() throws Exception {
        try (ActivityController<Reader> controller = launch()) {
            Reader reader = controller.get();
            View bar = tagged(reader, "shinigami_reader_bar");
            tagged(reader, "shinigami_reader_fullscreen").performClick();
            assertEquals(View.GONE, bar.getVisibility());
            reader.onBackPressed();
            assertEquals(View.VISIBLE, bar.getVisibility());
            assertFalse(reader.isFinishing());
        }
    }

    @Test
    public void overflowCarriesQualitySettings() throws Exception {
        try (ActivityController<Reader> controller = launch()) {
            Reader reader = controller.get();
            tagged(reader, "shinigami_reader_more").performClick();
            android.widget.PopupMenu popup = ShadowPopupMenu.getLatestPopupMenu();
            assertNotNull("overflow harus terbuka", popup);
            assertEquals(4, popup.getMenu().size());

            popup.getMenu().performIdentifierAction(1, 0);
            android.app.AlertDialog dialog = (android.app.AlertDialog) ShadowDialog.getLatestDialog();
            assertNotNull("dialog kualitas harus muncul", dialog);
            dialog.getListView().performItemClick(null, 2, 2);
            assertEquals(ShinigamiQuality.HD, ShinigamiQuality.stored(context));
        }
    }

    @Test
    public void chapterListOpensFromToolbar() throws Exception {
        try (ActivityController<Reader> controller = launch()) {
            Reader reader = controller.get();
            tagged(reader, "shinigami_reader_chapters").performClick();
            await(reader);
            android.app.AlertDialog dialog = (android.app.AlertDialog) ShadowDialog.getLatestDialog();
            assertNotNull("dialog daftar chapter", dialog);
            assertTrue("harus berisi chapter",
                    dialog.getListView() != null && dialog.getListView().getAdapter().getCount() > 0);
        }
    }

    @Test
    public void offlineNoticeAppearsOnceAndFadesWithinThreeSeconds() throws Exception {
        storePages(3);
        try (ActivityController<Reader> controller = launch()) {
            Reader reader = controller.get();
            TextView notice = (TextView) tagged(reader, "shinigami_reader_offline_notice");
            assertEquals(View.VISIBLE, notice.getVisibility());
            assertTrue(notice.getText().toString(), notice.getText().toString()
                    .contains("Dibaca dari penyimpanan offline"));

            // Scroll dan zoom tidak boleh memunculkannya lagi.
            RecyclerView list = (RecyclerView) tagged(reader, "shinigami_reader_list");
            list.scrollBy(0, 400);
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            Shadows.shadowOf(Looper.getMainLooper()).idleFor(
                    java.time.Duration.ofMillis(3600));
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(
                    java.time.Duration.ofMillis(400));
            assertTrue("notifikasi harus hilang otomatis",
                    notice.getVisibility() == View.GONE || notice.getAlpha() == 0f);

            list.scrollBy(0, -400);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals("tidak boleh muncul lagi setelah scroll",
                    View.GONE, notice.getVisibility());
        }
    }

    @Test
    public void noticeStaysHiddenAfterRotation() throws Exception {
        storePages(3);
        Bundle state;
        try (ActivityController<Reader> controller = launch()) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(
                    java.time.Duration.ofMillis(3600));
            state = new Bundle();
            controller.saveInstanceState(state);
        }
        try (ActivityController<Reader> controller =
                     Robolectric.buildActivity(Reader.class, intent()).setup(state)) {
            Reader reader = controller.get();
            await(reader);
            TextView notice = (TextView) tagged(reader, "shinigami_reader_offline_notice");
            assertEquals("setelah rotasi tidak boleh muncul lagi",
                    View.GONE, notice.getVisibility());
        }
    }

    @Test
    public void onlineChapterShowsNoOfflineNotice() throws Exception {
        try (ActivityController<Reader> controller = launch()) {
            Reader reader = controller.get();
            TextView notice = (TextView) tagged(reader, "shinigami_reader_offline_notice");
            assertEquals("chapter online tidak perlu notifikasi offline",
                    View.GONE, notice.getVisibility());
        }
    }
}
