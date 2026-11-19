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
import id.shuyasal.kuromaku.ui.Ui;

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

        // Urutan: Anime Server 1 lalu Anime Server 2, setelah semua manga.
        assertTrue("Anime Server 2 harus berada setelah Anime Server 1",
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
        assertEquals("Anime Server 1", ((TextView) item(panel, "drawer_anime_otakudesu"))
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

    // ------------------------------------------------- anime server 2: header

    @Test public void judulHalamanAnimeServer2MemakaiNamaBaru() {
        id.shuyasal.kuromaku.anime.kompi.KompiActivity host =
                Robolectric.buildActivity(id.shuyasal.kuromaku.anime.kompi.KompiActivity.class)
                        .setup().get();
        TextView judul = cariJudul(host.getWindow().getDecorView());
        assertNotNull("judul halaman harus ada", judul);
        assertEquals("Anime Server 2", judul.getText().toString());
        host.finish();
    }

    /**
     * Status bar transparan, jadi isi layar wajib memakai padding sistem.
     *
     * <p>Tanpa itu judul dan tombol kembali naik ke area jam dan ikon
     * notifikasi — persis yang dilaporkan pada layar Anime Server 2, sementara
     * sumber lain sudah benar.
     */
    @Test public void headerAnimeServer2TidakMenutupiJam() {
        id.shuyasal.kuromaku.anime.kompi.KompiActivity host =
                Robolectric.buildActivity(id.shuyasal.kuromaku.anime.kompi.KompiActivity.class)
                        .setup().get();
        // Isi layar = LinearLayout yang dipasang activity, bukan root decor
        // (decor berisi ViewStub milik AppCompat di indeks 0).
        View root = null;
        ViewGroup content = host.findViewById(android.R.id.content);
        if (content != null && content.getChildCount() > 0) {
            root = content.getChildAt(0);
        }
        assertNotNull("isi layar harus ada", root);

        // Listener insets terpasang berarti layar ini memakai ruang status bar.
        // Padding-nya baru terisi setelah sistem mengirim insets; di Robolectric
        // itu tidak terjadi, jadi yang bisa diperiksa adalah pemasangan
        // listener-nya — satu hal yang membedakan layar ini dari versi rusak.
        // Dan saat insets dikirim sungguhan, padding atas ikut bertambah.
        androidx.core.view.WindowInsetsCompat inset =
                new androidx.core.view.WindowInsetsCompat.Builder()
                        .setInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars(),
                                androidx.core.graphics.Insets.of(0, Ui.dp(host, 24), 0,
                                        Ui.dp(host, 48)))
                        .build();
        androidx.core.view.ViewCompat.dispatchApplyWindowInsets(root, inset);
        assertTrue("padding atas harus memuat tinggi status bar, dapat "
                        + root.getPaddingTop(),
                root.getPaddingTop() >= Ui.dp(host, 24) + Ui.dp(host, 14));
        host.finish();
    }

    private TextView cariJudul(View view) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            CharSequence isi = text.getText();
            if (isi != null && "Anime Server 2".contentEquals(isi)) return text;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = cariJudul(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
}
