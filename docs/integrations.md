# Integrations

All integrations are optional and discovered at runtime.

## Vanilla spawn protection and EssentialsX AntiBuild

Downloads: [EssentialsX](https://essentialsx.net/downloads.html) (including its
optional AntiBuild module).

LandWard can refuse claims that touch the primary world's built-in
`server.properties` `spawn-protection` square. This guard is enabled by the
following setting and is evaluated even when no
other plugin is installed. Paper may require its corresponding system setting
to honour vanilla spawn protection itself; LandWard still uses the configured
radius to keep its claims out of that area.

```yml
integrations:
  vanilla-spawn-protection:
    enabled: true
```

EssentialsX AntiBuild is declared as a soft dependency so it can enforce its
global build and interaction rules before/alongside LandWard. AntiBuild does
not offer region protection, so it does not define claimable boundaries; use
WorldGuard for protected spawn regions with custom shapes.

## Economy: Vault

Download: [Vault](https://dev.bukkit.org/projects/vault) and an economy provider
such as [EssentialsX](https://essentialsx.net/downloads.html). LandWard uses
Vault only when claim payments are configured as money; it keeps working when
Vault or an economy provider is absent.

## WorldGuard

Download: [WorldGuard](https://enginehub.org/worldguard). WorldGuard requires
[WorldEdit](https://enginehub.org/worldedit).

LandWard will reject a chunk claim when any part of that chunk intersects a protected WorldGuard region. The policy is configurable and defaults to denying every overlap.

## BlueMap, Squaremap and Pl3xMap

Downloads: [BlueMap](https://modrinth.com/plugin/bluemap),
[squaremap](https://modrinth.com/plugin/squaremap), and
[Pl3xMap](https://hangar.papermc.io/granny/Pl3xMap/).

Claim boundaries are published in dedicated, toggleable map layers. LandWard refreshes
the layer after a claim is created, removed or renamed. BlueMap, Squaremap and
Pl3xMap are all optional. Pl3xMap receives a managed JSON layer in its markers
directory; the other maps use their supported runtime marker APIs.

Install only the map plugin(s) your server needs and leave the matching option
enabled. If more than one map plugin is used
on the same server, configure a different web port for each map in that map
plugin's own configuration. LandWard never opens a web port itself.

```yml
integrations:
  bluemap:
    enabled: true
  squaremap:
    enabled: true
  pl3xmap:
    enabled: true
```

BlueMap may ask the server owner to accept its own Mojang-resource download before
BlueMap can render maps. This is a BlueMap requirement, not a LandWard setting.

## Plan

Download: [Plan](https://github.com/plan-player-analytics/Plan/releases).

When [Plan](https://github.com/plan-player-analytics/plan) is installed and this
setting is `true`, LandWard registers a read-only Plan DataExtension. Its server
card shows Player lands, Claimed chunks, Safe Zones, Safe Zone chunks, Unique
land owners and Land memberships. A player's Plan page shows their owned land
and claimed-chunk count plus the lands where they are a member. It stores no
additional player data and does not need a separate LandWard database table.

LandWard publishes a wide overview table, so Plan exposes **LandWard** as a
clickable item in the left sidebar. The page contains the complete claim and
Safe Zone statistics; no Plan files are modified.

Plan remains optional: without it LandWard starts and operates normally.

```yml
integrations:
  plan:
    enabled: true
```

## PlaceholderAPI

Download: [PlaceholderAPI](https://placeholderapi.com/downloads/).

When PlaceholderAPI is installed, LandWard automatically registers its internal
`landward` expansion. Nothing needs to be downloaded from eCloud.

| Placeholder | Returns |
| --- | --- |
| `%landward_land_name%` | The player's owned land name, or `none`. |
| `%landward_is_owner%` | `true` when the player owns a land; otherwise `false`. |
| `%landward_owned_chunks%` | Number of chunks in the player's owned land, or `0`. |
| `%landward_member_lands%` | Number of other lands the player belongs to. |
| `%landward_current_claim%` | Name of the land in the player's current chunk, or `none`. |
| `%landward_current_owner%` | Owner name of the current claimed chunk, or `none`. |
| `%landward_current_chunk_claimed%` | `true` when the player's current chunk is claimed; otherwise `false`. |

## Combat taggers

LandWard always has its own short-lived PvP-hit tracker, so combat protection
works even when no combat plugin is installed. When PlaceholderAPI and a
supported combat tagger are present, LandWard also checks the configured
external placeholders. The default mappings are:

| Combat tagger | Placeholder | Tagged value |
| --- | --- | --- |
| [CombatLogX](https://www.spigotmc.org/resources/combatlogx.31689/) | `%combatlogx_in_combat%` | `true` |
| [DeluxeCombat](https://deluxecombat.com/) | `%deluxecombat_in_combat%` | `true` |
| [PvPManager](https://www.curseforge.com/minecraft/bukkit-plugins/pvpmanager) | `%pvpmanager_combat_timeleft%` | a number greater than `0` |
| [EternalCombat](https://hangar.papermc.io/EternalCodeTeam/EternalCombat/) | `%eternalcombat_opponent%` | a non-empty opponent name |

An unresolved placeholder is treated as **not tagged**, preventing a missing
PlaceholderAPI expansion from blocking players. The value interpretation is
covered by automated contract tests. Before a release, still install each
tagger and its PlaceholderAPI expansion on a clean test server to verify its
current output and a real PvP encounter.

```yml
integrations:
  combat-tag:
    enabled: true
    claim-pvp-bypass: true
    block-claim-abandon: true
```
