# Integrations

All integrations are optional and discovered at runtime.

## WorldGuard

LandWard will reject a chunk claim when any part of that chunk intersects a protected WorldGuard region. The policy is configurable and defaults to denying every overlap.

## BlueMap and Dynmap

Claim boundaries are published in dedicated, toggleable map layers. LandWard refreshes
the layer after a claim is created or removed. BlueMap and Dynmap must be installed
and their marker functionality must be enabled.

## Plan

When [Plan](https://github.com/plan-player-analytics/plan) is installed and
`integrations.plan.enabled` is `true`, LandWard registers a read-only Plan DataExtension.
The Plan web panel then shows per-player ownership, owned-chunk and member-land
statistics, together with server-wide land and claimed-chunk totals. It stores no
additional player data and does not need a separate LandWard database table.

Plan remains optional: without it LandWard starts and operates normally.
