package life.furrer.l9

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import life.furrer.nine.core.prefs.NinePrefs
import life.furrer.nine.core.ui.Palette
import life.furrer.nine.core.ui.applyConfiguredTheme
import life.furrer.nine.core.ui.dialogThemeResId
import life.furrer.nine.core.ui.hideStatusBar

private const val ROW_SIDE_PADDING = 28
private const val MIN_SLOT_COUNT = 1
private const val MAX_SLOT_COUNT = 9
private const val MIN_CALENDAR_ENTRIES = 0
private const val MAX_CALENDAR_ENTRIES = 9

private const val ROW_SLOT_COUNT = 0
private const val ROW_SHOW_INDICES = 1
private const val ROW_ALIGNMENT = 2
private const val ROW_LEFT_APP = 3
private const val ROW_RIGHT_APP = 4
private const val ROW_CALENDAR_ENTRIES = 5
private const val ROW_CALENDARS = 6
private const val ROW_TIME_FORMAT = 7
private const val ROW_NOTIFICATIONS = 8
private const val ROW_SPEED_DIAL = 9
private const val ROW_CALENDAR_DIVIDER = 10
private const val ROW_LOCK_WALLPAPER = 11
private const val ROW_MONOSPACE_FONT = 12
private const val ROW_EXPORT_SETTINGS = 13
private const val ROW_IMPORT_SETTINGS = 14
private const val ROW_MANUAL_INTRO = 15
private const val ROW_MANUAL_KEY_MAPPING = 16
private const val ROW_MANUAL_ABOUT = 17
private const val ROW_MANUAL_WEBSITE = 18
private const val WEBSITE_URL = "https://nine.furrer.life"

private const val REQUEST_PICK_LEFT_APP = 300
private const val REQUEST_PICK_RIGHT_APP = 301
private const val REQUEST_EXPORT_SETTINGS = 302
private const val REQUEST_IMPORT_SETTINGS = 303

class SettingsActivity : Activity() {

    private var selectedIndex = 0
    private var is24Hour = false
    private var slotCount = 4
    private var showIndices = true
    private var alignment = ListAlignment.LEFT
    private var calendarEntryCount = 3
    private var showCalendarDivider = true
    private var blackWallpaperEnabled = false
    private var useMonospaceFont = false

    private lateinit var scrollView: ScrollView
    private lateinit var timeFormatRow: TextView
    private lateinit var slotCountRow: TextView
    private lateinit var showIndicesRow: TextView
    private lateinit var alignmentRow: TextView
    private lateinit var leftAppRow: TextView
    private lateinit var rightAppRow: TextView
    private lateinit var calendarEntriesRow: TextView
    private lateinit var calendarsRow: TextView
    private lateinit var notificationsRow: TextView
    private lateinit var speedDialRow: TextView
    private lateinit var calendarDividerRow: TextView
    private lateinit var lockWallpaperRow: TextView
    private lateinit var monospaceFontRow: TextView
    private lateinit var exportSettingsRow: TextView
    private lateinit var importSettingsRow: TextView
    private lateinit var manualIntroRow: TextView
    private lateinit var manualKeyMappingRow: TextView
    private lateinit var manualAboutRow: TextView
    private lateinit var manualWebsiteRow: TextView
    private val rows = mutableListOf<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyConfiguredTheme()

