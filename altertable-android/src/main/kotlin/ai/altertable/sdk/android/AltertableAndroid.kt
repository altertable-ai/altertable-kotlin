package ai.altertable.sdk.android

import ai.altertable.sdk.Altertable
import ai.altertable.sdk.AltertableConfig
import ai.altertable.sdk.AltertableConfigBuilder
import ai.altertable.sdk.AltertableInternal
import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Android-specific Altertable SDK setup.
 *
 * Initializes the shared [Altertable] instance with:
 * - [AndroidContextProvider] for device and app properties
 * - [SharedPreferencesStorage] for persistence
 * - [ProcessLifecycleObserver] for flush on app background (when [AltertableConfig.tracking.flushOnBackground] is true)
 * - [ActivityScreenTracker] for automatic screen view tracking (when [AltertableConfig.tracking.captureScreenViews] is true)
 *
 * Call [setup] from your [Application.onCreate].
 *
 * @param application The application context.
 * @param config The Altertable configuration.
 * @return The configured [Altertable] instance.
 */
public object AltertableAndroid {

    private var screenTracker: ActivityScreenTracker? = null

    private var lifecycleObserver: ProcessLifecycleObserver? = null
    
    private val setupLock = Any()

    /**
     * Initializes the Altertable SDK for Android using a DSL builder.
     *
     * @param application The application context (use [Application.applicationContext]).
     * @param block The configuration DSL block.
     * @return The configured [Altertable] instance.
     */
    @OptIn(AltertableInternal::class)
    public inline fun setup(
        application: Application,
        block: AltertableConfigBuilder.() -> Unit,
    ): Altertable {
        val config = AltertableConfigBuilder().apply(block).build()
        return setup(application, config)
    }

    /**
     * Initializes the Altertable SDK for Android.
     *
     * @param application The application context (use [Application.applicationContext]).
     * @param config The Altertable configuration.
     * @return The configured [Altertable] instance.
     */
    public fun setup(
        application: Application,
        config: AltertableConfig,
    ): Altertable {
        synchronized(setupLock) {
            unregister(application) // clean up any previous registration
            val storage = SharedPreferencesStorage.create(application)
            val contextProvider = AndroidContextProvider(application.applicationContext)
            // Use AndroidLogger as default if debug is enabled and no logger is provided
            val androidConfig = if (config.debug && config.logger == null) {
                @OptIn(AltertableInternal::class)
                AltertableConfigBuilder().from(config).apply {
                    logger = AndroidLogger
                }.build()
            } else {
                config
            }
            @OptIn(AltertableInternal::class)
            val instance = Altertable.setup(androidConfig, storage, contextProvider)

            if (config.tracking.flushOnBackground) {
                lifecycleObserver = ProcessLifecycleObserver(instance).also { observer ->
                    ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
                }
            }

            if (config.tracking.captureScreenViews) {
                screenTracker = ActivityScreenTracker(instance).also { tracker ->
                    application.registerActivityLifecycleCallbacks(tracker)
                }
            }

            return instance
        }
    }

    internal fun unregister(application: Application) {
        lifecycleObserver?.let { ProcessLifecycleOwner.get().lifecycle.removeObserver(it) }
        lifecycleObserver = null
        screenTracker?.let { application.unregisterActivityLifecycleCallbacks(it) }
        screenTracker = null
    }
}
