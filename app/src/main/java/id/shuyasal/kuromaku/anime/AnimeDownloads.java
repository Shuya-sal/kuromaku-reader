package id.shuyasal.kuromaku.anime;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Episode anime yang benar-benar tersimpan di penyimpanan khusus aplikasi.
 *
 * <p><b>Sumber kebenaran adalah berkas.</b> Sebuah episode dihitung tersimpan
 * hanya bila berkas medianya ada, ukurannya sama dengan yang dicatat, dan
 * kepalanya benar-benar berformat media (MP4/WebM). Status WorkManager tidak
 * dipakai untuk mengklaim apa pun, sehingga halaman Unduhan tetap benar tanpa
 * internet dan tanpa proses latar hidup.
 *
 * <p>Struktur di disk:
 * <pre>
 * files/anime-downloads/&lt;animeId&gt;/&lt;episodeKey&gt;.mp4    (data media)
 * files/anime-downloads/&lt;animeId&gt;/&lt;episodeKey&gt;.json   (keterangan)
 * files/anime-downloads/&lt;animeId&gt;/&lt;episodeKey&gt;.part   (sementara)
 * </pre>
 *
 * <p>Menghapus unduhan tidak pernah menyentuh progres menonton yang disimpan
 * {@link OtakudesuStore}; keduanya memang terpisah.
 */
public final class AnimeDownloads {

    static final String DIR = "anime-downloads";
    private static final String VIDEO_EXT = ".mp4";

    /** Satu anime beserta episode yang tersimpan di perangkat. */
    public static final class Anime {
        public final String animeId;
        public final String title;
        public final String poster;
        public final List<AnimeDownload> episodes = new ArrayList<>();

        Anime(String animeId, String title, String poster) {
            this.animeId = animeId == null ? "" : animeId;
            this.title = title == null ? "" : title;
            this.poster = poster == null ? "" : poster;
        }

        public int count() {
            return episodes.size();
        }

        public long bytes() {
            long total = 0;
            for (AnimeDownload episode : episodes) total += episode.bytes;
            return total;
        }

        /**
         * Episode terakhir yang ditonton, untuk tombol lanjutkan menonton.
         *
         * <p>Progres menonton dibaca dari {@link OtakudesuStore} dengan kunci
         * episode yang sama seperti pemutar ({@code OtakudesuSource.episodeId}).
         * Bila belum ada riwayat, episode pertama yang tersimpan dipakai supaya
         * tombolnya tetap berguna.
         */
        public AnimeDownload continueEpisode(OtakudesuStore store) {
            AnimeDownload best = null;
            long bestAt = -1L;
            for (AnimeDownload episode : episodes) {
                OtakudesuStore.Progress progress = store.progress(animeId,
                        OtakudesuSource.episodeId(episode.episodeUrl));
                if (progress == null) continue;
                if (progress.updatedAt > bestAt) {
                    bestAt = progress.updatedAt;
                    best = episode;
                }
            }
            if (best != null) return best;
            return episodes.isEmpty() ? null : episodes.get(0);
        }

        /** Persen menonton episode lanjutan, 0 bila belum pernah ditonton. */
        public int continuePercent(OtakudesuStore store) {
            AnimeDownload episode = continueEpisode(store);
            if (episode == null) return 0;
            OtakudesuStore.Progress progress = store.progress(animeId,
                    OtakudesuSource.episodeId(episode.episodeUrl));
            return progress == null ? 0 : progress.percent();
        }
    }

    private AnimeDownloads() {
    }

    /** Akar penyimpanan unduhan anime. */
    public static File root(Context context) {
        return new File(context.getFilesDir(), DIR);
    }

    public static File animeDir(Context context, String animeId) {
        return new File(root(context), safe(animeId));
    }

