package ai.altertable.sdk.android

import ai.altertable.sdk.Altertable
import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Tracks screen views when activities are resumed.
 *
 * Skips [android.app.FragmentActivity] subclasses that are typically containers
 * (e.g. NavHost fragments). Tracks the simple class name with "Activity" stripped.
 */
internal class ActivityScreenTracker(
    private val client: Altertable,
) : Application.ActivityLifecycleCallbacks {

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) {
        val screenName = extractScreenName(activity)
        client.screen(screenName)
    }

    override fun onActivityPaused(activity: Activity) = Unit

    private fun extractScreenName(activity: Activity): String {
        var name = activity.javaClass.simpleName
        if (name.endsWith("Activity")) {
            name = name.dropLast("Activity".length)
        }
        return name.ifEmpty { activity.javaClass.simpleName }
    }
}
