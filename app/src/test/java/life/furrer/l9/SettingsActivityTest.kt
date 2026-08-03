package life.furrer.l9

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SettingsActivityTest {

    private val context = RuntimeEnvironment.getApplication()

    private fun buildActivity(): SettingsActivity =
        Robolectric.buildActivity(SettingsActivity::class.java).setup().get()

    private fun rowContainer(activity: SettingsActivity): LinearLayout {
        val scrollView = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ScrollView
        return scrollView.getChildAt(0) as LinearLayout
    }

    private fun isSectionHeader(text: CharSequence): Boolean =
        text.isNotEmpty() && text.toString() == text.toString().uppercase()

    private fun sectionHeaders(activity: SettingsActivity): List<String> =
        (0 until rowContainer(activity).childCount)
            .map { rowContainer(activity).getChildAt(it) as TextView }
            .filter { isSectionHeader(it.text) }
            .map { it.text.toString() }

    private fun rows(activity: SettingsActivity): List<TextView> =
        (0 until rowContainer(activity).childCount)
            .map { rowContainer(activity).getChildAt(it) as TextView }
            .filterNot { isSectionHeader(it.text) }

    private fun moveTo(activity: SettingsActivity, index: Int) {
        repeat(index) { activity.onKeyDown(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, 0)) }
    }

    private fun press(activity: SettingsActivity, keyCode: Int) {
        activity.onKeyDown(keyCode, KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0))
    }

    @Test
    fun `sections render in the shared order, with no Hint bar row since L9 has none`() {
        val activity = buildActivity()
        assertEquals(
            listOf("APPS", "CALENDAR", "NOTIFICATIONS", "SPEED DIAL", "THEME", "BACKUP", "ABOUT"),
            sectionHeaders(activity),
        )
        assertFalse(rows(activity).any { it.text.contains("Hint bar") })
    }

    @Test
    fun `Home screen items steps within 1 and 9`() {
        val activity = buildActivity()
        assertTrue(rows(activity)[0].text.contains("Home screen items: 4"))

        press(activity, KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals(5, AppRepository.getSlotCount(context))
        repeat(10) { press(activity, KeyEvent.KEYCODE_DPAD_RIGHT) }
        assertEquals(9, AppRepository.getSlotCount(context))
        repeat(20) { press(activity, KeyEvent.KEYCODE_DPAD_LEFT) }
        assertEquals(1, AppRepository.getSlotCount(context))
    }

    @Test
    fun `Show indices toggles on Left, Right or OK`() {
        val activity = buildActivity()
        moveTo(activity, 1)
        assertTrue(AppRepository.getShowIndices(context))

        press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
        assertFalse(AppRepository.getShowIndices(context))
        press(activity, KeyEvent.KEYCODE_DPAD_LEFT)
        assertTrue(AppRepository.getShowIndices(context))
    }

    @Test
    fun `Alignment cycles through left, center, right and wraps`() {
        val activity = buildActivity()
        moveTo(activity, 2)
        assertEquals(ListAlignment.LEFT, AppRepository.getAlignment(context))

        press(activity, KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals(ListAlignment.CENTER, AppRepository.getAlignment(context))
        press(activity, KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals(ListAlignment.RIGHT, AppRepository.getAlignment(context))
        press(activity, KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals(ListAlignment.LEFT, AppRepository.getAlignment(context)) // wraps
    }

    @Test
    fun `Home screen font size and Browse all apps font size step independently within 14 and 48`() {
        val activity = buildActivity()
        moveTo(activity, 3)
        assertTrue(rows(activity)[3].text.contains("Home screen font size: 30"))

        press(activity, KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals(32, AppRepository.getHomeFontSize(context))
        assertEquals(AppRepository.DEFAULT_BROWSE_FONT_SIZE, AppRepository.getBrowseFontSize(context))

        press(activity, KeyEvent.KEYCODE_DPAD_DOWN)
        assertTrue(rows(activity)[4].text.contains("Browse all apps font size: 28"))
        press(activity, KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals(26, AppRepository.getBrowseFontSize(context))
        assertEquals(32, AppRepository.getHomeFontSize(context))
    }

    @Test
    fun `Left key row opens the app picker, allowing clear only when a shortcut is already set`() {
        val activity = buildActivity()
        moveTo(activity, 5)
        press(activity, KeyEvent.KEYCODE_DPAD_CENTER)

        val started = shadowOf(activity).nextStartedActivityForResult
        assertEquals(AppPickerActivity::class.java.name, started.intent.component?.className)
        assertFalse(started.intent.getBooleanExtra(AppPickerActivity.EXTRA_ALLOW_CLEAR, true))
    }

    @Test
    fun `Left-Right do nothing on the Left key action row`() {
        val activity = buildActivity()
        moveTo(activity, 5)
        press(activity, KeyEvent.KEYCODE_DPAD_LEFT)
        press(activity, KeyEvent.KEYCODE_DPAD_RIGHT)

        org.junit.Assert.assertNull(shadowOf(activity).nextStartedActivityForResult)
    }

    @Test
    fun `Back finishes the activity`() {
        val activity = buildActivity()
        press(activity, KeyEvent.KEYCODE_BACK)
        assertTrue(activity.isFinishing)
    }
}
