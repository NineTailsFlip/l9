package life.furrer.l9

import org.json.JSONObject

/** [customLabel] and [hidden] are never part of this entry's own JSON — they're resolved from
 *  AppRepository's package-keyed override store at load time (see AppRepository.withOverride), so
 *  a rename or hide made anywhere (a slot, the side keys, Browse all) is reflected everywhere else
 *  that references the same app, instead of drifting per place it's referenced. */
data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val customLabel: String? = null,
    val hidden: Boolean = false,
) {
    val displayLabel: String get() = customLabel ?: label

    fun toJson(): JSONObject = JSONObject().apply {
        put("packageName", packageName)
        put("activityName", activityName)
        put("label", label)
    }

    companion object {
        fun fromJson(obj: JSONObject): AppEntry = AppEntry(
            packageName = obj.getString("packageName"),
            activityName = obj.getString("activityName"),
            label = obj.getString("label"),
        )
    }
}
