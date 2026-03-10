# Altertable Kotlin SDK

[![Maven Central](https://img.shields.io/maven-central/v/ai.altertable.sdk/altertable-kotlin)](https://central.sonatype.com/artifact/ai.altertable.sdk/altertable-kotlin)
[![CI Status](https://github.com/altertable-ai/altertable-kotlin/actions/workflows/ci.yml/badge.svg)](https://github.com/altertable-ai/altertable-kotlin/actions/workflows/ci.yml)

The official, production-grade Kotlin SDK for Altertable Product Analytics.

## Installation

```kotlin
implementation("ai.altertable.sdk:altertable-kotlin:0.1.0")
```

## Quick Start

```kotlin
import ai.altertable.sdk.AltertableClient
import ai.altertable.sdk.AltertableConfig

// Initialize the client
val config = AltertableConfig(apiKey = "your-api-key")
AltertableClient.setup(config)

// Track an event
AltertableClient.shared().track(
    event = "Button Clicked",
    properties = mapOf("button_id" to "signup")
)
```

## API Reference

### `setup(config: AltertableConfig): AltertableClient`

Initializes the singleton client with the provided configuration.

```kotlin
val config = AltertableConfig(apiKey = "your-api-key")
AltertableClient.setup(config)
```

### `shared(): AltertableClient`

Returns the initialized singleton client instance.

```kotlin
val client = AltertableClient.shared()
```

### `track(event: String, properties: Map<String, Any> = emptyMap())`

Records an event with optional properties.

```kotlin
AltertableClient.shared().track(
    event = "Purchase",
    properties = mapOf("amount" to 29.99)
)
```

### `identify(userId: String)`

Identifies a user with a unique ID.

```kotlin
AltertableClient.shared().identify(userId = "user_123")
```

### `alias(newUserId: String)`

Links a new ID to the current user.

```kotlin
AltertableClient.shared().alias(newUserId = "user_456")
```

### `reset(resetDeviceId: Boolean = false)`

Clears the current session and identity.

```kotlin
AltertableClient.shared().reset(resetDeviceId = true)
```

### `configure(updates: AltertableConfig)`

Updates the configuration after initialization.

```kotlin
val newConfig = AltertableConfig(apiKey = "your-api-key", trackingConsent = TrackingConsentState.GRANTED)
AltertableClient.shared().configure(updates = newConfig)
```

### `getTrackingConsent(): TrackingConsentState`

Returns the current tracking consent state.

```kotlin
val consent = AltertableClient.shared().getTrackingConsent()
```

## Configuration

Initialize with an `AltertableConfig` object to configure the SDK.

| Option | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `apiKey` | `String` | (Required) | Your project API key. |
| `baseUrl` | `String` | `"https://api.altertable.ai"` | The API endpoint URL. |
| `environment` | `String` | `"production"` | Environment name. |
| `trackingConsent` | `TrackingConsentState` | `TrackingConsentState.GRANTED` | Controls tracking consent state. |
| `release` | `String?` | `null` | The release version of your app. |
| `onError` | `((AltertableError) -> Unit)?` | `null` | Callback for SDK errors. |
| `debug` | `Boolean` | `false` | Enables verbose logging. |
| `requestTimeout` | `Long` | `10000L` | Network request timeout in milliseconds. |
| `flushOnBackground` | `Boolean` | `true` | Automatically flush events when app backgrounds. |

## Development Workflow

| Step | Command | Description |
| :--- | :--- | :--- |
| Build | `./gradlew build` | Compiles the project and runs checks. |
| Format | `./gradlew ktlintFormat` | Formats the codebase. |
| Lint | `./gradlew ktlintCheck` | Runs the linter. |

## Testing

Run all tests:
```bash
./gradlew test
```

## Releasing

1. Update the version in `build.gradle.kts`.
2. Commit the changes and create a tag (e.g., `v1.0.0`).
3. Push the tag to GitHub.
4. The release is published via the [GitHub Actions release workflow](https://github.com/altertable-ai/altertable-kotlin/actions).

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Open a Pull Request

## License

See the [LICENSE](LICENSE) file for details.

## Links

- [Website](https://altertable.ai)
- [Documentation](https://altertable.ai/docs)
- [GitHub Repository](https://github.com/altertable-ai/altertable-kotlin)
