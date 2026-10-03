package life.furrer.l9

import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class ClockFormatTest {
    private val utc = TimeZone.getTimeZone("UTC")

    private fun at(hour: Int, minute: Int): Long = (hour * 60L + minute) * 60_000L

    @Test fun patterns() {
        assertEquals("HH:mm", ClockFormat.pattern(true))
        assertEquals("h:mm a", ClockFormat.pattern(false))
    }

    @Test fun formats() {
        val old = TimeZone.getDefault()
        TimeZone.setDefault(utc)
        try {
            assertEquals("15:05", ClockFormat.format(at(15, 5), true, Locale.US))
            assertEquals("3:05 PM", ClockFormat.format(at(15, 5), false, Locale.US))
            assertEquals("12:00 AM", ClockFormat.format(at(0, 0), false, Locale.US))
        } finally {
            TimeZone.setDefault(old)
        }
    }
}
