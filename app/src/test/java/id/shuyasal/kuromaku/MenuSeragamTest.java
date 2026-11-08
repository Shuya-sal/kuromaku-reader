package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
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
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;

import id.shuyasal.kuromaku.shinigami.ShinigamiConfig;
import id.shuyasal.kuromaku.shinigami.OfflineCatalog;
import id.shuyasal.kuromaku.shinigami.ShinigamiStore;

/**
 * Menu titik tiga yang seragam dan nama sumber yang tampil ke pengguna.
 *
 * <p>Yang dijaga: beranda Novel, Manga Server 2, dan Anime Streaming memakai
 * pola menu yang sama; pengaturan Manga Server 2 hanya memuat dua hal; dan nama
 * yang dibaca pengguna sudah memakai penomoran server tanpa mengubah sumbernya.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class MenuSeragamTest {

    private Context context;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(ShinigamiStore.FILE_NAME, 0).edit().clear().commit();
        context.getSharedPreferences(ShinigamiConfig.FILE_NAME, 0).edit().clear().commit();
    }

    // ------------------------------------------------------------- label

    @Test public void namaSumberMemakaiPenomoranServer() {
        assertEquals("Manga Server 1", context.getString(R.string.source_voratoon));
        assertEquals("Manga Server 2", context.getString(R.string.source_shinigami));
        assertEquals("Anime Streaming", context.getString(R.string.source_otakudesu));
        assertEquals("Novel", context.getString(R.string.source_novel));
    }

    @Test public void sourceHubMenampilkanEmpatNamaBaru() {
        try (ActivityController<SourceHubActivity> controller =
                     Robolectric.buildActivity(SourceHubActivity.class).setup()) {
            List<String> labels = new ArrayList<>();
            collectText(controller.get().getWindow().getDecorView(), labels);
            assertTrue("Novel tampil", labels.contains("Novel"));
            assertTrue("Manga Server 1 tampil", labels.contains("Manga Server 1"));
            assertTrue("Manga Server 2 tampil", labels.contains("Manga Server 2"));
            assertTrue("Anime Streaming tampil", labels.contains("Anime Streaming"));
            assertFalse("nama lama tidak boleh tersisa",
                    labels.contains("Manga Voratoon") || labels.contains("Manga Shinigami")
                            || labels.contains("Anime Otakudesu"));
        }
    }

    // ------------------------------------------------------------- Novel

    @Test public void berandaNovelMemakaiMenuTitikTiga() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity host = controller.get();
            View decor = host.getWindow().getDecorView();
            assertNotNull("menu titik tiga harus ada", decor.findViewWithTag("novel_overflow"));
            assertNull("ikon tema lama tidak lagi di toolbar", decor.findViewWithTag("theme"));
            assertNull("ikon muat ulang lama tidak lagi di toolbar",
                    decor.findViewWithTag("refresh"));
            assertNotNull("unduhan tetap sebagai aksi cepat",
                    decor.findViewWithTag("open_downloads"));
        }
    }

    @Test public void menuNovelMemuatTemaMuatUlangDanUnduhan() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity host = controller.get();
            host.getWindow().getDecorView().findViewWithTag("novel_overflow").performClick();
            ShadowLooper.idleMainLooper();

            android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertNotNull("menu harus terbuka", dialog);
            List<String> items = dialogTexts(dialog);
            assertTrue("tema tersedia", items.contains("Tema tampilan"));
            assertTrue(items.contains("Muat ulang katalog"));
            assertTrue(items.contains("Unduhan offline"));
        }
    }

    // ------------------------------------------------- Manga Server 2

    @Test public void berandaMangaServer2MemakaiMenuTitikTiga() {
        try (ActivityController<OfflineCatalog> controller =
                     Robolectric.buildActivity(OfflineCatalog.class).setup()) {
            OfflineCatalog host = controller.get();
            View decor = host.getWindow().getDecorView();
            assertNotNull("menu titik tiga harus ada", decor.findViewWithTag("shinigami_overflow"));
            assertNull("roda gigi lama tidak lagi dipakai",
                    decor.findViewWithTag("shinigami_settings"));
            assertNotNull("unduhan tetap sebagai aksi cepat",
                    decor.findViewWithTag("shinigami_downloads_button"));
            assertTrue("judul memakai nama baru", hasText(decor, "Manga Server 2"));
        }
    }

    @Test public void pengaturanMangaServer2HanyaTemaDanIndikator() throws Exception {
        try (ActivityController<OfflineCatalog> controller =
                     Robolectric.buildActivity(OfflineCatalog.class).setup()) {
            OfflineCatalog host = controller.get();
            host.getWindow().getDecorView().findViewWithTag("shinigami_overflow").performClick();
            ShadowLooper.idleMainLooper();

            android.app.Dialog menu = org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertNotNull("menu harus terbuka", menu);
            assertTrue("menu menuju Pengaturan", dialogTexts(menu).contains("Pengaturan"));

            // Isi pengaturan diperiksa lewat jalur yang sama dengan yang dipanggil
            // menu, sehingga tidak bergantung pada cara Robolectric menekan item.
            java.lang.reflect.Method show = null;
            Class<?> type = OfflineCatalog.class;
            while (type != null && show == null) {
                try {
                    show = type.getDeclaredMethod("showPreferences");
                } catch (NoSuchMethodException ignored) {
                    type = type.getSuperclass();
                }
            }
            assertNotNull("metode pengaturan harus ada", show);
            show.setAccessible(true);
            show.invoke(host);
            ShadowLooper.idleMainLooper();

            android.app.Dialog settings = org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertNotNull("pengaturan harus terbuka", settings);
            List<String> isi = dialogTexts(settings);
            assertTrue("tema ada", isi.contains("Tema tampilan"));
            assertTrue("indikator progres ada", isi.contains("Indikator progres membaca"));
            assertFalse("tidak ada pengaturan lain",
                    isi.contains("Domain sumber") || isi.contains("Domain"));
            for (String item : isi) {
                assertFalse("domain sumber tidak boleh tampil di pengaturan",
                        item.toLowerCase().contains("domain"));
            }
        }
    }

    // ------------------------------------------------------------ perkakas

    /** Teks yang tampil pada dialog, apa pun kelas AlertDialog-nya. */
    private static List<String> dialogTexts(android.app.Dialog dialog) {
        List<String> found = new ArrayList<>();
        android.view.Window window = dialog.getWindow();
        if (window == null) return found;
        collectText(window.getDecorView(), found);
        return found;
    }

    private static void collectText(View view, List<String> out) {
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null && text.length() > 0) out.add(text.toString());
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collectText(group.getChildAt(i), out);
        }
    }

    private static boolean hasText(View view, String expected) {
        List<String> texts = new ArrayList<>();
        collectText(view, texts);
        return texts.contains(expected);
    }
}