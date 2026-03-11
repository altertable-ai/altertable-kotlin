package ai.altertable.sdk

/**
 * Log severity levels for the Altertable SDK.
 */
public enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

/**
 * Logging callback for the Altertable SDK.
 *
 * Implement this interface to plug in your own logging framework (SLF4J, Timber, etc.)
 * or to customize how SDK debug messages are emitted.
 *
 * @see AltertableConfig.logger
 */
public fun interface AltertableLogger {
    /**
     * Logs a message at the given level.
     *
     * @param level The severity level (debug, info, warn, error).
     * @param message The message to log.
     */
    public fun log(
        level: LogLevel,
        message: String,
    )
}

/**
 * Default logger implementation that uses [println] for output.
 *
 * Suitable for JVM/server environments. For Android, use [AndroidLogger] instead.
 */
internal object DefaultLogger : AltertableLogger {
    override fun log(level: LogLevel, message: String) {
        println("[Altertable/${level.name}] $message")
    }
}
