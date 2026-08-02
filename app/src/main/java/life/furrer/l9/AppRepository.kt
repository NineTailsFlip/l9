package life.furrer.l9

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import life.furrer.nine.core.prefs.NinePrefs
import org.json.JSONArray
import org.json.JSONObject

enum class ListAlignment { LEFT, CENTER, RIGHT }

object AppRepository {
    private const val KEY_SLOTS = "slots"
    private const val KEY_SLOT_COUNT = "slot_count"
    private const val KEY_SHOW_INDICES = "show_indices"
    private const val KEY_ALIGNMENT = "list_alignment"
    private const val KEY_LEFT_APP = "left_app"
    private const val KEY_RIGHT_APP = "right_app"
    private const val KEY_SHOW_CALENDAR_DIVIDER = "show_calendar_divider"
    private const val KEY_BLACK_WALLPAPER_ENABLED = "black_wallpaper_enabled"
    private const val DEFAULT_SLOT_COUNT = 4

    fun getSlotCount(context: Context): Int =
        prefs(context).getInt(KEY_SLOT_COUNT, DEFAULT_SLOT_COUNT)

    fun setSlotCount(context: Context, count: Int) {
        prefs(context).edit().putInt(KEY_SLOT_COUNT, count).apply()
    }

    fun getShowIndices(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_INDICES, true)

    fun setShowIndices(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_INDICES, value).apply()
    }

    fun getShowCalendarDivider(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_CALENDAR_DIVIDER, true)

    fun setShowCalendarDivider(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_CALENDAR_DIVIDER, value).apply()
    }

    fun isBlackWallpaperEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BLACK_WALLPAPER_ENABLED, false)

    fun setBlackWallpaperEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_BLACK_WALLPAPER_ENABLED, value).apply()
    }

    fun getAlignment(context: Context): ListAlignment {
        val name = prefs(context).getString(KEY_ALIGNMENT, null) ?: return ListAlignment.LEFT
        return runCatching { ListAlignment.valueOf(name) }.getOrDefault(ListAlignment.LEFT)
    }

    fun setAlignment(context: Context, value: ListAlignment) {
        prefs(context).edit().putString(KEY_ALIGNMENT, value.name).apply()
    }

    fun loadSlots(context: Context): List<AppEntry?> {
        val count = getSlotCount(context)
        val json = prefs(context).getString(KEY_SLOTS, null) ?: return List(count) { null }
        val array = JSONArray(json)
        val slots = MutableList<AppEntry?>(count) { null }
        for (i in 0 until minOf(count, array.length())) {
            val obj = array.optJSONObject(i) ?: continue
            slots[i] = AppEntry.fromJson(obj)
        }
        return slots
    }

    fun saveSlots(context: Context, slots: List<AppEntry?>) {
        val array = JSONArray()
        for (slot in slots) {
            array.put(slot?.toJson() ?: JSONObject.NULL)
        }
        prefs(context).edit().putString(KEY_SLOTS, array.toString()).apply()
    }

    fun getLeftApp(context: Context): AppEntry? = loadSingleApp(context, KEY_LEFT_APP)

    fun setLeftApp(context: Context, entry: AppEntry?) = saveSingleApp(context, KEY_LEFT_APP, entry)

    fun getRightApp(context: Context): AppEntry? = loadSingleApp(context, KEY_RIGHT_APP)

    fun setRightApp(context: Context, entry: AppEntry?) = saveSingleApp(context, KEY_RIGHT_APP, entry)

    private fun loadSingleApp(context: Context, key: String): AppEntry? {
        val json = prefs(context).getString(key, null) ?: return null
        return AppEntry.fromJson(JSONObject(json))
    }

    private fun saveSingleApp(context: Context, key: String, entry: AppEntry?) {
        if (entry == null) {
            prefs(context).edit().remove(key).apply()
        } else {
            prefs(context).edit().putString(key, entry.toJson().toString()).apply()
        }
    }

    fun queryInstalledApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { info ->
                AppEntry(
                    packageName = info.activityInfo.packageName,
                    activityName = info.activityInfo.name,
                    label = info.loadLabel(pm).toString(),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    /** Launches [entry], returning false if the app is no longer installed/available. */
    fun launchApp(activity: Activity, entry: AppEntry): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(entry.packageName, entry.activityName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            activity.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    private fun prefs(context: Context) = NinePrefs.of(context)
}
