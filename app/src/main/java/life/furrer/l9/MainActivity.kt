package life.furrer.l9

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import life.furrer.nine.core.ui.Palette
import life.furrer.nine.core.ui.applyConfiguredTheme
import life.furrer.nine.core.ui.dialogThemeResId
import life.furrer.nine.core.ui.hideStatusBar
import life.furrer.nine.core.ui.recreateIfFontChanged
import life.furrer.nine.core.util.KeyRouter
import life.furrer.nine.core.util.NineAction

private const val SOON_WINDOW_MILLIS = 30 * 60 * 1000L
private const val LOW_BATTERY_THRESHOLD = 20
private const val REQUEST_PICK_APP = 1
private const val INDEX_COLUMN_WIDTH = 104
private const val ROW_SIDE_PADDING = 28
private const val DIVIDER_GREY = 0xFF333333.toInt()
private const val SPEED_DIAL_WARNING_DELAY_MS = 2000L
private const val SPEED_DIAL_CALL_DELAY_MS = 3000L
private const val STAR_SEQUENCE_TIMEOUT_MS = 500L

/** The home screen's own key vocabulary — Star means "reorder toggle" and digit 0 means "browse
 *  all" here, neither of which is universal key routing, just what this screen does with those
 *  keys. Translated from core's [NineAction] rather than duplicating [KeyRouter]'s keycode
 *  matching. */
private sealed interface Action {
    data object Up : Action
    data object Down : Action
    data object Select : Action
    data object BrowseAll : Action
    data object LeftKey : Action
    data object RightKey : Action
    data object ReorderToggle : Action
    data class Digit(val n: Int) : Action
}

private fun homeAction(keyCode: Int, number: Char): Action? = when (val action = KeyRouter.map(keyCode, number)) {
    NineAction.Up -> Action.Up
    NineAction.Down -> Action.Down
    NineAction.Select -> Action.Select
    NineAction.Left -> Action.LeftKey
    NineAction.Right -> Action.RightKey
    NineAction.Star -> Action.ReorderToggle
    is NineAction.Digit -> if (action.n == 0) Action.BrowseAll else Action.Digit(action.n)
    else -> null
}

class MainActivity : Activity() {

    private enum class PendingTarget { NONE, SLOT, LEFT, RIGHT }

    private var slots: List<AppEntry?> = emptyList()
    private var selectedIndex: Int = 0
    private var pendingTarget = PendingTarget.NONE
    private var pendingSlotIndex: Int = -1
    private var sideLongPressHandled = false
    private var pendingSideKeyCode: Int? = null
    private var reorderModeActive = false
    private var appliedMonospaceFont = false

    // Star key: bare tap alone does nothing. Tap then '#' enters reorder mode; tap then '0'
    // opens the picker to (re)assign the current slot; tap twice renames; long-press jumps into
    // Settings. See handleStarShortTap and onKeyDown's starTapArmed handling.
    private var pendingStarKeyCode: Int? = null
    private var starLongPressHandled = false
    private var starTapArmed = false
    private val starSequenceHandler = Handler(Looper.getMainLooper())
    private var starSequenceTimeoutRunnable: Runnable? = null
    private lateinit var rowContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var clockView: TextClock
    private lateinit var batteryView: TextView
    private lateinit var notificationDot: TextView
    private lateinit var calendarContainer: LinearLayout
    private lateinit var calendarDivider: View
    private var batteryReceiver: BroadcastReceiver? = null
    private var notificationReceiver: BroadcastReceiver? = null
    private val rowViews = mutableListOf<View>()
    private val numberViews = mutableListOf<TextView>()
    private val labelViews = mutableListOf<TextView>()
    private val spacerViews = mutableListOf<View>()

