package ai.altertable.sdk

/**
 * Hook for transforming or filtering events before they are queued or sent.
 *
 * Return the modified [Event] to allow the event, or `null` to drop it.
 * Hooks are invoked in order; each receives the result of the previous hook.
 */
public typealias EventInterceptor = (Event) -> Event?

/**
 * Public event type for interceptors.
 *
 * Provides a simplified view of events without exposing serialization details.
 */
public sealed class Event {
    /**
     * Track event with event name and properties.
     */
    public data class Track(
        public val event: String,
        public val properties: Map<String, Any>,
    ) : Event()

    /**
     * Identify event with user ID and traits.
     */
    public data class Identify(
        public val userId: String,
        public val traits: Map<String, Any>,
    ) : Event()

    /**
     * Alias event linking a new user ID.
     */
    public data class Alias(
        public val newUserId: String,
    ) : Event()
}
