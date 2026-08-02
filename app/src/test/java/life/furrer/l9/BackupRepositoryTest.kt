package life.furrer.l9

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {

    private val context = RuntimeEnvironment.getApplication()

    private fun fileUri(name: String): Uri = Uri.fromFile(File(context.cacheDir, name))

    @Test
    fun `exported settings round trip through import, restoring every preference type`() {
        AppRepository.setSlotCount(context, 6)
        AppRepository.setShowIndices(context, false)
        AppRepository.setAlignment(context, ListAlignment.RIGHT)
        SpeedDialRepository.setContact(context, 1, SpeedDialContact("Alice", "12345"))
        CalendarRepository.setSelectedCalendarIds(context, setOf(10L, 20L))

        val uri = fileUri("backup.json")
        assertTrue(BackupRepository.exportTo(context, uri))

        // Wipe every preference the export just captured, to prove import (not leftover state)
        // is what restores them.
        context.getSharedPreferences("l9", 0).edit().clear().apply()

        assertTrue(BackupRepository.importFrom(context, uri))

        assertEquals(6, AppRepository.getSlotCount(context))
        assertFalse(AppRepository.getShowIndices(context))
        assertEquals(ListAlignment.RIGHT, AppRepository.getAlignment(context))
        assertEquals(
            mapOf(1 to SpeedDialContact("Alice", "12345")),
            SpeedDialRepository.loadContacts(context),
        )
        assertEquals(setOf(10L, 20L), CalendarRepository.getSelectedCalendarIds(context))
    }

    @Test
    fun `import replaces existing preferences rather than merging with them`() {
        AppRepository.setSlotCount(context, 5)
        val uri = fileUri("backup.json")
        assertTrue(BackupRepository.exportTo(context, uri))

        // Change state after export but before import — import should overwrite this, not merge.
        AppRepository.setSlotCount(context, 9)
        AppRepository.setShowIndices(context, false)

        assertTrue(BackupRepository.importFrom(context, uri))

        assertEquals(5, AppRepository.getSlotCount(context))
        // showIndices wasn't part of the exported snapshot's non-default state at export time —
        // clear() during import means it falls back to its default (true) rather than keeping
        // the post-export change of false.
        assertTrue(AppRepository.getShowIndices(context))
    }

    @Test
    fun `import rejects a file that is not an L9 backup`() {
        val file = File(context.cacheDir, "not-l9.json")
        file.writeText("""{"app":"some-other-app","version":1,"preferences":{}}""")

        AppRepository.setSlotCount(context, 5)
        assertFalse(BackupRepository.importFrom(context, Uri.fromFile(file)))
        // Rejected import must leave existing settings untouched.
        assertEquals(5, AppRepository.getSlotCount(context))
    }

    @Test
    fun `import rejects malformed JSON without crashing`() {
        val file = File(context.cacheDir, "garbage.json")
        file.writeText("not json at all")

        assertFalse(BackupRepository.importFrom(context, Uri.fromFile(file)))
    }

    @Test
    fun `import of a missing file fails gracefully`() {
        val uri = fileUri("does-not-exist.json")
        assertFalse(BackupRepository.importFrom(context, uri))
    }
}
