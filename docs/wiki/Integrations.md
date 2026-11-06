# Integrations

All integrations are optional. LandWard works normally when none are installed.

## Vanilla spawn protection and EssentialsX

LandWard can reserve the primary world's built-in `server.properties`
`spawn-protection` square, even when no protection plugin is installed. It can
be disabled here:

```yaml
integrations:
  vanilla-spawn-protection:
    enabled: true
```

EssentialsX AntiBuild and Protect are soft dependencies. They apply their global
build and interaction rules alongside LandWard; use WorldGuard for a shaped
spawn region.

## WorldGuard

When WorldGuard is installed, LandWard refuses a claim that overlaps a protected WorldGuard region. This includes typical spawn regions.

```yaml
integrations:
  worldguard:
    enabled: true
    deny-any-overlap: true
```

## BlueMap, Squaremap and Pl3xMap

When BlueMap, Squaremap or Pl3xMap is installed, LandWard publishes claims in a dedicated, toggleable marker layer. The layer refreshes after a chunk is claimed, unclaimed or renamed.

```yaml
integrations:
  bluemap:
    enabled: true
  squaremap:
    enabled: true
  pl3xmap:
    enabled: true
```

Each map remains optional; LandWard never opens a web port. If you run more than
one map plugin, assign a separate web port in each map plugin's own configuration.
BlueMap may additionally require the server owner to accept its Mojang-resource
download before it can render maps.

![Actual BlueMap claim overlay: chunk boundaries follow exact 16×16 chunks.](https://raw.githubusercontent.com/DEVKruidenier/LandWard.wiki/master/images/codex-clipboard-9963635d-7113-4cf3-a038-4ecfbc94da43.png)

## Plan

When [Plan](https://github.com/plan-player-analytics/plan) is installed and this setting is enabled, LandWard adds read-only claim statistics to the Plan web panel.

```yaml
integrations:
  plan:
    enabled: true
```

- Per player: owns a land, owned chunks, and member lands.
- Per server: number of lands and claimed chunks.

No additional LandWard database table is created for this integration. Plan is a soft dependency, so LandWard starts normally without it.

## PlaceholderAPI

When PlaceholderAPI is installed, LandWard registers these placeholders:

| Placeholder | Returns |
| --- | --- |
| `%landward_land_name%` | The player's owned land name, or `none`. |
| `%landward_is_owner%` | `true` when the player owns a land; otherwise `false`. |
| `%landward_owned_chunks%` | Number of chunks in the player's owned land, or `0`. |
| `%landward_member_lands%` | Number of other lands the player belongs to. |
| `%landward_current_claim%` | Name of the land in the player's current chunk, or `none`. |
| `%landward_current_owner%` | Owner name of the current claimed chunk, or `none`. |
| `%landward_current_chunk_claimed%` | `true` when the player's current chunk is claimed; otherwise `false`. |

LandWard registers its internal expansion automatically when PlaceholderAPI is installed. You do **not** download it from eCloud; eCloud is for independently distributed expansions.

## Combat taggers

LandWard has its own short-lived PvP-hit tracker, so the claim-combat safeguard works even when no combat plugin is installed. It can also read configured PlaceholderAPI values from CombatLogX, DeluxeCombat, PvPManager and EternalCombat when their matching PlaceholderAPI expansion is available.

With the following settings, two tagged combatants may continue their PvP encounter inside normal claims until their tag expires, while tagged players cannot unclaim land. An unresolved external placeholder is treated as **not tagged**, so a missing expansion cannot lock players out of a command.

```yaml
integrations:
  combat-tag:
    enabled: true
    claim-pvp-bypass: true
    block-claim-abandon: true
```

---

Next: [Configuration](Configuration) · [Visual Guide](Visual-Guide)
