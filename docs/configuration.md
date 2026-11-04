# Configuration

`config.yml` controls language selection, storage, claim limits, enabled worlds, and optional integrations. Secrets such as MySQL passwords must not be committed to source control.

SQLite is the default. Set `storage.type` to `mysql` and complete the documented
`storage.mysql` fields to use a shared MySQL/MariaDB database. LandWard creates
its `lands`, `claims` and `land_members` tables automatically.

## Claim limits

A player can own one land and may be a member of any number of other lands.
`claims.maximum-chunks-per-land` limits the chunks belonging to that owned land.
Set the value to `-1` to allow unlimited chunks.

## Claim payments

Set `payments.mode` to `free` (default) for no cost or `experience` to charge
raw XP points for every claimed chunk. `payments.experience-points-per-chunk`
defaults to 150. Vault economy support is configured through the same mode
setting. `vault` uses the active Vault economy provider (such as EssentialsX
Economy) and refuses a paid claim when the provider is unavailable.

## Anonymous metrics

LandWard uses [bStats](https://bstats.org/plugin/bukkit/LandWard/35128) to collect
anonymous, aggregated usage statistics. No player names, chat, world data, server
addresses, or exact locations are collected.

Set `metrics.enabled` to `false` to disable metrics for LandWard. Server owners can
also disable bStats globally in `plugins/bStats/config.yml`.

## Update checking

LandWard checks GitHub Releases asynchronously during startup. The check never
blocks Paper's main server thread. Set `updates.enabled` to `false` to disable it.

## Console colors

LandWard uses Paper's Adventure component logger for colored, structured console
output. Set `console.colored-output` to `false` for plain output.

## Plan analytics

With Plan installed, `integrations.plan.enabled: true` publishes a read-only
LandWard panel in Plan. It contains server totals (lands and claimed chunks) and
per-player values (whether the player owns a land, owned chunks and member lands).
No new database table or player tracking is created by LandWard.

## Spawn protection

`integrations.vanilla-spawn-protection.enabled` prevents LandWard claims that
overlap the built-in `server.properties` `spawn-protection` square around the
primary world spawn. Set `spawn-protection: 0` to disable that vanilla setting,
or set the LandWard integration to `false` if the area should be claimable.
