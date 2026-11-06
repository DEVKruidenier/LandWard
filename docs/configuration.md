# Configuration

`config.yml` controls language selection, storage, claim limits, disabled-world exceptions, and optional integrations. Secrets such as MySQL passwords must not be committed to source control.

On update, LandWard adds genuinely new defaults and preserves values changed by the administrator. It also maintains a baseline/backup to recognise prior defaults and remove obsolete layout keys when a new structure replaces them. Review the generated file after major updates.

SQLite is the default. Set `type` to `mysql` and complete the nested `mysql:`
fields in the storage section to use a shared MySQL/MariaDB database. LandWard creates
its `lands`, `claims`, `admin_lands` and `land_members` tables automatically.

## Claim limits

A player can own one land and may be a member of any number of other lands.
The `maximum-chunks-per-land` value limits the chunks belonging to that owned
land. Set it to `-1` to allow unlimited chunks:

```yml
claims:
  maximum-chunks-per-land: -1
```

## World scope

LandWard is active in every world except those listed in this section:

```yml
claims:
  disabled-worlds:
    - myworld
```

Use this list for exceptions, such as creative, skyblock or minigame worlds. It scopes
claims, claim protection and map markers without deleting existing claim data. A fresh
configuration includes `myworld` as a harmless example; remove or replace that entry
with your own excluded worlds.

## Claim payments

Use the following section. `free` is the default; `experience` charges raw XP
points for every claimed chunk and `vault` uses the active Vault economy
provider. The XP cost defaults to 150.

```yml
payments:
  mode: free
  experience-points-per-chunk: 150
```

Vault economy support is configured through the same mode
setting. `vault` uses the active Vault economy provider (such as EssentialsX
Economy) and refuses a paid claim when the provider is unavailable.

## Rename cost

Land renaming is free by default. Set a positive value here to charge that many
**XP levels** after a rename is confirmed in the
anvil GUI. A player without enough levels cannot confirm the change.

```yml
rename:
  experience-level-cost: 0
```

## Anonymous metrics

LandWard uses [bStats](https://bstats.org/plugin/bukkit/LandWard/35128) to collect
anonymous, aggregated usage statistics. No player names, chat, world data, server
addresses, or exact locations are collected.

Set this value to `false` to disable metrics for LandWard. Server owners can
also disable bStats globally in `plugins/bStats/config.yml`.

```yml
metrics:
  enabled: true
```

## Update checking

LandWard checks GitHub Releases asynchronously during startup. The check never
blocks Paper's main server thread. Set this value to `false` to disable it.

```yml
updates:
  enabled: true
  maximum-download-megabytes: 32
```

When an administrator runs `/claim info` and a release is marked **Outdated**,
they can click it or run `/claim update`. LandWard accepts only an official
GitHub Release JAR with a published SHA-256 digest, verifies its size, checksum
and `plugin.yml`, then saves it in `plugins/LandWard/updates/`. It never replaces
a loaded JAR. Stop the server and replace the plugin JAR with the staged file;
the `maximum-download-megabytes` value above limits the accepted download size.
Paper does not provide a safe live replacement for a running plugin's classes,
listeners, integrations and open storage connections, so LandWard deliberately
does not offer a live update.

## Console colors

LandWard uses Paper's Adventure component logger for colored, structured console
output. Set this value to `false` for plain output.

```yml
console:
  colored-output: true
```

## Plan analytics

With Plan installed, this setting publishes a read-only
LandWard panel in Plan. It contains server totals (lands and claimed chunks) and
per-player values (whether the player owns a land, owned chunks and member lands).
No new database table or player tracking is created by LandWard.

```yml
integrations:
  plan:
    enabled: true
```

## Spawn protection

This setting prevents LandWard claims that
overlap the built-in `server.properties` `spawn-protection` square around the
primary world spawn. Set `spawn-protection: 0` to disable that vanilla setting,
or set the LandWard integration to `false` if the area should be claimable.

```yml
integrations:
  vanilla-spawn-protection:
    enabled: true
```

## Map integrations

Each map entry has an `enabled` switch. A map plugin is never
required: LandWard detects it at runtime and quietly skips its layer when absent.

```yml
integrations:
  bluemap:
    enabled: true
  squaremap:
    enabled: true
  pl3xmap:
    enabled: true
```

## PlaceholderAPI and combat tags

When PlaceholderAPI is installed, LandWard registers its internal `landward`
expansion automatically; it does not need to be downloaded from eCloud. The
documented placeholders are available immediately.

LandWard includes a fallback PvP-hit combat tag and can additionally read
configured PlaceholderAPI values from CombatLogX, DeluxeCombat, PvPManager and
EternalCombat. Configure them in this section; a missing or
unresolved external placeholder is treated as not tagged.

```yml
integrations:
  combat-tag:
    enabled: true
    claim-pvp-bypass: true
    block-claim-abandon: true
```
