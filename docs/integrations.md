# Integrations

All integrations are optional and discovered at runtime.

## WorldGuard

LandWard will reject a chunk claim when any part of that chunk intersects a protected WorldGuard region. The policy is configurable and defaults to denying every overlap.

## BlueMap and Dynmap

Claim boundaries will be published in a dedicated, toggleable map layer. Marker updates are event-driven and debounced after land changes.
