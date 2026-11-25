package life.furrer.l9

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log

/** One row of the Recents screen: a task the user can return to or close. */
data class RecentTask(val taskId: Int, val packageName: String, val label: String, val baseIntent: Intent?)

/**
 * The phone's recent tasks, for [RecentsActivity]. On the thor2 image L9 is the system's recents
 * component (config_recentsComponentName), which is what lets it see every app's tasks
 * ([ActivityManager.getRecentTasks] answers the recents component in full) and holds
 * START_TASKS_FROM_RECENTS and REMOVE_TASKS, both granted to the recents package. Opening and
 * closing a task go through IActivityTaskManager, which has no SDK surface; the image allows L9
 * those calls (sysconfig hidden-api-whitelisted-app). Anywhere else — another launcher setup, an
 * emulator — every call here degrades instead of throwing: the list holds only what Android lets
 * an ordinary app see, opening falls back to moveTaskToFront or the task's own intent, and
 * closing reports that it couldn't.
 */
// IActivityTaskManager is the only way to open a task from Recents or close one; see above.
@SuppressLint("PrivateApi")
object RecentTasks {
    private const val TAG = "L9Recents"
    private const val MAX_TASKS = 30

    fun load(context: Context): List<RecentTask> {
        val am = context.getSystemService(ActivityManager::class.java) ?: return emptyList()
        @Suppress("DEPRECATION")
        val infos = runCatching {
            am.getRecentTasks(MAX_TASKS, ActivityManager.RECENT_IGNORE_UNAVAILABLE)
        }.getOrElse {
            Log.w(TAG, "getRecentTasks failed", it)
            return emptyList()
        }
        val pm = context.packageManager
        return infos.mapNotNull { info ->
            val component = info.baseActivity ?: info.baseIntent?.component ?: return@mapNotNull null
            val appLabel = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(component.packageName, 0)).toString()
            }.getOrDefault(component.packageName)
            RecentTask(
                taskId = info.taskId,
                packageName = component.packageName,
                label = taskLabel(appLabel, info.taskDescription?.label),
                baseIntent = info.baseIntent,
            )
        }.let { filter(it, context.packageName, homePackages(context)) }
    }

    /** Leaves out L9 itself (home and this screen) and any other home app's task: Recents lists
     *  the apps you switch between, not the screen you switch from. */
    internal fun filter(tasks: List<RecentTask>, ownPackage: String, homePackages: Set<String>): List<RecentTask> =
        tasks.filter { it.packageName != ownPackage && it.packageName !in homePackages }

    /** The app's name, with the task's own title after it when the app gives one that says more
     *  (two Settings tasks, a browser tab) — never the title alone, which may not name the app. */
    internal fun taskLabel(appLabel: String, taskTitle: CharSequence?): String {
        val title = taskTitle?.toString()?.trim().orEmpty()
        return if (title.isEmpty() || title.equals(appLabel, ignoreCase = true)) appLabel else "$appLabel · $title"
    }

    private fun homePackages(context: Context): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.queryIntentActivities(home, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    }

    /** Brings [task] back the way the stock Recents does, falling back to moveTaskToFront and
     *  then to a fresh start of its intent. false only if all three failed. */
    fun open(context: Context, task: RecentTask): Boolean {
        val fromRecents = runCatching {
            val service = taskService()
            val method = service.javaClass.getMethod("startActivityFromRecents", Int::class.javaPrimitiveType, Bundle::class.java)
            method.invoke(service, task.taskId, null) as Int
        }
        // ActivityManager.START_SUCCESS (0) and the other non-negative codes all mean "started or
        // brought to front"; negative codes are failures.
        if ((fromRecents.getOrNull() ?: -1) >= 0) return true
        fromRecents.exceptionOrNull()?.let { Log.w(TAG, "startActivityFromRecents failed", it) }

        // moveTaskToFront returns normally even for a task it can't bring back (one restored from
        // disk with no live activity) — there's no way to tell from here, so it counts as done.
        val am = context.getSystemService(ActivityManager::class.java)
        if (am != null && runCatching { am.moveTaskToFront(task.taskId, 0) }.isSuccess) return true
        val intent = task.baseIntent ?: return false
        return runCatching {
            context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }

    /** Removes [task] from Recents and stops its activities. false if the system refused. */
    fun close(task: RecentTask): Boolean = runCatching {
        val service = taskService()
        val method = service.javaClass.getMethod("removeTask", Int::class.javaPrimitiveType)
        method.invoke(service, task.taskId) as Boolean
    }.getOrElse {
        Log.w(TAG, "removeTask failed", it)
        false
    }

    private fun taskService(): Any =
        Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)!!
}
