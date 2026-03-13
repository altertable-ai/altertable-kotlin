# Contributing to Altertable Kotlin SDK

## Development Setup

1. Fork and clone the repository
2. Install dependencies: `./gradlew build`
3. Run tests: `./gradlew test`

## Making Changes

1. Create a branch from `main`
2. Make your changes
3. Add or update tests
4. Run the full check suite: `./gradlew check`
5. Commit using [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `docs:`, etc.)
6. Open a pull request

## Code Style

This project uses `detekt` for linting and `spotless` for formatting. Run `./gradlew detekt spotlessCheck` before committing, or use `./gradlew spotlessApply` to auto-fix formatting issues.

## Tests

- Unit tests are required for all new functionality
- Integration tests run in CI using altertable-mock (no credentials required)
- Run tests locally: `./gradlew test`
- Run integration tests: `docker compose up -d && ./gradlew integrationTest`

## Pull Requests

- Keep PRs focused on a single change
- Update `CHANGELOG.md` under `[Unreleased]` (Release Please will handle this automatically)
- Ensure CI passes before requesting review
- When changing the public API, run `./gradlew updateLegacyAbi` to update binary compatibility reference
