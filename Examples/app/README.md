# Altertable Example App

An Android Compose app demonstrating how to integrate the Altertable Kotlin SDK into a signup funnel.

## What it demonstrates

- Initializing the SDK via `AltertableAndroid.setup()` in `Application.onCreate()`
- Tracking funnel progression with `track(event, properties)`
- Identifying a user on form submission with `identify(userId, traits)`
- Aliasing user IDs with `alias(newUserId)`
- Resetting identity on logout with `reset()`
- Automatic screen view tracking via `ActivityScreenTracker`

## Prerequisites

### 1. Android SDK

Install via [Android Studio](https://developer.android.com/studio) or the command-line tools:

```bash
brew install --cask android-commandlinetools
yes | sdkmanager --sdk_root="$HOME/Library/Android/sdk" --licenses
sdkmanager --sdk_root="$HOME/Library/Android/sdk" "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

### 2. Point Gradle at the SDK (one-time)

```bash
echo "sdk.dir=$HOME/Library/Android/sdk" >> local.properties
```

### 3. Add your API key (one-time)

```bash
echo "altertable.api.key=pk_..." >> local.properties
```

### 4. Create and start an emulator (one-time setup)

```bash
# Install emulator + system image
sdkmanager --sdk_root="$HOME/Library/Android/sdk" \
  "emulator" "cmdline-tools;latest" \
  "system-images;android-35;google_apis;arm64-v8a"

# Create AVD
"$HOME/Library/Android/sdk/cmdline-tools/latest/bin/avdmanager" create avd \
  --name "Pixel_API35" \
  --package "system-images;android-35;google_apis;arm64-v8a" \
  --device "pixel_6"
```

Start the emulator (keep this running in a separate terminal):

```bash
$HOME/Library/Android/sdk/emulator/emulator -avd Pixel_API35 -no-audio
```

> Use the `google_apis` image, not `google_play`. The `google_apis` image allows system-level CA certificate injection required for Proxyman.

## Running the example

From the repo root:

```bash
./gradlew :example-app:installDebug
$HOME/Library/Android/sdk/platform-tools/adb shell am start -n com.altertable.example/.MainActivity
```

## Inspecting network traffic with Proxyman

To see the events being sent to `https://api.altertable.ai` in [Proxyman](https://proxyman.io):

### Step 1 — Configure the emulator (one-time)

1. Open **Proxyman** on your Mac.
2. In the menu bar, go to **Certificate > Install Certificate on Android / iOS**.
3. Select the **Android Emulator** tab.
4. Click **Override All Emulators**. This does two things automatically:
   - Sets the HTTP proxy on the emulator to route traffic through Proxyman
   - Injects the Proxyman CA certificate at the system level so HTTPS is decrypted

### Step 2 — Relaunch the app

The app must be force-stopped and relaunched after the override for the proxy to take effect:

```bash
$HOME/Library/Android/sdk/platform-tools/adb shell am force-stop com.altertable.example
$HOME/Library/Android/sdk/platform-tools/adb shell am start -n com.altertable.example/.MainActivity
```

### Step 3 — Generate events

Navigate through the signup funnel. Each step fires events:

| Action | Events sent |
| :----- | :---------- |
| App launch | `screen` — Personal Info |
| Continue on step 1 | `track` — step completed, `screen` — Create Account |
| Continue on step 2 | `track` — step completed, `screen` — Choose Plan |
| Complete Signup | `identify` — user ID + traits, `alias`, `track` — `signup_started` |
| Get Started | `screen` — dashboard |

### Step 4 — Check Proxyman

In Proxyman, look for `api.altertable.ai` in the left sidebar. You will see POST requests to `/track` and `/identify` with the full JSON body decrypted.

> `network_security_config.xml` in this app already trusts user-installed CAs in debug builds, which is required for Proxyman's HTTPS interception to work.

## Project structure

```
Examples/app/
├── AltertableExampleApp.kt   # Application entry point, SDK initialization
├── MainActivity.kt           # Compose UI with signup funnel and analytics calls
└── README.md                 # This file
```
