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
- set the default rules for members;
- customise the rights of each individual member;
- open the land map and member management controls.

## Member rights

When a player land is created, it receives the member-right defaults from the
server configuration. In **Settings**, its owner can change those defaults and
select any member to customise build access, containers and protected entities
for that person. The member-specific value takes priority over the land's
default. Changing the land default affects members added afterwards; it never
silently replaces a member's individual choice.

Doors, interaction blocks, mob rules and PvP between members are land-wide
protection rules. They apply to every trusted member of that land. The world's
own PvP setting always remains the upper limit.

![Configuring member defaults and individual member rights in the real Minecraft GUI.](https://raw.githubusercontent.com/DEVKruidenier/LandWard.wiki/master/images/claimsettings.gif)

## Administration and Safe Zones

ClaimAdmin keeps server-owned Safe Zones separate from player claims. Administrators can review player claims, manage Safe Zones and choose which worlds use LandWard.

![Real Minecraft capture of the ClaimAdmin menu.](https://raw.githubusercontent.com/DEVKruidenier/LandWard.wiki/master/images/claimadmin.gif)

Safe Zone settings control public PvP, mob rules, containers, doors and
interactions. Safe Zone members start with the `admin-claims.member-rights`
defaults and administrators can customise build, container and protected-entity
access for each member separately.

![Real Minecraft capture of ClaimAdmin settings.](https://raw.githubusercontent.com/DEVKruidenier/LandWard.wiki/master/images/claimadminsettings.gif)

## Members who own a land too

A player may be a member of multiple other lands *and* own one separate land. `/claim` continues to show their create/owned-land flow while providing a read-only **Member lands** overview for lands that they do not own. It deliberately does not include the claim map or owner settings.

## Entry and protection feedback

Players receive a concise localized notice when entering a claimed area. Protection feedback explains why an action is blocked. Operators who have `landward.bypass` must deliberately enable it with `/claim bypass`; it switches off automatically when they leave.

---

Next: [Commands](Commands) · [Permissions](Permissions)
