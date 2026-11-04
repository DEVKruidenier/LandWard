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
