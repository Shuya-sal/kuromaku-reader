package id.shuyasal.kuromaku.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/** Library table access. Everything here is a single small query. */
@Dao
public interface NovelDao {

    @Query("SELECT * FROM novels ORDER BY position ASC, addedAt ASC")
    List<NovelRecord> all();

    @Query("SELECT * FROM novels WHERE id = :id LIMIT 1")
    NovelRecord byId(String id);

    /** Used to refuse a duplicate before it is inserted. */
    @Query("SELECT * FROM novels WHERE url = :url LIMIT 1")
    NovelRecord byUrl(String url);

    @Query("SELECT COUNT(*) FROM novels")
    int count();

    @Query("SELECT IFNULL(MAX(position), -1) FROM novels")
    int maxPosition();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(NovelRecord record);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<NovelRecord> records);

    /** Deletes the library entry only; downloads and progress stay untouched. */
    @Query("DELETE FROM novels WHERE id = :id")
    void delete(String id);

    /** Updates the refreshed metadata without touching position or progress. */
    @Query("UPDATE novels SET title = :title, titleJp = :titleJp, fullTitle = :fullTitle,"
            + " author = :author, illustrator = :illustrator, genres = :genres,"
            + " status = :status, coverUrl = :coverUrl, coverRatio = :coverRatio,"
            + " synopsis = :synopsis, synopsisPreview = :synopsisPreview,"
            + " slugPrefix = :slugPrefix, lastRefresh = :lastRefresh WHERE id = :id")
    void updateMetadata(String id, String title, String titleJp, String fullTitle,
                        String author, String illustrator, String genres, String status,
                        String coverUrl, float coverRatio, String synopsis,
                        int synopsisPreview, String slugPrefix, long lastRefresh);

    /**
     * Points an existing row at a different catalogue source.
     *
     * <p>Used when a built-in novel's source changes between app versions: the row
     * keeps its id (so progress, bookmarks and offline folders still resolve), and
     * only the three columns describing how to read the catalogue are rewritten. The
     * URL is passed back unchanged so a stored address is never replaced.
     */
    @Query("UPDATE novels SET url = :url, catalogMode = :catalogMode,"
            + " slugPrefix = :slugPrefix, feedLabel = :feedLabel WHERE id = :id")
    void updateCatalogueSource(String id, String url, int catalogMode,
                               String slugPrefix, String feedLabel);
}
