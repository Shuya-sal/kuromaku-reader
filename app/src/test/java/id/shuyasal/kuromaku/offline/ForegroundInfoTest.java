package id.shuyasal.kuromaku.offline;

import static org.junit.Assert.assertNotNull;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.WorkerParameters;
import androidx.work.impl.utils.taskexecutor.SerialExecutor;
import androidx.work.impl.utils.taskexecutor.TaskExecutor;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * ForegroundInfo untuk pekerjaan yang dijalankan ulang.
 *
 * <p>Bawaan {@code Worker.getForegroundInfoAsync()} melempar
 * {@code IllegalStateException("Not implemented")}. WorkManager memanggilnya
 * ketika pekerjaan perlu dipromosikan menjadi foreground di luar
 * {@code doWork()} — misalnya setelah proses aplikasi dibunuh sistem. Tanpa
 * override, pekerjaan yang dijalankan ulang gagal dengan exception, dan itulah
 * yang membuat unduhan tampak "crash" setelah aplikasi dibuka kembali.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ForegroundInfoTest {

    private static WorkerParameters params(Data input) {
        TaskExecutor taskExecutor = new TaskExecutor() {
            @Override public Executor getMainThreadExecutor() {
                return Runnable::run;
            }

            @Override public SerialExecutor getSerialTaskExecutor() {
                return new SerialExecutor() {
                    @Override public void execute(Runnable runnable) {
                        runnable.run();
                    }

                    @Override public boolean hasPendingTasks() {
                        return false;
                    }
                };
            }
        };
        return new WorkerParameters(UUID.randomUUID(), input, Collections.emptyList(),
                null, 0, 1, Runnable::run, taskExecutor, null,
                (context, id, data) -> SettableFuture.create(),
                (context, id, info) -> SettableFuture.create());
    }

    private static ForegroundInfo ambil(ListenableFuture<ForegroundInfo> future) throws Exception {
        return future.get(5, TimeUnit.SECONDS);
    }

    @Test public void novelPunyaForegroundInfo() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        Data input = new Data.Builder()
                .putString(DownloadWorker.KEY_NOVEL, "novel-uji")
                .putInt(DownloadWorker.KEY_VOLUME, 1)
                .build();
        ForegroundInfo info = ambil(new DownloadWorker(context, params(input))
                .getForegroundInfoAsync());
        assertNotNull("novel harus punya ForegroundInfo", info);
        assertNotNull("notifikasi tidak boleh null", info.getNotification());
    }

    @Test public void mangaPunyaForegroundInfo() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        Data input = new Data.Builder().putString("chapters", "[]").build();
        ForegroundInfo info = ambil(
                new id.shuyasal.kuromaku.shinigami.ShinigamiDownloadWorker(context, params(input))
                        .getForegroundInfoAsync());
        assertNotNull("manga harus punya ForegroundInfo", info);
        assertNotNull(info.getNotification());
    }

    @Test public void animePunyaForegroundInfo() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        Data input = new Data.Builder()
                .putString("episode_key", "ep-uji")
                .putString("episode_label", "Episode 1")
                .build();
        ForegroundInfo info = ambil(
                new id.shuyasal.kuromaku.anime.AnimeDownloadWorker(context, params(input))
                        .getForegroundInfoAsync());
        assertNotNull("anime harus punya ForegroundInfo", info);
        assertNotNull(info.getNotification());
    }
}
