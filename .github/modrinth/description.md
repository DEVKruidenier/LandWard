![LandWard banner](https://raw.githubusercontent.com/wiki/DEVKruidenier/LandWard/images/landward-banner-v2.png)

# LandWard

**Simple land claims. Reliable survival protection.**

LandWard is a GUI-first chunk-claim plugin for Paper, Purpur, Bukkit, Folia and Spigot survival servers. Players
create a named land, open a visual chunk map and click the chunks they want to
protect—no golden shovel, selection wand or claim shop required.

**Supported servers:**

- Paper 26.2 / 26.3
- Purpur 26.2 / 26.3
- Bukkit (CraftBukkit) 26.2 / 26.3
- Folia 26.2 / 26.3
- Spigot 26.2 / 26.3

Java 25 required.

**Velocity compatibility:** LandWard runs on a supported backend server behind Velocity. Install LandWard in the backend server's `plugins/` folder.

## Languages

**English is the default language.** LandWard automatically uses a supported
Minecraft client language when enabled. Server owners can change the default
and console language in `config.yml` and customize all text in `lang/<locale>.yml`.

**32 supported languages:** Arabic, Brazilian Portuguese, Czech, Danish, Dutch, English, Faroese, Filipino, Finnish, French, German, Hindi, Hungarian, Icelandic, Indonesian, Italian, Japanese, Korean, Malay, Norwegian Bokmål, Norwegian Nynorsk, Polish, Romanian, Russian, Simplified Chinese, Spanish, Swedish, Thai, Traditional Chinese, Turkish, Ukrainian and Vietnamese.

See [Languages and locale codes](https://github.com/DEVKruidenier/LandWard/wiki/Languages) for setup and automatic fallback behavior.

## Servers using LandWard

- **play.martialcraft.nl**
- Want your server listed here? Join **play.martialcraft.nl** and use
  `/mail send Kruidenier <yourServerIP>`.

## Highlights

- Visual chunk map centred on the player's current chunk.
- Player lands with member management and configurable member rights.
- Dedicated administrator tools for player-claim moderation and Safe Zones.
- Optional SQLite or MySQL/MariaDB storage with live migration tools.
- Optional Vault economy, WorldGuard, BlueMap, Squaremap, Pl3xMap, Plan,
  PlaceholderAPI, bStats and combat-tagger integrations.
- Localized player-facing messages and configurable protection behaviour.

## Personal Block Protection

Protect individual blocks inside player lands in LandWard 1.0.6.0 and newer. This includes normal, trapped and copper chests, barrels, shulker boxes, doors, trapdoors, fence gates, levers and buttons.

1. Open `/claim` and choose **Block Protection → Protect Block**.
2. Right-click the chest, door or other supported block inside your land.
3. Choose **Private** or **Shared**. Private keeps access to yourself under the default settings. Shared lets you grant access to selected land members. Land permissions still apply.

**My Protected Blocks** lets you review your locks, change who has access or remove a lock. A double chest or a two-block door uses one lock for both halves. Members can protect blocks LandWard recorded them placing.

On existing servers, set `block-protection.enabled` to `true` in `plugins/LandWard/config.yml` if it is disabled. See the [Block Protection guide](https://github.com/DEVKruidenier/LandWard/wiki/Block-Protection) and [FAQ & Troubleshooting](https://github.com/DEVKruidenier/LandWard/wiki/FAQ-and-Troubleshooting).

**Start protecting a block** — select **Protect Block** and follow the prompt to right-click an eligible block.

![Starting block selection from the land menu](https://raw.githubusercontent.com/wiki/DEVKruidenier/LandWard/images/gui-showcase/1.0.6.0-beta9/landward-protect-block-selection.gif)

**Find your protected doors and chests** — open **My Protected Blocks** and choose a lock to manage.

![Reviewing locked doors, a copper chest and a shulker box](https://raw.githubusercontent.com/wiki/DEVKruidenier/LandWard/images/gui-showcase/1.0.6.0-beta9/landward-my-protected-blocks.gif)

**Manage chest access** — open the chest's lock settings to review its access, share it with land members or remove the lock.

![Reviewing access and sharing controls for a protected chest](https://raw.githubusercontent.com/wiki/DEVKruidenier/LandWard/images/gui-showcase/1.0.6.0-beta9/landward-block-protection-settings.gif)

## Land settings and warps

Land owners can configure member rights and rules for fire spread, fall damage and explosions. Administrators can set separate rules for Safe Zones through `/claimadmin`. Use **Warps** in the land menu to save named destinations that owners and members can use. See [Protection settings and named warps](https://github.com/DEVKruidenier/LandWard/wiki/Protection-and-Warps).

## See it in action

**Create a land** — open `/claim`, choose **Create land**, and enter the land name
in the native Minecraft anvil interface.

![Opening the land menu after creating a land](https://raw.githubusercontent.com/wiki/DEVKruidenier/LandWard/images/gui-showcase/1.0.6.0-beta9/landward-main-menu.gif)

**Claim map** — open the visual map centred on your current chunk, then click an
available neighbouring chunk to protect it.

![Using the LandWard claim map](https://raw.githubusercontent.com/wiki/DEVKruidenier/LandWard/images/gui-showcase/1.0.6.0-beta9/landward-chunk-map.gif)

**Player claim settings** — configure the land-wide protection defaults and
member permissions from the in-game settings menu.

![Player claim settings](https://raw.githubusercontent.com/wiki/DEVKruidenier/LandWard/images/gui-showcase/1.0.6.0-beta9/landward-access-members.gif)

**Administration menu** — open ClaimAdmin to review player lands, manage Safe
Zones and configure active worlds.

![ClaimAdmin menu](https://raw.githubusercontent.com/wiki/DEVKruidenier/LandWard/images/gui-showcase/1.0.6.0-beta9/landward-admin-main-menu.gif)

**Player claim administration** — select a player's land, inspect its claim map,
add or remove individual chunks, or remove the complete land after confirmation.

![Player claim administration](https://raw.githubusercontent.com/wiki/DEVKruidenier/LandWard/images/gui-showcase/1.0.6.0-beta9/landward-admin-player-claim.gif)

**Safe Zone settings** — configure building, entities, containers, doors,
interactions, mobs and PvP for server-owned areas, with individual member rights.
World and WorldGuard PvP restrictions remain authoritative.

![Safe Zone settings](https://raw.githubusercontent.com/wiki/DEVKruidenier/LandWard/images/gui-showcase/1.0.6.0-beta9/landward-admin-settings.gif)

**Active worlds** — enable or disable LandWard per world without changing the
server's other worlds.

![Active world settings](https://raw.githubusercontent.com/wiki/DEVKruidenier/LandWard/images/gui-showcase/1.0.6.0-beta9/landward-active-worlds.gif)

## Documentation and support

For the complete feature list and detailed usage instructions, see the
[LandWard Wiki](https://github.com/DEVKruidenier/LandWard/wiki).

- [Installation, configuration and commands](https://github.com/DEVKruidenier/LandWard/wiki)
- [Report an issue](https://github.com/DEVKruidenier/LandWard/issues)
- [Source code](https://github.com/DEVKruidenier/LandWard)

LandWard is licensed under GPL-3.0-or-later.
