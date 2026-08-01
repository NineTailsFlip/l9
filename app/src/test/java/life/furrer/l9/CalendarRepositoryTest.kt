package life.furrer.l9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
