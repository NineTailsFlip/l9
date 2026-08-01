package life.furrer.l9

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
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

    @Test
    fun `saved slots round trip, preserving empty slots and order`() {
        val entryA = AppEntry("a.pkg", "a.Activity", "App A")
        val entryB = AppEntry("b.pkg", "b.Activity", "App B", customLabel = "B custom")
        AppRepository.setSlotCount(context, 4)
        AppRepository.saveSlots(context, listOf(entryA, null, entryB, null))

        val reloaded = AppRepository.loadSlots(context)
        assertEquals(listOf(entryA, null, entryB, null), reloaded)
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
