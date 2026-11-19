package id.shuyasal.kuromaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
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
import org.robolectric.shadows.ShadowDialog;

import id.shuyasal.kuromaku.shinigami.ShinigamiActivity;
import id.shuyasal.kuromaku.ui.Prefs;

/**
 * Tiga keluhan yang muncul bersama setelah rilis 2.74.0:
 *
 * <ol>
 *   <li>label versi belum ada di halaman awal;</li>
 *   <li>menu Manga Server 3 tidak membuka apa pun dari halaman Manga Server 2;</li>
 *   <li>Anime Server 2 tidak ada di menu samping mana pun.</li>
 * </ol>
 *
 * <p>Ketiganya diuji lewat kontrol yang benar-benar diketuk pembaca: kartu di
 * beranda dan baris menu di drawer. Memanggil metode activity langsung akan
 * tetap lulus walau barisnya tidak bisa diklik — dan itu justru cacat yang
 * dilaporkan.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w411dp-h891dp-xhdpi")
public class HubVersionAndDrawerRoutesTest {

    private Context context = ApplicationProvider.getApplicationContext();

    @Before public void siapkan() {
        context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    private SourceHubActivity hub() {
        return Robolectric.buildActivity(SourceHubActivity.class).setup().get();
    }

    private LinearLayout drawerOf(Activity host, String menuTag) {
        View menu = host.getWindow().getDecorView().findViewWithTag(menuTag);
        assertNotNull("tombol menu '" + menuTag + "' harus ada", menu);
        menu.performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull("drawer harus terbuka", dialog);
        View root = dialog.findViewById(android.R.id.content);
        return (LinearLayout) ((ViewGroup) root).getChildAt(0);
    }

    private View item(LinearLayout panel, String tag) {
        View found = panel.findViewWithTag(tag);
        assertNotNull("baris menu '" + tag + "' harus ada", found);
        return found;
    }

    // ------------------------------------------------------------- versi rilis

    @Test public void halamanAwalMenampilkanVersiRilis() {
        SourceHubActivity hub = hub();
        TextView version = hub.getWindow().getDecorView().findViewWithTag("source_hub_version");
        assertNotNull("label versi harus ada di halaman awal", version);

        // Nilainya dibaca dari konfigurasi build, bukan ditulis tangan: kalau
        // nomor rilis naik, label ini ikut tanpa ada yang perlu ingat.
        assertEquals("Versi " + BuildConfig.VERSION_NAME, version.getText().toString());
        assertTrue("versi harus berbentuk angka bertitik: " + version.getText(),
                version.getText().toString().matches("Versi \\d+(\\.\\d+)+"));
    }

    @Test public void versiTampilSejajarDenganNamaAplikasi() {
        SourceHubActivity hub = hub();
        View baris = hub.getWindow().getDecorView().findViewWithTag("source_hub_eyebrow_row");
        assertNotNull("baris judul atas harus ada", baris);
        assertNotNull("nama aplikasi tetap ada", baris.findViewWithTag("source_hub_eyebrow"));
        assertNotNull("versi berada di baris yang sama",
                baris.findViewWithTag("source_hub_version"));
    }

    // ------------------------------------------------------ server 3 dari srv 2

    @Test public void menuServer3DariServer2MembukaKomiku() {
        ShinigamiActivity host = Robolectric.buildActivity(ShinigamiActivity.class)
                .setup().get();
        LinearLayout panel = drawerOf(host, "section_menu");

        item(panel, "drawer_manga_komiku").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Intent started = Shadows.shadowOf(host).getNextStartedActivity();
        assertNotNull("mengetuk Manga Server 3 harus membuka halaman, bukan hanya menutup menu",
                started);
        assertEquals(id.shuyasal.kuromaku.komiku.KomikuActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test public void menuServer3DariDetailServer2JugaMembukaKomiku() {
        ShinigamiActivity host = Robolectric.buildActivity(ShinigamiActivity.class)
                .setup().get();
        // Layar detail memakai drawer yang sama lewat helper navigasi.
        LinearLayout panel = drawerOf(host, "section_menu");
        item(panel, "drawer_manga_komiku").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        Intent started = Shadows.shadowOf(host).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(id.shuyasal.kuromaku.komiku.KomikuActivity.class.getName(),
                started.getComponent().getClassName());
    }

    // --------------------------------------------------------- anime server 2

    @Test public void menuSampingPunyaAnimeServer2() {
        ShinigamiActivity host = Robolectric.buildActivity(ShinigamiActivity.class)
                .setup().get();
        LinearLayout panel = drawerOf(host, "section_menu");

        TextView kompi = (TextView) item(panel, "drawer_anime_kompi");
        assertEquals("Anime Server 2", kompi.getText().toString());

        // Urutan: Anime Streaming lalu Anime Server 2, setelah semua manga.
        assertTrue("Anime Server 2 harus berada setelah Anime Streaming",
                panel.indexOfChild(kompi) > panel.indexOfChild(
                        item(panel, "drawer_anime_otakudesu")));
    }

    @Test public void animeServer2DariMenuMembukaHalamannya() {
        ShinigamiActivity host = Robolectric.buildActivity(ShinigamiActivity.class)
                .setup().get();
        LinearLayout panel = drawerOf(host, "section_menu");

        item(panel, "drawer_anime_kompi").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Intent started = Shadows.shadowOf(host).getNextStartedActivity();
        assertNotNull("Anime Server 2 harus terbuka dari menu", started);
        assertEquals(id.shuyasal.kuromaku.anime.kompi.KompiActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test public void halamanAnimeServer2DitandaiAktifDiMenunya() {
        id.shuyasal.kuromaku.anime.kompi.KompiActivity host =
                Robolectric.buildActivity(id.shuyasal.kuromaku.anime.kompi.KompiActivity.class)
                        .setup().get();
        LinearLayout panel = drawerOf(host, "section_menu");

        TextView kompi = (TextView) item(panel, "drawer_anime_kompi");
        assertEquals("Anime Server 2  ✓", kompi.getText().toString());
        assertEquals("Anime Streaming", ((TextView) item(panel, "drawer_anime_otakudesu"))
                .getText().toString());
        host.finish();
    }

    @Test public void halamanAnimeServer2BisaPindahKeSumberLain() {
        id.shuyasal.kuromaku.anime.kompi.KompiActivity host =
                Robolectric.buildActivity(id.shuyasal.kuromaku.anime.kompi.KompiActivity.class)
                        .setup().get();
        LinearLayout panel = drawerOf(host, "section_menu");

        item(panel, "drawer_manga_komiku").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Intent started = Shadows.shadowOf(host).getNextStartedActivity();
        assertNotNull("dari Anime Server 2 harus bisa pindah ke Manga Server 3", started);
        assertEquals(id.shuyasal.kuromaku.komiku.KomikuActivity.class.getName(),
                started.getComponent().getClassName());
        host.finish();
    }

    // ------------------------------------------------------- regresi lain

    @Test public void serverLainTetapPunyaJalanKeAnimeServer2() {
        // Halaman Novel memakai drawer yang sama; entri baru harus ikut di sana.
        MainActivity novel = Robolectric.buildActivity(MainActivity.class).setup().get();
        LinearLayout panel = drawerOf(novel, "section_menu");
        item(panel, "drawer_anime_kompi").performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        Intent started = Shadows.shadowOf(novel).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(id.shuyasal.kuromaku.anime.kompi.KompiActivity.class.getName(),
                started.getComponent().getClassName());
    }
}
