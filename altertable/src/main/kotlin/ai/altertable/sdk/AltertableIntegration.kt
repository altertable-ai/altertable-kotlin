package ai.altertable.sdk

import java.io.Closeable

/**
 * Integration interface for extending the Altertable SDK with platform-specific or custom behavior.
 *
 * Integrations are created during [Altertable.setup] and automatically closed during [Altertable.close].
 * Use this for lifecycle hooks, automatic event tracking, or platform-specific features.
 *
 * The [create] method returns a [Closeable] that will be closed when the client is closed,
 * eliminating the need for manual uninstall logic.
 */
public fun interface AltertableIntegration {
    /**
     * Called when the integration is created during client setup.
     *
     * @param client The configured [Altertable] instance.
     * @param config The [AltertableConfig] used for setup.
     * @return A [Closeable] that will be closed when the client is closed.
     *         Use this to unregister listeners, cancel jobs, or release resources.
     */
    public fun create(
        client: Altertable,
        config: AltertableConfig,
    ): Closeable
}
