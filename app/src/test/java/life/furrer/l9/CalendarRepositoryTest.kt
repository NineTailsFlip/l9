package life.furrer.l9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class CalendarRepositoryTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `permission is denied by default in a fresh test environment`() {
        assertFalse(CalendarRepository.hasPermission(context))
    }

    @Test
    fun `entry count defaults to 3 and round trips`() {
        assertEquals(3, CalendarRepository.getEntryCount(context))
        CalendarRepository.setEntryCount(context, 5)
        assertEquals(5, CalendarRepository.getEntryCount(context))
    }

    @Test
    fun `selected calendar ids are null until the user customizes them`() {
        // Null vs empty-set is a meaningful distinction here: null means "default to all
        // calendars", set by the app (see CalendarRepository's own doc comment); an explicitly
        // empty set means the user deselected everything.
        assertNull(CalendarRepository.getSelectedCalendarIds(context))
    }

    @Test
    fun `an explicitly empty calendar selection round trips as empty, not null`() {
        CalendarRepository.setSelectedCalendarIds(context, emptySet())
        assertEquals(emptySet<Long>(), CalendarRepository.getSelectedCalendarIds(context))
    }

    @Test
    fun `a non-empty calendar selection round trips`() {
        CalendarRepository.setSelectedCalendarIds(context, setOf(1L, 2L, 42L))
        assertEquals(setOf(1L, 2L, 42L), CalendarRepository.getSelectedCalendarIds(context))
    }

    @Test
    fun `queryUpcomingEvents returns nothing without calendar permission`() {
        assertTrue(CalendarRepository.queryUpcomingEvents(context, 5).isEmpty())
    }

    @Test
    fun `queryUpcomingEvents returns nothing when the limit is zero or negative`() {
        assertTrue(CalendarRepository.queryUpcomingEvents(context, 0).isEmpty())
        assertTrue(CalendarRepository.queryUpcomingEvents(context, -1).isEmpty())
    }

    @Test
    fun `queryCalendars returns nothing without calendar permission`() {
        assertTrue(CalendarRepository.queryCalendars(context).isEmpty())
    }

    @Test
    fun `hasPermission reflects a granted READ_CALENDAR permission`() {
        shadowOf(context).grantPermissions(android.Manifest.permission.READ_CALENDAR)
        assertTrue(CalendarRepository.hasPermission(context))
    }

    @Test
    fun `queryUpcomingEvents does not crash when permission is granted but no calendar provider is configured`() {
        shadowOf(context).grantPermissions(android.Manifest.permission.READ_CALENDAR)
        // No calendar content provider is registered in this test environment, so this exercises
        // the "permission granted, but nothing to query" path without a real calendar app.
        assertTrue(CalendarRepository.queryCalendars(context).isEmpty())
        assertTrue(CalendarRepository.queryUpcomingEvents(context, 5).isEmpty())
    }
}

class CalendarRepositoryAllDayTest {
    private val zurich = java.time.ZoneId.of("Europe/Zurich")
    private val la = java.time.ZoneId.of("America/Los_Angeles")

    private fun utc(y: Int, m: Int, d: Int) =
        java.time.LocalDate.of(y, m, d).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun local(zone: java.time.ZoneId, y: Int, m: Int, d: Int, h: Int, min: Int = 0) =
        java.time.ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    private fun allDay(zone: java.time.ZoneId, now: Long, fromDay: Int, toDayExclusive: Int) =
        CalendarRepository.resolveEvent("e", utc(2026, 7, fromDay), utc(2026, 7, toDayExclusive), true, now, zone)

    @Test
    fun `yesterday's all-day event is gone at 01-30 in Zurich`() {
        val now = local(zurich, 2026, 7, 11, 1, 30)
        assertNull(allDay(zurich, now, 10, 11))
    }

    @Test
    fun `today's all-day event is shown for the whole local day in Zurich`() {
        val ev = allDay(zurich, local(zurich, 2026, 7, 11, 23, 30), 11, 12)!!
        assertEquals(java.time.LocalDate.of(2026, 7, 11), ev.date)
    }

    @Test
    fun `tomorrow's all-day event is tomorrow in the evening in Los Angeles`() {
        val now = local(la, 2026, 7, 11, 20)
        val ev = allDay(la, now, 12, 13)!!
        assertEquals(java.time.LocalDate.of(2026, 7, 12), ev.date)
    }

    @Test
    fun `today's all-day event is still shown in the evening in Los Angeles`() {
        val now = local(la, 2026, 7, 11, 20)
        val ev = allDay(la, now, 11, 12)!!
        assertEquals(java.time.LocalDate.of(2026, 7, 11), ev.date)
    }

    @Test
    fun `all-day event ending at UTC midnight today is over in Los Angeles`() {
        // 17:30 local on the 11th is already the 12th in UTC, but the local date is still the 11th.
        val now = local(la, 2026, 7, 11, 17, 30)
        assertNull(allDay(la, now, 10, 11))
    }

    @Test
    fun `all-day event two days ahead is not shown`() {
        assertNull(allDay(la, local(la, 2026, 7, 11, 20), 13, 14))
        assertNull(allDay(zurich, local(zurich, 2026, 7, 11, 12), 13, 14))
    }

    @Test
    fun `ongoing multi-day all-day event shows as today until its exclusive end`() {
        val zone = zurich
        val during = allDay(zone, local(zone, 2026, 7, 11, 12), 9, 13)!!
        assertEquals(java.time.LocalDate.of(2026, 7, 11), during.date)
        assertNull(allDay(zone, local(zone, 2026, 7, 13, 0, 30), 9, 13))
        assertNotNull(allDay(zone, local(zone, 2026, 7, 12, 23, 59), 9, 13))
    }

    @Test
    fun `timed events keep real instants`() {
        val now = local(zurich, 2026, 7, 11, 12)
        val begin = local(zurich, 2026, 7, 12, 9)
        val ev = CalendarRepository.resolveEvent("t", begin, begin + 3_600_000, false, now, zurich)!!
        assertEquals(java.time.LocalDate.of(2026, 7, 12), ev.date)
        assertNull(CalendarRepository.resolveEvent("t", now - 7_200_000, now - 1, false, now, zurich))
    }
}
