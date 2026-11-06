package id.shuyasal.kuromaku;

import android.app.Application;
import id.shuyasal.kuromaku.ui.ThemeManager;

public class KuromakuApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ThemeManager.applyTheme(this);
    }
}