    private val speedDialHandler = Handler(Looper.getMainLooper())
    private var speedDialWarningRunnable: Runnable? = null
    private var speedDialCallRunnable: Runnable? = null
    private var pendingSpeedDialDigit: Int? = null
    private var speedDialWarningShown = false
    private var speedDialFired = false
    // Identifies the physical hold that started the current speed-dial timer, independent of
    // pendingSpeedDialDigit — see onKeyDown's use of it for why the two can't be conflated.
    private var speedDialSessionDownTime: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appliedMonospaceFont = applyConfiguredTheme()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ROW_SIDE_PADDING, 24, ROW_SIDE_PADDING, 24)
        }
        clockView = TextClock(this).apply {
            textSize = 20f
            setTextColor(Color.WHITE)
        }
        notificationDot = TextView(this).apply {
            text = "•"
            textSize = 28f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 12, 0)
            visibility = View.GONE
        }
        batteryView = TextView(this).apply {
            textSize = 16f
            setTextColor(Palette.GRAY_DIM)
        }
        header.addView(clockView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(notificationDot)
        header.addView(batteryView)

        calendarContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        calendarDivider = View(this).apply {
            setBackgroundColor(DIVIDER_GREY)
            visibility = View.GONE
        }

        rowContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollView = ScrollView(this).apply {
            addView(rowContainer)
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header)
            addView(calendarContainer)
            addView(
                calendarDivider,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2).apply {
                    setMargins(ROW_SIDE_PADDING, 12, ROW_SIDE_PADDING, 12)
                },
            )
            addView(scrollView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
        hideStatusBar()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }

    override fun onResume() {
        super.onResume()
        if (recreateIfFontChanged(appliedMonospaceFont)) return
        configureClock()
        renderCalendar()
        slots = AppRepository.loadSlots(this)
        selectedIndex = 0
        reorderModeActive = false
        buildRows()
        render()
        registerBatteryReceiver()
        registerNotificationReceiver()
    }

    private fun configureClock() {
        clockView.format24Hour = "HH:mm"
        clockView.format12Hour = null
    }

    private fun renderCalendar() {
        calendarContainer.removeAllViews()
        val entryCount = CalendarRepository.getEntryCount(this)
        val events = if (entryCount > 0) CalendarRepository.queryUpcomingEvents(this, entryCount) else emptyList()
        if (events.isEmpty()) {
            calendarContainer.setPadding(0, 0, 0, 0)
            calendarDivider.visibility = View.GONE
            return
        }
        // Bottom padding matches the app rows' own top padding (3), so together with the
        // divider's symmetric margins the gap above and below it comes out equal.
        calendarContainer.setPadding(ROW_SIDE_PADDING, 0, ROW_SIDE_PADDING, 3)
        calendarDivider.visibility = if (AppRepository.getShowCalendarDivider(this)) View.VISIBLE else View.GONE
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val zone = ZoneId.systemDefault()
        val tomorrow = LocalDate.now(zone).plusDays(1)
        val now = System.currentTimeMillis()
        events.forEach { event ->
            val time = if (event.allDay) "All day" else timeFormat.format(Date(event.startMillis))
            val isTomorrow = Instant.ofEpochMilli(event.startMillis).atZone(zone).toLocalDate() == tomorrow
            // Query already excludes events that have ended, so "starts within the window" also
            // covers events that are already in progress.
            val isSoon = !event.allDay && event.startMillis <= now + SOON_WINDOW_MILLIS

            val prefix = if (isTomorrow) "tomorrow " else ""
            val timeText = "$prefix$time"
            val timeView = TextView(this).apply {
                textSize = 12f
                maxLines = 1
                text = SpannableString(timeText).apply {
                    var start = 0
                    if (isTomorrow) {
                        setSpan(ForegroundColorSpan(Palette.GRAY_FAINT), 0, prefix.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        start = prefix.length
                    }
                    val timeColor = if (isSoon) Palette.GREEN else Palette.GRAY_MUTED
                    setSpan(ForegroundColorSpan(timeColor), start, timeText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            // Title is right-aligned and truncated (not the time) so long titles never crowd the
            // time — a fixed start-padding of roughly two characters keeps at least that much of
            // a gap even when the title is long enough to need ellipsizing.
            val titleView = TextView(this).apply {
                textSize = 12f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.END
                setTextColor(if (isSoon) Palette.GREEN else Palette.GRAY_DIM)
                text = event.title
                setPadding(paint.measureText("00").toInt(), 0, 0, 0)
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 4, 0, 4)
                addView(timeView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            calendarContainer.addView(row)
        }
    }

    override fun onPause() {
        super.onPause()
        batteryReceiver?.let { unregisterReceiver(it) }
        batteryReceiver = null
        notificationReceiver?.let { unregisterReceiver(it) }
        notificationReceiver = null
        cancelSpeedDialTimer()
        // Deliberately NOT clearing pendingSpeedDialDigit here: placing the call itself
        // (placeCall's startActivity) triggers this same onPause while the user may still be
        // physically holding the digit. If we cleared it, the auto-repeat key-downs Android
        // keeps sending for that still-held key would stop being swallowed and fall through to
        // the normal "launch this slot's app" handling — placing the call correctly but then
        // also launching whatever app is assigned to that same digit. onKeyUp still clears it
        // once the key is genuinely released, and a fresh press always overwrites it anyway.
        // A key-down handled here (e.g. OK inside a picker we just launched) can have its
        // matching key-up delivered to us instead, once focus returns — since that activity
        // finishes before the physical key is released. Drop any in-flight tracking so a
        // stray key-up on resume can't be mistaken for a real press we witnessed.
        pendingSideKeyCode = null
        pendingStarKeyCode = null
        cancelStarSequenceTimeout()
        starTapArmed = false
    }

    private fun registerBatteryReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    val percent = level * 100 / scale
                    batteryView.text = "$percent%"
                    batteryView.setTextColor(if (percent < LOW_BATTERY_THRESHOLD) Palette.RED else Palette.GRAY_DIM)
                }
            }
        }
        batteryReceiver = receiver
        registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private fun registerNotificationReceiver() {
        updateNotificationDot()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                updateNotificationDot()
            }
        }
        notificationReceiver = receiver
        registerReceiver(
            receiver,
            IntentFilter(NotificationRepository.ACTION_NOTIFICATIONS_CHANGED),
            Context.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun updateNotificationDot() {
        notificationDot.visibility = if (NotificationRepository.hasNotifications) View.VISIBLE else View.GONE
    }

    private fun buildRows() {
        rowContainer.removeAllViews()
        rowViews.clear()
        numberViews.clear()
        labelViews.clear()
        spacerViews.clear()
        slots.forEach { _ ->
            val numberView = TextView(this).apply {
                textSize = 18f
                gravity = Gravity.END
            }
            val labelView = TextView(this).apply {
                textSize = 30f
            }
            // Balances numberView's width on the opposite side so that centering
            // [number, label, spacer] as a block centers the label alone.
            val spacerView = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(INDEX_COLUMN_WIDTH, 0)
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(numberView)
                addView(labelView)
                addView(spacerView)
            }
            numberViews.add(numberView)
            labelViews.add(labelView)
            spacerViews.add(spacerView)
            rowViews.add(row)
            rowContainer.addView(
                row,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
    }

    private fun render() {
        val showIndices = AppRepository.getShowIndices(this)
        val alignment = AppRepository.getAlignment(this)
        val rowGravity = when (alignment) {
            ListAlignment.LEFT -> Gravity.START
            ListAlignment.CENTER -> Gravity.CENTER_HORIZONTAL
            ListAlignment.RIGHT -> Gravity.END
        }
        val balanceSpacer = showIndices && alignment == ListAlignment.CENTER
        // Left alignment folds the index into the label text itself (like the calendar's
        // "21:00  Test" entries above) instead of a separate column, so the whole row — index
        // included — starts at the same x position as those calendar entries.
        val inlineIndex = showIndices && alignment == ListAlignment.LEFT

        slots.forEachIndexed { index, entry ->
            val selected = index == selectedIndex
            val number = if (index == 9) "0" else "${index + 1}"
            val numberView = numberViews[index]
            numberView.text = number
            numberView.setTextColor(if (selected) Palette.GRAY_DIM else Palette.GRAY_FAINT)
            numberView.visibility = if (showIndices && !inlineIndex) View.VISIBLE else View.GONE
            numberView.setPadding(0, 0, 32, 0)
            numberView.layoutParams = LinearLayout.LayoutParams(INDEX_COLUMN_WIDTH, ViewGroup.LayoutParams.WRAP_CONTENT)
            spacerViews[index].visibility = if (balanceSpacer) View.INVISIBLE else View.GONE

            val label = labelViews[index]
            val baseText = entry?.displayLabel ?: "press *0 to set"
            val displayText = if (selected && reorderModeActive) "↕ $baseText" else baseText
            val labelColor = if (selected) Color.WHITE else Palette.GRAY_DIM
            if (inlineIndex) {
                val prefix = "$number  "
                val fullText = "$prefix$displayText"
                label.text = SpannableString(fullText).apply {
                    val numberColor = if (selected) Palette.GRAY_DIM else Palette.GRAY_FAINT
                    setSpan(ForegroundColorSpan(numberColor), 0, prefix.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(ForegroundColorSpan(labelColor), prefix.length, fullText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (selected) {
                        setSpan(UnderlineSpan(), prefix.length, fullText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                label.paintFlags = label.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
            } else {
                label.text = displayText
                label.setTextColor(labelColor)
                label.paintFlags = if (selected) {
                    label.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                } else {
                    label.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
                }
            }

            val row = rowViews[index] as LinearLayout
            row.gravity = rowGravity
            row.setPadding(ROW_SIDE_PADDING, 3, ROW_SIDE_PADDING, 3)
        }
        rowViews.getOrNull(selectedIndex)?.let { row ->
            scrollView.post { scrollView.smoothScrollTo(0, row.top) }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (reorderModeActive) {
            return handleReorderKeyDown(keyCode, event)
        }
        // '*' followed by '#' enters reorder mode; '*' followed by '0' opens the picker to
        // (re)assign the current slot's app — takes priority over both digit-0's speed dial and
        // its normal BrowseAll meaning, since arming '*' is a deliberate command prefix.
        if (starTapArmed && event.repeatCount == 0) {
            when (keyCode) {
                KeyEvent.KEYCODE_POUND -> {
                    cancelStarSequenceTimeout()
                    starTapArmed = false
                    reorderModeActive = true
                    render()
                    return true
                }
                KeyEvent.KEYCODE_0 -> {
                    cancelStarSequenceTimeout()
                    starTapArmed = false
                    openPickerForSlot(selectedIndex)
                    return true
                }
            }
        }
        val speedDialDigit = digitFor(keyCode, event)
        if (speedDialDigit != null) {
            if (event.repeatCount == 0) {
                val contact = SpeedDialRepository.loadContacts(this)[speedDialDigit]
                if (contact != null) {
                    pendingSpeedDialDigit = speedDialDigit
                    speedDialSessionDownTime = event.downTime
                    speedDialWarningShown = false
                    speedDialFired = false
                    startSpeedDialTimer(contact)
                    return true
                }
            } else if (pendingSpeedDialDigit == speedDialDigit || event.downTime == speedDialSessionDownTime) {
                // Auto-repeat while our own hold timer is running for this digit. The downTime
                // check also catches the case where placeCall()'s startActivity (for the call UI)
                // triggered onPause mid-hold and a synthetic key-up/down pair around that focus
                // change cleared pendingSpeedDialDigit early, even though the physical key was
                // never actually released — confirmed via logcat: the "up" and next "down" shared
                // the same downTime and a continuing (non-zero) repeatCount. Without this,
                // whatever app is assigned to the same digit launches right after the call.
                return true
            }
        }
        if (keyCode == KeyEvent.KEYCODE_STAR) {
            // Deferred to onKeyUp/onKeyLongPress: a bare tap alone does nothing (see
            // handleStarShortTap), so short-press behavior can't be decided on key-down.
            event.startTracking()
            pendingStarKeyCode = keyCode
            return true
        }
        val action = homeAction(keyCode, event.number)
        if (action == Action.Select) {
            onSelect(selectedIndex)
            return true
        }
        if (action == Action.LeftKey || action == Action.RightKey) {
            event.startTracking()
            pendingSideKeyCode = keyCode
            return true
        }
        when (action) {
            Action.Up -> {
                selectedIndex = (selectedIndex - 1 + slots.size) % slots.size
                render()
                return true
            }
            Action.Down -> {
                selectedIndex = (selectedIndex + 1) % slots.size
                render()
                return true
            }
            is Action.Digit -> {
                val index = action.n - 1
                if (index in slots.indices) {
                    selectedIndex = index
                    render()
                    onSelect(index)
                }
                return true
            }
            Action.BrowseAll -> {
                startActivity(
                    Intent(this, AppPickerActivity::class.java)
                        .putExtra(AppPickerActivity.EXTRA_LAUNCH_DIRECTLY, true)
                )
                return true
            }
            else -> {}
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) return true
        return super.onKeyDown(keyCode, event)
    }

    /** While reordering, only up/down (swap) and star/back (drop) do anything — everything
     *  else is swallowed so a stray digit/OK/etc. press can't act on a slot mid-move. */
    private fun handleReorderKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (homeAction(keyCode, event.number)) {
            Action.Up -> swapSlot(-1)
            Action.Down -> swapSlot(1)
            Action.ReorderToggle -> {
                reorderModeActive = false
                render()
            }
            else -> {}
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            reorderModeActive = false
            render()
        }
        return true
    }

    private fun swapSlot(direction: Int) {
        val newIndex = (selectedIndex + direction + slots.size) % slots.size
        val updated = slots.toMutableList()
        val moved = updated[selectedIndex]
        updated[selectedIndex] = updated[newIndex]
        updated[newIndex] = moved
        slots = updated
        selectedIndex = newIndex
        AppRepository.saveSlots(this, slots)
        render()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val speedDialDigit = digitFor(keyCode, event)
        if (speedDialDigit != null && pendingSpeedDialDigit == speedDialDigit) {
            cancelSpeedDialTimer()
            if (!speedDialFired && !speedDialWarningShown) {
                // Short tap: fall through to this digit's normal meaning.
                if (speedDialDigit == 0) {
                    startActivity(
                        Intent(this, AppPickerActivity::class.java)
                            .putExtra(AppPickerActivity.EXTRA_LAUNCH_DIRECTLY, true)
                    )
                } else {
                    val index = speedDialDigit - 1
                    if (index in slots.indices) {
                        selectedIndex = index
                        render()
                        onSelect(index)
                    }
                }
            }
            pendingSpeedDialDigit = null
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_STAR) {
            // Only act if this activity actually witnessed the matching key-down — a stray
            // key-up can otherwise arrive here for a press this activity never saw start (see
            // onPause for the general explanation of this class of race).
            if (pendingStarKeyCode == keyCode && !starLongPressHandled) {
                handleStarShortTap()
            }
            starLongPressHandled = false
            pendingStarKeyCode = null
            return true
        }
        val action = homeAction(keyCode, event.number)
        if (action == Action.LeftKey || action == Action.RightKey) {
            if (pendingSideKeyCode == keyCode && !sideLongPressHandled) {
                onSideSelect(if (action == Action.LeftKey) PendingTarget.LEFT else PendingTarget.RIGHT)
            }
            sideLongPressHandled = false
            pendingSideKeyCode = null
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun digitFor(keyCode: Int, event: KeyEvent): Int? {
        if (keyCode !in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) return null
        return event.number.digitToIntOrNull()
    }

    private fun startSpeedDialTimer(contact: SpeedDialContact) {
        val warning = Runnable {
            speedDialWarningShown = true
            Toast.makeText(this, "Calling ${contact.name} in 1 second…", Toast.LENGTH_SHORT).show()
        }
        val call = Runnable {
            speedDialFired = true
            placeCall(contact)
        }
        speedDialWarningRunnable = warning
        speedDialCallRunnable = call
        speedDialHandler.postDelayed(warning, SPEED_DIAL_WARNING_DELAY_MS)
        speedDialHandler.postDelayed(call, SPEED_DIAL_CALL_DELAY_MS)
    }

    private fun cancelSpeedDialTimer() {
        speedDialWarningRunnable?.let { speedDialHandler.removeCallbacks(it) }
        speedDialCallRunnable?.let { speedDialHandler.removeCallbacks(it) }
        speedDialWarningRunnable = null
        speedDialCallRunnable = null
    }

    private fun placeCall(contact: SpeedDialContact) {
        if (!SpeedDialRepository.hasCallPermission(this)) {
            Toast.makeText(this, "Call permission not granted — check Settings", Toast.LENGTH_LONG).show()
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", contact.phoneNumber, null)))
        } catch (e: SecurityException) {
            Toast.makeText(this, "Couldn't place the call", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_STAR) {
            starLongPressHandled = true
            cancelStarSequenceTimeout()
            starTapArmed = false
            startActivity(Intent(this, SettingsActivity::class.java))
            return true
        }
        val action = homeAction(keyCode, event.number)
        if (action == Action.LeftKey || action == Action.RightKey) {
            sideLongPressHandled = true
            openPickerForSide(if (action == Action.LeftKey) PendingTarget.LEFT else PendingTarget.RIGHT)
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    /** First '*' tap arms a short window for a follow-up (see onKeyDown's starTapArmed handling
     *  for '#'/'0'); a second '*' tap within it renames the selected slot instead. */
    private fun handleStarShortTap() {
        if (starTapArmed) {
            cancelStarSequenceTimeout()
            starTapArmed = false
            onRenameRequested(selectedIndex)
        } else {
            starTapArmed = true
            val timeout = Runnable {
                starTapArmed = false
                starSequenceTimeoutRunnable = null
            }
            starSequenceTimeoutRunnable = timeout
            starSequenceHandler.postDelayed(timeout, STAR_SEQUENCE_TIMEOUT_MS)
        }
    }

    private fun cancelStarSequenceTimeout() {
        starSequenceTimeoutRunnable?.let { starSequenceHandler.removeCallbacks(it) }
        starSequenceTimeoutRunnable = null
    }

    private fun onRenameRequested(index: Int) {
        val entry = slots.getOrNull(index) ?: return
        val builder = AlertDialog.Builder(this, dialogThemeResId())
        // Built from the dialog's own themed context (not the activity's) so the EditText's
        // cursor/underline picks up the dialog theme's white accent instead of the activity
        // theme's default one.
        val input = EditText(builder.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_FILTER or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine()
            setText(entry.displayLabel)
            setSelection(text.length)
        }
        builder
            .setTitle("Rename")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newLabel = input.text.toString().trim()
                val updated = slots.toMutableList()
                updated[index] = entry.copy(customLabel = newLabel.ifEmpty { null })
                slots = updated
                AppRepository.saveSlots(this, slots)
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun onSelect(index: Int) {
        val entry = slots.getOrNull(index)
        if (entry == null) {
            openPickerForSlot(index)
        } else {
            launch(entry, index)
        }
    }

    private fun launch(entry: AppEntry, index: Int) {
        if (!AppRepository.launchApp(this, entry)) {
            onLaunchFailed(index)
        }
    }

    private fun onLaunchFailed(index: Int) {
        Toast.makeText(this, "Couldn't open that app", Toast.LENGTH_SHORT).show()
        val updated = slots.toMutableList()
        updated[index] = null
        slots = updated
        AppRepository.saveSlots(this, slots)
        render()
    }

    private fun onSideSelect(target: PendingTarget) {
        val entry = if (target == PendingTarget.LEFT) AppRepository.getLeftApp(this) else AppRepository.getRightApp(this)
        if (entry == null) {
            openPickerForSide(target)
        } else if (!AppRepository.launchApp(this, entry)) {
            Toast.makeText(this, "Couldn't open that app", Toast.LENGTH_SHORT).show()
            if (target == PendingTarget.LEFT) AppRepository.setLeftApp(this, null) else AppRepository.setRightApp(this, null)
        }
    }

    private fun openPickerForSlot(index: Int) {
        pendingTarget = PendingTarget.SLOT
        pendingSlotIndex = index
        val allowClear = slots.getOrNull(index) != null
        startActivityForResult(
            Intent(this, AppPickerActivity::class.java).putExtra(AppPickerActivity.EXTRA_ALLOW_CLEAR, allowClear),
            REQUEST_PICK_APP,
        )
    }

    private fun openPickerForSide(target: PendingTarget) {
        pendingTarget = target
        val current = if (target == PendingTarget.LEFT) AppRepository.getLeftApp(this) else AppRepository.getRightApp(this)
        startActivityForResult(
            Intent(this, AppPickerActivity::class.java).putExtra(AppPickerActivity.EXTRA_ALLOW_CLEAR, current != null),
            REQUEST_PICK_APP,
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_APP && resultCode == RESULT_OK && data != null) {
            val entry = if (data.getBooleanExtra(AppPickerActivity.EXTRA_CLEAR, false)) {
                null
            } else {
                AppEntry(
                    packageName = data.getStringExtra(AppPickerActivity.EXTRA_PACKAGE_NAME)!!,
                    activityName = data.getStringExtra(AppPickerActivity.EXTRA_ACTIVITY_NAME)!!,
                    label = data.getStringExtra(AppPickerActivity.EXTRA_LABEL)!!,
                )
            }
            when (pendingTarget) {
                PendingTarget.SLOT -> if (pendingSlotIndex >= 0) {
                    val updated = slots.toMutableList()
                    updated[pendingSlotIndex] = entry
                    slots = updated
                    AppRepository.saveSlots(this, slots)
                    render()
                }
                PendingTarget.LEFT -> AppRepository.setLeftApp(this, entry)
                PendingTarget.RIGHT -> AppRepository.setRightApp(this, entry)
                PendingTarget.NONE -> {}
            }
        }
        pendingTarget = PendingTarget.NONE
        pendingSlotIndex = -1
    }
}
