# Integrations

All integrations are optional and discovered at runtime.

## Vanilla spawn protection and EssentialsX AntiBuild

LandWard can refuse claims that touch the primary world's built-in
`server.properties` `spawn-protection` square. This guard is enabled through
`integrations.vanilla-spawn-protection.enabled` and is evaluated even when no
other plugin is installed. Paper may require its corresponding system setting
to honour vanilla spawn protection itself; LandWard still uses the configured
radius to keep its claims out of that area.

EssentialsX AntiBuild is declared as a soft dependency so it can enforce its
global build and interaction rules before/alongside LandWard. AntiBuild does
not offer region protection, so it does not define claimable boundaries; use
WorldGuard for protected spawn regions with custom shapes.

## WorldGuard

LandWard will reject a chunk claim when any part of that chunk intersects a protected WorldGuard region. The policy is configurable and defaults to denying every overlap.

## BlueMap, Dynmap, Squaremap and Pl3xMap

Claim boundaries are published in dedicated, toggleable map layers. LandWard refreshes
the layer after a claim is created, removed or renamed. BlueMap, Dynmap, Squaremap
and Pl3xMap are all optional. Pl3xMap receives a managed JSON layer in its markers
directory; the other maps use their supported runtime marker APIs.

Install only the map plugin(s) your server needs and leave the matching
`integrations.<map>.enabled` option enabled. If more than one map plugin is used
on the same server, configure a different web port for each map in that map
plugin's own configuration. LandWard never opens a web port itself.

BlueMap may ask the server owner to accept its own Mojang-resource download before
BlueMap can render maps. This is a BlueMap requirement, not a LandWard setting.

> **Dynmap availability:** LandWard's Dynmap bridge is optional and uses Dynmap's
> marker API when a compatible Dynmap build is installed. At the time of writing,
> Dynmap's official releases do not list Paper 26.x support, so it is deliberately
> not included in the Paper 26.2 test server. Do not install an unofficial port on
> a production server solely for this integration.

## Plan

When [Plan](https://github.com/plan-player-analytics/plan) is installed and
`integrations.plan.enabled` is `true`, LandWard registers a read-only Plan DataExtension.
The Plan web panel then shows per-player ownership, owned-chunk and member-land
statistics, together with server-wide land and claimed-chunk totals. It stores no
additional player data and does not need a separate LandWard database table.

Plan remains optional: without it LandWard starts and operates normally.

## PlaceholderAPI

When PlaceholderAPI is installed, LandWard registers an internal `landward`
expansion. Available placeholders are `%landward_land_name%`,
`%landward_is_owner%`, `%landward_owned_chunks%`, `%landward_member_lands%`,
`%landward_current_claim%`, `%landward_current_owner%` and
`%landward_current_chunk_claimed%`.

## Combat taggers

LandWard always has its own short-lived PvP-hit tracker, so combat protection
works even when no combat plugin is installed. When PlaceholderAPI and a
supported combat tagger are present, LandWard also checks the configured
external placeholders. The default mappings are:

| Combat tagger | Placeholder | Tagged value |
| --- | --- | --- |
| CombatLogX | `%combatlogx_in_combat%` | `true` |
| DeluxeCombat | `%deluxecombat_in_combat%` | `true` |
| PvPManager | `%pvpmanager_combat_timeleft%` | a number greater than `0` |
| EternalCombat | `%eternalcombat_opponent%` | a non-empty opponent name |

An unresolved placeholder is treated as **not tagged**, preventing a missing
PlaceholderAPI expansion from blocking players. The value interpretation is
covered by automated contract tests. Before a release, still install each
tagger and its PlaceholderAPI expansion on a clean test server to verify its
current output and a real PvP encounter.
