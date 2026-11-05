package id.shuyasal.kuromaku.shinigami;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Penulisan satu halaman chapter ke penyimpanan aplikasi.
 *
 * <p>Urutannya: validasi header + MIME lebih dulu, tulis ke berkas sementara,
 * fsync, baru ganti nama menjadi berkas final. Berkas sementara selalu dihapus
 * saat gagal, sehingga halaman setengah jadi tidak pernah dianggap tersimpan
 * dan unduhan bisa dilanjutkan dari halaman yang benar-benar lengkap.
 */
final class ShinigamiPageStore {
    private static final int MAX_BYTES = 32 * 1024 * 1024;

    private ShinigamiPageStore() {
    }

    static File fileFor(ShinigamiDownloader local, String chapterId, int index) {
        File dir = local.chapterDir(chapterId);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IllegalStateException("Tidak dapat membuat folder unduhan.");
        }
        return new File(dir, index + ".img");
    }

    /** Jumlah halaman yang sudah lengkap dan lolos validasi. */
    static int savedCount(ShinigamiDownloader local, String chapterId, int total) {
        int saved = 0;
        for (int index = 0; index < total; index++) {
            if (local.pageFile(chapterId, index) != null) saved++;
        }
        return saved;
    }

    static int percent(ShinigamiDownloader local, String chapterId, int total) {
        if (total <= 0) return 0;
        return Math.max(0, Math.min(100, savedCount(local, chapterId, total) * 100 / total));
    }

    /**
     * Simpan satu halaman. Isi yang bukan gambar, atau yang terpotong karena
     * koneksi putus, tidak pernah menjadi berkas final.
     */
    static void save(InputStream in, File target, int code, String mime) throws IOException {
        byte[] head = new byte[16];
        int read = 0;
        while (read < head.length) {
            int step = in.read(head, read, head.length - read);
            if (step == -1) break;
            read += step;
            if (read >= 8) break;
        }
        if (read < 8) throw new IOException("Gambar terlalu kecil.");
        ShinigamiImageRequest.validate(code, mime, java.util.Arrays.copyOf(head, read));

        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Tidak dapat membuat folder unduhan.");
        }
        File tmp = new File(parent, target.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(head, 0, read);
            byte[] buffer = new byte[8192];
            long total = read;
            int step;
            while ((step = in.read(buffer)) != -1) {
                total += step;
                if (total > MAX_BYTES) throw new IOException("Gambar terlalu besar.");
                out.write(buffer, 0, step);
            }
            out.flush();
            out.getFD().sync();
        } catch (IOException | RuntimeException error) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw error;
        }
        if (!tmp.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            if (!tmp.renameTo(target)) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                throw new IOException("Tidak dapat menyimpan gambar.");
            }
        }
    }
}
