package life.furrer.l9

import android.app.Activity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ThemingTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `applyConfiguredTheme applies the monospace theme by default and reports it`() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        assertTrue(activity.applyConfiguredTheme())
    }

    @Test
    fun `applyConfiguredTheme reports false once monospace is turned off`() {
        AppRepository.setUseMonospaceFont(context, false)
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        assertFalse(activity.applyConfiguredTheme())
    }

    @Test
    fun `recreateIfFontChanged is a no-op when the setting matches what was applied`() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        assertFalse(activity.recreateIfFontChanged(appliedMonospaceFont = true))
    }

    @Test
    fun `recreateIfFontChanged reports true when the setting no longer matches`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        AppRepository.setUseMonospaceFont(context, false)
        assertTrue(activity.recreateIfFontChanged(appliedMonospaceFont = true))
    }

    @Test
    fun `dialogThemeResId follows the monospace font setting`() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        AppRepository.setUseMonospaceFont(context, true)
        assertEquals(R.style.Theme_L9_Dialog_Monospace, activity.dialogThemeResId())

        AppRepository.setUseMonospaceFont(context, false)
        assertEquals(R.style.Theme_L9_Dialog, activity.dialogThemeResId())
    }
}
