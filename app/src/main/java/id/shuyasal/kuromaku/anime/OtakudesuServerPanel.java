package id.shuyasal.kuromaku.anime;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Panel "Pilih Server Streaming".
 *
 * <p>Tiga bagian, urut menurut kepentingan pengguna:
 * <ol>
 *   <li><b>Siap diputar</b> — hanya mirror yang berkasnya benar-benar terbaca
 *       pemutar native, dikelompokkan per kualitas, dengan tombol Putar.</li>
 *   <li><b>Sedang diperiksa</b> — mirror yang pemeriksaannya berjalan; tidak
 *       ada tombol apa pun, hanya indikator.</li>
 *   <li><b>Server lainnya</b> — dilipat; berisi mirror yang gagal atau belum
 *       dapat diverifikasi, lengkap dengan alasannya.</li>
 * </ol>
 *
 * <p>Status tidak pernah disimpulkan dari nama server. "Siap diputar" hanya
 * muncul setelah halaman pemutar mirror itu benar-benar menyebut alamat berkas
 * media yang bisa dibaca Media3.
 */
final class OtakudesuServerPanel {

    /** Tema ringkas agar panel mengikuti tema aplikasi. */
    static final class Palette {
        final int surface;
        final int onSurface;
        final int muted;
        final int accent;
        final int ripple;

        Palette(int surface, int onSurface, int muted, int accent, int ripple) {
            this.surface = surface;
            this.onSurface = onSurface;
            this.muted = muted;
            this.accent = accent;
            this.ripple = ripple;
        }
    }

    interface Listener {
        /** Pengguna memilih satu server. */
        void onPick(OtakudesuPlayback.Mirror mirror);

        /** Periksa ulang mirror yang gagal. */
        void onRetryFailed();

        /** Buka halaman sumber di peramban. */
        void onWatchSource();
    }

    private final Context context;
    private final Palette palette;
    private LinearLayout column;

    OtakudesuServerPanel(Context context, Palette palette) {
        this.context = context;
        this.palette = palette;
    }

    /**
     * Menyusun tampilan panel.
     *
     * @param mirrors  semua mirror yang disebut halaman episode
     * @param probed   kunci mirror → sumber yang sudah terverifikasi
     * @param failed   kunci mirror → hasil pemeriksaan yang tidak bisa diputar
     * @param checking kunci mirror yang sedang atau akan diperiksa
     * @param listener aksi pengguna
     */
    View build(List<OtakudesuPlayback.Mirror> mirrors,
               LinkedHashMap<String, OtakudesuPlayback.Source> probed,
               LinkedHashMap<String, OtakudesuServerPicker.Probed> failed,
               Set<String> checking,
               Listener listener) {
        ScrollView scroll = new ScrollView(context);
        scroll.setTag("anime_server_panel");
        scroll.setBackgroundColor(Color.TRANSPARENT);

        column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(4), dp(4), dp(4), dp(16));
        scroll.addView(column, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (mirrors.isEmpty()) {
            column.addView(note("Halaman episode ini tidak menyebut satu pun mirror."));
            return scroll;
        }

        List<OtakudesuPlayback.Mirror> playable = new ArrayList<>();
        List<OtakudesuPlayback.Mirror> pending = new ArrayList<>();
        List<OtakudesuPlayback.Mirror> others = new ArrayList<>();
        for (OtakudesuPlayback.Mirror mirror : mirrors) {
            if (mirror == null) continue;
            String id = mirror.key();
            if (probed.get(id) != null) playable.add(mirror);
            else if (checking.contains(id)) pending.add(mirror);
            else others.add(mirror);
        }

        if (!playable.isEmpty()) {
            column.addView(sectionHeader("Siap diputar"));
            // Dikelompokkan per kualitas: pengguna memilih resolusi lebih dulu,
            // baru server — sama seperti urutan di halaman sumber.
            for (String quality : qualities(playable)) {
                column.addView(groupHeader(quality));
                for (OtakudesuPlayback.Mirror mirror : playable) {
                    if (!quality.equals(qualityOf(mirror))) continue;
                    column.addView(row(mirror, probed.get(mirror.key()), null, false, listener));
                }
            }
        }

        if (!pending.isEmpty()) {
            column.addView(sectionHeader("Sedang diperiksa"));
            for (OtakudesuPlayback.Mirror mirror : pending) {
                column.addView(row(mirror, null, null, true, listener));
            }
        }

        if (!others.isEmpty()) {
            column.addView(othersSection(others, failed, listener));
        }

        if (playable.isEmpty() && pending.isEmpty()) {
            column.addView(emptyState(listener));
        }
        return scroll;
    }

