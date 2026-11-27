package life.furrer.l9

import android.app.Activity
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.EditText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class RenameActivityTest {
    private val context = RuntimeEnvironment.getApplication()
    private val entry = AppEntry("com.example.mail", "com.example.mail.Main", "Mail", customLabel = "Post")

    private fun open(): RenameActivity =
        Robolectric.buildActivity(RenameActivity::class.java, RenameActivity.intentFor(context, entry)).setup().get()

    private fun RenameActivity.field(): EditText {
        fun find(v: android.view.View): EditText? = when (v) {
            is EditText -> v
            is ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { find(v.getChildAt(it)) }
            else -> null
        }
        return find(findViewById(android.R.id.content))!!
    }

    /** A key as the pre-IME hook sees it: DOWN then UP. */
    private fun RenameActivity.press(keyCode: Int) {
        val field = field()
        field.onKeyPreIme(keyCode, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        field.onKeyPreIme(keyCode, KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun label(): String? = AppRepository.loadOverrides(context)["${entry.packageName}/${entry.activityName}"]?.customLabel

    @Test
    fun opensOnTheCurrentName() {
        assertEquals("Post", open().field().text.toString())
    }

    @Test
    fun okSavesTheTypedName() {
        val activity = open()
        activity.field().setText("  Letters ")
        activity.press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals("Letters", label())
        assertTrue(activity.isFinishing)
        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
    }

    @Test
    fun anEmptyNameGoesBackToTheAppsOwn() {
        AppRepository.setCustomLabel(context, entry.packageName, entry.activityName, "Post")
        val activity = open()
        activity.field().setText("")
        activity.press(KeyEvent.KEYCODE_ENTER)
        assertNull(label())
    }

    @Test
    fun backDeletesACharacterThenLeavesWithoutSaving() {
        val activity = open()
        activity.field().setText("Po")
        activity.press(KeyEvent.KEYCODE_BACK)
        assertEquals("P", activity.field().text.toString())
        assertFalse(activity.isFinishing)
        activity.press(KeyEvent.KEYCODE_BACK)
        activity.press(KeyEvent.KEYCODE_BACK)
        assertTrue(activity.isFinishing)
        assertNull(label())
    }
}
