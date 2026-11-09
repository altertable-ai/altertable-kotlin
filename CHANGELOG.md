# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

### Added
- Add `TrackingMode.AUDIENCE` for identity-free, memory-only audience measurement.

### Changed
- Deprecate `environment`; it is no longer sent. Use an environment-bound API key, and flush events sent with an unbound key before upgrading.

### Fixed
- Drop events when a `beforeSend` hook returns null.
- Do not send events on `flush()` while consent is not granted.

## [0.1.0] - 2026-03-10

### Added
- First public release of the Altertable Product Analytics Kotlin SDK.
- Core Event Tracking API (`track`).
- Identity Management API (`identify`, `alias`, `reset`).
- `AltertableConfig` for simplified configuration.
- Jetpack Compose Example App demonstrating complete user journey (Signup Funnel, Event Tracking, Identity Management, State Persistence).

### Changed
- Renamed `MobileConfig` to `AltertableConfig` for consistency across our ecosystem.
