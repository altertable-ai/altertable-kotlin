# Altertable Kotlin SDK

You can use this SDK to send Product Analytics events to Altertable from Kotlin applications.

## Install

```kotlin
implementation("ai.altertable.sdk:altertable-kotlin:0.1.0")
```

## Quick start

```kotlin
import ai.altertable.sdk.AltertableClient
import ai.altertable.sdk.AltertableConfig

val config = AltertableConfig(apiKey = "your-api-key")
AltertableClient.setup(config)

AltertableClient.shared().track(
    event = "Button Clicked",
    properties = mapOf("button_id" to "signup")
)
```

## API reference

### `setup(config: AltertableConfig): AltertableClient`

Initializes the singleton client.

### `shared(): AltertableClient`

Returns the initialized singleton instance.

### `track(event: String, properties: Map<String, Any> = emptyMap())`

Sends an event.

### `identify(userId: String)`

Associates a user identifier with subsequent events.

### `alias(newUserId: String)`

Merges identity from the current user into `newUserId`.

### `reset(resetDeviceId: Boolean = false)`

Clears local identity and session state.

### `configure(updates: AltertableConfig)`

Updates runtime configuration.

### `getTrackingConsent(): TrackingConsentState`

Returns current tracking consent state.

## Configuration

| Option | Type | Default | Description |
|---|---|---|---|
| `apiKey` | `String` | (required) | Project API key. |
| `baseUrl` | `String` | `"https://api.altertable.ai"` | API endpoint URL. |
| `environment` | `String` | `"production"` | Environment name. |
| `trackingConsent` | `TrackingConsentState` | `GRANTED` | Tracking consent mode. |
| `release` | `String?` | `null` | App release version. |
| `onError` | `((AltertableError) -> Unit)?` | `null` | Error callback. |
| `debug` | `Boolean` | `false` | Enables verbose logs. |
| `requestTimeout` | `Long` | `10000L` | Request timeout in ms. |
| `flushOnBackground` | `Boolean` | `true` | Flushes events when app backgrounds. |

## Development

Prerequisites: JDK 11+ and Gradle wrapper.

```bash
./gradlew dependencies
./gradlew test
./gradlew ktlintCheck
```

## License

See [LICENSE](LICENSE).