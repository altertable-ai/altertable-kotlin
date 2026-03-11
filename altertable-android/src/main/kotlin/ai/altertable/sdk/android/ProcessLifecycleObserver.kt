package ai.altertable.sdk.android

import ai.altertable.sdk.Altertable
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Observes process lifecycle and flushes the Altertable queue when the app moves to background.
 */
internal class ProcessLifecycleObserver(
    private val client: Altertable,
) : DefaultLifecycleObserver {

    override fun onStop(owner: LifecycleOwner) {
        client.flush()
    }
}
