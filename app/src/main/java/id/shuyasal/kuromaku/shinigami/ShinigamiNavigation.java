package id.shuyasal.kuromaku.shinigami;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;

import id.shuyasal.kuromaku.MainActivity;
import id.shuyasal.kuromaku.MangaActivity;
import id.shuyasal.kuromaku.ui.Palette;
import id.shuyasal.kuromaku.ui.SectionDrawer;

public final class ShinigamiNavigation {
    private ShinigamiNavigation() {
    }

    public static Dialog showDrawer(Activity activity, Palette palette, boolean catalogue) {
        return SectionDrawer.show(activity, SectionDrawer.Section.MANGA_SHINIGAMI, palette,
                new SectionDrawer.Listener() {
                    @Override
                    public void onNovel() {
                        activity.startActivity(new Intent(activity, MainActivity.class));
                        activity.finish();
                    }

                    @Override
                    public void onMangaVoratoon() {
                        activity.startActivity(new Intent(activity, MangaActivity.class));
                        activity.finish();
                    }

                    @Override
                    public void onShinigami() {
                        if (catalogue) return;
                        activity.startActivity(new Intent(activity, ShinigamiActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                        activity.finish();
                    }

                    @Override
                    public void onBackup() {
                        data(activity, "backup");
                    }

                    @Override
                    public void onRestore() {
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
