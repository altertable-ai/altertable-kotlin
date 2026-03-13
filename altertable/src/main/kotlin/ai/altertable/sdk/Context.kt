package ai.altertable.sdk

/**
 * Provides system and device properties for event enrichment.
 *
 * Implement this interface to supply platform-specific context (e.g., Android).
 * The default [DefaultContextProvider] provides JVM-level properties.
 */
@AltertableInternal
public interface ContextProvider {
    /**
     * Returns a map of system properties to merge into track event properties.
     * Keys should match the Altertable API property names (e.g., $lib, app_name, os).
     */
    public fun getSystemProperties(): Map<String, Any>
}

/**
 * Default JVM context provider.
 *
 * Supplies OS and runtime properties. App-specific properties (app_name, app_version)
 * are "unknown" on plain JVM; use [AndroidContextProvider] on Android for full context.
 */
@OptIn(AltertableInternal::class)
@Suppress("FunctionOnlyReturningConstant")
internal object DefaultContextProvider : ContextProvider {
    override fun getSystemProperties(): Map<String, Any> =
        buildMap {
            put(ContextProperties.LIB, ContextProperties.LIBRARY_NAME)
            put(ContextProperties.LIB_VERSION, ContextProperties.LIBRARY_VERSION)
            put(ContextProperties.APP_NAME, appName())
            put(ContextProperties.APP_VERSION, appVersion())
            put(ContextProperties.APP_BUILD, appBuild())
            put(ContextProperties.APP_NAMESPACE, appNamespace())
            put(ContextProperties.OS, System.getProperty("os.name", "unknown"))
            put(ContextProperties.OS_VERSION, System.getProperty("os.version", "unknown"))
            put(ContextProperties.DEVICE_MANUFACTURER, "unknown")
            put(ContextProperties.DEVICE_MODEL, System.getProperty("os.arch", "unknown"))
            put(ContextProperties.DEVICE_NAME, "JVM")
            put(ContextProperties.DEVICE_TYPE, "Server")
            viewport()?.let { put(ContextProperties.VIEWPORT, it) }
        }

    private fun appName(): String = System.getProperty("sun.java.command")?.split(" ")?.firstOrNull() ?: "unknown"

    private fun appVersion(): String = ContextProperties.LIBRARY_VERSION

    private fun appBuild(): String = "unknown"

    private fun appNamespace(): String = "unknown"

    private fun viewport(): String? = null
}
