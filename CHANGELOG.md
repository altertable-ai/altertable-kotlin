# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

### Added
- Add lifecycle-fixed audience mode for Kotlin and Android with identity-free payloads, in-memory state and queues, reduced context, and sanitization after event hooks.

### Fixed
- Honor event hooks returning null and prevent explicit flushes from sending events while consent is pending.

## [0.1.0] - 2026-03-10

### Added
- First public release of the Altertable Product Analytics Kotlin SDK.
- Core Event Tracking API (`track`).
- Identity Management API (`identify`, `alias`, `reset`).
- `AltertableConfig` for simplified configuration.
- Jetpack Compose Example App demonstrating complete user journey (Signup Funnel, Event Tracking, Identity Management, State Persistence).

### Changed
- Renamed `MobileConfig` to `AltertableConfig` for consistency across our ecosystem.
