package life.furrer.l9

import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import life.furrer.nine.core.ui.MenuSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast
import java.time.Duration

/** Covers MainActivity's key-routing state machine — the core of the app's interaction model —
 *  via direct onKeyDown/onKeyUp/onKeyLongPress calls, the same entry points Android itself uses.
 *  Timer-based behavior (speed dial) is driven forward through Robolectric's paused main looper
 *  rather than real sleeps. */
@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

    private val context = RuntimeEnvironment.getApplication()
    private val appA = AppEntry("a.pkg", "a.Activity", "App A")
    private val appB = AppEntry("b.pkg", "b.Activity", "App B")

    @Before
    fun setUp() {
        AppRepository.setSlotCount(context, 3)
        AppRepository.saveSlots(context, listOf(appA, appB, null))
    }

    private fun buildActivity(): MainActivity =
        Robolectric.buildActivity(MainActivity::class.java).setup().get()

    private fun down(activity: MainActivity, keyCode: Int, repeatCount: Int = 0): KeyEvent {
        val event = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, repeatCount)
        activity.onKeyDown(keyCode, event)
        return event
    }

    private fun tap(activity: MainActivity, keyCode: Int) {
        val event = down(activity, keyCode)
        activity.onKeyUp(keyCode, event)
    }

    private fun menuSheetOf(activity: MainActivity): MenuSheet {
        val root = activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as FrameLayout
        return root.getChildAt(1) as MenuSheet
    }

    private fun menuLabels(menuSheet: MenuSheet): List<String> {
        val column = menuSheet.getChildAt(0) as LinearLayout
        val listContainer = column.getChildAt(1) as LinearLayout
        return (0 until listContainer.childCount).map { (listContainer.getChildAt(it) as TextView).text.toString() }
    }

    @Test
    fun `pressing the digit for an assigned slot launches its app`() {
        val activity = buildActivity()
        down(activity, KeyEvent.KEYCODE_2)

        val started = shadowOf(activity).nextStartedActivity
        assertEquals("b.pkg", started.component?.packageName)
    }

    @Test
    fun `pressing the digit for an empty slot opens the picker instead of launching`() {
        val activity = buildActivity()
        down(activity, KeyEvent.KEYCODE_3)

        val started = shadowOf(activity).nextStartedActivityForResult
        assertEquals(AppPickerActivity::class.java.name, started.intent.component?.className)
        assertEquals(false, started.intent.getBooleanExtra(AppPickerActivity.EXTRA_ALLOW_CLEAR, true))
    }

    @Test
    fun `up from the first slot wraps to the last`() {
        val activity = buildActivity()
        down(activity, KeyEvent.KEYCODE_DPAD_UP)
        // Slot 3 (index 2) is empty, so confirming selection should open its picker.
        down(activity, KeyEvent.KEYCODE_DPAD_CENTER)

        val started = shadowOf(activity).nextStartedActivityForResult
        assertEquals(AppPickerActivity::class.java.name, started.intent.component?.className)
    }

    @Test
    fun `down from the last slot wraps to the first`() {
        val activity = buildActivity()
        down(activity, KeyEvent.KEYCODE_DPAD_DOWN)
        down(activity, KeyEvent.KEYCODE_DPAD_DOWN)
        down(activity, KeyEvent.KEYCODE_DPAD_DOWN)
        down(activity, KeyEvent.KEYCODE_DPAD_CENTER)

        val started = shadowOf(activity).nextStartedActivity
        assertEquals("a.pkg", started.component?.packageName)
    }

    @Test
    fun `digit 0 opens browse-all directly, not slot 10`() {
        val activity = buildActivity()
        down(activity, KeyEvent.KEYCODE_0)

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(AppPickerActivity::class.java.name, started.component?.className)
        assertTrue(started.getBooleanExtra(AppPickerActivity.EXTRA_LAUNCH_DIRECTLY, false))
    }

    @Test
    fun `star opens a menu offering Link app, Rename, Reorder and Settings for an occupied slot`() {
        val activity = buildActivity()
        tap(activity, KeyEvent.KEYCODE_STAR)

        assertEquals(
            listOf("1 Link app", "2 Rename", "3 Reorder", "4 Settings"),
            menuLabels(menuSheetOf(activity)),
        )
    }

    @Test
    fun `star's menu skips Rename for an empty slot`() {
        val activity = buildActivity()
        down(activity, KeyEvent.KEYCODE_DPAD_UP) // wraps to slot 3 (index 2), which is empty
        tap(activity, KeyEvent.KEYCODE_STAR)

        assertEquals(listOf("1 Link app", "2 Reorder", "3 Settings"), menuLabels(menuSheetOf(activity)))
    }

    @Test
    fun `choosing Reorder from the star menu enters reorder mode, and down swaps the selected slot down`() {
        val activity = buildActivity()
        tap(activity, KeyEvent.KEYCODE_STAR)
        down(activity, KeyEvent.KEYCODE_3) // "3 Reorder"

        down(activity, KeyEvent.KEYCODE_DPAD_DOWN)

        // Slot 0 (App A) and slot 1 (App B) should have swapped.
        assertEquals(listOf(appB, appA, null), AppRepository.loadSlots(context))
    }

    @Test
    fun `a bare star tap exits reorder mode without acting on other keys`() {
        val activity = buildActivity()
        tap(activity, KeyEvent.KEYCODE_STAR)
        down(activity, KeyEvent.KEYCODE_3) // "3 Reorder"

        tap(activity, KeyEvent.KEYCODE_STAR)
        // Now out of reorder mode: a digit press should launch normally instead of swapping.
        down(activity, KeyEvent.KEYCODE_1)

        val started = shadowOf(activity).nextStartedActivity
        assertEquals("a.pkg", started.component?.packageName)
        // Untouched by the aborted reorder.
        assertEquals(listOf(appA, appB, null), AppRepository.loadSlots(context))
    }


    @Test
    fun `long-pressing star opens Settings`() {
        val activity = buildActivity()
        val event = down(activity, KeyEvent.KEYCODE_STAR)
        activity.onKeyLongPress(KeyEvent.KEYCODE_STAR, event)

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(SettingsActivity::class.java.name, started.component?.className)
    }

    /** Simulates holding [keyCode] down and releasing it [heldMillis] later — separate from the
     *  Looper's own fake clock (which [tap]/[down] never advance either), since MainActivity now
     *  reads the hold length off the up [KeyEvent]'s own down/event times rather than off a fixed
     *  Handler delay. */
    private fun holdAndRelease(activity: MainActivity, keyCode: Int, heldMillis: Long) {
        activity.onKeyDown(keyCode, KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0))
        activity.onKeyUp(keyCode, KeyEvent(0, heldMillis, KeyEvent.ACTION_UP, keyCode, 0))
    }

    @Test
    fun `holding a speed-dial digit past the debounce and letting go does not place the call by itself`() {
        shadowOf(context).grantPermissions(android.Manifest.permission.CALL_PHONE)
        SpeedDialRepository.setContact(context, 2, SpeedDialContact("Alice", "12345"))
        val activity = buildActivity()

        down(activity, KeyEvent.KEYCODE_2)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(1000))
        assertEquals("Press Call to call Alice", ShadowToast.getTextOfLatestToast())

        activity.onKeyUp(KeyEvent.KEYCODE_2, KeyEvent(0, 500, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_2, 0))

        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `pressing Call within the grace window after letting go places the call`() {
        shadowOf(context).grantPermissions(android.Manifest.permission.CALL_PHONE)
        SpeedDialRepository.setContact(context, 2, SpeedDialContact("Alice", "12345"))
        val activity = buildActivity()

        down(activity, KeyEvent.KEYCODE_2)
        activity.onKeyUp(KeyEvent.KEYCODE_2, KeyEvent(0, 500, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_2, 0))
        down(activity, KeyEvent.KEYCODE_CALL)

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(android.content.Intent.ACTION_CALL, started.action)
        assertEquals("tel:12345", started.data?.toString())
    }

    @Test
    fun `pressing Call after the grace window expires does not place the call`() {
        shadowOf(context).grantPermissions(android.Manifest.permission.CALL_PHONE)
        SpeedDialRepository.setContact(context, 2, SpeedDialContact("Alice", "12345"))
        val activity = buildActivity()

        down(activity, KeyEvent.KEYCODE_2)
        activity.onKeyUp(KeyEvent.KEYCODE_2, KeyEvent(0, 500, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_2, 0))
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(1000))
        down(activity, KeyEvent.KEYCODE_CALL)

        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `pressing Call while holding a speed-dial digit places the call immediately`() {
        shadowOf(context).grantPermissions(android.Manifest.permission.CALL_PHONE)
        SpeedDialRepository.setContact(context, 2, SpeedDialContact("Alice", "12345"))
        val activity = buildActivity()

        down(activity, KeyEvent.KEYCODE_2)
        down(activity, KeyEvent.KEYCODE_CALL)

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(android.content.Intent.ACTION_CALL, started.action)
        assertEquals("tel:12345", started.data?.toString())
    }

    @Test
    fun `letting go after Call already placed the call does not call again`() {
        shadowOf(context).grantPermissions(android.Manifest.permission.CALL_PHONE)
        SpeedDialRepository.setContact(context, 2, SpeedDialContact("Alice", "12345"))
        val activity = buildActivity()

        down(activity, KeyEvent.KEYCODE_2)
        down(activity, KeyEvent.KEYCODE_CALL)
        shadowOf(activity).nextStartedActivity // consume the call already placed by Call

        activity.onKeyUp(KeyEvent.KEYCODE_2, KeyEvent(0, 1000, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_2, 0))

        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `pressing Call without call permission warns instead of calling`() {
        SpeedDialRepository.setContact(context, 2, SpeedDialContact("Alice", "12345"))
        val activity = buildActivity()

        holdAndRelease(activity, KeyEvent.KEYCODE_2, heldMillis = 500)
        down(activity, KeyEvent.KEYCODE_CALL)

        assertEquals("Call permission not granted — check Settings", ShadowToast.getTextOfLatestToast())
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `releasing a speed-dial digit quickly falls through to launching that slot`() {
        // Digit 2 has both a speed-dial contact and slot 2's app (App B) assigned — a quick tap
        // must launch App B, not silently do nothing, since it never held past the debounce.
        SpeedDialRepository.setContact(context, 2, SpeedDialContact("Alice", "12345"))
        val activity = buildActivity()

        tap(activity, KeyEvent.KEYCODE_2)

        val started = shadowOf(activity).nextStartedActivity
        assertEquals("b.pkg", started.component?.packageName)
    }

    @Test
    fun `short-pressing the left key launches the configured shortcut`() {
        AppRepository.setLeftApp(context, appA)
        val activity = buildActivity()

        tap(activity, KeyEvent.KEYCODE_DPAD_LEFT)

        val started = shadowOf(activity).nextStartedActivity
        assertEquals("a.pkg", started.component?.packageName)
    }

    @Test
    fun `long-pressing the left key opens the picker to reassign it instead of launching`() {
        AppRepository.setLeftApp(context, appA)
        val activity = buildActivity()

        val event = down(activity, KeyEvent.KEYCODE_DPAD_LEFT)
        activity.onKeyLongPress(KeyEvent.KEYCODE_DPAD_LEFT, event)

        val started = shadowOf(activity).nextStartedActivityForResult
        assertEquals(AppPickerActivity::class.java.name, started.intent.component?.className)
        // App A is already assigned, so the picker should offer a "clear" option.
        assertTrue(started.intent.getBooleanExtra(AppPickerActivity.EXTRA_ALLOW_CLEAR, false))

        // The long-press must suppress the short-press launch on the eventual key-up. Robolectric
        // records a startActivityForResult call in both its plain and for-result started-activity
        // queues, so the picker start above still left one entry in the plain queue too — drain
        // that expected entry first, then confirm onKeyUp didn't add a second (real launch) one.
        shadowOf(activity).nextStartedActivity
        activity.onKeyUp(KeyEvent.KEYCODE_DPAD_LEFT, event)
        assertNull(shadowOf(activity).nextStartedActivity)
    }
}
