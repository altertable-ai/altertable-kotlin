# Altertable Kotlin SDK

[![Maven Central](https://img.shields.io/maven-central/v/ai.altertable.sdk/altertable-kotlin)](https://central.sonatype.com/artifact/ai.altertable.sdk/altertable-kotlin)
[![CI Status](https://github.com/altertable-ai/altertable-kotlin/actions/workflows/ci.yml/badge.svg)](https://github.com/altertable-ai/altertable-kotlin/actions/workflows/ci.yml)

The official, production-grade Kotlin SDK for Altertable Product Analytics.

## Requirements

- JDK 17 or higher
- Gradle 8.x (wrapper included)

## Installation

```kotlin
implementation("ai.altertable.sdk:altertable-kotlin:0.1.0")
```

For Android projects, also add the Android module:

```kotlin
implementation("ai.altertable.sdk:altertable-android:0.1.0")
```

## Quick Start

### JVM / Server

```kotlin
import ai.altertable.sdk.Altertable

// Initialize the singleton client using DSL
Altertable.setup {
    apiKey = "your-api-key"
    environment = "production"
}

// Track an event
Altertable.shared?.track(
    event = "Button Clicked",
    properties = mapOf("button_id" to "signup")
)
```

### Android

```kotlin
import ai.altertable.sdk.android.AltertableAndroid
import android.app.Application

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        AltertableAndroid.setup(this) {
            apiKey = "your-api-key"
            environment = "production"
        }
    }
}
```

Then track events anywhere:

```kotlin
import ai.altertable.sdk.Altertable

Altertable.shared?.track(
    event = "Button Clicked",
    properties = mapOf("button_id" to "signup")
)
```

## API Reference

### Initialization

#### `Altertable.setup { }`

Initializes the singleton client using a DSL builder. This is the recommended way to initialize the SDK.

```kotlin
import ai.altertable.sdk.Altertable

Altertable.setup {
    apiKey = "your-api-key"
    environment = "production"
    debug = true
    
    network {
        baseUrl = "https://api.altertable.ai"
        requestTimeout = 10.seconds
        maxRetries = 3
    }
    
    tracking {
        consent = TrackingConsent.GRANTED
        captureScreenViews = true
        flushOnBackground = true
        maxQueueSize = 1000
    }
    
    release = "1.0.0"
    logger = MyCustomLogger()
    beforeSend = listOf { event ->
        // Transform or filter events
        event
    }
}
```

#### `Altertable.shared`

Returns the initialized singleton client instance, or `null` if `setup()` hasn't been called.

```kotlin
val client = Altertable.shared
client?.track("Event")
```

#### `Altertable.create { }`

Creates a new non-singleton client instance. Use this for multi-environment setups, testing, or when embedding the SDK in a library.

```kotlin
val testClient = Altertable.create {
    apiKey = "test-key"
    environment = "test"
}
```

### Event Tracking

#### `track(event: String, properties: Map<String, Any> = emptyMap())`

Records an event with optional properties.

```kotlin
client.track(
    event = "Purchase",
    properties = mapOf("amount" to 29.99, "currency" to "USD")
)
```

#### `screen(name: String, properties: Map<String, Any> = emptyMap())`

Tracks a screen view event. Automatically includes `screen_name` in properties.

```kotlin
client.screen(
    name = "HomeScreen",
    properties = mapOf("section" to "main")
)
```

### User Identity

#### `identify(userId: String, traits: Map<String, Any> = emptyMap())`

Identifies a user with a unique ID and optional traits. Links the current anonymous state to the known user identity.

```kotlin
client.identify(
    userId = "user_123",
    traits = mapOf(
        "email" to "user@example.com",
        "name" to "John Doe"
    )
)
```

#### `updateTraits(traits: Map<String, Any>)`

Updates user traits for the current identified user. Must call `identify()` first.

```kotlin
client.updateTraits(mapOf("plan" to "premium"))
```

#### `alias(newUserId: String)`

Associates a new user ID with the existing user ID. Useful for linking anonymous and identified users.

```kotlin
client.alias(newUserId = "user_456")
```

#### `reset(resetDeviceId: Boolean = false, resetTrackingConsent: Boolean = false)`

Resets the client state, clearing the current user identity and session. Optionally resets the device ID and tracking consent.

```kotlin
// Reset identity and session only
client.reset()

// Also reset device ID
client.reset(resetDeviceId = true)

// Also reset consent to GRANTED
client.reset(resetTrackingConsent = true)
```

### Configuration

#### `configure(block: RuntimeConfigBuilder.() -> Unit)`

Updates the client's configuration dynamically. Only safe-to-change-at-runtime options are available (tracking consent, debug, logger, beforeSend).

```kotlin
client.configure {
    tracking {
        consent = TrackingConsent.GRANTED
    }
    debug = true
}
```

#### `trackingConsent: StateFlow<TrackingConsent>`

Reactive flow of the current tracking consent state. Use this to observe consent changes in Compose or with lifecycle-aware components.

```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState

@Composable
fun ConsentObserver() {
    val consent = client.trackingConsent.collectAsState()
    
    LaunchedEffect(consent.value) {
        when (consent.value) {
            TrackingConsent.GRANTED -> {
                // Consent granted
            }
            TrackingConsent.DENIED -> {
                // Consent denied
            }
            TrackingConsent.PENDING -> {
                // Waiting for consent
            }
            TrackingConsent.DISMISSED -> {
                // Consent dialog dismissed
            }
        }
    }
}
```

#### `errors: SharedFlow<AltertableError>`

Reactive flow of errors that occur during SDK operations.

```kotlin
import androidx.compose.runtime.LaunchedEffect

LaunchedEffect(Unit) {
    client.errors.collect { error ->
        when (error) {
            is AltertableError.Validation -> {
                // Validation error
            }
            is AltertableError.Api -> {
                // API error (HTTP status, error code)
            }
            is AltertableError.Network -> {
                // Network error
            }
        }
    }
}
```

### Super Properties

Super properties are automatically included in all tracked events. Use bracket syntax for fire-and-forget operations, or suspend methods for synchronous access.

```kotlin
// Fire-and-forget (non-coroutine contexts)
client.superProperties["app_version"] = "1.0.0"
client.superProperties["user_type"] = "premium"
client.superProperties.removeAsync("user_type")

// Suspend methods (coroutine contexts)
lifecycleScope.launch {
    client.superProperties.setValue("app_version", "1.0.0")
    val version = client.superProperties.get("app_version")
    client.superProperties.remove("user_type")
    val allProps = client.superProperties.toMap()
}
```

### Flushing Events

#### `flush()`

Force-flushes any pending events in the queue. This is a fire-and-forget operation that does not block the calling thread.

```kotlin
client.flush()
```

#### `awaitFlush()`

Force-flushes any pending events in the queue and suspends until completion. Use this when you need to ensure events are sent before proceeding (e.g., before app termination).

```kotlin
// In a suspend function or coroutine
lifecycleScope.launch {
    client.awaitFlush()
}
```

**Note:** Do not call `awaitFlush()` from the main thread on Android.

### Lifecycle

#### `close()`

Releases resources (coroutine scope, HTTP client, integrations). Call when the client is no longer needed.

```kotlin
client.close()
```

## Android Integration

### Setup

Use `AltertableAndroid.setup()` in your `Application.onCreate()`:

```kotlin
import ai.altertable.sdk.android.AltertableAndroid
import android.app.Application

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        AltertableAndroid.setup(this) {
            apiKey = "your-api-key"
            environment = "production"
            debug = BuildConfig.DEBUG
            
            tracking {
                captureScreenViews = true  // Auto-track Activity screen views
                flushOnBackground = true   // Flush events when app backgrounds
            }
        }
    }
}
```

### Compose Integration

#### `ProvideAltertable` and `LocalAltertable`

Provide the Altertable client to your Compose hierarchy:

```kotlin
import ai.altertable.sdk.Altertable
import ai.altertable.sdk.android.ProvideAltertable
import ai.altertable.sdk.android.rememberAltertable

@Composable
fun MyApp() {
    val client = Altertable.shared ?: return
    
    ProvideAltertable(client) {
        // Your app content
        HomeScreen()
    }
}

@Composable
fun HomeScreen() {
    val client = rememberAltertable()
    // Use client here
}
```

#### Screen Tracking in Compose

Track screen views using the `Modifier.screenView()` extension:

```kotlin
import ai.altertable.sdk.android.screenView

@Composable
fun HomeScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .screenView(name = "Home")
    ) {
        // Screen content
    }
}
```

Or use the `TrackScreenView` composable:

```kotlin
import ai.altertable.sdk.android.TrackScreenView

@Composable
fun HomeScreen() {
    TrackScreenView(name = "Home")
    
    // Screen content
}
```

### Automatic Screen Tracking

When `captureScreenViews` is enabled (default), the SDK automatically tracks screen views when Activities appear. Screen names are extracted by removing the `Activity` suffix from the class name (e.g., `HomeActivity` → `Home`).

To disable automatic tracking:

```kotlin
AltertableAndroid.setup(this) {
    apiKey = "your-api-key"
    tracking {
        captureScreenViews = false
    }
}
```

Then use manual tracking with `client.screen()` or Compose modifiers.

## Tracking Consent

Control event collection based on user consent:

```kotlin
// Start with consent pending
Altertable.setup {
    apiKey = "your-api-key"
    tracking {
        consent = TrackingConsent.PENDING
    }
}

// Events are queued until consent is granted
Altertable.shared?.track("Button Clicked")

// When user grants consent
Altertable.shared?.configure {
    tracking {
        consent = TrackingConsent.GRANTED
    }
}
// Queued events are automatically flushed

// If consent is denied
Altertable.shared?.configure {
    tracking {
        consent = TrackingConsent.DENIED
    }
}
// Queue is cleared, new events are dropped
```

Consent states:
- `GRANTED`: Events sent immediately
- `DENIED`: Events dropped, queue cleared
- `PENDING`: Events queued until consent changes
- `DISMISSED`: Events queued (same as `PENDING`)

## Custom Storage

The Android module provides `SharedPreferencesStorage` by default. For custom storage implementations (e.g., DataStore), implement the `Storage` interface. Note that `Storage` is marked `@AltertableInternal` and is intended for use by platform-specific modules.

```kotlin
import ai.altertable.sdk.Storage
import kotlinx.coroutines.flow.first
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey

@OptIn(AltertableInternal::class)
class DataStoreStorage(
    private val dataStore: DataStore<Preferences>
) : Storage {
    override suspend fun get(key: String): String? {
        return dataStore.data.first()[stringPreferencesKey(key)]
    }
    
    override suspend fun set(key: String, value: String) {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey(key)] = value
        }
    }
    
    override suspend fun remove(key: String) {
        dataStore.edit { prefs ->
            prefs.remove(stringPreferencesKey(key))
        }
    }
    
    override suspend fun migrate(from: String, to: String) {
        val value = get(from)
        if (value != null) {
            set(to, value)
            remove(from)
        }
    }
}
```

## Configuration

Initialize with an `AltertableConfig` object or use the DSL builder. All configuration options:

| Option | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `apiKey` | `String` | (Required) | Your project API key. |
| `environment` | `String` | `"production"` | Environment name (e.g., "production", "staging"). |
| `release` | `String?` | `null` | The release version of your app. |
| `debug` | `Boolean` | `false` | Enables verbose logging. |
| `dispatcher` | `CoroutineDispatcher` | `Dispatchers.IO` | Coroutine dispatcher for async operations. |
| `logger` | `AltertableLogger?` | `null` | Optional logger for SDK events. |
| `integrations` | `List<AltertableIntegration>` | `[]` | Integrations to install on setup. |
| `beforeSend` | `List<EventInterceptor>` | `[]` | Hooks to transform or filter events before sending. Return `null` to drop an event. |

### Network Configuration

| Option | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `network.baseUrl` | `String` | `"https://api.altertable.ai"` | The API endpoint URL. |
| `network.requestTimeout` | `Duration` | `10.seconds` | Network request timeout. |
| `network.maxRetries` | `Int` | `3` | Maximum retry attempts for failed requests (5xx and network errors). |

### Tracking Configuration

| Option | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `tracking.consent` | `TrackingConsent` | `GRANTED` | Initial tracking consent state. |
| `tracking.captureScreenViews` | `Boolean` | `true` | Automatically track screen views on Android (Activity-based). |
| `tracking.flushOnBackground` | `Boolean` | `true` | Automatically flush events when app goes into background (Android only). |
| `tracking.maxQueueSize` | `Int` | `1000` | Maximum events to hold in the queue (older events dropped when exceeded). |

## Example App

The [`Examples/app`](Examples/app) directory contains an Android Compose app demonstrating a full signup funnel with `track`, `identify`, `alias`, and `reset` calls.

```bash
ALTERTABLE_API_KEY=pk_... ./gradlew :example-app:installDebug
```

See [`Examples/app/README.md`](Examples/app/README.md) for setup instructions including Android SDK installation.

## Development Workflow

| Step | Command | Description |
| :--- | :--- | :--- |
| Build | `make build` or `./gradlew build` | Compiles and runs unit tests. |
| Format | `make format` or `./gradlew spotlessApply` | Formats the codebase. |
| Lint | `make lint` or `./gradlew detekt` | Runs the linter. |
| API docs | `make docs` or `./gradlew dokkaHtml` | Generates API documentation. |
| Full check | `make check` or `./gradlew check` | Runs all verification tasks. |

## Testing

Run unit tests:
```bash
./gradlew test
```

Run integration tests (requires [altertable-mock](https://github.com/altertable-ai/altertable-mock)):
```bash
docker compose up -d
./gradlew integrationTest
```

Or use the Makefile: `make integration`

## API Documentation

Generate API documentation:

```bash
./gradlew dokkaHtml
```

Documentation will be available at `altertable/build/dokka/html/index.html` and `altertable-android/build/dokka/html/index.html`.

## Releasing

Releases are automated via [Release Please](https://github.com/googleapis/release-please) and the [GitHub Actions release workflow](https://github.com/altertable-ai/altertable-kotlin/actions). When you change the public API, the ABI validation will catch breaking changes during the build.

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
