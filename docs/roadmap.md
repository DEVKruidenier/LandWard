# Roadmap

This page tracks work deliberately deferred from the current test build. Items are
not considered released features until they have automated coverage and an in-game
Paper verification.

## Storage migration

Provide an administrator-only, explicit migration workflow for both directions:

- SQLite → MySQL/MariaDB
- MySQL/MariaDB → SQLite

The workflow must create a timestamped source backup, validate the target
connection before writing, transfer lands, chunks, members and administrative
claims in one controlled operation, compare source and target record totals, and
leave the currently active backend unchanged when validation fails. It must never
run as an implicit side effect of changing `storage.type`.

## Release update workflow

Expose the result of the GitHub update check in `/claim info` as **Latest** or
**Outdated**. For administrators, an available update should offer a safe download
action which verifies the release artifact and stages it for the next server
restart; it must not hot-replace a running plugin JAR.

## Broader server compatibility

Evaluate and document tested Paper releases beyond 26.2, and publish only the
platform/version combinations that have passed the full acceptance suite.
