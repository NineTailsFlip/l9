package life.furrer.l9

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

private const val DIM_GREY = 0xFF8A8A8A.toInt()
private const val ROW_SIDE_PADDING = 28
private const val KEY_COLUMN_WIDTH = 170

/** Read-only scrollable text screen used for all three Manual pages — the content differs, the
 *  chrome (title + body + Back-to-close) doesn't, so one Activity covers all of them. */
class ManualActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyConfiguredTheme()

        val page = intent.getStringExtra(EXTRA_PAGE) ?: PAGE_INTRO
        val title = when (page) {
            PAGE_KEY_MAPPING -> "Key mapping"
            PAGE_ABOUT -> "About"
            else -> "Intro"
        }

        val titleView = TextView(this).apply {
            text = title
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding(ROW_SIDE_PADDING, 30, ROW_SIDE_PADDING, 20)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            addView(titleView)
            if (page == PAGE_KEY_MAPPING) {
                keyMappingRows().forEach { (key, description) -> addView(keyMappingRow(key, description)) }
                addView(View(this@ManualActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 30)
                })
            } else {
                val body = if (page == PAGE_ABOUT) aboutText() else introText()
                addView(
                    TextView(this@ManualActivity).apply {
                        text = body
                        textSize = 18f
                        setTextColor(DIM_GREY)
                        setLineSpacing(8f, 1f)
                        setPadding(ROW_SIDE_PADDING, 0, ROW_SIDE_PADDING, 30)
                    },
                )
            }
        }
        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(Color.BLACK)
                addView(container)
            },
        )
        hideStatusBar()
    }

    /** One row: a fixed-width key-combo column and a wrapping description column, aligned like a
     *  borderless table rather than the plain-paragraph style of the other two pages. */
    private fun keyMappingRow(key: String, description: String): LinearLayout {
        val keyView = TextView(this).apply {
            text = key
            textSize = 15f
            setTextColor(Color.WHITE)
        }
        val descriptionView = TextView(this).apply {
            text = description
            textSize = 14f
            setTextColor(DIM_GREY)
            setLineSpacing(4f, 1f)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(ROW_SIDE_PADDING, 10, ROW_SIDE_PADDING, 10)
            addView(keyView, LinearLayout.LayoutParams(KEY_COLUMN_WIDTH, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(descriptionView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun introText(): String = """
        L9 ("Launcher on 9 keys", an analogue to T9 "Text on 9 keys") is a minimal, all-black
        home launcher built for keypad phones — a D-pad and a numeric keypad, no touchscreen
        assumed.

        The home screen shows a fixed list of app slots, however many you configure in Settings.
        Move the selection with Up/Down, press OK to launch. Each number key 1-9 jumps straight
        to that slot and launches it immediately. Press 0 to browse every installed app instead
        of just the configured slots.

        Left and Right are a second pair of one-tap shortcuts to apps of your choice, independent
        of the numbered slots. You can also assign a contact to any digit for speed dial: hold
        the key down and, after a short warning, it places the call.

        The "*" key is a command prefix for the less common actions — reordering slots, renaming
        a slot, reassigning a slot's app, and jumping into Settings. See "Key mapping" for the
        full list of what every key does.
    """.trimIndent()

    private fun keyMappingRows(): List<Pair<String, String>> = listOf(
        "Up / Down" to "Move the selection.",
        "OK" to "Launch the selected slot's app.",
        "1-9" to "Jump to and launch that slot directly.",
        "0" to "Browse every installed app.",
        "Left / Right" to "Launch the configured shortcut app.",
        "Hold Left / Right" to "Reassign that shortcut's app.",
        "Hold a digit\nwith speed dial set" to "Call the assigned contact after a warning.",
        "*" to "Does nothing on its own — see below.",
        "* then #" to "Toggle reorder mode. Up/Down swaps the selected slot with its " +
            "neighbor; * or Back drops it back in place.",
        "* then 0" to "(Re)assign the app for the currently selected slot.",
        "* twice" to "Rename the currently selected slot.",
        "Hold *" to "Open Settings.",
        "Back" to "Cancel the current screen, or exit reorder mode.",
    )

    private fun aboutText(): String {
        val info = packageManager.getPackageInfo(packageName, 0)
        return """
            L9 — Launcher on 9 keys

            Version ${info.versionName} (build ${info.longVersionCode})

            Source: https://git.sr.ht/~timofurrer/l9
            Tickets: https://todo.sr.ht/~timofurrer/l9

            Licensed under the GNU General Public License v3.0 or later. See the LICENSE file
            included with the source for the full text.
        """.trimIndent()
    }

    companion object {
        const val EXTRA_PAGE = "page"
        const val PAGE_INTRO = "intro"
        const val PAGE_KEY_MAPPING = "key_mapping"
        const val PAGE_ABOUT = "about"
    }
}