        val rowContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollView = ScrollView(this).apply {
            addView(rowContainer)
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
        setContentView(scrollView)
        hideStatusBar()

        slotCountRow = makeRow()
        showIndicesRow = makeRow()
        alignmentRow = makeRow()
        leftAppRow = makeRow()
        rightAppRow = makeRow()
        calendarEntriesRow = makeRow()
        calendarsRow = makeRow()
        timeFormatRow = makeRow()
        notificationsRow = makeRow()
        speedDialRow = makeRow()
        calendarDividerRow = makeRow()
        lockWallpaperRow = makeRow()
        monospaceFontRow = makeRow()
        exportSettingsRow = makeRow()
        importSettingsRow = makeRow()
        manualIntroRow = makeRow()
        manualKeyMappingRow = makeRow()
        manualAboutRow = makeRow()
        manualWebsiteRow = makeRow()

        addSectionHeader(rowContainer, "Apps", isFirst = true)
        addRows(rowContainer, slotCountRow, showIndicesRow, alignmentRow, leftAppRow, rightAppRow)

        addSectionHeader(rowContainer, "Calendar")
        addRows(rowContainer, calendarEntriesRow, calendarsRow)

        addSectionHeader(rowContainer, "Time")
        addRows(rowContainer, timeFormatRow)

        addSectionHeader(rowContainer, "Notifications")
        addRows(rowContainer, notificationsRow)

        addSectionHeader(rowContainer, "Speed dial")
        addRows(rowContainer, speedDialRow)

        addSectionHeader(rowContainer, "Theme")
        addRows(rowContainer, calendarDividerRow, lockWallpaperRow, monospaceFontRow)

        addSectionHeader(rowContainer, "Backup")
        addRows(rowContainer, exportSettingsRow, importSettingsRow)

        addSectionHeader(rowContainer, "Manual")
        addRows(rowContainer, manualIntroRow, manualKeyMappingRow, manualAboutRow, manualWebsiteRow)
    }

    private fun addSectionHeader(rowContainer: LinearLayout, title: String, isFirst: Boolean = false) {
        val header = TextView(this).apply {
            text = title.uppercase()
            textSize = 15f
            setTextColor(Palette.GRAY_MUTED)
            letterSpacing = 0.1f
            setPadding(ROW_SIDE_PADDING, if (isFirst) 30 else 48, ROW_SIDE_PADDING, 10)
        }
        rowContainer.addView(header)
    }

    private fun addRows(rowContainer: LinearLayout, vararg newRows: TextView) {
        newRows.forEach { row ->
            rows.add(row)
            rowContainer.addView(row)
        }
    }

    private fun makeRow(): TextView = TextView(this).apply {
        textSize = 28f
        setPadding(ROW_SIDE_PADDING, 3, ROW_SIDE_PADDING, 3)
    }

    override fun onResume() {
        super.onResume()
        reloadState()
        render()
    }

