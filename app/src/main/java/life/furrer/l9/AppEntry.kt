package life.furrer.l9

import org.json.JSONObject

data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val customLabel: String? = null,
) {
    val displayLabel: String get() = customLabel ?: label

    fun toJson(): JSONObject = JSONObject().apply {
        put("packageName", packageName)
        put("activityName", activityName)
        put("label", label)
        if (customLabel != null) put("customLabel", customLabel)
    }

    companion object {
        fun fromJson(obj: JSONObject): AppEntry = AppEntry(
            packageName = obj.getString("packageName"),
            activityName = obj.getString("activityName"),
            label = obj.getString("label"),
            customLabel = obj.optString("customLabel").ifEmpty { null },
        )
    }
}
