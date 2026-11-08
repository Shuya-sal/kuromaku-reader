package id.shuyasal.kuromaku;

import android.app.Application;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import id.shuyasal.kuromaku.offline.DownloadQuarantine;
import id.shuyasal.kuromaku.offline.DownloadReconciler;
import id.shuyasal.kuromaku.offline.DownloadStateFile;
import id.shuyasal.kuromaku.ui.ThemeManager;

public class KuromakuApplication extends Application {

    private static final String TAG = "KuromakuDownloads";

    /**
     * Pekerjaan pemulihan unduhan.
     *
     * <p>Satu thread, bukan main thread: membaca antrean WorkManager dan memeriksa
     * berkas unduhan bisa memakan waktu pada pustaka yang besar, dan startup
     * aplikasi tidak boleh menunggunya. Semua kegagalan ditelan di sini — aplikasi
     * harus tetap bisa dibuka walau pemulihan gagal.
     */
    private static final ExecutorService RECOVERY = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeManager.applyTheme(this);

        // Status unduhan dipasang lebih dulu supaya layar mana pun yang dibuka
        // pertama sudah membaca keadaan yang benar, bukan keadaan kosong.
        // Paling awal: kalau proses ini mati, penyebabnya tercatat di
        // files/last-crash.txt dan bisa dibaca dari dalam aplikasi.
        id.shuyasal.kuromaku.offline.CrashLog.install(this);
        DownloadStateFile.install(this);
        RECOVERY.execute(() -> {
            try {
                // Paling awal: kalau proses sebelumnya mati di tengah unduhan,
                // pekerjaan itu dibatalkan sebelum WorkManager menjalankannya lagi.
                id.shuyasal.kuromaku.offline.DownloadCrashGuard.recover(this);
            } catch (Throwable error) {
                Log.w(TAG, "pemulihan penanda unduhan dilewati", error);
            }
            try {
                DownloadReconciler.reconcile(this);
            } catch (Throwable error) {
                Log.w(TAG, "rekonsiliasi status unduhan dilewati", error);
            }
            try {
                DownloadQuarantine.run(this);
            } catch (Throwable error) {
                Log.w(TAG, "karantina unduhan dilewati", error);
            }
        });
    }
}
