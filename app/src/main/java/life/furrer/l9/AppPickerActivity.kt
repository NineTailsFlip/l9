package life.furrer.l9

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import life.furrer.nine.core.settings.openNineSettings
import life.furrer.nine.core.ui.MenuItem
import life.furrer.nine.core.ui.MenuSheet
import life.furrer.nine.core.ui.Palette
import life.furrer.nine.core.ui.SearchField
import life.furrer.nine.core.ui.applyConfiguredTheme
import life.furrer.nine.core.ui.hideStatusBar
import life.furrer.nine.core.util.KeyRouter
import life.furrer.nine.core.util.NineAction

private const val DIVIDER_GREY = 0xFF333333.toInt()
private const val ROW_SIDE_PADDING = 28

/** `*` isn't included here — it's handled by its own dedicated block in dispatchKeyEvent, ahead
 *  of [SearchField.handleKeyEvent], the same shape ContactsActivity's star handling already
 *  uses. */
private val RESERVED_NAV_KEYS = setOf(
    KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_DPAD_CENTER,
    KeyEvent.KEYCODE_ENTER,
)

private fun matchesSearch(label: String, query: String): Boolean {
    if (query.isEmpty()) return true
    val words = label.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
    return words.any { it.startsWith(query.lowercase()) }
}

class AppPickerActivity : Activity() {

    private var apps: List<AppEntry> = emptyList()
    private var visibleApps: List<AppEntry> = emptyList()
    private var selectedIndex: Int = 0
    private var launchDirectly: Boolean = false
    private var allowClear: Boolean = false
    // Not persisted — resets to false every time this screen opens fresh, since showing hidden
    // apps is an occasional maintenance action rather than a view mode worth remembering.
    private var showHidden: Boolean = false
    private lateinit var rowContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var searchField: SearchField
    private lateinit var noMatchesLabel: TextView
    private lateinit var menuSheet: MenuSheet
    /** rows[0] is a pseudo-entry ("Clear this slot") when [allowClear]; the rest line up 1:1 with
     *  [visibleApps]. Browse-all ([launchDirectly]) reaches L9 Settings via the `*` menu instead
     *  of a pseudo-row, so it isn't listed alongside real apps. */
    private val rows = mutableListOf<TextView>()
    /** Parallel to [rows] — a hidden app is dimmed further than an ordinary unselected row
     *  instead of being labelled, so "show hidden apps" reads as a visual demotion rather than a
     *  wall of "· hidden" suffixes. */
    private val rowIsHidden = mutableListOf<Boolean>()

    /** Tracks `*`'s own down-then-up — it has no long-press meaning here, unlike MainActivity's
     *  star key, so a plain seen-down check is enough. Separate from [SearchField]'s own internal
     *  tracking, since `*` is handled before ever reaching it (see dispatchKeyEvent). */
    private val seenKeyDowns = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyConfiguredTheme()

        launchDirectly = intent.getBooleanExtra(EXTRA_LAUNCH_DIRECTLY, false)
        allowClear = intent.getBooleanExtra(EXTRA_ALLOW_CLEAR, false)
        apps = AppRepository.queryInstalledApps(this, includeHidden = showHidden)

