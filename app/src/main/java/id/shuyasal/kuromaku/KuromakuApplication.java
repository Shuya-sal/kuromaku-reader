package id.shuyasal.kuromaku;

import android.app.Application;
import android.util.Log;

import id.shuyasal.kuromaku.offline.DownloadReconciler;
import id.shuyasal.kuromaku.offline.DownloadStateFile;
import id.shuyasal.kuromaku.ui.ThemeManager;

public class KuromakuApplication extends Application {

    private static final String TAG = "KuromakuDownloads";

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeManager.applyTheme(this);

        // Status unduhan dipasang lebih dulu supaya layar mana pun yang dibuka
        // pertama sudah membaca keadaan yang benar, bukan keadaan kosong.
        DownloadStateFile.install(this);
        // Rekonsiliasi berjalan langsung: berkas statusnya kecil dan pemeriksaan
        // berkas hanya dilakukan untuk entri yang mengklaim tersimpan. Ini
        // menghindari thread latar yang bisa hidup melewati siklus hidup proses.
        try {
            DownloadReconciler.reconcile(this);
        } catch (RuntimeException error) {
            Log.w(TAG, "rekonsiliasi status unduhan dilewati", error);
        }
    }
}
