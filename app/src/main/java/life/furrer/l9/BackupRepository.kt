package life.furrer.l9

import android.content.Context
import android.net.Uri
import org.json.JSONObject

/** Exports/imports the entire "l9" SharedPreferences file as JSON — every repository in this app
 *  (AppRepository, CalendarRepository, SpeedDialRepository) shares that one file, so a full
 *  backup/restore is just a dump/replace of its contents, no per-feature logic needed here. */
object BackupRepository {
    private const val PREFS_NAME = "l9"
    private const val BACKUP_APP_ID = "l9"
    private const val BACKUP_VERSION = 1

    fun exportTo(context: Context, uri: Uri): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val values = JSONObject()
            prefs.all.forEach { (key, value) ->
                when (value) {
                    is String -> values.put(key, value)
                    is Int -> values.put(key, value)
                    is Boolean -> values.put(key, value)
                    is Long -> values.put(key, value)
                    is Float -> values.put(key, value.toDouble())
                    // Sets and anything else aren't used by this app's preferences.
                    else -> Unit
                }
            }
            val root = JSONObject().apply {
                put("app", BACKUP_APP_ID)
                put("version", BACKUP_VERSION)
                put("preferences", values)
            }
            val output = context.contentResolver.openOutputStream(uri) ?: return false
            output.use { it.write(root.toString(2).toByteArray()) }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun importFrom(context: Context, uri: Uri): Boolean {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return false
            val text = input.use { it.readBytes().decodeToString() }
            val root = JSONObject(text)
            if (root.optString("app") != BACKUP_APP_ID) return false
            val values = root.getJSONObject("preferences")
            val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            editor.clear()
            values.keys().forEach { key ->
                when (val value = values.get(key)) {
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Long -> editor.putLong(key, value)
                    is Double -> editor.putFloat(key, value.toFloat())
                    else -> Unit
                }
            }
            editor.apply()
            true
        } catch (e: Exception) {
            false
        }
    }
}
