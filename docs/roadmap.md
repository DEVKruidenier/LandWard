# Roadmap

This page tracks release follow-up work. Implemented features are documented here
only when they still need broader Paper gameplay verification before the first
stable release.

## Storage migration

The administrator-only, explicit migration workflow is implemented for both
directions:

- SQLite → MySQL/MariaDB
- MySQL/MariaDB → SQLite

The workflow must create a timestamped source backup, validate the target
connection before writing, transfer lands, chunks, members and administrative
claims in one controlled operation, compare source and target record totals, and
leave the currently active backend unchanged when validation fails. It must never
run as an implicit side effect of changing `storage.type`.

## Release update workflow

The GitHub update check exposes **Latest** or **Outdated** in `/claim info`. For
administrators, `/claim update` safely downloads and verifies the release artifact
and stages it for the next server restart; it never hot-replaces a running plugin
JAR. The remaining release task is real-release acceptance testing.

## Broader server compatibility

Paper 26.2 with Java 25 is the current tested runtime. Paper 26.3 is queued for
a clean-environment acceptance run; it must not be advertised until the full
matrix passes. Folia remains explicitly unsupported while LandWard is adapted
for regionised scheduling and tested on Folia. The supported-platform policy is
documented in [Compatibility](compatibility.md).
