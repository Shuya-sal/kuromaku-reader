package id.shuyasal.kuromaku.komiku;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Katalog lengkap Komiku yang tersimpan di perangkat.
 *
 * <p>Beranda Komiku hanya memuat 40 judul, padahal katalognya sekitar delapan
 * ribu. Supaya judul di luar 40 itu tetap bisa dicari dan digulir tanpa
 * jaringan, katalog diambil halaman demi halaman dari alamat paginasi situs,
 * disatukan dengan slug sebagai kunci (judul yang muncul di dua halaman tidak
 * digandakan), lalu disimpan ke berkas.
 *
 * <p>Berkas ditulis lewat berkas sementara lalu diganti nama, jadi katalog lama
 * tetap utuh bila pengambilan terhenti di tengah jalan.
 */
public final class KomikuCatalog {

    /** Berkas katalog di penyimpanan internal aplikasi. */
    static final String FILE_NAME = "komiku-catalog.json";

    private static final int FORMAT = 1;

    public static final class Stats {
        public final int titles;
        public final int pages;
        public final long updatedAt;

        Stats(int titles, int pages, long updatedAt) {
            this.titles = titles;
            this.pages = pages;
            this.updatedAt = updatedAt;
        }

        public boolean isEmpty() {
            return titles == 0;
        }
    }

    /** Hasil satu putaran pengambilan. */
    public static final class Result {
        public final List<KomikuManga> items;
        public final int pagesRead;
        public final boolean complete;
        public final String stoppedBecause;

        Result(List<KomikuManga> items, int pagesRead, boolean complete, String stoppedBecause) {
            this.items = items;
            this.pagesRead = pagesRead;
            this.complete = complete;
            this.stoppedBecause = stoppedBecause == null ? "" : stoppedBecause;
        }
    }

    /**
     * Dipanggil di antara halaman supaya UI bisa menyebut kemajuannya dan
     * pemanggil bisa menyimpan hasil sementara.
     *
     * <p>Daftar yang diberikan adalah salinan beku dari yang terkumpul sejauh
     * itu, jadi menyimpannya tidak mengganggu pengambilan berikutnya.
     * {@code hasNext} menyatakan masih ada halaman berikutnya — bukan jumlah
     * halaman, karena situs tidak pernah menyebutkannya.
     */
    public interface Listener {
        /** @return {@code false} untuk berhenti (mis. layar ditutup). */
        boolean onPage(int page, List<KomikuManga> collected, boolean hasNext);
    }

    private final File file;

    public KomikuCatalog(Context context) {
        this.file = new File(context.getFilesDir(), FILE_NAME);
    }

    KomikuCatalog(File file) {
        this.file = file;
    }

    public File file() {
        return file;
    }

    // ---------------------------------------------------------------- baca

