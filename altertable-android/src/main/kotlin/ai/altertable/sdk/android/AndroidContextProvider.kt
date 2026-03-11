package ai.altertable.sdk.android

import ai.altertable.sdk.ContextProperties
import ai.altertable.sdk.ContextProvider
import ai.altertable.sdk.AltertableInternal
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * Android-specific context provider.
 *
 * Supplies device and app properties from [Build], [PackageManager], and display metrics.
 */
@OptIn(AltertableInternal::class)
public class AndroidContextProvider(
    private val applicationContext: Context,
) : ContextProvider {

    companion object {
        private const val MIN_TABLET_WIDTH_DP = 600
    }

    private val packageInfo: android.content.pm.PackageInfo? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                applicationContext.packageManager.getPackageInfo(applicationContext.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    override fun getSystemProperties(): Map<String, Any> =
        buildMap {
            put(ContextProperties.LIB, ContextProperties.LIBRARY_NAME)
            put(ContextProperties.LIB_VERSION, ContextProperties.LIBRARY_VERSION)
            put(ContextProperties.APP_NAME, appName())
            put(ContextProperties.APP_VERSION, appVersion())
            put(ContextProperties.APP_BUILD, appBuild())
            put(ContextProperties.APP_NAMESPACE, applicationContext.packageName)
            put(ContextProperties.OS, "Android")
            put(ContextProperties.OS_VERSION, Build.VERSION.RELEASE)
            put(ContextProperties.DEVICE_MANUFACTURER, Build.MANUFACTURER)
            put(ContextProperties.DEVICE_MODEL, Build.MODEL)
            put(ContextProperties.DEVICE_NAME, "${Build.MANUFACTURER} ${Build.MODEL}")
            put(ContextProperties.DEVICE_TYPE, deviceType())
            viewport()?.let { put(ContextProperties.VIEWPORT, it) }
        }

    private fun appName(): String =
        try {
            val appInfo = applicationContext.packageManager.getApplicationInfo(applicationContext.packageName, 0)
            (applicationContext.packageManager.getApplicationLabel(appInfo) as? String) ?: "unknown"
        } catch (_: PackageManager.NameNotFoundException) {
            "unknown"
        }

    private fun appVersion(): String =
        packageInfo?.versionName ?: "unknown"

    private fun appBuild(): String =
        packageInfo?.let { pkgInfo ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toString()
            }
        } ?: "unknown"

    private fun deviceType(): String =
        when {
            applicationContext.resources.configuration.smallestScreenWidthDp >= MIN_TABLET_WIDTH_DP -> "Tablet"
            else -> "Mobile"
        }

    private fun viewport(): String? =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val windowManager = applicationContext.getSystemService(WindowManager::class.java)
                val bounds = windowManager.currentWindowMetrics.bounds
                "${bounds.width()}x${bounds.height()}"
            } else {
                @Suppress("DEPRECATION")
                val windowManager = applicationContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                @Suppress("DEPRECATION")
                val metrics = DisplayMetrics().also { windowManager?.defaultDisplay?.getMetrics(it) }
                "${metrics.widthPixels}x${metrics.heightPixels}"
            }
        } catch (_: Exception) {
            null
        }
}
