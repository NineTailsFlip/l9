package life.furrer.l9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppEntryTest {

    @Test
    fun `round trips through json`() {
        val entry = AppEntry(
            packageName = "com.example.app",
            activityName = "com.example.app.MainActivity",
            label = "Example",
        )
        val restored = AppEntry.fromJson(entry.toJson())
        assertEquals(entry, restored)
    }

    @Test
    fun `customLabel and hidden are never part of the json - they're resolved from AppRepository's override store instead`() {
        val entry = AppEntry(
            packageName = "com.example.app",
            activityName = "com.example.app.MainActivity",
            label = "Example",
            customLabel = "My Example",
            hidden = true,
        )
        val restored = AppEntry.fromJson(entry.toJson())
        assertNull(restored.customLabel)
        assertFalse(restored.hidden)
    }

    @Test
    fun `displayLabel falls back to label when there is no custom label`() {
        val entry = AppEntry("pkg", "activity", label = "Original")
        assertEquals("Original", entry.displayLabel)
    }

    @Test
    fun `displayLabel prefers the custom label when set`() {
        val entry = AppEntry("pkg", "activity", label = "Original", customLabel = "Renamed")
        assertEquals("Renamed", entry.displayLabel)
    }
}
