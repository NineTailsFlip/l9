package life.furrer.l9

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** Covers AppPickerActivity's search-as-you-type filtering and its three selection modes
 *  (plain picker, launch-directly/browse-all, allow-clear) — the picker is the only way to
 *  assign an app to a slot or shortcut, so its filtering and confirm logic is core behavior. */
@RunWith(RobolectricTestRunner::class)
class AppPickerActivityTest {

    private val context = RuntimeEnvironment.getApplication()

    private fun launcherApp(packageName: String, activityName: String, label: String): ResolveInfo =
        ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                this.packageName = packageName
                name = activityName
                applicationInfo = android.content.pm.ApplicationInfo().apply {
                    this.packageName = packageName
                }
            }
            nonLocalizedLabel = label
        }

    @Before
    fun setUp() {
        // All three must be registered in a single call: setResolveInfosForIntent replaces the
        // entire entry for an equal Intent, and every launcher query intent constructed this way
        // (ACTION_MAIN + CATEGORY_LAUNCHER, no data/extras) is equal to every other one, so
        // separate calls would each discard the previous app.
        shadowOf(context.packageManager).setResolveInfosForIntent(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            listOf(
                launcherApp("com.example.alpha", "com.example.alpha.Main", "Alpha"),
                launcherApp("com.example.beta", "com.example.beta.Main", "Beta Two"),
                launcherApp("com.example.gamma", "com.example.gamma.Main", "Gamma"),
            ),
        )
    }

    private fun buildActivity(launchDirectly: Boolean = false, allowClear: Boolean = false): AppPickerActivity {
        val intent = Intent(context, AppPickerActivity::class.java)
            .putExtra(AppPickerActivity.EXTRA_LAUNCH_DIRECTLY, launchDirectly)
            .putExtra(AppPickerActivity.EXTRA_ALLOW_CLEAR, allowClear)
        return Robolectric.buildActivity(AppPickerActivity::class.java, intent).setup().get()
    }

    private fun searchInputOf(activity: AppPickerActivity): EditText {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val outer = content.getChildAt(0) as LinearLayout
        return outer.getChildAt(0) as EditText
    }

    /** Sets the search text. The activity discards the very first text change if it lands right
     *  after focus (a workaround for the hardware IME leaking a stray character — see
     *  AppPickerActivity's dispatchKeyEvent/focusGainedAtMillis comments), so an initial no-op
     *  edit is used here to get past that guard before setting the real query. */
    private fun typeSearch(activity: AppPickerActivity, query: String) {
        val input = searchInputOf(activity)
        input.setText(" ")
        input.setText(query)
    }

    private fun visibleRowLabels(activity: AppPickerActivity): List<String> {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val outer = content.getChildAt(0) as LinearLayout
        val scrollView = outer.getChildAt(2) as ScrollView
        val rowContainer = scrollView.getChildAt(0) as LinearLayout
        return (0 until rowContainer.childCount)
            .map { rowContainer.getChildAt(it) }
            .filterIsInstance<TextView>()
            .map { it.text.toString() }
    }

    @Test
    fun `with no search text every installed app is listed alphabetically`() {
        val activity = buildActivity()
        assertEquals(listOf("Alpha", "Beta Two", "Gamma"), visibleRowLabels(activity))
    }

    @Test
    fun `search matches by word prefix, not substring`() {
        val activity = buildActivity()
        typeSearch(activity, "tw")

        // "Beta Two" matches because "Two" starts with "tw"; "Alpha"/"Gamma" don't contain any
        // word starting with "tw" even though matching is case-insensitive and per-word.
        assertEquals(listOf("Beta Two"), visibleRowLabels(activity))
    }

    @Test
    fun `search is case-insensitive`() {
        val activity = buildActivity()
        typeSearch(activity, "GAM")
        assertEquals(listOf("Gamma"), visibleRowLabels(activity))
    }

    @Test
    fun `confirming the only search match returns that app`() {
        val activity = buildActivity()
        typeSearch(activity, "gam")
        activity.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))

        val result = shadowOf(activity).resultIntent
        assertEquals("com.example.gamma", result?.getStringExtra(AppPickerActivity.EXTRA_PACKAGE_NAME))
        assertEquals(android.app.Activity.RESULT_OK, shadowOf(activity).resultCode)
    }

    @Test
    fun `launch-directly mode prepends a settings row and launches the app immediately on confirm`() {
        val activity = buildActivity(launchDirectly = true)
        // Row 0 is the "L9 Settings" pseudo-row, so move down once to reach the first real app.
        activity.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, 0))
        activity.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))

        val started = shadowOf(activity).nextStartedActivity
        assertEquals("com.example.alpha", started.component?.packageName)
    }

    @Test
    fun `launch-directly mode opens Settings when the pseudo-row itself is confirmed`() {
        val activity = buildActivity(launchDirectly = true)
        activity.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(SettingsActivity::class.java.name, started.component?.className)
    }

    @Test
    fun `allow-clear mode returns a clear result when its pseudo-row is confirmed`() {
        val activity = buildActivity(allowClear = true)
        activity.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))

        val result = shadowOf(activity).resultIntent
        assertEquals(true, result?.getBooleanExtra(AppPickerActivity.EXTRA_CLEAR, false))
        assertEquals(android.app.Activity.RESULT_OK, shadowOf(activity).resultCode)
    }

    @Test
    fun `back cancels the picker`() {
        val activity = buildActivity()
        activity.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0))

        assertEquals(android.app.Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
        assertNull(shadowOf(activity).nextStartedActivity)
    }
}
