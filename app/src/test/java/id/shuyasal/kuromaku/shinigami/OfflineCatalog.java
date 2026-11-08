package id.shuyasal.kuromaku.shinigami;

/**
 * Beranda Manga Server 2 dengan sumber offline.
 *
 * <p>Dipakai pengujian menu: tidak ada satu pun permintaan jaringan, sehingga
 * hasilnya tidak bergantung pada koneksi mesin yang menjalankan tes.
 */
public class OfflineCatalog extends ShinigamiActivity {
    @Override protected ShinigamiSource createSource() {
        return new ShinigamiSource(this, url -> "{\"retcode\":0,\"data\":[]}");
    }
}
