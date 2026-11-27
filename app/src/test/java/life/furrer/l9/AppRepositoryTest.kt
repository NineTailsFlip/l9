package life.furrer.l9

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AppRepositoryTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `slot count defaults to 4 and persists once set`() {
        assertEquals(4, AppRepository.getSlotCount(context))
        AppRepository.setSlotCount(context, 7)
        assertEquals(7, AppRepository.getSlotCount(context))
    }

    @Test
    fun `show indices defaults to true`() {
        assertTrue(AppRepository.getShowIndices(context))
        AppRepository.setShowIndices(context, false)
        assertFalse(AppRepository.getShowIndices(context))
    }

    @Test
    fun `alignment defaults to LEFT and round trips`() {
        assertEquals(ListAlignment.LEFT, AppRepository.getAlignment(context))
        AppRepository.setAlignment(context, ListAlignment.CENTER)
        assertEquals(ListAlignment.CENTER, AppRepository.getAlignment(context))
    }

    @Test
    fun `a corrupted alignment value falls back to LEFT instead of crashing`() {
        context.getSharedPreferences("l9", 0).edit().putString("list_alignment", "NOT_A_REAL_VALUE").apply()
        assertEquals(ListAlignment.LEFT, AppRepository.getAlignment(context))
    }

    @Test
    fun `loadSlots on first run returns all-null slots sized to the slot count`() {
        AppRepository.setSlotCount(context, 3)
        val slots = AppRepository.loadSlots(context)
        assertEquals(listOf(null, null, null), slots)
    }

    private fun install(packageName: String, activityName: String, label: String) {
        val activity = android.content.pm.ActivityInfo().apply {
            this.packageName = packageName
            name = activityName
            nonLocalizedLabel = label
            applicationInfo = android.content.pm.ApplicationInfo().apply { this.packageName = packageName }
        }
        org.robolectric.Shadows.shadowOf(context.packageManager).addOrUpdateActivity(activity)
    }

    @Test
    fun `a new phone's home screen is the installed Nine apps, in order`() {
        install("io.github.ninetailsflip.tg9", "dev.tg9.chats.ChatListActivity", "Telegram")
        install("io.github.ninetailsflip.p9", "life.furrer.p9.dial.DialerActivity", "Phone")
        install("io.github.ninetailsflip.c9", "life.furrer.c9.CalendarLauncher", "Calendar")
        val slots = AppRepository.loadSlots(context)
        assertEquals(listOf("Phone", "Telegram", "Calendar", null), slots.map { it?.label })
        // Seeded once: cleared slots stay cleared.
        AppRepository.saveSlots(context, listOf(null, null, null, null))
        assertEquals(listOf(null, null, null, null), AppRepository.loadSlots(context))
    }

    @Test
    fun `seeding grows the slot count to fit every default app`() {
        AppRepository.DEFAULT_HOME.forEachIndexed { i, (pkg, activity) -> install(pkg, activity, "App $i") }
        val slots = AppRepository.loadSlots(context)
        assertEquals(AppRepository.DEFAULT_HOME.size, AppRepository.getSlotCount(context))
        assertTrue(slots.all { it != null })
    }

    @Test
    fun `a slot shows the app's current name, not the one it had when chosen`() {
        install("io.github.ninetailsflip.c9", "life.furrer.c9.CalendarLauncher", "Calendar")
        AppRepository.saveSlots(context, listOf(AppEntry("io.github.ninetailsflip.c9", "life.furrer.c9.CalendarLauncher", "C9 Calendar")))
        assertEquals("Calendar", AppRepository.loadSlots(context)[0]?.label)
    }

    @Test
    fun `a slot on a stock app the image took out moves to its Nine app, and stays moved`() {
        install("io.github.ninetailsflip.p9", "life.furrer.p9.dial.DialerActivity", "Phone")
        val dialer = AppEntry("com.android.dialer", "com.android.dialer.main.impl.MainActivity", "Phone")
        val other = AppEntry("a.pkg", "a.Activity", "App A")
        AppRepository.saveSlots(context, listOf(other, dialer, null, null))

        val p9 = AppEntry("io.github.ninetailsflip.p9", "life.furrer.p9.dial.DialerActivity", "Phone")
        assertEquals(listOf(other, p9, null, null), AppRepository.loadSlots(context))
        // Saved: a smaller slot count keeps the move, and the slot past it is untouched.
        AppRepository.setSlotCount(context, 2)
        assertEquals(listOf(other, p9), AppRepository.loadSlots(context))
    }

    @Test
    fun `a stock app that's still there, or with no Nine app installed, stays`() {
        install("com.android.contacts", "com.android.contacts.activities.PeopleActivity", "Contacts")
        val contacts = AppEntry("com.android.contacts", "com.android.contacts.activities.PeopleActivity", "Contacts")
        val etar = AppEntry("org.lineageos.etar", "com.android.calendar.AllInOneActivity", "Calendar")
        AppRepository.saveSlots(context, listOf(contacts, etar, null, null))
        assertEquals(listOf(contacts, etar, null, null), AppRepository.loadSlots(context))
    }

    @Test
    fun `a side key on a stock app the image took out moves too`() {
        install("io.github.ninetailsflip.m9", "life.furrer.m9.conversations.ConversationListActivity", "Messages")
        AppRepository.setRightApp(context, AppEntry("com.android.messaging", "com.android.messaging.ui.conversationlist.ConversationListActivity", "Messaging"))
        val m9 = AppEntry("io.github.ninetailsflip.m9", "life.furrer.m9.conversations.ConversationListActivity", "Messages")
        assertEquals(m9, AppRepository.getRightApp(context))
        assertEquals("io.github.ninetailsflip.m9", JSONObject(context.getSharedPreferences("l9", 0).getString("right_app", null)!!).getString("packageName"))
    }

    @Test
    fun `saved slots round trip, preserving empty slots and order`() {
        val entryA = AppEntry("a.pkg", "a.Activity", "App A")
        val entryB = AppEntry("b.pkg", "b.Activity", "App B")
        AppRepository.setSlotCount(context, 4)
        AppRepository.saveSlots(context, listOf(entryA, null, entryB, null))

        val reloaded = AppRepository.loadSlots(context)
        assertEquals(listOf(entryA, null, entryB, null), reloaded)
    }

    @Test
    fun `renaming an app applies to every slot referencing it, not just the one it was renamed from`() {
        val entryB = AppEntry("b.pkg", "b.Activity", "App B")
        AppRepository.setSlotCount(context, 2)
        AppRepository.saveSlots(context, listOf(entryB, entryB))

        AppRepository.setCustomLabel(context, "b.pkg", "b.Activity", "B custom")

        val reloaded = AppRepository.loadSlots(context)
        assertEquals("B custom", reloaded[0]?.displayLabel)
        assertEquals("B custom", reloaded[1]?.displayLabel)
    }

    @Test
    fun `clearing a custom label reverts display back to the app's own label`() {
        val entryA = AppEntry("a.pkg", "a.Activity", "App A")
        AppRepository.setSlotCount(context, 1)
        AppRepository.saveSlots(context, listOf(entryA))
        AppRepository.setCustomLabel(context, "a.pkg", "a.Activity", "Renamed")

        AppRepository.setCustomLabel(context, "a.pkg", "a.Activity", null)

        assertEquals("App A", AppRepository.loadSlots(context)[0]?.displayLabel)
    }

    @Test
    fun `hiding an app is reflected on any slot entry for it too`() {
        val entryA = AppEntry("a.pkg", "a.Activity", "App A")
        AppRepository.setSlotCount(context, 1)
        AppRepository.saveSlots(context, listOf(entryA))

        AppRepository.setHidden(context, "a.pkg", "a.Activity", true)

        assertEquals(true, AppRepository.loadSlots(context)[0]?.hidden)
    }

    @Test
    fun `overrides for unrelated apps don't leak onto other slots`() {
        val entryA = AppEntry("a.pkg", "a.Activity", "App A")
        val entryB = AppEntry("b.pkg", "b.Activity", "App B")
        AppRepository.setSlotCount(context, 2)
        AppRepository.saveSlots(context, listOf(entryA, entryB))

        AppRepository.setCustomLabel(context, "a.pkg", "a.Activity", "A custom")

        val reloaded = AppRepository.loadSlots(context)
        assertEquals("A custom", reloaded[0]?.displayLabel)
        assertEquals("App B", reloaded[1]?.displayLabel)
    }

    @Test
    fun `loading slots after shrinking the slot count truncates to the new size`() {
        val entryA = AppEntry("a.pkg", "a.Activity", "App A")
        val entryB = AppEntry("b.pkg", "b.Activity", "App B")
        AppRepository.setSlotCount(context, 2)
        AppRepository.saveSlots(context, listOf(entryA, entryB))
        AppRepository.setSlotCount(context, 1)

        assertEquals(listOf(entryA), AppRepository.loadSlots(context))
    }

    @Test
    fun `loading slots after growing the slot count pads with nulls`() {
        val entryA = AppEntry("a.pkg", "a.Activity", "App A")
        AppRepository.setSlotCount(context, 1)
        AppRepository.saveSlots(context, listOf(entryA))
        AppRepository.setSlotCount(context, 3)

        assertEquals(listOf(entryA, null, null), AppRepository.loadSlots(context))
    }

    @Test
    fun `left and right shortcut apps default to null and round trip independently`() {
        assertNull(AppRepository.getLeftApp(context))
        assertNull(AppRepository.getRightApp(context))

        val left = AppEntry("left.pkg", "left.Activity", "Left App")
        val right = AppEntry("right.pkg", "right.Activity", "Right App")
        AppRepository.setLeftApp(context, left)
        AppRepository.setRightApp(context, right)

        assertEquals(left, AppRepository.getLeftApp(context))
        assertEquals(right, AppRepository.getRightApp(context))

        AppRepository.setLeftApp(context, null)
        assertNull(AppRepository.getLeftApp(context))
        // Clearing left must not disturb right.
        assertEquals(right, AppRepository.getRightApp(context))
    }

    @Test
    fun `launchApp returns true and starts the intent when it resolves`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val entry = AppEntry("some.pkg", "some.Activity", "Some App")

        assertTrue(AppRepository.launchApp(activity, entry))

        val started = org.robolectric.Shadows.shadowOf(activity).nextStartedActivity
        assertEquals("some.pkg", started.component?.packageName)
        assertEquals("some.Activity", started.component?.className)
        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
            started.flags,
        )
    }

    @Test
    fun `launchApp returns false instead of crashing when the app is gone`() {
        val activity = ThrowingActivity(ActivityNotFoundException())
        val entry = AppEntry("gone.pkg", "gone.Activity", "Gone App")

        assertFalse(AppRepository.launchApp(activity, entry))
    }

    @Test
    fun `launchApp returns false instead of crashing when launch is denied`() {
        val activity = ThrowingActivity(SecurityException())
        val entry = AppEntry("denied.pkg", "denied.Activity", "Denied App")

        assertFalse(AppRepository.launchApp(activity, entry))
    }

    /** A minimal Activity stand-in whose startActivity always throws, to exercise
     *  AppRepository.launchApp's catch branches deterministically (Robolectric's own shadow
     *  Activity doesn't reject unresolvable components by default). */
    private class ThrowingActivity(private val toThrow: RuntimeException) : Activity() {
        override fun startActivity(intent: Intent?) {
            throw toThrow
        }
    }
}
