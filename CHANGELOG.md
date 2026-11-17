# Changelog

All notable changes to this project will be documented in this file.

## [0.2.0](https://github.com/altertable-ai/altertable-kotlin/compare/v0.1.0...v0.2.0) (2026-11-17)


### Features

* add audience measurement mode ([#29](https://github.com/altertable-ai/altertable-kotlin/issues/29)) ([a9ba5d5](https://github.com/altertable-ai/altertable-kotlin/commit/a9ba5d58364f7d47153dde524899eb4cb8c37c0e))
* bootstrap Kotlin SDK with v0.9.0 specs ([#2](https://github.com/altertable-ai/altertable-kotlin/issues/2)) ([bf94b59](https://github.com/altertable-ai/altertable-kotlin/commit/bf94b599382d6be357141e56293e64bfa6e9e2ed))
* **core:** add HTTP event batching and configurable flush ([#18](https://github.com/altertable-ai/altertable-kotlin/issues/18)) ([d3b1bb7](https://github.com/altertable-ai/altertable-kotlin/commit/d3b1bb7826d977ca0aa5bb5fc36d2de5e009f420))
* deprecate analytics environment configuration ([#30](https://github.com/altertable-ai/altertable-kotlin/issues/30)) ([48e96ae](https://github.com/altertable-ai/altertable-kotlin/commit/48e96aeb3c50440ebef8bcdb100be1d818ad809b))
* Implement Core, Identity, Session, and Storage ([#3](https://github.com/altertable-ai/altertable-kotlin/issues/3)) ([d929a07](https://github.com/altertable-ai/altertable-kotlin/commit/d929a071106744b1ebb8be8fb8f52cb36da16ee1))
* Implement Queue, Transport, and Endpoints ([#4](https://github.com/altertable-ai/altertable-kotlin/issues/4)) ([6d8a554](https://github.com/altertable-ai/altertable-kotlin/commit/6d8a554a15e783fae4167ebe8fa902a165e6b5aa))
* Phase 14 & 15 - Example App and SDK Release 0.1.0 ([#5](https://github.com/altertable-ai/altertable-kotlin/issues/5)) ([e2ef3bd](https://github.com/altertable-ai/altertable-kotlin/commit/e2ef3bd3621e2da3d4546fc5c467d028dd7a2f67))


### Bug Fixes

* **batch:** preserve FIFO chunk ordering ([#27](https://github.com/altertable-ai/altertable-kotlin/issues/27)) ([8ad4b41](https://github.com/altertable-ai/altertable-kotlin/commit/8ad4b412836ae5ce9741e038888f7decfbf8b04c))
* **ci:** use supported release-please release type ([#16](https://github.com/altertable-ai/altertable-kotlin/issues/16)) ([75ada68](https://github.com/altertable-ai/altertable-kotlin/commit/75ada68409d48bdc3beca170c331b6c39484eeb3))
* render bug report runtime label ([#23](https://github.com/altertable-ai/altertable-kotlin/issues/23)) ([dd6245d](https://github.com/altertable-ai/altertable-kotlin/commit/dd6245d7e3a28068a17b8ef9737736278e5f516e))
* send prefixed built-in properties ([#14](https://github.com/altertable-ai/altertable-kotlin/issues/14)) ([48b8777](https://github.com/altertable-ai/altertable-kotlin/commit/48b877797b94be1dbf5f951176931eda8990f720))


### Documentation

* add comprehensive KDoc comments to public APIs ([#8](https://github.com/altertable-ai/altertable-kotlin/issues/8)) ([8a88b11](https://github.com/altertable-ai/altertable-kotlin/commit/8a88b11b97d82e64af3821377544678e89cf1dca))
* **kotlin:** sync community files ([#25](https://github.com/altertable-ai/altertable-kotlin/issues/25)) ([304a171](https://github.com/altertable-ai/altertable-kotlin/commit/304a171a02eb497e0588eee0d72c7441932d0636))
* Polish README.md to match JS and Swift SDKs ([#6](https://github.com/altertable-ai/altertable-kotlin/issues/6)) ([156fc6e](https://github.com/altertable-ai/altertable-kotlin/commit/156fc6e5c920c570f8d2d856626983f560b7955f))

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
