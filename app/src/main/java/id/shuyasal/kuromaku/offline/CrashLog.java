package id.shuyasal.kuromaku.offline;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Penangkap crash di dalam aplikasi.
 *
 * <p>Alasan keberadaannya: laporan "force close" tanpa stack trace tidak bisa
 * didiagnosis, dan pengguna tidak selalu punya adb. Handler ini menulis
 * exception yang mematikan proses ke {@code files/last-crash.txt} <b>sebelum</b>
 * proses benar-benar berhenti, lalu meneruskan ke handler bawaan Android supaya
 * perilaku normal (dialog "aplikasi berhenti") tetap sama.
 *
 * <p>Isi berkas sengaja dibatasi: versi aplikasi, versi Android, nama thread, dan
 * stack trace. Tidak ada token, cookie, atau data pribadi yang dicatat.
 */
public final class CrashLog {

    private static final String TAG = "KuromakuCrash";
    private static final String FILE_NAME = "last-crash.txt";
    private static final int MAX_CHARS = 12_000;
    /**
     * Penjaga re-entrancy, bukan penjaga "sekali seumur proses".
     *
     * <p>Handler bisa dipanggil berkali-kali (misalnya crash saat menangani
     * crash); yang harus dicegah adalah rekursi di thread yang sama. Memakai
     * penanda global akan membuat crash kedua dalam satu proses tidak tercatat.
     */
    private static final ThreadLocal<Boolean> WRITING = new ThreadLocal<>();

    private CrashLog() {
    }

    /** Konteks aplikasi untuk menulis berkas; dipasang sekali per proses. */
    private static Context appContext;
    private static Thread.UncaughtExceptionHandler previousHandler;
    private static boolean installed;

    /**
     * Pasang handler sekali per proses. Aman dipanggil berulang.
     *
     * <p>Idempoten dengan sengaja: memasang handler baru setiap kali Application
     * dibuat akan membuat rantai handler tumbuh tanpa batas. Setiap exception
     * kemudian ditangani berlapis-lapis — ratusan penulisan berkas untuk satu
     * crash, dan itu sendiri bisa menghabiskan memori.
     */
    public static synchronized void install(Context context) {
        if (context == null) return;
        appContext = context.getApplicationContext();
        if (installed) return;
        installed = true;
        previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        // Ditangkap sebagai nilai tetap: membaca field statis di dalam handler
        // membuat handler memanggil dirinya sendiri setelah dipasang ulang.
        final Thread.UncaughtExceptionHandler previous = previousHandler;
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            write(appContext, thread, error);
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    /** Lepas handler; hanya dipakai pengujian supaya tidak bocor antar-tes. */
    static synchronized void resetForTests() {
        if (!installed) return;
        Thread.setDefaultUncaughtExceptionHandler(previousHandler);
        previousHandler = null;
        appContext = null;
        installed = false;
    }

    private static void write(Context app, Thread thread, Throwable error) {
        // Satu crash = satu catatan; handler bisa dipanggil lebih dari sekali.
        if (Boolean.TRUE.equals(WRITING.get())) return;
        WRITING.set(Boolean.TRUE);
        try {
            writeOnce(app, thread, error);
        } catch (Throwable ignored) {
            // Menangani crash tidak boleh menghasilkan crash baru.
        } finally {
            WRITING.remove();
        }
    }

    private static void writeOnce(Context app, Thread thread, Throwable error) {
        StringWriter buffer = new StringWriter();
        try (PrintWriter writer = new PrintWriter(buffer)) {
            writer.println("waktu: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    .format(new Date()));
            writer.println("aplikasi: " + versionName(app) + " (" + versionCode(app) + ")");
            writer.println("android: API " + Build.VERSION.SDK_INT + " (" + Build.VERSION.RELEASE
                    + ", " + Build.MANUFACTURER + " " + Build.MODEL + ")");
            writer.println("thread: " + (thread == null ? "?" : thread.getName()));
            writer.println();
            writer.println("FATAL EXCEPTION");
            error.printStackTrace(writer);
            Throwable cause = error.getCause();
            int guard = 0;
            while (cause != null && guard++ < 5) {
                writer.println();
                writer.println("Caused by:");
                cause.printStackTrace(writer);
                cause = cause.getCause();
            }
        }
        String text = buffer.toString();
        if (text.length() > MAX_CHARS) text = text.substring(0, MAX_CHARS) + "\n…dipotong";
        try (FileOutputStream out = new FileOutputStream(file(app))) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        } catch (IOException writeError) {
            Log.w(TAG, "laporan crash tidak bisa disimpan", writeError);
        } catch (Throwable other) {
            Log.w(TAG, "laporan crash tidak bisa dibuat", other);
        }
    }

    /** Isi laporan crash terakhir, atau string kosong bila tidak ada. */
    public static String read(Context context) {
        if (context == null) return "";
        File file = file(context.getApplicationContext());
        if (!file.isFile()) return "";
        try (FileInputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            return "";
        }
    }

    public static boolean exists(Context context) {
        return context != null && file(context.getApplicationContext()).isFile();
    }

    public static void clear(Context context) {
        if (context == null) return;
        File file = file(context.getApplicationContext());
        if (file.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private static File file(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    private static String versionName(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0)
                    .versionName;
        } catch (Exception error) {
            return "?";
        }
    }

    private static int versionCode(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0)
                    .versionCode;
        } catch (Exception error) {
            return 0;
        }
    }
}