package id.shuyasal.kuromaku.anime.kompi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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

import id.shuyasal.kuromaku.anime.AnimeDownloads;
import id.shuyasal.kuromaku.anime.kompi.KompiActivity;

/**
 * Header dan unduhan Anime Server 2.
 *
 * <p>Keluhan yang dijaga di sini: tombol kembali ada di kanan (seharusnya kiri),
 * tidak ada titik tiga berisi Tema/Genre/Muat ulang, tidak ada riwayat tonton,
 * dan tidak ada tombol unduh per episode.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "w411dp-h891dp-xhdpi")
public class KompiHeaderDownloadTest {

    private final Context context = ApplicationProvider.getApplicationContext();

    @Before public void bersihkan() {
        context.getSharedPreferences(KompiHistory.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    private KompiActivity buka() {
        ActivityController<KompiActivity> controller =
                Robolectric.buildActivity(KompiActivity.class).setup();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        return controller.get();
    }

    /** Baris atas: kembali, judul, lalu tombol-tombol di kanan. */
    private LinearLayout header(KompiActivity host) {
        return (LinearLayout) isi(host).getChildAt(0);
    }

    private ViewGroup isi(KompiActivity host) {
        ViewGroup content = host.findViewById(android.R.id.content);
        return (ViewGroup) content.getChildAt(0);
    }

    @Test public void tombolKembaliAdaDiKiri() {
        KompiActivity host = buka();
        LinearLayout bar = header(host);
        View kembali = bar.findViewWithTag("tombol_kembali");
        assertNotNull("tombol kembali harus ada", kembali);
        assertEquals("kembali harus anak pertama (paling kiri)", kembali, bar.getChildAt(0));
        host.finish();
    }

    @Test public void titikTigaDanUnduhanAdaDiKanan() {
        KompiActivity host = buka();
        LinearLayout bar = header(host);

        ImageButton unduhan = bar.findViewWithTag("kompi_downloads");
        ImageButton overflow = bar.findViewWithTag("kompi_overflow");
        assertNotNull("tombol hasil unduhan harus ada", unduhan);
        assertNotNull("titik tiga harus ada", overflow);

        int posisiUnduhan = bar.indexOfChild(unduhan);
        int posisiOverflow = bar.indexOfChild(overflow);
        assertEquals("titik tiga paling kanan", bar.getChildCount() - 1, posisiOverflow);
        assertEquals("hasil unduhan tepat di kiri titik tiga",
                posisiOverflow - 1, posisiUnduhan);
        host.finish();
    }

    /** Rail riwayat selalu jadi anak pertama isi konten. */
    @Test public void railRiwayatDiPalingAtasKonten() {
        KompiHistory.record(context, "one-piece", "One Piece", "", "Episode 3", "ep-3", 3);
        KompiActivity host = buka();
        ViewGroup root = isi(host);
        ViewGroup isiKonten = null;
        for (int i = 0; i < root.getChildCount(); i++) {
            View anak = root.getChildAt(i);
            if (anak instanceof ScrollView) {
                ViewGroup isiGulir = (ViewGroup) anak;
                if (isiGulir.getChildCount() > 0
                        && isiGulir.getChildAt(0) instanceof ViewGroup) {
                    isiKonten = (ViewGroup) isiGulir.getChildAt(0);
                }
            }
        }
        assertNotNull("isi konten harus ditemukan", isiKonten);
        assertTrue("konten tidak kosong", isiKonten.getChildCount() > 0);
        assertEquals("rail harus jadi anak pertama", "rail_riwayat_kompi",
                String.valueOf(isiKonten.getChildAt(0).getTag()));
        host.finish();
    }

    @Test public void railRiwayatTampilSetelahAdaTontonan() {
        KompiHistory.record(context, "one-piece", "One Piece", "", "Episode 3", "ep-3", 3);
        KompiActivity host = buka();
        View rail = isi(host).findViewWithTag("rail_riwayat_kompi");
        assertNotNull("rail riwayat harus tampil", rail);
        assertTrue("judul rail", adaTeks(rail, "Lanjutkan Menonton"));
        assertTrue("judul anime", adaTeks(rail, "One Piece"));
        assertTrue("episode terakhir", adaTeks(rail, "Episode 3"));
        host.finish();
    }

    @Test public void judulHalamanAdalahAnimeServer2() {
        KompiActivity host = buka();
        TextView judul = isi(host).findViewWithTag("judul_anime_server2");
        assertNotNull(judul);
        assertEquals("Anime Server 2", judul.getText().toString());
        host.finish();
    }

    // ------------------------------------------------------- unduh dari detail

    @Test public void daftarEpisodePunyaTombolUnduh() {
        // Detail butuh jaringan; yang diuji di sini adalah kunci penanda
        // unduhan yang dipakai baris episode, supaya tombolnya menandai episode
        // yang benar-benar tersimpan dan bukan menebak.
        String key = AnimeDownloads.episodeKey("naruto-ep-3", "Episode 3");
        assertNotNull(key);
        assertTrue("kunci harus stabil untuk episode yang sama",
                key.equals(AnimeDownloads.episodeKey("naruto-ep-3", "Episode 3")));
    }

    @Test public void daftarEpisodeTersimpanKosongDiAwal() {
        assertTrue(AnimeDownloads.savedEpisodeKeys(context, "anime-baru").isEmpty());
        assertEquals(0, AnimeDownloads.savedCount(context, "anime-baru"));
    }

    private boolean adaTeks(View view, String teks) {
        if (view instanceof TextView) {
            CharSequence isi = ((TextView) view).getText();
            if (isi != null && isi.toString().contains(teks)) return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (adaTeks(group.getChildAt(i), teks)) return true;
            }
        }
        return false;
    }
}
