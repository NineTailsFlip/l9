package life.furrer.l9

import android.Manifest
import android.app.Activity
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

private const val DIM_GREY = 0xFF8A8A8A.toInt()
private const val ROW_SIDE_PADDING = 28
private const val REQUEST_CALENDAR_PERMISSION = 100

class CalendarSelectionActivity : Activity() {

    private var calendars: List<CalendarInfo> = emptyList()
    private var selected: MutableSet<Long> = mutableSetOf()
    private var selectedIndex = 0
    private lateinit var rowContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private val rows = mutableListOf<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyConfiguredTheme()

        rowContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollView = ScrollView(this).apply {
            addView(rowContainer)
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
        setContentView(scrollView)
        hideStatusBar()

        if (CalendarRepository.hasPermission(this)) {
            loadCalendars()
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), REQUEST_CALENDAR_PERMISSION)
        }
        buildRows()
        render()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CALENDAR_PERMISSION) {
            loadCalendars()
            buildRows()
            render()
        }
    }

    private fun loadCalendars() {
        calendars = CalendarRepository.queryCalendars(this)
        selected = (CalendarRepository.getSelectedCalendarIds(this) ?: calendars.map { it.id }.toSet()).toMutableSet()
    }

    private fun buildRows() {
        rowContainer.removeAllViews()
        rows.clear()
        if (calendars.isEmpty()) {
            val hasPermission = CalendarRepository.hasPermission(this)
            val message = TextView(this).apply {
                text = if (hasPermission) "No calendars found" else "Calendar permission denied"
                textSize = 24f
                setTextColor(DIM_GREY)
                setPadding(ROW_SIDE_PADDING, 30, ROW_SIDE_PADDING, 30)
            }
            rowContainer.addView(message)
            return
        }
        calendars.forEach { _ ->
            val row = TextView(this).apply {
                textSize = 26f
                setPadding(ROW_SIDE_PADDING, 26, ROW_SIDE_PADDING, 26)
            }
            rows.add(row)
            rowContainer.addView(row)
        }
    }

    private fun render() {
        calendars.forEachIndexed { index, calendar ->
            val row = rows[index]
            val checked = if (calendar.id in selected) "[x]" else "[ ]"
            row.text = "$checked ${calendar.displayName}"
            val isSelectedRow = index == selectedIndex
            row.setTextColor(if (isSelectedRow) Color.WHITE else DIM_GREY)
            row.paintFlags = if (isSelectedRow) {
                row.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            } else {
                row.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
            }
        }
        rows.getOrNull(selectedIndex)?.let { row ->
            scrollView.post { scrollView.smoothScrollTo(0, row.top) }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (rows.isEmpty()) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                finish()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }
        when (KeyRouter.map(keyCode, event.number)) {
            Action.Up -> {
                selectedIndex = (selectedIndex - 1 + rows.size) % rows.size
                render()
                return true
            }
            Action.Down -> {
                selectedIndex = (selectedIndex + 1) % rows.size
                render()
                return true
            }
            Action.Select -> {
                toggleSelected()
                return true
            }
            else -> {}
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun toggleSelected() {
        val calendar = calendars.getOrNull(selectedIndex) ?: return
        if (calendar.id in selected) selected.remove(calendar.id) else selected.add(calendar.id)
        CalendarRepository.setSelectedCalendarIds(this, selected)
        render()
    }
}
