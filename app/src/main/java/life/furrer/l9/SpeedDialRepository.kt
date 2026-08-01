package life.furrer.l9

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONObject

data class SpeedDialContact(val name: String, val phoneNumber: String)

object SpeedDialRepository {
    private const val PREFS_NAME = "l9"
    private const val KEY_SPEED_DIALS = "speed_dials"

    fun hasCallPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    /** Keyed by physical digit 0-9. */
    fun loadContacts(context: Context): Map<Int, SpeedDialContact> {
        val json = prefs(context).getString(KEY_SPEED_DIALS, null) ?: return emptyMap()
        val obj = JSONObject(json)
        val result = mutableMapOf<Int, SpeedDialContact>()
        obj.keys().forEach { key ->
            val digit = key.toIntOrNull() ?: return@forEach
            val entry = obj.getJSONObject(key)
            result[digit] = SpeedDialContact(
                name = entry.getString("name"),
                phoneNumber = entry.getString("phoneNumber"),
            )
        }
        return result
    }

    fun setContact(context: Context, digit: Int, contact: SpeedDialContact?) {
        val current = loadContacts(context).toMutableMap()
        if (contact == null) current.remove(digit) else current[digit] = contact
        val obj = JSONObject()
        current.forEach { (d, c) ->
            val entry = JSONObject()
            entry.put("name", c.name)
            entry.put("phoneNumber", c.phoneNumber)
            obj.put(d.toString(), entry)
        }
        prefs(context).edit().putString(KEY_SPEED_DIALS, obj.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
