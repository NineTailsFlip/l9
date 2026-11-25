package life.furrer.l9

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecentTasksTest {

    private fun task(id: Int, pkg: String, label: String = pkg) = RecentTask(id, pkg, label, null)

    @Test
    fun filterDropsL9AndHomeApps() {
        val tasks = listOf(task(1, "a.app"), task(2, "io.github.ninetailsflip.l9"), task(3, "other.home"), task(4, "b.app"))
        val kept = RecentTasks.filter(tasks, "io.github.ninetailsflip.l9", setOf("other.home"))
        assertEquals(listOf(1, 4), kept.map { it.taskId })
    }

    @Test
    fun labelAddsATitleOnlyWhenItSaysMore() {
        assertEquals("Settings", RecentTasks.taskLabel("Settings", null))
        assertEquals("Settings", RecentTasks.taskLabel("Settings", "  settings "))
        assertEquals("Settings · Wi-Fi", RecentTasks.taskLabel("Settings", "Wi-Fi"))
    }

    @Test
    fun closeReportsFailureInsteadOfThrowing() {
        // No IActivityTaskManager.removeTask outside a real device: must come back false.
        assertEquals(false, RecentTasks.close(task(7, "a.app")))
    }

    @Test
    fun screenOpensEmptyAndBackCloses() {
        val activity = Robolectric.buildActivity(RecentsActivity::class.java).setup().get()
        activity.onKeyDown(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
        activity.onKeyDown(KeyEvent.KEYCODE_BACK, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        assertTrue(activity.isFinishing)
    }
}
