# Configuration

`config.yml` controls language selection, storage, claim limits, enabled worlds, and optional integrations. Secrets such as MySQL passwords must not be committed to source control.

On update, LandWard preserves every existing value and only adds newly introduced default keys. Administrator choices and credentials remain untouched.

SQLite is the default. Set `storage.type` to `mysql` and complete the documented
`storage.mysql` fields to use a shared MySQL/MariaDB database. LandWard creates
its `lands`, `claims` and `land_members` tables automatically.

## Claim limits

A player can own one land and may be a member of any number of other lands.
`claims.maximum-chunks-per-land` limits the chunks belonging to that owned land.
Set the value to `-1` to allow unlimited chunks.

## World scope

`claims.enabled-worlds` is an allow-list and `claims.disabled-worlds` is a deny-list.
Leave the allow-list empty for all worlds except explicitly disabled ones. These lists
scope claims, claim protection and map markers, so `enabled-worlds: [world]` keeps
Creative and Skyblock worlds entirely outside LandWard.

## Claim payments

Set `payments.mode` to `free` (default) for no cost or `experience` to charge
raw XP points for every claimed chunk. `payments.experience-points-per-chunk`
defaults to 150. Vault economy support is configured through the same mode
setting. `vault` uses the active Vault economy provider (such as EssentialsX
Economy) and refuses a paid claim when the provider is unavailable.

## Rename cost

Land renaming is free by default. Set rename.experience-level-cost to a positive
integer to charge that many **XP levels** after a rename is confirmed in the anvil
GUI. A player without enough levels cannot confirm the change.

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

## Map integrations

`integrations.bluemap`, `integrations.dynmap`, `integrations.squaremap` and
`integrations.pl3xmap` each have an `enabled` switch. A map plugin is never
required: LandWard detects it at runtime and quietly skips its layer when absent.
