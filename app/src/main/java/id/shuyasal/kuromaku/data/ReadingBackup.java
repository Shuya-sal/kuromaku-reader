package id.shuyasal.kuromaku.data;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Backup and restore of the reading state, as a file the user owns.
 *
 * <p>The reading position lives in one shared preference file: bookmark, exact
 * scroll offset, per-chapter progress, and the volume accordion state. Every one of
 * those is keyed per novel, so the store is a flat map of mixed types. A backup that
 * dropped the type would restore "0" into an int key and silently reset progress, so
 * each value is written with its type and read back accordingly.
 *
 * <p>This is deliberately not part of the activity: the export and import have to be
 * provable without an Android activity, and a restore that half-fails is worse than
 * one that refuses. {@link #restore} validates the whole document first and reports
 * what it accepted, so the caller can say how much came back.
 */
public final class ReadingBackup {

    /** Written into the file and checked on restore; bump if the shape changes. */
    public static final String FORMAT = "kuromaku-reading-v1";

    /** A reading store for three novels is a few kilobytes; this is a ceiling, not a target. */
    public static final int MAX_BYTES = 5_000_000;

    private static final String KEY_FORMAT = "format";
    private static final String KEY_PREFS = "preferences";
    private static final String KEY_TYPE = "type";
    private static final String KEY_VALUE = "value";

    private static final String KEY_SHINIGAMI = "shinigami_preferences";

    private ReadingBackup() {
    }

    /** The whole store as a JSON document, every value tagged with its type. */
    public static String export(SharedPreferences store) throws JSONException {
        return export(store, null);
    }

    public static String export(SharedPreferences store, SharedPreferences shinigami)
            throws JSONException {
        JSONObject document = new JSONObject();
        document.put(KEY_FORMAT, FORMAT);
        document.put(KEY_PREFS, values(store));
        if (shinigami != null) {
            JSONObject extra = values(shinigami);
            if (extra.length() > 0) document.put(KEY_SHINIGAMI, extra);
        }
        return document.toString(2);
    }

    private static JSONObject values(SharedPreferences store) throws JSONException {
        JSONObject values = new JSONObject();
        for (Map.Entry<String, ?> entry : store.getAll().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key == null) continue;
            JSONObject typed = new JSONObject();
            if (value instanceof String) {
                typed.put(KEY_TYPE, "string");
                typed.put(KEY_VALUE, value);
            } else if (value instanceof Integer) {
                typed.put(KEY_TYPE, "int");
                typed.put(KEY_VALUE, value);
            } else if (value instanceof Long) {
                typed.put(KEY_TYPE, "long");
                typed.put(KEY_VALUE, value);
            } else if (value instanceof Float) {
                typed.put(KEY_TYPE, "float");
                typed.put(KEY_VALUE, value);
            } else if (value instanceof Boolean) {
                typed.put(KEY_TYPE, "boolean");
                typed.put(KEY_VALUE, value);
            } else if (value instanceof Set) {
                // The accordion state. Restoring it as a plain string would lose
                // every open volume, and the reader would look like it forgot them.
                JSONArray array = new JSONArray();
                for (Object item : (Set<?>) value) {
                    if (item instanceof String) array.put(item);
                }
                typed.put(KEY_TYPE, "stringset");
                typed.put(KEY_VALUE, array);
            } else {
                // A type this format does not carry. Skipping it keeps the export
                // readable instead of failing the whole backup over one key.
                continue;
            }
            values.put(key, typed);
        }
        return values;
    }

    /**
     * Reads a backup document into the store and returns how many values it accepted.
     *
     * <p>The document is parsed completely before anything is written, so a truncated
     * or foreign file leaves the existing progress untouched. Existing keys that the
     * file does not mention are left alone: restoring a backup of one novel must not
     * wipe another novel's bookmark.
     */
    public static int restore(SharedPreferences store, String document) throws JSONException {
        return restore(store, document, null);
    }

    public static int restore(SharedPreferences store, String document,
                              SharedPreferences shinigami) throws JSONException {
        if (document == null) throw new JSONException("Berkas backup kosong");
        if (document.length() > MAX_BYTES) throw new JSONException("Berkas backup terlalu besar");

        JSONObject root = new JSONObject(document);
        String format = root.optString(KEY_FORMAT, "");
        if (!FORMAT.equals(format)) {
            throw new JSONException("Format backup tidak dikenali: " + format);
        }
        JSONObject values = root.optJSONObject(KEY_PREFS);
        if (values == null) throw new JSONException("Backup tidak memuat progres");

        // Validate everything first; only then touch the store.
        List<String> keys = new ArrayList<>();
        List<Object> pending = new ArrayList<>();
        readTyped(values, keys, pending);

        List<String> extraKeys = new ArrayList<>();
        List<Object> extraPending = new ArrayList<>();
        JSONObject extraValues = root.optJSONObject(KEY_SHINIGAMI);
        if (extraValues != null) {
            readTyped(extraValues, extraKeys, extraPending);
        }

        SharedPreferences.Editor editor = store.edit();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            Object value = pending.get(i);
            if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Set) {
                @SuppressWarnings("unchecked")
                Set<String> set = (Set<String>) value;
                editor.putStringSet(key, set);
            }
        }
        if (!editor.commit()) throw new JSONException("Gagal menyimpan progres");

        int restoredExtra = 0;
        if (shinigami != null && !extraKeys.isEmpty()) {
            SharedPreferences.Editor extraEditor = shinigami.edit();
            for (int i = 0; i < extraKeys.size(); i++) {
                String key = extraKeys.get(i);
                Object value = extraPending.get(i);
                if (value instanceof String) extraEditor.putString(key, (String) value);
                else if (value instanceof Integer) extraEditor.putInt(key, (Integer) value);
                else if (value instanceof Long) extraEditor.putLong(key, (Long) value);
                else if (value instanceof Float) extraEditor.putFloat(key, (Float) value);
                else if (value instanceof Boolean) extraEditor.putBoolean(key, (Boolean) value);
                else if (value instanceof Set) {
                    @SuppressWarnings("unchecked")
                    Set<String> set = (Set<String>) value;
                    extraEditor.putStringSet(key, set);
                }
            }
            if (!extraEditor.commit()) throw new JSONException("Gagal menyimpan progres manga");
            restoredExtra = extraKeys.size();
        }

        return keys.size() + restoredExtra;
    }

    private static void readTyped(JSONObject values, List<String> keys, List<Object> pending)
            throws JSONException {
        Iterator<String> iterator = values.keys();
        while (iterator.hasNext()) {
            String key = iterator.next();
            JSONObject typed = values.optJSONObject(key);
            if (typed == null) continue;
            String type = typed.optString(KEY_TYPE, "");
            Object value;
            switch (type) {
                case "string":
                    value = typed.getString(KEY_VALUE);
                    break;
                case "int":
                    value = typed.getInt(KEY_VALUE);
                    break;
                case "long":
                    value = typed.getLong(KEY_VALUE);
                    break;
                case "float":
                    value = (float) typed.getDouble(KEY_VALUE);
                    break;
                case "boolean":
                    value = typed.getBoolean(KEY_VALUE);
                    break;
                case "stringset":
                    JSONArray array = typed.getJSONArray(KEY_VALUE);
                    Set<String> set = new HashSet<>();
                    for (int i = 0; i < array.length(); i++) set.add(array.getString(i));
                    value = set;
                    break;
                default:
                    // Unknown type from a newer build: skip this key, keep the rest.
                    continue;
            }
            keys.add(key);
            pending.add(value);
        }
    }
}
