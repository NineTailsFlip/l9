package life.furrer.l9

import android.content.ComponentName
import android.provider.Settings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NotificationRepositoryTest {

    private val context = RuntimeEnvironment.getApplication()
    private val self = ComponentName(context, NotificationListener::class.java).flattenToString()

    @Test
    fun `listener is not enabled when the secure setting is unset`() {
        assertFalse(NotificationRepository.isListenerEnabled(context))
    }

    @Test
    fun `listener is not enabled when the setting lists only other listeners`() {
        Settings.Secure.putString(
            context.contentResolver,
            "enabled_notification_listeners",
            "some.other.pkg/some.other.Listener",
        )
        assertFalse(NotificationRepository.isListenerEnabled(context))
    }

    @Test
    fun `listener is enabled when it is the only entry`() {
        Settings.Secure.putString(context.contentResolver, "enabled_notification_listeners", self)
        assertTrue(NotificationRepository.isListenerEnabled(context))
    }

    @Test
    fun `listener is enabled when listed alongside other listeners`() {
        Settings.Secure.putString(
            context.contentResolver,
            "enabled_notification_listeners",
            "some.other.pkg/some.other.Listener:$self:another.pkg/another.Listener",
        )
        assertTrue(NotificationRepository.isListenerEnabled(context))
    }
}
