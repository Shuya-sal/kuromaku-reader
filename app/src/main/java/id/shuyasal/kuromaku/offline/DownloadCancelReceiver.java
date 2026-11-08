package id.shuyasal.kuromaku.offline;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.work.WorkManager;

/**
 * Aksi <b>Batal</b> pada notifikasi unduhan.
 *
 * <p>Membatalkan pekerjaan lewat tag yang dikirim notifikasi itu sendiri, bukan
 * lewat "semua unduhan": menekan Batal pada satu episode tidak boleh
 * menghentikan unduhan lain.
 */
public class DownloadCancelReceiver extends BroadcastReceiver {

    private static final String TAG = "KuromakuDownloads";

    @Override public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        if (!ForegroundDownloads.ACTION_CANCEL.equals(intent.getAction())) return;
        String tag = intent.getStringExtra(ForegroundDownloads.EXTRA_TAG);
        if (tag == null || tag.isEmpty()) return;
        try {
            WorkManager.getInstance(context.getApplicationContext()).cancelAllWorkByTag(tag);
        } catch (RuntimeException error) {
            Log.w(TAG, "pembatalan dari notifikasi gagal", error);
        }
    }
}