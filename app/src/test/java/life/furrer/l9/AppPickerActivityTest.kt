package life.furrer.l9

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import life.furrer.nine.core.ui.MenuSheet
import life.furrer.nine.core.ui.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private fun rootOf(activity: AppPickerActivity): FrameLayout =
        activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as FrameLayout

    private fun searchInputOf(activity: AppPickerActivity): EditText {
        val outer = rootOf(activity).getChildAt(0) as LinearLayout
        return outer.getChildAt(0) as EditText
    }

    private fun menuSheetOf(activity: AppPickerActivity): MenuSheet =
        rootOf(activity).getChildAt(1) as MenuSheet

    private fun menuLabels(menuSheet: MenuSheet): List<String> {
        val column = menuSheet.getChildAt(0) as LinearLayout
        val listContainer = column.getChildAt(1) as LinearLayout
        return (0 until listContainer.childCount).map { (listContainer.getChildAt(it) as TextView).text.toString() }
    }

    private fun tapStar(activity: AppPickerActivity) {
        activity.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_STAR, 0))
        activity.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_STAR, 0))
    }

    /** A short OK tap — down immediately followed by up, well under the long-press timeout —
     *  confirms the selection, same as a real quick press. */
    private fun tapOk(activity: AppPickerActivity) {
        activity.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))
        activity.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0))
    }

    /** Chooses menu item [n] (1-based, matching the on-screen numbering) the same way a real
     *  digit key press does — down only, since MenuSheet acts on ACTION_DOWN. */
    private fun chooseMenuItem(activity: AppPickerActivity, n: Int) {
        val keyCode = KeyEvent.KEYCODE_0 + n
        activity.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0))
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

    private fun visibleRows(activity: AppPickerActivity): List<TextView> {
        val outer = rootOf(activity).getChildAt(0) as LinearLayout
        val scrollView = outer.getChildAt(2) as ScrollView
        val rowContainer = scrollView.getChildAt(0) as LinearLayout
        return (0 until rowContainer.childCount)
            .map { rowContainer.getChildAt(it) }
            .filterIsInstance<TextView>()
    }

    private fun visibleRowLabels(activity: AppPickerActivity): List<String> =
        visibleRows(activity).map { it.text.toString() }

    private fun rowTextColors(activity: AppPickerActivity): List<Int> =
        visibleRows(activity).map { it.currentTextColor }

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
        tapOk(activity)

        val result = shadowOf(activity).resultIntent
        assertEquals("com.example.gamma", result?.getStringExtra(AppPickerActivity.EXTRA_PACKAGE_NAME))
        assertEquals(android.app.Activity.RESULT_OK, shadowOf(activity).resultCode)
    }

    @Test
    fun `launch-directly mode has no settings pseudo-row and launches the first app immediately on confirm`() {
        val activity = buildActivity(launchDirectly = true)
        tapOk(activity)

        val started = shadowOf(activity).nextStartedActivity
        assertEquals("com.example.alpha", started.component?.packageName)
    }

    @Test
    fun `launch-directly mode offers L9 Settings from the star menu instead`() {
        val activity = buildActivity(launchDirectly = true)
        tapStar(activity)

        assertEquals(
            listOf("1 Rename", "2 Hide", "3 Show hidden apps", "4 L9 Settings"),
            menuLabels(menuSheetOf(activity)),
        )
        chooseMenuItem(activity, 4)

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(SettingsActivity::class.java.name, started.component?.className)
    }

    @Test
    fun `allow-clear mode returns a clear result when its pseudo-row is confirmed`() {
        val activity = buildActivity(allowClear = true)
        tapOk(activity)

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

    @Test
    fun `star opens a menu offering Rename, Hide and the show-hidden toggle`() {
        val activity = buildActivity()
        tapStar(activity)

        assertEquals(listOf("1 Rename", "2 Hide", "3 Show hidden apps"), menuLabels(menuSheetOf(activity)))
    }

    @Test
    fun `hiding an app via the menu drops it from the list`() {
        val activity = buildActivity()
        tapStar(activity)
        chooseMenuItem(activity, 2) // Hide (selected row is Alpha, the first one)

        assertEquals(listOf("Beta Two", "Gamma"), visibleRowLabels(activity))
    }

    @Test
    fun `show hidden apps reveals a hidden app with its label unchanged but dimmed further, and unhide restores it`() {
        val activity = buildActivity()
        tapStar(activity)
        chooseMenuItem(activity, 2) // Hide Alpha
        tapStar(activity)
        chooseMenuItem(activity, 3) // Show hidden apps

        // Labels never gain a "hidden" suffix — the row is just dimmed instead. Selection landed
        // back on Alpha (index 0) after the toggle, so move off it to check its unselected color.
        assertEquals(listOf("Alpha", "Beta Two", "Gamma"), visibleRowLabels(activity))
        activity.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, 0))
        assertEquals(Palette.GRAY_FAINT, rowTextColors(activity)[0])
        assertEquals(Color.WHITE, rowTextColors(activity)[1]) // Beta Two, now selected

        activity.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP, 0)) // back to Alpha
        tapStar(activity)
        assertEquals(listOf("1 Rename", "2 Unhide", "3 Hide hidden apps"), menuLabels(menuSheetOf(activity)))
        chooseMenuItem(activity, 2) // Unhide

        assertEquals(listOf("Alpha", "Beta Two", "Gamma"), visibleRowLabels(activity))
        assertEquals(Color.WHITE, rowTextColors(activity)[0])
    }

    @Test
    fun `clearing the search back to empty in launch-directly mode restarts fresh, releasing the IME's grip on star`() {
        // Confirmed on-device: this hardware's T9 IME swallows `*` once the search field has
        // taken real focus, and neither clearing view focus nor Activity.recreate() releases it —
        // only a genuinely fresh Activity/window does (SearchField.onDeactivated's own doc
        // comment). Browse all (launchDirectly) is a plain startActivity with no result owed to
        // anyone, so it's safe to restart.
        val activity = buildActivity(launchDirectly = true)
        typeSearch(activity, "gam")

        typeSearch(activity, "")
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertTrue(activity.isFinishing)
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(AppPickerActivity::class.java.name, started?.component?.className)
        assertTrue(started?.getBooleanExtra(AppPickerActivity.EXTRA_LAUNCH_DIRECTLY, false) ?: false)
    }

    @Test
    fun `clearing the search back to empty in the link picker does not restart, since that would cancel the pending result`() {
        val activity = buildActivity(allowClear = true)
        typeSearch(activity, "gam")

        typeSearch(activity, "")
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertFalse(activity.isFinishing)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `hiding an app here is reflected in AppRepository's global override`() {
        val activity = buildActivity()
        tapStar(activity)
        chooseMenuItem(activity, 2) // Hide Alpha

        assertEquals(true, AppRepository.loadOverrides(context)["com.example.alpha/com.example.alpha.Main"]?.hidden)
    }
}
