package life.furrer.l9

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object NotificationRepository {
    const val ACTION_NOTIFICATIONS_CHANGED = "life.furrer.l9.NOTIFICATIONS_CHANGED"

    @Volatile
    var hasNotifications: Boolean = false

    fun isListenerEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?: return false
        val self = ComponentName(context, NotificationListener::class.java).flattenToString()
        return enabled.split(":").any { it == self }
    }
}
