package life.furrer.l9

import android.content.Context
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The home screen's clock and agenda follow the system 12h/24h setting. */
object ClockFormat {
    const val PATTERN_24 = "HH:mm"
    const val PATTERN_12 = "h:mm a"

    fun pattern(is24Hour: Boolean): String = if (is24Hour) PATTERN_24 else PATTERN_12

    fun format(millis: Long, is24Hour: Boolean, locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat(pattern(is24Hour), locale).format(Date(millis))

    fun format(context: Context, millis: Long): String =
        format(millis, DateFormat.is24HourFormat(context))
}
