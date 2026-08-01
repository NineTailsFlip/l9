package life.furrer.l9

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

private const val DIM_GREY = 0xFF8A8A8A.toInt()
private const val DIVIDER_GREY = 0xFF333333.toInt()
private const val LEAKED_CHAR_WINDOW_MS = 900L
private const val ROW_SIDE_PADDING = 28

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
    private var searchText: String = ""
    private var selectedIndex: Int = 0
    private var launchDirectly: Boolean = false
    private var allowClear: Boolean = false
    private lateinit var rowContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var searchInput: EditText
    private lateinit var noMatchesLabel: TextView
    /** rows[0] is a pseudo-entry ("L9 Settings" or "Clear this slot") when [launchDirectly] or
     *  [allowClear]; the rest line up 1:1 with [visibleApps]. */
    private val rows = mutableListOf<TextView>()

    /** Guards against the key-up of whichever press opened this screen (e.g. "0" for browse-all)
     *  leaking in after our EditText already has focus — see onPause/dispatchKeyEvent. */
    private val seenKeyDowns = mutableSetOf<Int>()

    // The IME (a hardware T9 predictive keyboard) processes key presses independently of our
    // Activity's own key dispatch — confirmed via logcat, a real single tap of "0" that opens
    // this screen still shows up as a committed character ~300-450ms later, well after our own
    // dispatchKeyEvent never even saw it. It's the IME finishing composing a key it captured
    // before our EditText existed and committing it to whichever input connection appears next.
    // We can't intercept that at the key-event level, so instead we discard the first text
    // change if it lands suspiciously soon after this screen gained focus.
    private var focusGainedAtMillis: Long = 0
    private var firstTextChangeHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyConfiguredTheme()

        launchDirectly = intent.getBooleanExtra(EXTRA_LAUNCH_DIRECTLY, false)
        allowClear = intent.getBooleanExtra(EXTRA_ALLOW_CLEAR, false)
        apps = AppRepository.queryInstalledApps(this)

        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Real EditText so the device's active input method (a T9/multi-tap IME on this hardware)
        // handles predictive/multi-tap text entry itself, same as any other app's text field —
        // we don't reimplement multi-tap logic ourselves.
        searchInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_FILTER or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine()
            background = null
            // Not focusable until the first real T9 key press (see dispatchKeyEvent) — a lone
            // focusable view in a new window gets auto-focused by Android otherwise, which would
            // establish an IME connection immediately and let the key that opened this screen
            // leak a composed character into it.
            isFocusable = false
            isFocusableInTouchMode = false
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(DIM_GREY)
            hint = "Type to search"
            setPadding(ROW_SIDE_PADDING, 20, ROW_SIDE_PADDING, 10)
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    if (!firstTextChangeHandled) {
                        firstTextChangeHandled = true
                        val sinceFocus = System.currentTimeMillis() - focusGainedAtMillis
                        if (!s.isNullOrEmpty() && sinceFocus < LEAKED_CHAR_WINDOW_MS) {
                            s.clear()
                            return
                        }
                    }
                    searchText = s?.toString() ?: ""
                    rebuildRows()
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
        outer.addView(searchInput)

        noMatchesLabel = TextView(this).apply {
            text = "No matches"
            textSize = 16f
            setTextColor(DIM_GREY)
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

        setContentView(outer)
        hideStatusBar()

        rebuildRows()
    }

    override fun onPause() {
        super.onPause()
        seenKeyDowns.clear()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            focusGainedAtMillis = System.currentTimeMillis()
            hideStatusBar()
        }
    }

    private fun rebuildRows() {
        visibleApps = apps.filter { matchesSearch(it.displayLabel, searchText) }
        rowContainer.removeAllViews()
        rows.clear()

        if (launchDirectly) {
            addPseudoRow("L9 Settings")
        } else if (allowClear) {
            addPseudoRow("Clear this slot")
        }

        visibleApps.forEach { entry ->
            val row = TextView(this).apply {
                text = entry.displayLabel
                textSize = 28f
                setPadding(ROW_SIDE_PADDING, 3, ROW_SIDE_PADDING, 3)
            }
            rows.add(row)
            rowContainer.addView(row)
        }

        noMatchesLabel.visibility = if (searchText.isNotEmpty() && visibleApps.isEmpty()) View.VISIBLE else View.GONE

        selectedIndex = selectedIndex.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
        render()
    }

    private fun addPseudoRow(text: String) {
        val row = TextView(this).apply {
            this.text = text
            textSize = 28f
            setPadding(ROW_SIDE_PADDING, 3, ROW_SIDE_PADDING, 3)
        }
        rows.add(row)
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
            row.setTextColor(if (selected) Color.WHITE else DIM_GREY)
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

    /** Up/Down/Select always drive our own list navigation, never the text field — a single-line
     *  search box has no use for them, and the field stays unfocused/inert until a real T9 key is
     *  pressed. Everything else (digits, etc.) focuses the field on first press, then falls
     *  through to the normal view dispatch so the EditText and its IME see it exactly like any
     *  other app would. Back is given to the IME first (so it can clear composing text), and only
     *  cancels this screen if the IME didn't consume it. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (super.dispatchKeyEvent(event)) return true
            if (event.action == KeyEvent.ACTION_DOWN) {
                setResult(RESULT_CANCELED)
                finish()
            }
            return true
        }

        if (event.keyCode in RESERVED_NAV_KEYS) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (KeyRouter.map(event.keyCode, event.number)) {
                    Action.Up -> moveSelection(-1)
                    Action.Down -> moveSelection(1)
                    Action.Select -> confirmSelection()
                    else -> {}
                }
            }
            return true
        }

        // Any other key (digits, *, #, ...): only forward it if we actually saw the matching
        // key-down ourselves. A key-up here without one means the press started before this
        // screen existed (e.g. the "0" that opened browse-all) and must not reach the IME.
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                seenKeyDowns.add(event.keyCode)
                // Only a genuine T9 key (never a reserved nav key, handled above) activates the
                // search field — it stays unfocused/inert while the user is just browsing.
                if (!searchInput.isFocusable) {
                    searchInput.isFocusable = true
                    searchInput.isFocusableInTouchMode = true
                    searchInput.requestFocus()
                }
            }
            KeyEvent.ACTION_UP -> if (!seenKeyDowns.remove(event.keyCode)) return true
        }

        return super.dispatchKeyEvent(event)
    }

    private fun moveSelection(direction: Int) {
        if (rows.isEmpty()) return
        selectedIndex = (selectedIndex + direction + rows.size) % rows.size
        render()
    }

    private fun confirmSelection() {
        if (launchDirectly && selectedIndex == 0) {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
            return
        }
        if (allowClear && selectedIndex == 0) {
            setResult(RESULT_OK, Intent().putExtra(EXTRA_CLEAR, true))
            finish()
            return
        }
        val appIndex = if (launchDirectly || allowClear) selectedIndex - 1 else selectedIndex
        val entry = visibleApps.getOrNull(appIndex) ?: return
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

    companion object {
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_ACTIVITY_NAME = "activity_name"
        const val EXTRA_LABEL = "label"
        const val EXTRA_LAUNCH_DIRECTLY = "launch_directly"
        const val EXTRA_ALLOW_CLEAR = "allow_clear"
        const val EXTRA_CLEAR = "clear"
    }
}