    /** Katalog tersimpan, atau daftar kosong bila belum pernah diambil. */
    public List<KomikuManga> load() {
        if (!file.isFile()) return new ArrayList<>();
        try {
            String text = readFile();
            JSONObject root = new JSONObject(text);
            JSONArray array = root.optJSONArray("items");
            if (array == null) return new ArrayList<>();
            List<KomikuManga> out = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String id = item.optString("id", "");
                if (id.isEmpty()) continue;
                out.add(new KomikuManga(id, item.optString("title", ""),
                        item.optString("url", ""), item.optString("cover", ""),
                        item.optString("type", ""), item.optString("chapter", ""),
                        item.optString("updated", "")));
            }
            return out;
        } catch (Exception broken) {
            // Berkas rusak tidak boleh membuat beranda gagal: katalog dianggap
            // belum ada, lalu diambil ulang saat pengguna memintanya.
            return new ArrayList<>();
        }
    }

    /** Ringkasan isi katalog tersimpan. */
    public Stats stats() {
        if (!file.isFile()) return new Stats(0, 0, 0);
        try {
            JSONObject root = new JSONObject(readFile());
            return new Stats(root.optInt("count", 0), root.optInt("pages", 0),
                    root.optLong("updatedAt", 0));
        } catch (Exception broken) {
            return new Stats(0, 0, 0);
        }
    }

    public boolean exists() {
        return file.isFile() && !load().isEmpty();
    }

    // --------------------------------------------------------------- tulis

    public void save(List<KomikuManga> items, int pages) throws IOException {
        JSONArray array = new JSONArray();
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (KomikuManga manga : items) {
            if (manga == null || manga.id.isEmpty()) continue;
            if (seen.put(manga.id, Boolean.TRUE) != null) continue;
            JSONObject item = new JSONObject();
            try {
                item.put("id", manga.id);
                item.put("title", manga.title);
                item.put("url", manga.url);
                item.put("cover", manga.coverUrl);
                item.put("type", manga.type);
                item.put("chapter", manga.latestChapter);
                item.put("updated", manga.updated);
            } catch (Exception ignored) {
                continue;
            }
            array.put(item);
        }
        JSONObject root = new JSONObject();
        try {
            root.put("format", FORMAT);
            root.put("count", array.length());
            root.put("pages", pages);
            root.put("updatedAt", System.currentTimeMillis());
            root.put("items", array);
        } catch (Exception ignored) {
            // JSONObject hanya menolak kunci null; kunci di atas selalu ada.
        }
        File temp = new File(file.getParentFile(), FILE_NAME + ".tmp");
        FileOutputStream stream = new FileOutputStream(temp);
        try (Writer writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8)) {
            writer.write(root.toString());
        }
        if (file.isFile() && !file.delete()) {
            // Gagal menghapus berkas lama bukan alasan membuang hasil baru;
            // rename di bawah akan menggantikannya bila sistem mengizinkan.
        }
        if (!temp.renameTo(file)) {
            throw new IOException("Katalog tidak dapat disimpan.");
        }
    }

    public void clear() {
        if (file.isFile() && !file.delete()) {
            // Tidak ada yang bisa dilakukan; katalog akan tertimpa saat diambil.
        }
    }

    private String readFile() throws IOException {
        byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------ pengambil

    /**
     * Ambil seluruh katalog dari alamat paginasi situs.
     *
     * <p>Berhenti saat halaman terakhir tercapai, saat halaman tidak memuat
     * judul baru (tanda paginasi berakhir), atau saat pemanggil meminta
     * berhenti. Hasil sebagian tetap dikembalikan bersama {@code complete=false}
     * — tidak pernah dilempar sebagai kegagalan total hanya karena satu halaman
     * lambat.
     */
    public Result fetchAll(KomikuSource source, Listener listener) {
        Map<String, KomikuManga> collected = new LinkedHashMap<>();
        int page = 1;
        int pagesRead = 0;
        boolean complete = false;
        String stopped = "";
        boolean hasNext = true;

        while (page <= KomikuSource.CATALOG_MAX_PAGES) {
            KomikuSource.CatalogPage batch;
            try {
                batch = source.catalogPageWithNext(page);
            } catch (KomikuSource.SourceException failure) {
                stopped = failure.getMessage();
                break;
            }
            pagesRead++;
            if (batch.items.isEmpty()) {
                complete = true;
                break;
            }
            int before = collected.size();
            for (KomikuManga manga : batch.items) {
                if (manga == null || manga.id.isEmpty()) continue;
                collected.putIfAbsent(manga.id, manga);
            }
            if (collected.size() == before && page > 1) {
                // Halaman ini tidak menambah judul baru: paginasi sudah habis.
                complete = true;
                break;
            }
            hasNext = batch.nextPage > page;
            if (listener != null && !listener.onPage(page,
                    new ArrayList<>(collected.values()), hasNext)) {
                stopped = "dihentikan";
                break;
            }
            if (!hasNext) {
                complete = true;
                break;
            }
            page++;
        }
        return new Result(new ArrayList<>(collected.values()), pagesRead, complete, stopped);
    }
}
