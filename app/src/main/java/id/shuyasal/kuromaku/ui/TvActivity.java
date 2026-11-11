package id.shuyasal.kuromaku.ui;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Dasar untuk semua layar yang harus bisa dipakai dengan remote televisi.
 *
 * <p>Semua layar Kuromaku dibangun dari kode, bukan dari berkas tata letak, dan
 * tidak satu pun view dibuat bisa menerima fokus. Di ponsel itu tidak masalah —
 * semuanya diketuk. Di televisi, view yang tidak bisa menerima fokus tidak akan
 * pernah bisa dipilih, sehingga layarnya tampak normal tetapi benar-benar tidak
 * bisa dijalankan.
 *
 * <p>Kelas ini menutup celah tersebut sekali untuk semua layar: setelah isi
 * layar terpasang, seluruh view yang bisa diketuk dibuat bisa dipilih dengan
 * tombol arah, diberi sorotan yang terlihat, dan view pertama langsung disorot
 * supaya pengguna tahu di mana kendali berada.
 *
 * <p>Pada ponsel dan tablet tidak ada yang berubah: pemasangan dilewati
 * sepenuhnya, jadi sentuhan, guliran, dan tampilan tetap persis seperti
 * sebelumnya.
 */
public abstract class TvActivity extends AppCompatActivity {

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        // Layar lebar: konten dibatasi agar baris teks tidak terlalu panjang
        // untuk dibaca dari sofa.
        if (TvMode.isTelevision(this)) {
            int max = Ui.dp(this, TvMode.maxContentWidthDp(this));
            if (max > 0) {
                android.view.Window window = getWindow();
                if (window != null) {
                    window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT);
                }
            }
        }
    }

    @Override public void setContentView(View view) {
        super.setContentView(view);
        prepareTv(view);
    }

    @Override public void setContentView(int layoutRes) {
        super.setContentView(layoutRes);
        prepareTv(findViewById(android.R.id.content));
    }

    /**
     * Siapkan fokus remote setelah isi layar selesai dibangun.
     *
     * <p>Dijalankan setelah satu putaran tata letak supaya ukuran dan posisi
     * view sudah diketahui — sorotan mengikuti bentuk kartu yang sebenarnya.
     */
    protected void prepareTv(View root) {
        if (root == null || !TvMode.isTelevision(this)) return;
        root.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            TvFocus.prepare(root, Palette.of(new Prefs(this).resolvedTheme()));
            if (getCurrentFocus() == null) {
                View first = firstFocusable(root);
                if (first != null) first.requestFocus();
            }
        });
    }

    /** View pertama yang bisa dipilih, untuk sorotan awal. */
    protected static View firstFocusable(View root) {
        if (root == null || root.getVisibility() != View.VISIBLE) return null;
        if (root.isFocusable() && root.isEnabled()) return root;
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) {
                View found = firstFocusable(group.getChildAt(index));
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Tombol kembali pada remote menutup layar, sama seperti tombol kembali
     * bawaan. Dibiarkan eksplisit supaya perilakunya tidak bergantung pada
     * pemetaan tombol perangkat.
     */
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (TvMode.isTelevision(this)
                && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                && event.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK) {
            // Diteruskan ke bawaan: perilaku kembali (termasuk keluar aplikasi
            // dari layar awal) sudah benar.
            return super.dispatchKeyEvent(event);
        }
        return super.dispatchKeyEvent(event);
    }
}
