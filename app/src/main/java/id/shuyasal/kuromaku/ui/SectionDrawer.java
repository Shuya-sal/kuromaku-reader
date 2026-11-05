package id.shuyasal.kuromaku.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public final class SectionDrawer {

    public enum Section {
        NOVEL,
        MANGA_VORATON,
        MANGA_SHINIGAMI
    }

    public interface Listener {
        void onNovel();
        void onMangaVoratoon();
        void onShinigami();
        void onBackup();
        void onRestore();

        /** Kembali ke halaman pemilih sumber. Implementasi boleh diabaikan. */
        default void onHome() {
        }
    }

    private SectionDrawer() {}

    public static Dialog show(Activity activity, Section currentSection, Palette palette, Listener listener) {
        Dialog drawer = new Dialog(activity);
        drawer.setCanceledOnTouchOutside(true);
        drawer.setCancelable(true);

        final float threshold = Ui.dp(activity, 30);
        LinearLayout panel = new LinearLayout(activity) {
            private float startX;
            private float startY;
            private boolean tracking;

            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX();
                        startY = event.getRawY();
                        tracking = true;
                        break;
                    case MotionEvent.ACTION_MOVE:
                    case MotionEvent.ACTION_UP:
                        if (tracking) {
                            float deltaX = event.getRawX() - startX;
                            float deltaY = event.getRawY() - startY;
                            if (deltaX < -threshold && Math.abs(deltaX) > Math.abs(deltaY)) {
                                tracking = false;
                                drawer.dismiss();
                                return true;
                            }
                        }
                        if (event.getActionMasked() == MotionEvent.ACTION_UP) tracking = false;
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        tracking = false;
                        break;
                    default:
                        break;
                }
                return super.dispatchTouchEvent(event);
            }
        };
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(palette.surface);
        panel.setPadding(Ui.dp(activity, 20), Ui.dp(activity, 30), Ui.dp(activity, 20), Ui.dp(activity, 24));

        TextView heading = Ui.label(activity, "Kuromaku Reader", 21, palette.text);
        panel.addView(heading);

        addDrawerItem(activity, panel, activity.getString(
                id.shuyasal.kuromaku.R.string.home), "drawer_home", false, palette, () -> {
            drawer.dismiss();
            if (listener != null) listener.onHome();
        });

        String novelLabel = currentSection == Section.NOVEL ? "Novel  ✓" : "Novel";
        addDrawerItem(activity, panel, novelLabel, "drawer_novel", currentSection == Section.NOVEL, palette, () -> {
            drawer.dismiss();
            if (listener != null) listener.onNovel();
        });

        String mangaLabel = currentSection == Section.MANGA_VORATON ? "Manga Voratoon  ✓" : "Manga Voratoon";
        addDrawerItem(activity, panel, mangaLabel, "drawer_manga_voratoon",
                currentSection == Section.MANGA_VORATON, palette, () -> {
            drawer.dismiss();
            if (listener != null) listener.onMangaVoratoon();
        });

        String shinigamiLabel = currentSection == Section.MANGA_SHINIGAMI
                ? "Manga Shinigami  ✓" : "Manga Shinigami";
        addDrawerItem(activity, panel, shinigamiLabel, "drawer_manga_shinigami",
                currentSection == Section.MANGA_SHINIGAMI, palette, () -> {
            drawer.dismiss();
            if (listener != null) listener.onShinigami();
        });

        View separator = new View(activity);
        separator.setBackgroundColor(palette.divider);
        LinearLayout.LayoutParams sep = new LinearLayout.LayoutParams(-1, Ui.dp(activity, 1));
        sep.setMargins(0, Ui.dp(activity, 16), 0, Ui.dp(activity, 16));
        panel.addView(separator, sep);

        addDrawerItem(activity, panel, "Backup progres membaca", "drawer_backup", false, palette, () -> {
            drawer.dismiss();
            if (listener != null) listener.onBackup();
        });

        addDrawerItem(activity, panel, "Restore progres membaca", "drawer_restore", false, palette, () -> {
            drawer.dismiss();
            if (listener != null) listener.onRestore();
        });

        ViewCompat.setOnApplyWindowInsetsListener(panel, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(
                    Ui.dp(activity, 20) + bars.left,
                    Ui.dp(activity, 20) + bars.top,
                    Ui.dp(activity, 20) + bars.right,
                    Ui.dp(activity, 24) + bars.bottom
            );
            return insets;
        });

        drawer.setContentView(panel);

        Window window = drawer.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.gravity = Gravity.START | Gravity.TOP;
            params.width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.82f);
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            params.dimAmount = 0.55f;
            window.setAttributes(params);
        }

        drawer.show();

        if (drawer.getWindow() != null) {
            Window window2 = drawer.getWindow();
            window2.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.82f), -1);
            View panelView = window2.getDecorView();
            boolean slide = Motion.enabled(activity);
            panelView.setTranslationX(slide ? -activity.getResources().getDisplayMetrics().widthPixels : 0f);
            if (slide) {
                panelView.animate().translationX(0).setDuration(Motion.DURATION_MS).start();
            }
        }

        return drawer;
    }

    private static void addDrawerItem(Context context, LinearLayout panel, String title, String tag,
                                      boolean active, Palette palette, Runnable action) {
        TextView item = Ui.label(context, title, 17, active ? palette.accent : palette.text);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(Ui.dp(context, 12), Ui.dp(context, 16), Ui.dp(context, 12), Ui.dp(context, 16));
        item.setTag(tag);
        if (active) {
            item.setTypeface(null, Typeface.BOLD);
            int bg = palette.surface == 0xffffffff ? 0xffe8f0fc : 0xff263a55;
            int border = palette.surface == 0xffffffff ? 0xffc2d7f8 : 0xff3b557a;
            item.setBackground(Ui.rounded(bg, border, 8, context));
        }
        item.setOnClickListener(v -> action.run());
        panel.addView(item, new LinearLayout.LayoutParams(-1, Ui.dp(context, 64)));
    }
}