    /** Bagian lipat berisi mirror yang tidak bisa diputar, beserta alasannya. */
    private View othersSection(List<OtakudesuPlayback.Mirror> others,
                               LinkedHashMap<String, OtakudesuServerPicker.Probed> failed,
                               Listener listener) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);

        LinearLayout hidden = new LinearLayout(context);
        hidden.setOrientation(LinearLayout.VERTICAL);
        hidden.setTag("anime_server_others");
        hidden.setVisibility(View.GONE);
        for (OtakudesuPlayback.Mirror mirror : others) {
            hidden.addView(row(mirror, null, failed.get(mirror.key()), false, listener));
        }
        box.addView(hidden);

        Button toggle = new Button(context);
        toggle.setTag("anime_server_toggle");
        toggle.setText("Server lainnya (" + others.size() + ")");
        toggle.setAllCaps(false);
        toggle.setTextColor(palette.muted);
        toggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        toggle.setBackgroundColor(Color.TRANSPARENT);
        toggle.setMinHeight(dp(48));
        toggle.setOnClickListener(v -> {
            boolean show = hidden.getVisibility() != View.VISIBLE;
            hidden.setVisibility(show ? View.VISIBLE : View.GONE);
            toggle.setText(show
                    ? "Sembunyikan server lainnya"
                    : "Server lainnya (" + others.size() + ")");
        });
        box.addView(toggle);

        // Retry hanya relevan bila ada yang benar-benar gagal diperiksa.
        if (!failed.isEmpty()) {
            Button retry = new Button(context);
            retry.setTag("anime_server_retry");
            retry.setText("Periksa ulang yang gagal");
            retry.setAllCaps(false);
            retry.setTextColor(palette.accent);
            retry.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            retry.setBackgroundColor(Color.TRANSPARENT);
            retry.setMinHeight(dp(48));
            retry.setOnClickListener(v -> listener.onRetryFailed());
            box.addView(retry);
        }
        return box;
    }

    /** Tidak ada satu pun server yang bisa diputar: beri dua jalan keluar. */
    private View emptyState(Listener listener) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setTag("anime_server_empty");
        box.setPadding(dp(12), dp(16), dp(12), dp(8));

        TextView message = new TextView(context);
        message.setText("Belum ada server yang bisa diputar di aplikasi untuk episode ini.");
        message.setTextColor(palette.onSurface);
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        box.addView(message);

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(10), 0, 0);

        Button retry = new Button(context);
        retry.setTag("anime_server_retry");
        retry.setText("Coba lagi");
        retry.setAllCaps(false);
        retry.setTextColor(palette.accent);
        retry.setBackground(id.shuyasal.kuromaku.ui.Ui.rounded(
                palette.accent & 0x33FFFFFF, palette.ripple, 10, context));
        retry.setOnClickListener(v -> listener.onRetryFailed());
        actions.addView(retry);

        Button source = new Button(context);
        source.setTag("anime_server_source");
        source.setText("Tonton di Sumber");
        source.setAllCaps(false);
        source.setTextColor(palette.onSurface);
        source.setBackground(id.shuyasal.kuromaku.ui.Ui.rounded(
                0x1AFFFFFF, 0x33FFFFFF, 10, context));
        source.setOnClickListener(v -> listener.onWatchSource());
        LinearLayout.LayoutParams sourceParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sourceParams.leftMargin = dp(8);
        actions.addView(source, sourceParams);

        box.addView(actions);
        return box;
    }

    private TextView sectionHeader(String text) {
        TextView header = new TextView(context);
        header.setText(text);
        header.setTag("anime_server_section");
        header.setTextColor(palette.onSurface);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setPadding(dp(12), dp(14), dp(12), dp(2));
        return header;
    }

    private TextView groupHeader(String quality) {
        TextView header = new TextView(context);
        header.setText(quality);
        header.setTag("anime_server_group");
        header.setTextColor(palette.accent);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        header.setPadding(dp(12), dp(8), dp(12), dp(2));
        return header;
    }

    private View row(OtakudesuPlayback.Mirror mirror,
                     OtakudesuPlayback.Source source,
                     OtakudesuServerPicker.Probed failure,
                     boolean checking,
                     Listener listener) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setTag("anime_server_" + mirror.index);
        row.setBackground(id.shuyasal.kuromaku.ui.Ui.rounded(
                0x14FFFFFF, 0x22FFFFFF, 12, context));
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setMinimumHeight(dp(48));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(4), 0, 0);
        row.setLayoutParams(params);

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(LinearLayout.VERTICAL);

        // Resolusi diulang pada baris server: header grup bisa tergulir keluar
        // layar, dan pengguna tetap harus tahu resolusi pilihannya.
        LinearLayout heading = new LinearLayout(context);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(context);
        name.setText(serverName(mirror));
        name.setTag("anime_server_name");
        // Warna permukaan tema dipakai apa adanya supaya kontras terjaga pada
        // tema terang maupun gelap; sebelumnya teks dipaksa putih 0xFFFFFFFF.
        name.setTextColor(palette.onSurface);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        heading.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView quality = new TextView(context);
        quality.setText(mirror.quality.isEmpty() ? "\u2014" : mirror.quality);
        quality.setTag("anime_server_resolution");
        quality.setTextColor(palette.muted);
        quality.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        quality.setPadding(dp(8), 0, 0, 0);
        heading.addView(quality);

        text.addView(heading);
        text.addView(statusView(source, failure, checking));
        row.addView(text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (checking) {
            // Indikator kecil, bukan tombol: tidak ada yang perlu ditekan.
            ProgressBar spinner = new ProgressBar(context);
            spinner.setTag("anime_server_spinner");
            spinner.setIndeterminate(true);
            spinner.setLayoutParams(new LinearLayout.LayoutParams(dp(20), dp(20)));
            row.addView(spinner);
            return row;
        }

        if (source != null) {
            Button action = new Button(context);
            action.setText("Putar");
            action.setTag("anime_server_action");
            action.setAllCaps(false);
            action.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            // Tombol berwarna tema punya kontras yang sama dengan bilah lainnya,
            // jadi tombol ini tetap terbaca pada kedua tema.
            action.setTextColor(palette.accent);
            action.setBackground(id.shuyasal.kuromaku.ui.Ui.rounded(
                    palette.accent & 0x33FFFFFF, palette.ripple, 10, context));
            action.setPadding(dp(14), dp(8), dp(14), dp(8));
            action.setMinimumHeight(dp(40));
            action.setMinimumWidth(dp(64));
            action.setOnClickListener(v -> listener.onPick(mirror));
            row.addView(action);
        }
        return row;
    }

    /**
     * Status satu baris server.
     *
     * <p>Empat keadaan dibedakan dengan teks: siap, sedang diperiksa, tidak
     * kompatibel, dan belum diperiksa. Tidak ada tebakan dari nama.
     */
    private TextView statusView(OtakudesuPlayback.Source source,
                                OtakudesuServerPicker.Probed failure,
                                boolean checking) {
        TextView status = new TextView(context);
        status.setTag("anime_server_status");
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        if (source != null) {
            status.setText("Siap diputar"
                    + (source.kind == OtakudesuPlayback.Kind.HLS ? " · HLS" : " · MP4"));
            status.setTextColor(0xFF7FD18A);
        } else if (checking) {
            status.setText("Memeriksa…");
            status.setTextColor(palette.muted);
        } else if (failure != null) {
            status.setText(statusText(failure));
            status.setTextColor(failure.status == OtakudesuServerPicker.Status.PROTECTED_SERVER
                    ? 0xFFD8B26A : 0xFFD18A8A);
        } else {
            status.setText("Belum diperiksa");
            status.setTextColor(palette.muted);
        }
        return status;
    }

    /** Kalimat status untuk mirror yang tidak bisa diputar, beserta alasannya. */
    static String statusText(OtakudesuServerPicker.Probed failure) {
        if (failure == null || failure.status == null) return "Belum diperiksa";
        String note = failure.note == null ? "" : failure.note.trim();
        switch (failure.status) {
            case PROTECTED_SERVER:
                return "Tidak kompatibel — hanya pemutar penyedia"
                        + (note.isEmpty() ? "" : " (" + note + ")");
            case UNAVAILABLE:
                return "Tidak tersedia" + (note.isEmpty() ? "" : " — " + note);
            case READY:
                return "Siap diputar";
            default:
                return "Belum diperiksa";
        }
    }

    private static String qualityOf(OtakudesuPlayback.Mirror mirror) {
        return mirror.quality == null || mirror.quality.isEmpty() ? "\u2014" : mirror.quality;
    }

    /** Kualitas unik, urut seperti kemunculan pertama di halaman episode. */
    private static List<String> qualities(List<OtakudesuPlayback.Mirror> mirrors) {
        Set<String> seen = new LinkedHashSet<>();
        for (OtakudesuPlayback.Mirror mirror : mirrors) seen.add(qualityOf(mirror));
        return new ArrayList<>(seen);
    }

    /** Nama server apa adanya dari halaman episode; tidak pernah dikarang. */
    static String serverName(OtakudesuPlayback.Mirror mirror) {
        if (mirror == null) return "Server tanpa nama";
        String name = mirror.provider == null ? "" : mirror.provider.trim();
        return name.isEmpty() ? "Server tanpa nama" : name;
    }

    private TextView note(String message) {
        TextView view = new TextView(context);
        view.setText(message);
        view.setTextColor(palette.muted);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        view.setPadding(dp(12), dp(12), dp(12), dp(12));
        return view;
    }

    private int dp(int value) {
        return id.shuyasal.kuromaku.ui.Ui.dp(context, value);
    }

    /** Ringkasan status untuk tombol "Server": mis. "ODCDN 480p". */
    static String summary(OtakudesuPlayback.Mirror mirror) {
        if (mirror == null) return "Server";
        String name = mirror.provider == null ? "" : mirror.provider.trim();
        if (name.isEmpty()) name = "Server";
        if (mirror.quality == null || mirror.quality.isEmpty()) return name;
        return String.format(Locale.ROOT, "%s %s", name, mirror.quality);
    }

    /**
     * Label tombol Server: jumlah yang benar-benar siap, bukan jumlah mirror.
     *
     * <p>"Server (9)" membuat pengguna mengira sembilan pilihan bisa diputar.
     * Yang ditampilkan: {@code 2 siap · 9 mirror}.
     */
    static String counter(int ready, int total) {
        if (total <= 0) return "Server";
        if (ready <= 0) return total + " mirror";
        return ready + " siap · " + total + " mirror";
    }

    /** Pilihan sintetis untuk hasil pemeriksaan yang dipakai pemutar. */
    static List<OtakudesuPlayback.Choice> choices(List<OtakudesuPlayback.Mirror> mirrors,
                                                 LinkedHashMap<String, OtakudesuPlayback.Source> probed) {
        List<OtakudesuPlayback.Choice> choices = new ArrayList<>();
        for (int i = 0; i < mirrors.size(); i++) {
            OtakudesuPlayback.Mirror mirror = mirrors.get(i);
            choices.add(new OtakudesuPlayback.Choice(
                    mirror.quality,
                    summary(mirror),
                    probed.get(mirror.key()),
                    i,
                    probed.get(mirror.key()) == null
                            ? OtakudesuServerPicker.Status.UNTESTED
                            : OtakudesuServerPicker.Status.READY));
        }
        return choices;
    }
}