        searchField = SearchField(this).apply {
            onQueryChanged = { rebuildRows() }
            // finish()+a fresh startActivity is the only thing confirmed to release this
            // device's T9 IME's grip on `*` once the search field has taken real focus (see
            // SearchField.onDeactivated's own doc comment) — but that would silently cancel the
            // link picker's pending activity-for-result contract (MainActivity would see a
            // RESULT_CANCELED the moment the user backspaced their query to empty, not just when
            // they actually backed out). Browse all is a plain startActivity with no result owed
            // to anyone, so it's the only mode that gets the fresh-window recovery; the link
            // picker keeps the same "clear the search before using `*`" limit ContactsActivity
            // already has elsewhere.
            onDeactivated = { if (launchDirectly) restartFresh() }
            editText.apply {
                textSize = 16f
                setTextColor(Color.WHITE)
                setHintTextColor(Palette.GRAY_DIM)
                hint = "Type to search"
                setPadding(ROW_SIDE_PADDING, 20, ROW_SIDE_PADDING, 10)
            }
        }

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(searchField.editText)
        }

        noMatchesLabel = TextView(this).apply {
            text = "No matches"
            textSize = 16f
            setTextColor(Palette.GRAY_DIM)
            setPadding(ROW_SIDE_PADDING, 10, ROW_SIDE_PADDING, 10)
            visibility = View.GONE
        }
        outer.addView(noMatchesLabel)

        rowContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollView = ScrollView(this).apply {
            addView(rowContainer)
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
        outer.addView(scrollView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        menuSheet = MenuSheet(this)
        val root = FrameLayout(this).apply {
            addView(outer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(menuSheet, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        setContentView(root)
        hideStatusBar()

        rebuildRows()
    }

    override fun onPause() {
        super.onPause()
        seenKeyDowns.clear()
        searchField.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            searchField.onWindowFocusGained()
            hideStatusBar()
        }
    }

    /** Not a visible flicker (overridePendingTransition(0, 0) on both ends) — see
     *  [SearchField.onDeactivated]'s own doc comment for why an in-place recreate() or just
     *  clearing view focus don't work, confirmed on-device, and only a genuinely fresh
     *  Activity/window does. */
    private fun restartFresh() {
        finish()
        overridePendingTransition(0, 0)
        startActivity(Intent(this, AppPickerActivity::class.java).putExtra(EXTRA_LAUNCH_DIRECTLY, true))
        overridePendingTransition(0, 0)
    }

    private fun rebuildRows() {
        visibleApps = apps.filter { matchesSearch(it.displayLabel, searchField.query) }
        rowContainer.removeAllViews()
        rows.clear()
        rowIsHidden.clear()

        if (allowClear) {
            addPseudoRow(intent.getStringExtra(EXTRA_CLEAR_LABEL) ?: "Clear this slot")
        }

        val fontSize = AppRepository.getBrowseFontSize(this).toFloat()
        visibleApps.forEach { entry ->
            val row = TextView(this).apply {
                text = entry.displayLabel
                textSize = fontSize
                setPadding(ROW_SIDE_PADDING, 3, ROW_SIDE_PADDING, 3)
            }
            rows.add(row)
            rowIsHidden.add(entry.hidden)
            rowContainer.addView(row)
        }

        noMatchesLabel.visibility = if (searchField.query.isNotEmpty() && visibleApps.isEmpty()) View.VISIBLE else View.GONE

        selectedIndex = selectedIndex.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
        render()
    }

    private fun addPseudoRow(text: String) {
        val row = TextView(this).apply {
            this.text = text
            textSize = AppRepository.getBrowseFontSize(this@AppPickerActivity).toFloat()
            setPadding(ROW_SIDE_PADDING, 3, ROW_SIDE_PADDING, 3)
        }
        rows.add(row)
        rowIsHidden.add(false)
        rowContainer.addView(row)

        val divider = View(this).apply { setBackgroundColor(DIVIDER_GREY) }
        rowContainer.addView(
            divider,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2).apply {
                setMargins(ROW_SIDE_PADDING, 12, ROW_SIDE_PADDING, 12)
            },
        )
    }

    private fun render() {
        rows.forEachIndexed { index, row ->
            val selected = index == selectedIndex
            row.setTextColor(
                when {
                    selected -> Color.WHITE
                    rowIsHidden.getOrNull(index) == true -> Palette.GRAY_FAINT
                    else -> Palette.GRAY_DIM
                },
            )
            row.paintFlags = if (selected) {
                row.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            } else {
                row.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
            }
        }
        rows.getOrNull(selectedIndex)?.let { row ->
            scrollView.post { scrollView.smoothScrollTo(0, row.top) }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (menuSheet.interceptKeyEvent(event)) return true
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (searchField.handleBack(event) { super.dispatchKeyEvent(it) }) return true
            if (event.action == KeyEvent.ACTION_DOWN) {
                setResult(RESULT_CANCELED)
                finish()
            }
            return true
        }

        // `*` opens the rename/hide/show-hidden menu on whichever row is selected — reserved
        // ahead of the search field/IME. Confirmed on-device (same quirk as ContactsActivity's
        // own identical comment): once the field has taken real IME focus, this device's
        // hardware T9 IME swallows both `*` and `#` for its own mode-cycling before either ever
        // reaches dispatchKeyEvent, and long-press-OK isn't a usable fallback either — confirmed
        // on-device that a long press there is claimed at the system level for the physical
        // call/send button (opens P9 via the CALL_BUTTON intent instead of ever reaching this
        // Activity). [SearchField.onDeactivated] (see restartFresh) is what actually restores
        // `*`, once the search goes back to empty.
        if (event.keyCode == KeyEvent.KEYCODE_STAR) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> seenKeyDowns.add(event.keyCode)
                KeyEvent.ACTION_UP -> if (seenKeyDowns.remove(event.keyCode)) openMenu()
            }
            return true
        }

        if (searchField.handleKeyEvent(event, RESERVED_NAV_KEYS)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.keyCode in RESERVED_NAV_KEYS) {
                when (KeyRouter.map(event.keyCode, event.number)) {
                    NineAction.Up -> moveSelection(-1)
                    NineAction.Down -> moveSelection(1)
                    NineAction.Select -> confirmSelection()
                    else -> {}
                }
            }
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    private fun moveSelection(direction: Int) {
        if (rows.isEmpty()) return
        selectedIndex = (selectedIndex + direction + rows.size) % rows.size
        render()
    }

    /** null both when nothing is selected and when the selected row is the pseudo-row ("Clear
     *  this slot") rather than a real app — callers that only care about acting on an app (like
     *  the `*` menu) can treat both the same way. */
    private fun selectedAppOrNull(): AppEntry? {
        if (allowClear && selectedIndex == 0) return null
        val appIndex = if (allowClear) selectedIndex - 1 else selectedIndex
        return visibleApps.getOrNull(appIndex)
    }

    private fun confirmSelection() {
        if (allowClear && selectedIndex == 0) {
            setResult(RESULT_OK, Intent().putExtra(EXTRA_CLEAR, true))
            finish()
            return
        }
        val entry = selectedAppOrNull() ?: return
        if (launchDirectly) {
            if (AppRepository.launchApp(this, entry)) {
                finish()
            } else {
                Toast.makeText(this, "Couldn't open that app", Toast.LENGTH_SHORT).show()
            }
            return
        }
        val data = Intent().apply {
            putExtra(EXTRA_PACKAGE_NAME, entry.packageName)
            putExtra(EXTRA_ACTIVITY_NAME, entry.activityName)
            putExtra(EXTRA_LABEL, entry.label)
        }
        setResult(RESULT_OK, data)
        finish()
    }

    /** Rename/hide act on the selected app (see [selectedAppOrNull]); "show hidden" is always
     *  offered regardless of selection. Renaming and hiding both go through AppRepository's
     *  package-keyed override, so they apply everywhere the app shows up (slots, side keys, this
     *  same list next time), not just this row. */
    private fun openMenu() {
        val items = mutableListOf<MenuItem>()
        val selected = selectedAppOrNull()
        if (selected != null) {
            items += MenuItem("Rename") { onRenameRequested(selected) }
            items += if (selected.hidden) {
                MenuItem("Unhide") { setHidden(selected, false) }
            } else {
                MenuItem("Hide") { setHidden(selected, true) }
            }
        }
        items += MenuItem(if (showHidden) "Hide hidden apps" else "Show hidden apps") { toggleShowHidden() }
        if (launchDirectly) {
            items += MenuItem("L9 Settings") { openL9Settings() }
        }
        menuSheet.show(items)
    }

    private fun openL9Settings() {
        openNineSettings()
        finish()
    }

    private fun onRenameRequested(entry: AppEntry) {
        showRenameDialog(entry.displayLabel) { newLabel ->
            AppRepository.setCustomLabel(this, entry.packageName, entry.activityName, newLabel)
            refreshApps()
        }
    }

    private fun setHidden(entry: AppEntry, hidden: Boolean) {
        AppRepository.setHidden(this, entry.packageName, entry.activityName, hidden)
        refreshApps()
    }

    private fun toggleShowHidden() {
        showHidden = !showHidden
        refreshApps()
    }

    private fun refreshApps() {
        apps = AppRepository.queryInstalledApps(this, includeHidden = showHidden)
        rebuildRows()
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_ACTIVITY_NAME = "activity_name"
        const val EXTRA_LABEL = "label"
        const val EXTRA_LAUNCH_DIRECTLY = "launch_directly"
        const val EXTRA_ALLOW_CLEAR = "allow_clear"
        /** What the clear row says; the default fits a numbered slot, the Left/Right shortcuts pass their own. */
        const val EXTRA_CLEAR_LABEL = "clear_label"
        const val EXTRA_CLEAR = "clear"
    }
}
