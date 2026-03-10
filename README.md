# Altertable Product Analytics Kotlin SDK

The official Kotlin SDK for Altertable Product Analytics.

## Installation

Add the dependency to your `build.gradle.kts` (or `build.gradle`):

```kotlin
dependencies {
    implementation("ai.altertable.sdk:altertable-kotlin:0.1.0")
}
```

## Configuration

Initialize the SDK in your Application class or main entry point using `AltertableConfig`.

```kotlin
import ai.altertable.sdk.AltertableClient
import ai.altertable.sdk.AltertableConfig
import kotlinx.coroutines.launch

// Inside a coroutine scope
val config = AltertableConfig(
    apiKey = "your_api_key_here",
    environment = "production",
    debug = true
)
AltertableClient.configure(config)
```

## Usage Examples

### Tracking Events

Track user interactions using `track`.

```kotlin
// Track a basic event
AltertableClient.track("product_viewed")

// Track an event with properties
AltertableClient.track(
    eventName = "checkout_started",
    properties = mapOf(
        "cart_value" to 120.50,
        "item_count" to 3
    )
)
```

### Identity Management

Identify users to link events across sessions using `identify`.

```kotlin
// Identify a user by ID
AltertableClient.identify("user_12345")

// Identify with user traits/properties
AltertableClient.identify(
    id = "user_12345",
    properties = mapOf(
        "email" to "user@example.com",
        "plan" to "premium"
    )
)
```

### Aliasing Users

Link an anonymous session ID to an authenticated user ID using `alias`.

```kotlin
// Alias the current anonymous user to their new persistent ID
AltertableClient.alias("user_12345")
```

## Example Application

Check out the Jetpack Compose example app in the `Examples/` directory to see a complete user journey (Signup Funnel, Event Tracking, Identity Management, State Persistence).

## License

Apache 2.0
