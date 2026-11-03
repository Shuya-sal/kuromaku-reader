package id.shuyasal.kuromaku.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/** Stored per-volume covers. One small query per novel. */
@Dao
public interface VolumeCoverDao {

    @Query("SELECT * FROM volume_covers WHERE novelId = :novelId ORDER BY volume ASC")
    List<VolumeCoverRecord> byNovel(String novelId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<VolumeCoverRecord> rows);

    /** Drops the rows for one novel so a new parse replaces them as a whole. */
    @Query("DELETE FROM volume_covers WHERE novelId = :novelId")
    void deleteNovel(String novelId);

    @Query("SELECT COUNT(*) FROM volume_covers")
    int count();
}