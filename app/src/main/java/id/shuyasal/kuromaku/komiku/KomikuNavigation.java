package id.shuyasal.kuromaku.komiku;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;

import id.shuyasal.kuromaku.MainActivity;
import id.shuyasal.kuromaku.MangaActivity;
import id.shuyasal.kuromaku.anime.OtakudesuActivity;
import id.shuyasal.kuromaku.shinigami.ShinigamiActivity;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.SectionDrawer;

/** Menu samping Manga Server 3; isinya sama dengan menu sumber lain. */
public final class KomikuNavigation {

    private KomikuNavigation() {
    }

    public static Dialog showDrawer(Activity activity, Palette palette) {
        return SectionDrawer.show(activity, SectionDrawer.Section.MANGA_KOMIKU, palette,
                new SectionDrawer.Listener() {
                    @Override public void onHome() {
                        activity.startActivity(new Intent(activity,
                                id.shuyasal.kuromaku.SourceHubActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                        activity.finish();
                    }

                    @Override public void onNovel() {
                        activity.startActivity(new Intent(activity, MainActivity.class));
                        activity.finish();
                    }

                    @Override public void onMangaVoratoon() {
                        activity.startActivity(new Intent(activity, MangaActivity.class));
                        activity.finish();
                    }

                    @Override public void onShinigami() {
                        activity.startActivity(new Intent(activity, ShinigamiActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                        activity.finish();
                    }

                    @Override public void onKomiku() {
                        // Sudah di Manga Server 3.
                    }

                    @Override public void onAnimeOtakudesu() {
                        activity.startActivity(new Intent(activity, OtakudesuActivity.class));
                        activity.finish();
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