# Contributing to altertable-kotlin

## Development Setup

1. Fork and clone the repository
2. Install dependencies: `./gradlew assemble`
3. Run tests: `./gradlew test`

## Making Changes

1. Create a branch from `main`
2. Make your changes
3. Add or update tests
4. Run the full check suite: `./gradlew check`
5. Commit using [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `docs:`, etc.)
6. Open a pull request

## Code Style

This project uses `detekt` for linting and `spotless` for formatting. Run `./gradlew detekt spotlessCheck` before committing.

## Tests

- Unit tests are required for all new functionality
- Integration tests run in CI when credentials are available
- Run tests locally: `./gradlew test`

## Pull Requests

- Keep PRs focused on a single change
- Update `CHANGELOG.md` under `[Unreleased]`
- Ensure CI passes before requesting review
