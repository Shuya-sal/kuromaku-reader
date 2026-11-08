package id.shuyasal.kuromaku.anime;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Panel "Pilih Server Streaming" berisi mirror yang benar-benar disebut halaman
 * episode, dikelompokkan per resolusi.
 *
 * <p>Setiap baris menyebut dua hal terpisah: nama server, dan status
 * pemeriksaannya. Status tidak pernah disimpulkan dari nama — "Siap diputar"
 * hanya muncul setelah halaman pemutar server itu benar-benar dibuka dan di
 * dalamnya tertulis alamat berkas media.
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
        /** Pengguna memilih satu server; sumber {@code null} berarti belum diperiksa. */
        void onPick(OtakudesuPlayback.Mirror mirror);

        /** Pengguna meminta seluruh server diperiksa lebih dulu. */
        void onCheckAll();
    }

    private final Context context;
    private final Palette palette;
    private LinearLayout column;

    OtakudesuServerPanel(Context context, Palette palette) {
        this.context = context;
        this.palette = palette;
    }

    /**
     * Susunan lama: tanpa daftar kegagalan. Dipertahankan agar pemanggil lama
     * tetap berjalan; semua mirror lalu dianggap belum diperiksa.
     */
    View build(List<OtakudesuPlayback.Mirror> mirrors,
               LinkedHashMap<String, OtakudesuPlayback.Source> probed,
               List<String> probing,
               Listener listener) {
        return build(mirrors, probed,
                new LinkedHashMap<String, OtakudesuServerPicker.Probed>(), probing, listener);
    }

    /**
     * Menyusun tampilan panel.
     *
     * <p>Urutan mengikuti prinsip "jangan menawarkan tombol putar palsu":
     * server yang benar-benar terverifikasi tampil lebih dulu dengan tombol
     * <b>Putar</b>; mirror lain hanya muncul setelah pengguna membuka
     * <b>Lihat semua server</b>, dengan status apa adanya dan tombol
     * <b>Periksa</b> — bukan Putar.
     *
     * @param mirrors semua mirror yang tersedia, urut seperti di sumber
     * @param probed  kunci mirror → sumber yang sudah terverifikasi
     * @param failed  kunci mirror → hasil pemeriksaan yang tidak bisa diputar
     * @param probing kunci mirror yang sedang diperiksa
     * @param listener aksi pengguna
     */
    View build(List<OtakudesuPlayback.Mirror> mirrors,
               LinkedHashMap<String, OtakudesuPlayback.Source> probed,
               LinkedHashMap<String, OtakudesuServerPicker.Probed> failed,
               List<String> probing,
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
        List<OtakudesuPlayback.Mirror> others = new ArrayList<>();
        for (OtakudesuPlayback.Mirror mirror : mirrors) {
            if (mirror == null) continue;
            String id = mirror.key();
            if (probed.get(id) != null) playable.add(mirror);
            else others.add(mirror);
        }

        if (playable.isEmpty()) {
            column.addView(note("Belum ada server yang terverifikasi dapat diputar di aplikasi. "
                    + "Periksa server di bawah, atau buka halaman sumbernya."));
        }
        for (OtakudesuPlayback.Mirror mirror : playable) {
            String id = mirror.key();
            column.addView(row(mirror, mirror.index, probed.get(id), failed.get(id),
                    probing.contains(id), listener));
        }
        if (!playable.isEmpty() && !others.isEmpty()) {
            column.addView(separator());
        }

        if (!others.isEmpty()) {
            // Tanpa server yang bisa diputar, daftar lain langsung terbuka:
            // menyembunyikannya hanya membuat layar tampak kosong.
            final boolean startExpanded = playable.isEmpty();
            LinearLayout hidden = new LinearLayout(context);
            hidden.setOrientation(LinearLayout.VERTICAL);
            hidden.setTag("anime_server_others");
            hidden.setVisibility(startExpanded ? View.VISIBLE : View.GONE);
            for (OtakudesuPlayback.Mirror mirror : others) {
                String id = mirror.key();
                hidden.addView(row(mirror, mirror.index, null, failed.get(id),
                        probing.contains(id), listener));
            }
            column.addView(hidden);

            // Tanpa satu pun server terverifikasi, daftar ini adalah isi panel:
            // tombol sembunyikan hanya akan menyisakan layar kosong.
            if (startExpanded) {
                column.addView(checkAllRow(listener));
                return scroll;
            }

            Button toggle = new Button(context);
            toggle.setTag("anime_server_toggle");
            toggle.setText("Lihat semua server (" + others.size() + ")");
            toggle.setAllCaps(false);
            toggle.setTextColor(palette.muted);
            toggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            toggle.setBackgroundColor(Color.TRANSPARENT);
            toggle.setMinHeight(dp(48));
            toggle.setOnClickListener(v -> {
                boolean show = hidden.getVisibility() != View.VISIBLE;
                hidden.setVisibility(show ? View.VISIBLE : View.GONE);
                toggle.setText(show
                        ? "Sembunyikan server lain"
                        : "Lihat semua server (" + others.size() + ")");
            });
            column.addView(toggle);
        }

        column.addView(checkAllRow(listener));
        return scroll;
    }

    private View separator() {
        View line = new View(context);
        line.setBackgroundColor(palette.muted & 0x33FFFFFF);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        params.setMargins(0, dp(10), 0, dp(4));
        line.setLayoutParams(params);
        return line;
    }

    private TextView groupHeader(String quality) {
        TextView header = new TextView(context);
        header.setText(quality);
        header.setTag("anime_server_group");
        header.setTextColor(palette.accent);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setPadding(dp(12), dp(14), dp(12), dp(6));
        return header;
    }

    private View row(OtakudesuPlayback.Mirror mirror, int index,
                     OtakudesuPlayback.Source source, OtakudesuServerPicker.Probed failure,
                     boolean checking, Listener listener) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setTag("anime_server_" + index);
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

        boolean playable = source != null;
        Button action = new Button(context);
        // Tombol hanya berbunyi "Putar" bila berkasnya benar-benar sudah
        // terverifikasi. Selain itu "Periksa" — tidak ada klaim palsu.
        action.setText(playable ? "Putar" : (checking ? "Memeriksa…" : "Periksa"));
        action.setTag("anime_server_action");
        action.setAllCaps(false);
        action.setEnabled(!checking);
        action.setAlpha(checking ? 0.5f : 1f);
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

        return row;
    }

    /** Nama server apa adanya dari halaman episode; tidak pernah dikarang. */
    /** Nama server apa adanya dari halaman episode; tidak pernah dikarang. */
    static String serverName(OtakudesuPlayback.Mirror mirror) {
        if (mirror == null) return "Server tanpa nama";
        String name = mirror.provider == null ? "" : mirror.provider.trim();
        return name.isEmpty() ? "Server tanpa nama" : name;
    }

    /**
     * Status satu baris server.
     *
     * <p>Empat keadaan dibedakan dengan teks: terverifikasi, sedang diperiksa,
     * tidak kompatibel, dan belum diperiksa. Tidak ada tebakan dari nama.
     */
    private TextView statusView(OtakudesuPlayback.Source source,
                                OtakudesuServerPicker.Probed failure,
                                boolean checking) {
        TextView status = new TextView(context);
        status.setTag("anime_server_status");
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        if (source != null) {
            status.setText("Terverifikasi dapat diputar"
                    + (source.kind == OtakudesuPlayback.Kind.HLS ? " · HLS" : " · MP4"));
            status.setTextColor(0xFF7FD18A);
        } else if (checking) {
            status.setText("Sedang diperiksa…");
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
                return "Terverifikasi dapat diputar";
            default:
                return "Belum diperiksa";
        }
    }

    private View checkAllRow(Listener listener) {
        Button checkAll = new Button(context);
        checkAll.setText("Periksa semua server");
        checkAll.setTag("anime_server_check_all");
        checkAll.setAllCaps(false);
        checkAll.setTextColor(palette.onSurface);
        checkAll.setBackground(id.shuyasal.kuromaku.ui.Ui.rounded(
                0x1AFFFFFF, 0x33FFFFFF, 12, context));
        checkAll.setMinHeight(dp(48));
        checkAll.setOnClickListener(v -> listener.onCheckAll());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(14), 0, 0);
        checkAll.setLayoutParams(params);
        return checkAll;
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
