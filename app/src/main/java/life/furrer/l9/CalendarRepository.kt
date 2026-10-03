package life.furrer.l9

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.json.JSONArray

data class CalendarInfo(val id: Long, val displayName: String)

data class CalendarEventEntry(
    val title: String,
    val startMillis: Long,
    val allDay: Boolean,
    /** The local calendar day this entry is shown under (an ongoing multi-day all-day event: today). */
    val date: LocalDate,
)

object CalendarRepository {
    private const val PREFS_NAME = "l9"
    private const val KEY_CALENDAR_IDS = "calendar_ids"
    private const val KEY_ENTRY_COUNT = "calendar_entry_count"
    private const val DEFAULT_ENTRY_COUNT = 3

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun getEntryCount(context: Context): Int =
        prefs(context).getInt(KEY_ENTRY_COUNT, DEFAULT_ENTRY_COUNT)

    fun setEntryCount(context: Context, count: Int) {
        prefs(context).edit().putInt(KEY_ENTRY_COUNT, count).apply()
    }

    /** Null means the user has never customized this — callers should default to "all calendars". */
    fun getSelectedCalendarIds(context: Context): Set<Long>? {
        val json = prefs(context).getString(KEY_CALENDAR_IDS, null) ?: return null
        val array = JSONArray(json)
        val ids = mutableSetOf<Long>()
        for (i in 0 until array.length()) ids.add(array.getLong(i))
        return ids
    }

    fun setSelectedCalendarIds(context: Context, ids: Set<Long>) {
        val array = JSONArray()
        ids.forEach { array.put(it) }
        prefs(context).edit().putString(KEY_CALENDAR_IDS, array.toString()).apply()
    }

    fun queryCalendars(context: Context): List<CalendarInfo> {
        if (!hasPermission(context)) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
        )
        val result = mutableListOf<CalendarInfo>()
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)
            ?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    result.add(
                        CalendarInfo(
                            id = cursor.getLong(idIndex),
                            displayName = cursor.getString(nameIndex) ?: "Calendar",
                        )
                    )
                }
            }
        return result.sortedBy { it.displayName.lowercase() }
    }

    /** Current + upcoming events from today and tomorrow only, earliest first, capped at [limit]. */
    fun queryUpcomingEvents(context: Context, limit: Int): List<CalendarEventEntry> {
        if (limit <= 0 || !hasPermission(context)) return emptyList()
        val selectedIds = getSelectedCalendarIds(context) ?: queryCalendars(context).map { it.id }.toSet()
        if (selectedIds.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val windowEnd = LocalDate.now(zone).plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val today = LocalDate.now(zone)
        // All-day instances span UTC midnight to UTC midnight, so west of UTC today's one has
        // already "ended" in local time by the evening. Query from the start of yesterday and
        // filter by calendar date instead; timed events still drop out once their end passed.
        val windowStart = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = ContentUris.appendId(
            ContentUris.appendId(CalendarContract.Instances.CONTENT_URI.buildUpon(), windowStart),
            windowEnd,
        ).build()

        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_ID,
        )

        val results = mutableListOf<CalendarEventEntry>()
        context.contentResolver.query(
            uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC"
        )?.use { cursor ->
            val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val beginIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val endIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val allDayIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
            val calendarIdIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)
            while (cursor.moveToNext() && results.size < limit) {
                if (cursor.getLong(calendarIdIndex) !in selectedIds) continue
                val entry = resolveEvent(
                    title = cursor.getString(titleIndex) ?: "(untitled)",
                    begin = cursor.getLong(beginIndex),
                    end = cursor.getLong(endIndex),
                    allDay = cursor.getInt(allDayIndex) != 0,
                    now = now,
                    zone = zone,
                ) ?: continue
                results.add(entry)
            }
        }
        return results
    }

    /**
     * Maps a raw instance to an entry, or null if it is over or beyond tomorrow. All-day
     * instances are UTC-midnight to UTC-midnight (end exclusive) whatever the device zone, so
     * their dates are read in UTC and compared with the local date; timed ones use real instants.
     */
    internal fun resolveEvent(
        title: String,
        begin: Long,
        end: Long,
        allDay: Boolean,
        now: Long,
        zone: ZoneId,
    ): CalendarEventEntry? {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        if (!allDay) {
            if (end < now) return null
            val date = Instant.ofEpochMilli(begin).atZone(zone).toLocalDate()
            return CalendarEventEntry(title, begin, false, date)
        }
        val first = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate()
        val lastExclusive = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate()
        if (!lastExclusive.isAfter(today)) return null
        if (first.isAfter(today.plusDays(1))) return null
        val date = if (first.isBefore(today)) today else first
        return CalendarEventEntry(title, begin, true, date)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
