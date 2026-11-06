# Claim Management

LandWard is intentionally built around the Minecraft inventory GUI. There are no golden shovels, selection wands or claim-shop steps.

## Create your first land

1. Run `/claim`.
2. Select **Create land**.
3. Enter a name in the native anvil window and click the output slot.
4. Open **Chunk map**. The compass marks your current chunk; click an available grey chunk next to your land to claim it.

![Creating a LandWard land in the real Minecraft GUI.](https://raw.githubusercontent.com/DEVKruidenier/LandWard.wiki/master/images/claim.gif)

## Understanding the chunk map

| Tile state | Meaning |
| --- | --- |
| Compass | Your current chunk; the map is centred on this position. |
| Green | A chunk owned by your land. |
| Grey | An unclaimed chunk that may be eligible to claim. |
| Red | A chunk owned by another player. |
| Purple | A server-owned Safe Zone (admin claim). |

The tooltip shows the chunk coordinates and owner. By default, you can only
claim a chunk next to one you already own. Unclaiming always asks for
confirmation. Server owners can change this in [Configuration](Configuration).

![Navigating the real LandWard chunk map.](https://raw.githubusercontent.com/DEVKruidenier/LandWard.wiki/master/images/claimmap.gif)

## Owner controls

Only an owner can:

- rename the land;
- claim and unclaim chunks;
- add or remove members;
- set the shared rules for all members;
- open the land map and member management controls.

## Shared member rules

Member rights are intentionally land-wide—not individual per player—so every member receives the same predictable access. In **Settings**, an owner can toggle build access, containers, protected entities, doors and interaction blocks, mob rules, and PvP between members.

![Configuring shared member rules in the real Minecraft GUI.](https://raw.githubusercontent.com/DEVKruidenier/LandWard.wiki/master/images/claimsettings.gif)

The world’s own PvP setting always remains the upper limit: LandWard cannot enable member PvP in a world where PvP is disabled.

## Administration and Safe Zones

ClaimAdmin keeps server-owned Safe Zones separate from player claims. Administrators can review player claims, manage Safe Zones and choose which worlds use LandWard.

![Real Minecraft capture of the ClaimAdmin menu.](https://raw.githubusercontent.com/DEVKruidenier/LandWard.wiki/master/images/claimadmin.gif)

Safe Zone settings control public PvP, mob rules, containers, doors and interactions. Trusted Safe Zone members retain full access.

![Real Minecraft capture of ClaimAdmin settings.](https://raw.githubusercontent.com/DEVKruidenier/LandWard.wiki/master/images/claimadminsettings.gif)

## Members who own a land too

A player may be a member of multiple other lands *and* own one separate land. `/claim` continues to show their create/owned-land flow while providing a read-only **Member lands** overview for lands that they do not own. It deliberately does not include the claim map or owner settings.

## Entry and protection feedback

Players receive a concise localized notice when entering a claimed area. Protection feedback explains why an action is blocked. Operators who have `landward.bypass` must deliberately enable it with `/claim bypass`; it switches off automatically when they leave.

---

Next: [Commands](Commands) · [Permissions](Permissions) · [Visual Guide](Visual-Guide)
