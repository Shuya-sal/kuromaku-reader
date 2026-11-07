package id.shuyasal.kuromaku.shinigami;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w320dp-h640dp-mdpi")
public class ShinigamiReaderTest {
    static ShinigamiSource.Transport transport;
    public static class Reader extends ShinigamiReaderActivity {
        @Override protected ShinigamiSource createSource() {
            return new ShinigamiSource(this, transport);
        }
    }
    Context context;
    @Before public void setup() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(ShinigamiStore.FILE_NAME, 0).edit().clear().commit();
        transport = url -> fixture("live_chapter_detail.json");
    }
    static String fixture(String name) throws java.io.IOException {
        try (InputStream in = ShinigamiReaderTest.class.getResourceAsStream("/shinigami/" + name)) {
            if (in == null) throw new java.io.IOException(name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toString("UTF-8");
        }
    }
    Intent intent() {
        return new Intent(context, Reader.class)
            .putExtra(EXTRA_MANGA(), "14423a1b-8d7e-41e2-af00-ba0ba655d5f9")
            .putExtra(ShinigamiReaderActivity.EXTRA_MANGA_TITLE, "Regressor Instruction Manual")
            .putExtra(ShinigamiReaderActivity.EXTRA_CHAPTER_ID, "d6cd3f14-a528-4752-a546-0d14873e9a4a")
            .putExtra(ShinigamiReaderActivity.EXTRA_CHAPTER_NUMBER, 210d);
    }
    String EXTRA_MANGA() { return ShinigamiReaderActivity.EXTRA_MANGA_ID; }
    void await(Reader reader) throws Exception {
        Field field = ShinigamiReaderActivity.class.getDeclaredField("worker");
        field.setAccessible(true);
        ((ExecutorService)field.get(reader)).submit(() -> {}).get(5, TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    View tagged(Reader reader, String tag) {
        View view = reader.getWindow().getDecorView().findViewWithTag(tag);
        assertNotNull(tag, view); return view;
    }
    @Test public void realChapterPayloadCreatesOrderedNativePagesAndHistory() throws Exception {
        try (ActivityController<Reader> controller = Robolectric.buildActivity(Reader.class, intent()).setup()) {
            Reader reader = controller.get(); await(reader);
            RecyclerView list = (RecyclerView)tagged(reader, "shinigami_reader_list");
            assertEquals(18, list.getAdapter().getItemCount());
            assertEquals("d6cd3f14-a528-4752-a546-0d14873e9a4a", new ShinigamiStore(context).history().get(0).chapterId);
        }
    }
    @Test public void readerHasSharedDrawerWithFiveEntries() throws Exception {
        try (ActivityController<Reader> controller = Robolectric.buildActivity(Reader.class, intent()).setup()) {
            Reader reader = controller.get(); await(reader);
            // Toolbar reader tidak lagi memuat tombol drawer: menu utama ada di overflow.
            tagged(reader, "shinigami_reader_more").performClick();
            android.widget.PopupMenu popup =
                    org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu();
            assertNotNull("overflow harus terbuka", popup);
            popup.getMenu().performIdentifierAction(4, 0);
            assertNotNull(ShadowDialog.getLatestDialog().findViewById(android.R.id.content).findViewWithTag("drawer_manga_shinigami"));
            assertNotNull(ShadowDialog.getLatestDialog().findViewById(android.R.id.content).findViewWithTag("drawer_manga_voratoon"));
        }
    }
    @Test public void backExitsFullscreenBeforeLeavingReader() throws Exception {
        try (ActivityController<Reader> controller = Robolectric.buildActivity(Reader.class, intent()).setup()) {
            Reader reader = controller.get(); await(reader);
            tagged(reader, "shinigami_reader_fullscreen").performClick();
            assertEquals(View.GONE, tagged(reader, "shinigami_reader_fullscreen").getParent() instanceof View
                    ? ((View)tagged(reader, "shinigami_reader_fullscreen").getParent()).getVisibility() : -1);
            reader.onBackPressed();
            assertFalse(reader.isFinishing());
            assertEquals(View.VISIBLE, ((View)tagged(reader, "shinigami_reader_fullscreen").getParent()).getVisibility());
        }
    }
    void layout(Reader reader) {
        View root = reader.getWindow().getDecorView();
        root.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 320, 640);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    @Test public void pagesAreBoundLazilyNotAllAtOnce() throws Exception {
        try (ActivityController<Reader> controller = Robolectric.buildActivity(Reader.class, intent()).setup().visible()) {
            Reader reader = controller.get(); await(reader); layout(reader);
            RecyclerView list = (RecyclerView)tagged(reader, "shinigami_reader_list");
            assertEquals(18, list.getAdapter().getItemCount());
            assertTrue("halaman harus dimuat bertahap, terikat: " + list.getChildCount(),
                    list.getChildCount() > 0 && list.getChildCount() < 18);
        }
    }
    @Test public void savedPageIsRestoredAndProgressIsWrittenOnPause() throws Exception {
        ShinigamiStore store = new ShinigamiStore(context);
        store.setPage("d6cd3f14-a528-4752-a546-0d14873e9a4a", 5);
        try (ActivityController<Reader> controller = Robolectric.buildActivity(Reader.class, intent()).setup().visible()) {
            Reader reader = controller.get(); await(reader); layout(reader);
            RecyclerView list = (RecyclerView)tagged(reader, "shinigami_reader_list");
            androidx.recyclerview.widget.LinearLayoutManager manager =
                    (androidx.recyclerview.widget.LinearLayoutManager) list.getLayoutManager();
            assertEquals(5, manager.findFirstVisibleItemPosition());
            controller.pause();
            assertEquals(5, store.page("d6cd3f14-a528-4752-a546-0d14873e9a4a"));
            // Progres membaca kini dihitung dari jarak gulir dan panjang konten,
            // bukan dari (halaman + 1) / jumlah halaman. Angkanya tetap di dalam
            // rentang 0-100 dan tetap lebih dari nol setelah menggulir jauh.
            int progress = store.progress("d6cd3f14-a528-4752-a546-0d14873e9a4a");
            assertTrue("progres harus dihitung", progress > 0);
            assertTrue("progres harus 0-100", progress <= 100);
        }
    }
    @Test public void connectionFailureUsesCachedPageOrder() throws Exception {
        String id = "d6cd3f14-a528-4752-a546-0d14873e9a4a";
        new ShinigamiStore(context).setChapterPages(id, java.util.Arrays.asList(
                "https://storage.shngm.id/one.webp", "https://storage.shngm.id/two.webp"));
        transport = url -> { throw new ShinigamiSource.SourceException("Koneksi terputus.", new java.io.IOException("disconnect")); };
        try (ActivityController<Reader> controller = Robolectric.buildActivity(Reader.class, intent()).setup()) {
            Reader reader=controller.get(); await(reader);
            assertEquals(2, ((RecyclerView)tagged(reader,"shinigami_reader_list")).getAdapter().getItemCount());
        }
    }
    @Test public void deniedSourceShowsErrorAndDoesNotRecordRead() throws Exception {
        transport = url -> { throw new ShinigamiSource.SourceException("Sumber membatasi akses HTTP 403. Tidak melewati CAPTCHA."); };
        try (ActivityController<Reader> controller = Robolectric.buildActivity(Reader.class, intent()).setup()) {
            Reader reader = controller.get(); await(reader);
            assertTrue(((TextView)tagged(reader, "shinigami_reader_error")).getText().toString().contains("403"));
            assertEquals(View.VISIBLE, tagged(reader, "shinigami_reader_retry").getVisibility());
            assertTrue(new ShinigamiStore(context).history().isEmpty());
        }
    }
}
