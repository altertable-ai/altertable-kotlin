package ai.altertable.sdk

/**
 * Property keys and SDK identifiers used by [ContextProvider] for event enrichment.
 *
 * Use these when implementing custom [ContextProvider] implementations.
 */
@AltertableInternal
public object ContextProperties {
    public const val LIBRARY_NAME: String = "altertable-kotlin"
    public const val LIBRARY_VERSION: String = SDK_VERSION

    public const val LIB: String = "\$lib"
    public const val LIB_VERSION: String = "\$lib_version"
    public const val RELEASE: String = "\$release"
    public const val APP_NAME: String = "app_name"
    public const val APP_VERSION: String = "app_version"
    public const val APP_BUILD: String = "app_build"
    public const val APP_NAMESPACE: String = "app_namespace"
    public const val VIEWPORT: String = "\$viewport"
    public const val SCREEN_NAME: String = "screen_name"
    public const val OS: String = "os"
    public const val OS_VERSION: String = "os_version"
    public const val DEVICE_MANUFACTURER: String = "device_manufacturer"
    public const val DEVICE_MODEL: String = "device_model"
    public const val DEVICE_NAME: String = "device"
    public const val DEVICE_TYPE: String = "device_type"
}
