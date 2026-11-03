package id.shuyasal.kuromaku.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * The library database.
 *
 * <p>Why a database instead of a preferences string: the library is now written
 * by the user, so it needs a schema that can be extended, a uniqueness rule that
 * the engine enforces (one row per normalised URL) and atomic writes. A JSON blob
 * in preferences would lose all three.
 *
 * <p>{@code allowMainThreadQueries()} is deliberate and narrow: the table holds a
 * handful of rows, every query is an indexed single-row lookup or one full scan of
 * at most a few dozen rows, and the rest of the screen (catalogue refresh, cover
 * loading, downloads) stays off the main thread. Keeping the read synchronous
 * means the library is on screen in the first frame instead of after an async
 * round trip that would flash an empty list on every launch.
 */
@Database(entities = {NovelRecord.class}, version = 1, exportSchema = false)
public abstract class NovelDatabase extends RoomDatabase {

    private static final String NAME = "kuromaku_library.db";

    private static volatile NovelDatabase instance;

    public abstract NovelDao novels();

    public static NovelDatabase get(Context context) {
        if (instance == null) {
            synchronized (NovelDatabase.class) {
                if (instance == null) {
                    instance = build(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private static NovelDatabase build(Context context) {
        return Room.databaseBuilder(context, NovelDatabase.class, NAME)
                .allowMainThreadQueries()
                .build();
    }

    /**
     * In-memory database for tests.
     *
     * <p>Not a mock: it is the real Room implementation with the real DAO, so the
     * uniqueness of the primary key, the ordering query and the metadata update
     * statement are all exercised exactly as they run on a device.
     */
    public static NovelDatabase inMemory(Context context) {
        return Room.inMemoryDatabaseBuilder(context, NovelDatabase.class)
                .allowMainThreadQueries()
                .build();
    }

    /** Test hook: replaces the singleton with an in-memory database. */
    static void use(NovelDatabase database) {
        instance = database;
    }

    /** Test hook: forgets the open database so a fresh one is created. */
    public static synchronized void reset() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }
}
