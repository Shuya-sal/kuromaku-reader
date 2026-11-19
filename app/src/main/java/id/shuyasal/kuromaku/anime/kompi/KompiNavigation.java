package id.shuyasal.kuromaku.anime.kompi;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;

import id.shuyasal.kuromaku.MainActivity;
import id.shuyasal.kuromaku.MangaActivity;
import id.shuyasal.kuromaku.SourceHubActivity;
import id.shuyasal.kuromaku.komiku.KomikuActivity;
import id.shuyasal.kuromaku.shinigami.ShinigamiActivity;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.SectionDrawer;

/**
 * Menu samping Anime Server 2.
 *
 * <p>Halaman ini sebelumnya tidak punya menu sama sekali: setelah masuk lewat
 * kartu di beranda, tidak ada jalan ke sumber lain selain menekan kembali
 * berulang kali. Isi menunya sekarang sama persis dengan sumber lain.
 */
public final class KompiNavigation {

    private KompiNavigation() {
    }

    public static Dialog showDrawer(Activity activity, Palette palette) {
        return SectionDrawer.show(activity, SectionDrawer.Section.ANIME_KOMPI, palette,
                new SectionDrawer.Listener() {
                    @Override public void onHome() {
                        activity.startActivity(new Intent(activity, SourceHubActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                        activity.finish();
                    }

                    @Override public void onNovel() {
                        activity.startActivity(new Intent(activity, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                        activity.finish();
                    }

                    @Override public void onMangaVoratoon() {
                        activity.startActivity(new Intent(activity, MangaActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                        activity.finish();
                    }

                    @Override public void onShinigami() {
                        activity.startActivity(new Intent(activity, ShinigamiActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                        activity.finish();
                    }

                    @Override public void onKomiku() {
                        activity.startActivity(new Intent(activity, KomikuActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                        activity.finish();
                    }

                    @Override public void onAnimeOtakudesu() {
                        activity.startActivity(new Intent(activity,
                                id.shuyasal.kuromaku.anime.OtakudesuActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                        activity.finish();
                    }

                    @Override public void onAnimeKompi() {
                        // Sudah di halaman ini.
                    }

                    @Override public void onBackup() {
                        data(activity, "backup");
                    }

                    @Override public void onRestore() {
                        data(activity, "restore");
                    }
                });
    }

    private static void data(Activity activity, String action) {
        Intent intent = new Intent(activity, MainActivity.class);
        intent.putExtra("kuromaku_data_action", action);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
        activity.finish();
    }
}
