package id.shuyasal.kuromaku.offline;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Riwayat kematian proses dari Android sendiri.
 *
 * <p>{@link CrashLog} hanya menangkap exception Java. Aplikasi juga bisa berhenti
 * tanpa exception: crash di kode native (tombstone), ANR, atau dibunuh sistem
 * karena memori. Ketiganya justru yang paling sulit didiagnosis dari laporan
 * pengguna, dan ketiganya tercatat oleh sistem.
 *
 * <p>Sejak Android 11 (API 30) aplikasi boleh membaca riwayat prosesnya sendiri
 * lewat {@link ActivityManager#getHistoricalProcessExitInfos()}, termasuk potongan
 * tombstone untuk crash native. Data itu dipakai sebagai bahan laporan yang bisa
 * disalin pengguna — tanpa adb.
 */
public final class ExitReports {

    private static final String TAG = "KuromakuCrash";

    /** Hanya kejadian dalam rentang ini yang dianggap relevan. */
    private static final long WINDOW_MS = 24L * 60 * 60 * 1000;

    /** Batas potongan tombstone agar laporan tetap bisa dibaca. */
    private static final int MAX_TRACE_CHARS = 6000;

    private ExitReports() {
    }

    /** Ringkasan kejadian terakhir yang membuat proses berhenti tak wajar. */
    public static String summary(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return "";
        Context app = context.getApplicationContext();
        List<ApplicationExitInfo> riwayat;
        try {
            ActivityManager manager =
                    (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager == null) return "";
            riwayat = manager.getHistoricalProcessExitReasons(
                    app.getPackageName(), 0, 6);
        } catch (Throwable error) {
            Log.w(TAG, "riwayat kematian proses tidak bisa dibaca", error);
            return "";
        }
        if (riwayat == null || riwayat.isEmpty()) return "";

        long batas = System.currentTimeMillis() - WINDOW_MS;
        StringBuilder teks = new StringBuilder();
        int dilaporkan = 0;
        for (ApplicationExitInfo info : riwayat) {
            if (info == null || dilaporkan >= 3) break;
            if (info.getTimestamp() < batas) continue;
            if (!menarik(info.getReason())) continue;

            if (dilaporkan == 0) {
                teks.append("RIWAYAT KEMATIAN PROSES (dari Android)\n\n");
            }
            dilaporkan++;
            teks.append("waktu: ").append(waktu(info.getTimestamp())).append('\n');
            teks.append("jenis: ").append(jenis(info.getReason())).append('\n');
            teks.append("keterangan: ").append(nullSafe(info.getDescription())).append('\n');
            teks.append("pentingnya proses: ").append(info.getImportance()).append('\n');

            String jejak = jejak(info);
            if (!jejak.isEmpty()) {
                teks.append("jejak:\n").append(jejak).append('\n');
            }
            teks.append('\n');
        }
        return teks.toString();
    }

    /** Benar bila ada kejadian yang layak dilaporkan ke pengguna. */
    public static boolean adaLaporan(Context context) {
        return !summary(context).isEmpty();
    }

    private static boolean menarik(int reason) {
        return reason == ApplicationExitInfo.REASON_CRASH
                || reason == ApplicationExitInfo.REASON_CRASH_NATIVE
                || reason == ApplicationExitInfo.REASON_ANR
                || reason == ApplicationExitInfo.REASON_LOW_MEMORY
                || reason == ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE;
    }

    private static String jenis(int reason) {
        switch (reason) {
            case ApplicationExitInfo.REASON_CRASH:
                return "CRASH (exception Java)";
            case ApplicationExitInfo.REASON_CRASH_NATIVE:
                return "CRASH_NATIVE (kode native / tombstone)";
            case ApplicationExitInfo.REASON_ANR:
                return "ANR (aplikasi tidak merespons)";
            case ApplicationExitInfo.REASON_LOW_MEMORY:
                return "LOW_MEMORY (dibunuh sistem karena memori)";
            case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE:
                return "EXCESSIVE_RESOURCE_USAGE (dibatasi sistem)";
            case ApplicationExitInfo.REASON_USER_REQUESTED:
                return "USER_REQUESTED";
            case ApplicationExitInfo.REASON_SIGNALED:
                return "SIGNALED";
            default:
                return "lain (" + reason + ")";
        }
    }

    private static String jejak(ApplicationExitInfo info) {
        try {
            InputStream in = info.getTraceInputStream();
            if (in == null) return "";
            try (InputStream stream = in) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = stream.read(buffer)) > 0 && out.size() < MAX_TRACE_CHARS * 2) {
                    out.write(buffer, 0, read);
                }
                String teks = new String(out.toByteArray(), StandardCharsets.UTF_8);
                if (teks.isEmpty()) return "";
                return teks.length() > MAX_TRACE_CHARS
                        ? teks.substring(0, MAX_TRACE_CHARS) + "\n…dipotong"
                        : teks;
            }
        } catch (Throwable error) {
            return "";
        }
    }

    private static String waktu(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(millis));
    }

    private static String nullSafe(String text) {
        return text == null ? "" : text;
    }
}