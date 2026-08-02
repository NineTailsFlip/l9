package life.furrer.l9

import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.Settings
import android.widget.Toast
import java.io.IOException
import life.furrer.nine.core.NineSurface
import life.furrer.nine.core.settings.NineSettingsActivity
import life.furrer.nine.core.settings.SectionScope
import life.furrer.nine.core.settings.SettingsScreen
import life.furrer.nine.core.ui.dialogThemeResId

private const val MIN_SLOT_COUNT = 1
private const val MAX_SLOT_COUNT = 9
private const val MIN_CALENDAR_ENTRIES = 0
private const val MAX_CALENDAR_ENTRIES = 9

private const val REQUEST_PICK_LEFT_APP = 300
private const val REQUEST_PICK_RIGHT_APP = 301

/** L9's own sections on top of core's shared Theme/Backup/About — Apps, Calendar, Notifications,
 *  Speed dial. */
class SettingsActivity : NineSettingsActivity() {

    override fun buildAppSections(screen: SettingsScreen, surface: NineSurface) {
        screen.section("Apps") {
            intRange(
                label = "Home screen items",
                min = MIN_SLOT_COUNT,
                max = MAX_SLOT_COUNT,
                get = { AppRepository.getSlotCount(this@SettingsActivity) },
                set = { AppRepository.setSlotCount(this@SettingsActivity, it) },
            )
            toggle(
                label = "Show indices",
                get = { AppRepository.getShowIndices(this@SettingsActivity) },
                set = { AppRepository.setShowIndices(this@SettingsActivity, it) },
            )
            choice(
                label = "Alignment",
                options = { ListAlignment.values().toList() },
                get = { AppRepository.getAlignment(this@SettingsActivity) },
                set = { AppRepository.setAlignment(this@SettingsActivity, it ?: ListAlignment.LEFT) },
                render = { it.name.lowercase() },
            )
            action(
                label = "Left key",
                value = { AppRepository.getLeftApp(this@SettingsActivity)?.displayLabel ?: "not set" },
                onSelect = {
                    startActivityForResult(
                        Intent(this@SettingsActivity, AppPickerActivity::class.java)
                            .putExtra(AppPickerActivity.EXTRA_ALLOW_CLEAR, AppRepository.getLeftApp(this@SettingsActivity) != null),
                        REQUEST_PICK_LEFT_APP,
                    )
                },
            )
            action(
                label = "Right key",
                value = { AppRepository.getRightApp(this@SettingsActivity)?.displayLabel ?: "not set" },
                onSelect = {
                    startActivityForResult(
                        Intent(this@SettingsActivity, AppPickerActivity::class.java)
                            .putExtra(AppPickerActivity.EXTRA_ALLOW_CLEAR, AppRepository.getRightApp(this@SettingsActivity) != null),
                        REQUEST_PICK_RIGHT_APP,
                    )
                },
            )
        }

        screen.section("Calendar") {
            intRange(
                label = "Calendar entries",
                min = MIN_CALENDAR_ENTRIES,
                max = MAX_CALENDAR_ENTRIES,
                get = { CalendarRepository.getEntryCount(this@SettingsActivity) },
                set = { CalendarRepository.setEntryCount(this@SettingsActivity, it) },
            )
            action(
                label = "Calendars",
                value = { calendarsSummary() },
                onSelect = { startActivity(Intent(this@SettingsActivity, CalendarSelectionActivity::class.java)) },
            )
        }

        screen.section("Notifications") {
            action(
                label = "Notification access",
                value = { if (NotificationRepository.isListenerEnabled(this@SettingsActivity)) "granted" else "not granted" },
                onSelect = { confirmOpenNotificationSettings() },
            )
        }

        screen.section("Speed dial") {
            action(
                label = "Speed dial contacts",
                value = { "${SpeedDialRepository.loadContacts(this@SettingsActivity).size} set" },
                onSelect = { startActivity(Intent(this@SettingsActivity, SpeedDialSettingsActivity::class.java)) },
            )
        }
    }

    override fun buildThemeExtras(scope: SectionScope) {
        scope.toggle(
            label = "Calendar divider",
            get = { AppRepository.getShowCalendarDivider(this) },
            set = { AppRepository.setShowCalendarDivider(this, it) },
        )
        scope.toggle(
            label = "Set black wallpapers",
            get = { AppRepository.isBlackWallpaperEnabled(this) },
            set = { enabled -> if (enabled) confirmEnableBlackWallpapers() else AppRepository.setBlackWallpaperEnabled(this, false) },
            on = "enabled",
            off = "disabled",
        )
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
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
        refresh()
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
            .setPositiveButton("Set") { _, _ -> setWallpapersBlack() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setWallpapersBlack() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        try {
            WallpaperManager.getInstance(this)
                .setBitmap(bitmap, null, false, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
            AppRepository.setBlackWallpaperEnabled(this, true)
            Toast.makeText(this, "Wallpapers set to black", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            Toast.makeText(this, "Couldn't set wallpapers", Toast.LENGTH_SHORT).show()
        } finally {
            bitmap.recycle()
            refresh()
        }
    }
}
