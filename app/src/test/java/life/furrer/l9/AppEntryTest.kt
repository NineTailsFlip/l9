package life.furrer.l9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppEntryTest {

    @Test
    fun `round trips through json including a custom label`() {
        val entry = AppEntry(
            packageName = "com.example.app",
            activityName = "com.example.app.MainActivity",
            label = "Example",
            customLabel = "My Example",
        )
        val restored = AppEntry.fromJson(entry.toJson())
        assertEquals(entry, restored)
    }

    @Test
    fun `round trips through json without a custom label`() {
        val entry = AppEntry(
            packageName = "com.example.app",
            activityName = "com.example.app.MainActivity",
            label = "Example",
        )
        val restored = AppEntry.fromJson(entry.toJson())
        assertEquals(entry, restored)
        assertNull(restored.customLabel)
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

    @Test
    fun `an empty custom label in json is treated as absent`() {
        // AppRepository never writes an empty customLabel (MainActivity.onRenameRequested maps
        // blank input back to null before saving), but fromJson must still be defensive since it
        // also drives core's Backup import path, which trusts whatever the file contains.
        val obj = AppEntry("pkg", "activity", label = "Original").toJson()
        obj.put("customLabel", "")
        val restored = AppEntry.fromJson(obj)
        assertNull(restored.customLabel)
        assertEquals("Original", restored.displayLabel)
    }
}
