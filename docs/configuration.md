# Configuration

`config.yml` controls language selection, storage, claim limits, enabled worlds, and optional integrations. Secrets such as MySQL passwords must not be committed to source control.

SQLite is the default. MySQL support will use the same storage contract and schema migrations.

## Claim limits

A player can own one land and may be a member of any number of other lands.
`claims.maximum-chunks-per-land` limits the chunks belonging to that owned land.
Set the value to `-1` to allow unlimited chunks.

## Anonymous metrics

LandWard uses [bStats](https://bstats.org/plugin/bukkit/LandWard/35128) to collect
anonymous, aggregated usage statistics. No player names, chat, world data, server
addresses, or exact locations are collected.

Set `metrics.enabled` to `false` to disable metrics for LandWard. Server owners can
also disable bStats globally in `plugins/bStats/config.yml`.

## Update checking

LandWard checks GitHub Releases asynchronously during startup. The check never
blocks Paper's main server thread. Set `updates.enabled` to `false` to disable it.
