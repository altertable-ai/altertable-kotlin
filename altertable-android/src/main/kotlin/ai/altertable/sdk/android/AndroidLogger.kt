package ai.altertable.sdk.android

import ai.altertable.sdk.AltertableLogger
import ai.altertable.sdk.LogLevel
import android.util.Log

/**
 * Android logger implementation that uses [android.util.Log] for output.
 *
 * This is the default logger used when setting up the SDK via [AltertableAndroid.setup].
 */
public object AndroidLogger : AltertableLogger {
    private const val TAG = "Altertable"

    override fun log(level: LogLevel, message: String) {
        when (level) {
            LogLevel.DEBUG -> Log.d(TAG, message)
            LogLevel.INFO -> Log.i(TAG, message)
            LogLevel.WARN -> Log.w(TAG, message)
            LogLevel.ERROR -> Log.e(TAG, message)
        }
    }
}
