# Integrations

All integrations are optional. LandWard works normally when none are installed.

## Vanilla spawn protection and EssentialsX

LandWard can reserve the primary world's built-in `server.properties`
`spawn-protection` square, even when no protection plugin is installed. It can
be disabled with `integrations.vanilla-spawn-protection.enabled`.

EssentialsX AntiBuild and Protect are soft dependencies. They apply their global
build and interaction rules alongside LandWard; use WorldGuard for a shaped
spawn region.

## WorldGuard

When WorldGuard is installed, LandWard refuses a claim that overlaps a protected WorldGuard region. This includes typical spawn regions. Configure this under `integrations.worldguard`.

## BlueMap, Dynmap, Squaremap and Pl3xMap

When BlueMap, Dynmap, Squaremap or Pl3xMap is installed, LandWard publishes claims in a dedicated, toggleable marker layer. The layer refreshes after a chunk is claimed, unclaimed or renamed.

Each map remains optional; LandWard never opens a web port. If you run more than
one map plugin, assign a separate web port in each map plugin's own configuration.
BlueMap may additionally require the server owner to accept its Mojang-resource
download before it can render maps.

![Actual BlueMap claim overlay: chunk boundaries follow exact 16×16 chunks.](https://raw.githubusercontent.com/DEVKruidenier/LandWard.wiki/master/images/codex-clipboard-9963635d-7113-4cf3-a038-4ecfbc94da43.png)

> **Dynmap availability:** LandWard's Dynmap bridge is optional and uses Dynmap's
> marker API when a compatible Dynmap build is installed. At the time of writing,
> Dynmap's official releases do not list Paper 26.x support, so it is deliberately
> not included in the Paper 26.2 test server. Do not install an unofficial port on
> a production server solely for this integration.

## Plan

When [Plan](https://github.com/plan-player-analytics/plan) is installed and `integrations.plan.enabled` is `true`, LandWard adds read-only claim statistics to the Plan web panel.

- Per player: owns a land, owned chunks, and member lands.
- Per server: number of lands and claimed chunks.

No additional LandWard database table is created for this integration. Plan is a soft dependency, so LandWard starts normally without it.

## PlaceholderAPI

When PlaceholderAPI is installed, LandWard registers `%landward_...%` placeholders:
`land_name`, `is_owner`, `owned_chunks`, `member_lands`, `current_claim`,
`current_owner` and `current_chunk_claimed`.

LandWard registers its internal expansion automatically when PlaceholderAPI is installed. You do **not** download it from eCloud; eCloud is for independently distributed expansions.

## Combat taggers

LandWard has its own short-lived PvP-hit tracker, so the claim-combat safeguard works even when no combat plugin is installed. It can also read configured PlaceholderAPI values from CombatLogX, DeluxeCombat, PvPManager and EternalCombat when their matching PlaceholderAPI expansion is available.

When `integrations.combat-tag.claim-pvp-bypass` is enabled, two tagged combatants may continue their PvP encounter inside normal claims until their tag expires. `integrations.combat-tag.block-claim-abandon` also blocks unclaiming while tagged. An unresolved external placeholder is treated as **not tagged**, so a missing expansion cannot lock players out of a command.

---

Next: [Configuration](Configuration) · [Visual Guide](Visual-Guide)
