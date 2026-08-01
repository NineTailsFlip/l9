package life.furrer.l9

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        updateState()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        updateState()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        updateState()
    }

    private fun updateState() {
        val hasVisibleNotification = activeNotifications?.any { sbn ->
            sbn.notification.flags and Notification.FLAG_ONGOING_EVENT == 0
        } ?: false
        NotificationRepository.hasNotifications = hasVisibleNotification
        sendBroadcast(Intent(NotificationRepository.ACTION_NOTIFICATIONS_CHANGED).setPackage(packageName))
    }
}
