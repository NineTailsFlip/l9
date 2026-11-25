package life.furrer.l9

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import life.furrer.nine.core.ui.Dimens
import life.furrer.nine.core.ui.HintBar
import life.furrer.nine.core.ui.MenuItem
import life.furrer.nine.core.ui.MenuSheet
import life.furrer.nine.core.ui.NineActivity
import life.furrer.nine.core.ui.TextRole
import life.furrer.nine.core.ui.applyRole
import life.furrer.nine.core.ui.applyRowSelectionStyle
import life.furrer.nine.core.ui.dp

/**
 * Recent apps, in place of the stock launcher's Recents. The system opens it for the Recents key
 * (on thor2, a long press on the left soft key: PhoneWindowManager starts the component in
 * ro.config.thor2_recents); asked again while it's open, it closes — the same toggle the stock
 * screen has.
 *
 * Up/Down pick a task, OK returns to it, Left or the delete key closes it, `*` offers close and
 * close all, Back leaves.
 */
class RecentsActivity : NineActivity() {

    private var tasks: List<RecentTask> = emptyList()
    private var selectedIndex = 0
    private lateinit var rowContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var emptyLabel: TextView
    private lateinit var hintBar: HintBar
    private lateinit var menuSheet: MenuSheet
    private val rows = mutableListOf<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = TextView(this).apply {
            text = "Recent apps"
            applyRole(TextRole.TITLE)
            setPadding(dp(Dimens.SIDE_PADDING_DP), dp(Dimens.SECTION_FIRST_TOP_DP), dp(Dimens.SIDE_PADDING_DP), dp(8))
        }
        emptyLabel = TextView(this).apply {
            text = "No recent apps"
            applyRole(TextRole.ROW)
            setPadding(dp(Dimens.SIDE_PADDING_DP), dp(Dimens.ROW_VERTICAL_PADDING_DP), dp(Dimens.SIDE_PADDING_DP), 0)
        }
        rowContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollView = ScrollView(this).apply {
            addView(rowContainer)
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
        hintBar = HintBar(this)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(emptyLabel)
            addView(scrollView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(hintBar)
        }
        menuSheet = MenuSheet(this)
        setContentView(
            FrameLayout(this).apply {
                addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(menuSheet, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            },
        )
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    /** The Recents key pressed again while this is showing: close, like the stock toggle. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        finish()
    }

    private fun reload() {
        tasks = RecentTasks.load(this)
        selectedIndex = selectedIndex.coerceIn(0, (tasks.size - 1).coerceAtLeast(0))
        rowContainer.removeAllViews()
        rows.clear()
        tasks.forEach { task ->
            val row = TextView(this).apply {
                text = task.label
                applyRole(TextRole.ROW)
                maxLines = 1
                setPadding(dp(Dimens.SIDE_PADDING_DP), dp(Dimens.ROW_VERTICAL_PADDING_DP), dp(Dimens.SIDE_PADDING_DP), dp(Dimens.ROW_VERTICAL_PADDING_DP))
            }
            rows.add(row)
            rowContainer.addView(row)
        }
        render()
    }

    private fun render() {
        emptyLabel.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
        rows.forEachIndexed { index, row -> row.applyRowSelectionStyle(index == selectedIndex) }
        rows.getOrNull(selectedIndex)?.let { row -> scrollView.post { scrollView.smoothScrollTo(0, row.top) } }
        hintBar.setText(if (tasks.isEmpty()) "‹ back" else "ok open · ← close · * menu · ‹ back")
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (menuSheet.interceptKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> move(-1)
            KeyEvent.KEYCODE_DPAD_DOWN -> move(1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> openSelected()
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DEL -> closeSelected()
            KeyEvent.KEYCODE_STAR -> openMenu()
            KeyEvent.KEYCODE_BACK -> finish()
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    private fun move(direction: Int) {
        if (tasks.isEmpty()) return
        selectedIndex = (selectedIndex + direction + tasks.size) % tasks.size
        render()
    }

    private fun openSelected() {
        val task = tasks.getOrNull(selectedIndex) ?: return
        if (RecentTasks.open(this, task)) {
            finish()
        } else {
            Toast.makeText(this, "Couldn't open ${task.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun closeSelected() {
        val task = tasks.getOrNull(selectedIndex) ?: return
        if (!RecentTasks.close(task)) {
            Toast.makeText(this, "Couldn't close ${task.label}", Toast.LENGTH_SHORT).show()
        }
        reload()
    }

    private fun closeAll() {
        val failed = tasks.count { !RecentTasks.close(it) }
        if (failed > 0) Toast.makeText(this, "Couldn't close $failed", Toast.LENGTH_SHORT).show()
        reload()
        if (tasks.isEmpty()) finish()
    }

    private fun openMenu() {
        val hasTask = tasks.isNotEmpty()
        menuSheet.show(
            listOf(
                MenuItem("Open", enabled = hasTask) { openSelected() },
                MenuItem("Close", enabled = hasTask) { closeSelected() },
                MenuItem("Close all", enabled = hasTask) { closeAll() },
            ),
        )
    }
}