    private fun reloadState() {
        is24Hour = AppRepository.is24HourFormat(this)
        slotCount = AppRepository.getSlotCount(this)
        showIndices = AppRepository.getShowIndices(this)
        alignment = AppRepository.getAlignment(this)
        calendarEntryCount = CalendarRepository.getEntryCount(this)
        showCalendarDivider = AppRepository.getShowCalendarDivider(this)
        blackWallpaperEnabled = AppRepository.isBlackWallpaperEnabled(this)
        useMonospaceFont = NinePrefs.useMonospaceFont(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }

    private fun render() {
        timeFormatRow.text = "Time format: ${if (is24Hour) "24h" else "12h"}"
        slotCountRow.text = "Home screen items: $slotCount"
        showIndicesRow.text = "Show indices: ${if (showIndices) "yes" else "no"}"
        alignmentRow.text = "Alignment: ${alignment.name.lowercase()}"
        leftAppRow.text = "Left key: ${AppRepository.getLeftApp(this)?.displayLabel ?: "not set"}"
        rightAppRow.text = "Right key: ${AppRepository.getRightApp(this)?.displayLabel ?: "not set"}"
        calendarEntriesRow.text = "Calendar entries: $calendarEntryCount"
        calendarsRow.text = "Calendars: ${calendarsSummary()}"
        notificationsRow.text = "Notification access: ${if (NotificationRepository.isListenerEnabled(this)) "granted" else "not granted"}"
        speedDialRow.text = "Speed dial contacts: ${SpeedDialRepository.loadContacts(this).size} set"
        calendarDividerRow.text = "Calendar divider: ${if (showCalendarDivider) "yes" else "no"}"
        lockWallpaperRow.text = "Set black wallpapers: ${if (blackWallpaperEnabled) "enabled" else "disabled"}"
        monospaceFontRow.text = "Font: ${if (useMonospaceFont) "monospace" else "proportional"}"
        exportSettingsRow.text = "Export settings"
        importSettingsRow.text = "Import settings"
        manualIntroRow.text = "Intro"
        manualKeyMappingRow.text = "Key mapping"
        manualAboutRow.text = "About"
        manualWebsiteRow.text = "Open website"

        rows.forEachIndexed { index, row ->
            val selected = index == selectedIndex
            row.setTextColor(if (selected) Color.WHITE else Palette.GRAY_DIM)
            row.paintFlags = if (selected) {
                row.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            } else {
                row.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
            }
        }
        rows.getOrNull(selectedIndex)?.let { row ->
            // Scroll to the absolute top for the first row so its section header stays visible.
            scrollView.post { scrollView.smoothScrollTo(0, if (selectedIndex == 0) 0 else row.top) }
        }
    }

    private fun calendarsSummary(): String {
        if (!CalendarRepository.hasPermission(this)) return "permission needed"
        val all = CalendarRepository.queryCalendars(this)
        val selectedIds = CalendarRepository.getSelectedCalendarIds(this) ?: all.map { it.id }.toSet()
        return when {
            all.isEmpty() -> "none found"
            selectedIds.size == all.size -> "all"
            selectedIds.isEmpty() -> "none"
            else -> "${selectedIds.size} of ${all.size}"
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                selectedIndex = (selectedIndex - 1 + rows.size) % rows.size
                render()
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                selectedIndex = (selectedIndex + 1) % rows.size
                render()
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                adjust(-1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                adjust(1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                when (selectedIndex) {
                    ROW_LEFT_APP -> startActivityForResult(
                        Intent(this, AppPickerActivity::class.java)
                            .putExtra(AppPickerActivity.EXTRA_ALLOW_CLEAR, AppRepository.getLeftApp(this) != null),
                        REQUEST_PICK_LEFT_APP,
                    )
                    ROW_RIGHT_APP -> startActivityForResult(
                        Intent(this, AppPickerActivity::class.java)
                            .putExtra(AppPickerActivity.EXTRA_ALLOW_CLEAR, AppRepository.getRightApp(this) != null),
                        REQUEST_PICK_RIGHT_APP,
                    )
                    ROW_CALENDARS -> startActivity(Intent(this, CalendarSelectionActivity::class.java))
                    ROW_NOTIFICATIONS -> confirmOpenNotificationSettings()
                    ROW_SPEED_DIAL -> startActivity(Intent(this, SpeedDialSettingsActivity::class.java))
                    ROW_EXPORT_SETTINGS -> exportSettings()
                    ROW_IMPORT_SETTINGS -> confirmImportSettings()
                    ROW_MANUAL_INTRO -> openManual(ManualActivity.PAGE_INTRO)
                    ROW_MANUAL_KEY_MAPPING -> openManual(ManualActivity.PAGE_KEY_MAPPING)
                    ROW_MANUAL_ABOUT -> openManual(ManualActivity.PAGE_ABOUT)
                    ROW_MANUAL_WEBSITE -> openWebsite()
                    else -> adjust(1)
                }
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                finish()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_EXPORT_SETTINGS) {
            val uri = data?.data
            if (resultCode == RESULT_OK && uri != null) {
                val success = BackupRepository.exportTo(this, uri)
                Toast.makeText(this, if (success) "Settings exported" else "Export failed", Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (requestCode == REQUEST_IMPORT_SETTINGS) {
            val uri = data?.data
            if (resultCode == RESULT_OK && uri != null) {
                if (BackupRepository.importFrom(this, uri)) {
                    reloadState()
                    render()
                    Toast.makeText(this, "Settings imported", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Import failed — not a valid L9 backup file", Toast.LENGTH_SHORT).show()
                }
            }
            return
        }
        if (resultCode != RESULT_OK || data == null) return
        if (requestCode != REQUEST_PICK_LEFT_APP && requestCode != REQUEST_PICK_RIGHT_APP) return
        val entry = if (data.getBooleanExtra(AppPickerActivity.EXTRA_CLEAR, false)) {
            null
        } else {
            AppEntry(
                packageName = data.getStringExtra(AppPickerActivity.EXTRA_PACKAGE_NAME)!!,
                activityName = data.getStringExtra(AppPickerActivity.EXTRA_ACTIVITY_NAME)!!,
                label = data.getStringExtra(AppPickerActivity.EXTRA_LABEL)!!,
            )
        }
        if (requestCode == REQUEST_PICK_LEFT_APP) {
            AppRepository.setLeftApp(this, entry)
        } else {
            AppRepository.setRightApp(this, entry)
        }
        render()
    }

    private fun openManual(page: String) {
        startActivity(Intent(this, ManualActivity::class.java).putExtra(ManualActivity.EXTRA_PAGE, page))
    }

    private fun openWebsite() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WEBSITE_URL)))
    }

    private fun exportSettings() {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "l9-settings-$timestamp.json")
        }
        startActivityForResult(intent, REQUEST_EXPORT_SETTINGS)
    }

    private fun confirmImportSettings() {
        AlertDialog.Builder(this, dialogThemeResId())
            .setTitle("Import settings")
            .setMessage("This replaces all current L9 settings with the contents of the chosen file.")
            .setPositiveButton("Choose file") { _, _ ->
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    // Not restricted to "application/json" — many file managers report JSON
                    // files under a different MIME type, and BackupRepository validates the
                    // content itself once a file is picked.
                    type = "*/*"
                }
                startActivityForResult(intent, REQUEST_IMPORT_SETTINGS)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmOpenNotificationSettings() {
        AlertDialog.Builder(this, dialogThemeResId())
            .setTitle("Notification access")
            .setMessage(
                "L9 shows a dot next to the battery level when you have notifications. " +
                    "It never reads notification content. Enable \"L9\" on the next screen."
            )
            .setPositiveButton("Open settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmEnableBlackWallpapers() {
        AlertDialog.Builder(this, dialogThemeResId())
            .setTitle("Black wallpapers")
            .setMessage("Replace your current lock screen and home screen wallpapers with solid black?")
            .setPositiveButton("Set") { _, _ ->
                if (setWallpapersBlack()) {
                    blackWallpaperEnabled = true
                    AppRepository.setBlackWallpaperEnabled(this, true)
                }
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setWallpapersBlack(): Boolean {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        return try {
            WallpaperManager.getInstance(this)
                .setBitmap(bitmap, null, false, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
            Toast.makeText(this, "Wallpapers set to black", Toast.LENGTH_SHORT).show()
            true
        } catch (e: IOException) {
            Toast.makeText(this, "Couldn't set wallpapers", Toast.LENGTH_SHORT).show()
            false
        } finally {
            bitmap.recycle()
        }
    }

    private fun adjust(direction: Int) {
        when (selectedIndex) {
            ROW_TIME_FORMAT -> {
                is24Hour = !is24Hour
                AppRepository.setIs24HourFormat(this, is24Hour)
            }
            ROW_SLOT_COUNT -> {
                slotCount = (slotCount + direction).coerceIn(MIN_SLOT_COUNT, MAX_SLOT_COUNT)
                AppRepository.setSlotCount(this, slotCount)
            }
            ROW_SHOW_INDICES -> {
                showIndices = !showIndices
                AppRepository.setShowIndices(this, showIndices)
            }
            ROW_ALIGNMENT -> {
                val values = ListAlignment.values()
                val nextIndex = (values.indexOf(alignment) + direction + values.size) % values.size
                alignment = values[nextIndex]
                AppRepository.setAlignment(this, alignment)
            }
            ROW_CALENDAR_ENTRIES -> {
                calendarEntryCount = (calendarEntryCount + direction).coerceIn(MIN_CALENDAR_ENTRIES, MAX_CALENDAR_ENTRIES)
                CalendarRepository.setEntryCount(this, calendarEntryCount)
            }
            ROW_CALENDAR_DIVIDER -> {
                showCalendarDivider = !showCalendarDivider
                AppRepository.setShowCalendarDivider(this, showCalendarDivider)
            }
            ROW_LOCK_WALLPAPER -> {
                if (blackWallpaperEnabled) {
                    blackWallpaperEnabled = false
                    AppRepository.setBlackWallpaperEnabled(this, false)
                } else {
                    confirmEnableBlackWallpapers()
                }
            }
            ROW_MONOSPACE_FONT -> {
                useMonospaceFont = !useMonospaceFont
                NinePrefs.setUseMonospaceFont(this, useMonospaceFont)
                // The theme's default TextView style only applies to views created after it's
                // set, so this screen needs to be rebuilt for the change to show immediately.
                recreate()
            }
        }
        render()
    }
}
