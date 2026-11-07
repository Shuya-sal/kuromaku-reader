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
     * Menyusun tampilan panel.
     *
     * @param mirrors       semua mirror yang tersedia, urut seperti di sumber
     * @param probed        indeks mirror → sumber hasil pemeriksaan (boleh kosong)
     * @param probing       indeks mirror yang sedang diperiksa
     * @param listener      aksi pengguna
     */
    View build(List<OtakudesuPlayback.Mirror> mirrors,
               LinkedHashMap<Integer, OtakudesuPlayback.Source> probed,
               List<Integer> probing,
               Listener listener) {
        ScrollView scroll = new ScrollView(context);
        scroll.setTag("anime_server_panel");
        scroll.setBackgroundColor(Color.TRANSPARENT);

        column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(4), dp(4), dp(4), dp(16));
        scroll.addView(column, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinkedHashMap<String, List<OtakudesuPlayback.Mirror>> groups =
                OtakudesuServerPicker.grouped(mirrors);

        if (mirrors.isEmpty()) {
            column.addView(note("Halaman episode ini tidak menyebut satu pun mirror."));
            return scroll;
        }

        for (String quality : groups.keySet()) {
            column.addView(groupHeader(quality));
            for (OtakudesuPlayback.Mirror mirror : groups.get(quality)) {
                int index = mirrors.indexOf(mirror);
                column.addView(row(mirror, index, probed.get(index),
                        probing.contains(index), listener));
            }
        }

        column.addView(checkAllRow(listener));
        return scroll;
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
                     OtakudesuPlayback.Source source, boolean checking, Listener listener) {
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
        text.addView(statusView(source, checking));
        row.addView(text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button action = new Button(context);
        action.setText(source == null ? "Pilih" : "Putar");
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

        row.setOnClickListener(v -> listener.onPick(mirror));

        row.setOnClickListener(v -> listener.onPick(mirror));
        return row;
    }

    /** Nama server apa adanya dari halaman episode; tidak pernah dikarang. */
    static String serverName(OtakudesuPlayback.Mirror mirror) {
        if (mirror == null) return "Server tanpa nama";
        String name = mirror.provider == null ? "" : mirror.provider.trim();
        return name.isEmpty() ? "Server tanpa nama" : name;
    }

    private TextView statusView(OtakudesuPlayback.Source source, boolean checking) {
        TextView status = new TextView(context);
        status.setTag("anime_server_status");
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        if (checking) {
            status.setText("Memeriksa…");
            status.setTextColor(palette.muted);
        } else if (source != null) {
            status.setText("Siap diputar"
                    + (source.kind == OtakudesuPlayback.Kind.HLS ? " · HLS" : " · MP4"));
            status.setTextColor(0xFF7FD18A);
        } else {
            status.setText("Belum diperiksa");
            status.setTextColor(palette.muted);
        }
        return status;
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
                                                 LinkedHashMap<Integer, OtakudesuPlayback.Source> probed) {
        List<OtakudesuPlayback.Choice> choices = new ArrayList<>();
        for (int i = 0; i < mirrors.size(); i++) {
            OtakudesuPlayback.Mirror mirror = mirrors.get(i);
            choices.add(new OtakudesuPlayback.Choice(
                    mirror.quality,
                    summary(mirror),
                    probed.get(i),
                    i,
                    probed.get(i) == null
                            ? OtakudesuServerPicker.Status.UNTESTED
                            : OtakudesuServerPicker.Status.READY));
        }
        return choices;
    }
}