    /** Kunci episode yang stabil dari URL episode; aman dipakai sebagai nama berkas. */
    public static String episodeKey(String episodeUrl, String episodeLabel) {
        String seed = episodeUrl == null ? "" : episodeUrl.trim();
        if (seed.isEmpty()) seed = episodeLabel == null ? "" : episodeLabel.trim();
        if (seed.isEmpty()) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(seed.getBytes(StandardCharsets.UTF_8));
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < 8; i++) text.append(String.format(Locale.US, "%02x", hash[i]));
            return text.toString();
        } catch (Exception error) {
            return Integer.toHexString(seed.hashCode());
        }
    }

    public static File videoFile(Context context, String animeId, String episodeKey) {
        return new File(animeDir(context, animeId), safe(episodeKey) + VIDEO_EXT);
    }

    public static File metaFile(Context context, String animeId, String episodeKey) {
        return new File(animeDir(context, animeId), safe(episodeKey) + ".json");
    }

    public static File partFile(Context context, String animeId, String episodeKey) {
        return new File(animeDir(context, animeId), safe(episodeKey) + ".part");
    }

    /**
     * Episode yang benar-benar tersimpan dan lolos pemeriksaan.
     *
     * <p>Berkas harus ada, tidak kosong, ukurannya cocok dengan catatan, dan
     * kepalanya berformat media. Berkas setengah jadi dari unduhan yang gagal
     * tidak pernah dianggap tersimpan.
     */
    public static boolean isSaved(Context context, String animeId, String episodeKey) {
        if (episodeKey == null || episodeKey.isEmpty()) return false;
        File video = videoFile(context, animeId, episodeKey);
        if (!video.isFile() || video.length() <= 0) return false;
        AnimeDownload meta = readMeta(context, animeId, episodeKey);
        if (meta != null && meta.bytes > 0 && video.length() != meta.bytes) return false;
        return looksLikeMedia(video);
    }

    /** Keadaan berkas tanpa membaca catatan: dipakai untuk memutuskan sisa unduhan. */
    public static long savedBytes(Context context, String animeId, String episodeKey) {
        File video = videoFile(context, animeId, episodeKey);
        if (video.isFile() && video.length() > 0) return video.length();
        File part = partFile(context, animeId, episodeKey);
        return part.isFile() ? part.length() : 0L;
    }

    public static AnimeDownload readMeta(Context context, String animeId, String episodeKey) {
        File file = metaFile(context, animeId, episodeKey);
        if (!file.isFile()) return null;
        try {
            return AnimeDownload.fromJson(new JSONObject(read(file)));
        } catch (Exception error) {
            return null;
        }
    }

    public static void writeMeta(Context context, AnimeDownload meta) {
        if (meta == null || meta.episodeKey.isEmpty()) return;
        File dir = animeDir(context, meta.animeId);
        if (!dir.isDirectory() && !dir.mkdirs()) return;
        File target = metaFile(context, meta.animeId, meta.episodeKey);
        File temp = new File(dir, meta.episodeKey + ".json.tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(meta.toJson().toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.getFD().sync();
        } catch (IOException error) {
            return;
        }
        if (temp.renameTo(target)) return;
        // Sebagian sistem berkas menolak rename di atas berkas yang ada.
        if (target.delete() && temp.renameTo(target)) return;
        temp.delete();
    }

    /** Semua unduhan, dikelompokkan per anime dan diurutkan judul. */
    public static List<Anime> list(Context context) {
        Map<String, Anime> grouped = new LinkedHashMap<>();
        File root = root(context);
        File[] dirs = root.listFiles();
        if (dirs == null) return new ArrayList<>();
        for (File dir : dirs) {
            if (dir == null || !dir.isDirectory()) continue;
            String animeId = dir.getName();
            File[] files = dir.listFiles();
            if (files == null) continue;
            Anime group = null;
            List<AnimeDownload> found = new ArrayList<>();
            for (File file : files) {
                if (file == null || !file.isFile()) continue;
                if (!file.getName().endsWith(VIDEO_EXT)) continue;
                String key = file.getName().substring(0,
                        file.getName().length() - VIDEO_EXT.length());
                if (!isSaved(context, animeId, key)) continue;
                AnimeDownload meta = readMeta(context, animeId, key);
                if (meta == null) {
                    meta = new AnimeDownload(key, animeId, "", "", key, "", "", "",
                            "", file.length(), file.lastModified());
                }
                if (group == null) {
                    group = new Anime(animeId,
                            meta.animeTitle.isEmpty() ? animeId : meta.animeTitle,
                            meta.poster);
                }
                found.add(meta);
            }
            if (group == null) continue;
            Collections.sort(found, new Comparator<AnimeDownload>() {
                @Override public int compare(AnimeDownload a, AnimeDownload b) {
                    int byLabel = episodeNumber(a.episodeLabel) - episodeNumber(b.episodeLabel);
                    if (byLabel != 0) return byLabel;
                    return a.episodeLabel.compareToIgnoreCase(b.episodeLabel);
                }
            });
            group.episodes.addAll(found);
            grouped.put(animeId, group);
        }
        List<Anime> list = new ArrayList<>(grouped.values());
        Collections.sort(list, new Comparator<Anime>() {
            @Override public int compare(Anime a, Anime b) {
                return a.title.compareToIgnoreCase(b.title);
            }
        });
        return list;
    }

    /** Cari unduhan satu episode tanpa memindai seluruh koleksi. */
    public static AnimeDownload find(Context context, String animeId, String episodeKey) {
        if (!isSaved(context, animeId, episodeKey)) return null;
        return readMeta(context, animeId, episodeKey);
    }

    /** Hapus satu episode; mengembalikan true bila berkasnya memang hilang. */
    public static boolean delete(Context context, String animeId, String episodeKey) {
        if (episodeKey == null || episodeKey.isEmpty()) return false;
        File video = videoFile(context, animeId, episodeKey);
        File meta = metaFile(context, animeId, episodeKey);
        File part = partFile(context, animeId, episodeKey);
        boolean removed = !video.exists() || video.delete();
        meta.delete();
        part.delete();
        File dir = animeDir(context, animeId);
        String[] rest = dir.list();
        if (rest != null && rest.length == 0) dir.delete();
        return removed;
    }

    /**
     * Hapus seluruh unduhan satu anime.
     *
     * <p>Hanya berkas unduhan yang dibuang. Progres menonton di
     * {@link OtakudesuStore} tidak disentuh, jadi riwayat tetap ada setelah
     * unduhan dihapus.
     */
    public static int deleteAnime(Context context, String animeId) {
        File dir = animeDir(context, animeId);
        File[] files = dir.listFiles();
        int removed = 0;
        if (files != null) {
            for (File file : files) {
                if (file != null && file.isFile() && file.delete()) removed++;
            }
        }
        dir.delete();
        return removed;
    }

    /**
     * Bersihkan berkas sementara yang gagal.
     *
     * <p>Hanya {@code .part} yang dibuang; berkas lengkap dan catatannya tetap,
     * karena unduhan ulang yang gagal tidak boleh merusak episode yang sudah
     * tersimpan.
     */
    public static void clearPartials(Context context, String animeId, String episodeKey) {
        partFile(context, animeId, episodeKey).delete();
    }

    /** Apakah kepala berkas benar-benar berformat media yang dikenal. */
    static boolean looksLikeMedia(File file) {
        if (file == null || !file.isFile() || file.length() < 12) return false;
        byte[] head = new byte[12];
        try (InputStream in = new FileInputStream(file)) {
            int read = 0;
            while (read < head.length) {
                int step = in.read(head, read, head.length - read);
                if (step <= 0) break;
                read += step;
            }
            if (read < 12) return false;
        } catch (IOException error) {
            return false;
        }
        // MP4/MOV: kotak "ftyp" pada offset 4.
        if (head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p') return true;
        // WebM/Matroska: EBML 0x1A45DFA3.
        return (head[0] & 0xFF) == 0x1A && (head[1] & 0xFF) == 0x45
                && (head[2] & 0xFF) == 0xDF && (head[3] & 0xFF) == 0xA3;
    }

    static String read(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) Math.min(file.length(), 64 * 1024)];
            int read = 0;
            while (read < buffer.length) {
                int step = in.read(buffer, read, buffer.length - read);
                if (step <= 0) break;
                read += step;
            }
            return new String(buffer, 0, read, StandardCharsets.UTF_8);
        }
    }

    private static int episodeNumber(String label) {
        if (label == null) return Integer.MAX_VALUE;
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("(\\d+)").matcher(label);
        if (!matcher.find()) return Integer.MAX_VALUE;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException error) {
            return Integer.MAX_VALUE;
        }
    }

    private static String safe(String value) {
        if (value == null) return "";
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}