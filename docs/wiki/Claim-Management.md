# Claim Management

LandWard is intentionally built around the Minecraft inventory GUI. There are no golden shovels, selection wands or claim-shop steps.

## Create your first land

1. Run `/claim`.
2. Select **Create land**.
3. Enter a name in the native anvil window and click the output slot.
4. Open **Chunk map**. The compass marks your current chunk; click an eligible grey chunk adjacent to your land to claim it.

![LandWard’s real Minecraft main menu.](https://raw.githubusercontent.com/DEVKruidenier/LandWard.wiki/master/images/codex-clipboard-5d257eeb-0651-4ec1-87d9-23cd3c49b5f5.png)

## Understanding the chunk map

| Tile state | Meaning |
| --- | --- |
| Compass | Your current chunk; the map is centred on this position. |
| Green | A chunk owned by your land. |
| Grey | An unclaimed chunk that may be eligible to claim. |
| Red | A chunk owned by another player. |
| Purple | A server-owned Safe Zone (admin claim). |

The map shows the chunk coordinates and owner in each tile’s tooltip. Claiming requires adjacency when `claims.require-adjacent-chunks` is enabled. Unclaiming always asks for confirmation.

## Owner controls

Only an owner can:

- rename the land;
- claim and unclaim chunks;
- add or remove members;
- set the shared rules for all members;
- open the land map and member management controls.

## Shared member rules

Member rights are intentionally land-wide—not individual per player—so every member receives the same predictable access. In **Settings**, an owner can toggle build access, containers, protected entities, doors and interaction blocks, mob rules, and PvP between members.

![Real Minecraft settings menu from the LandWard test server.](https://raw.githubusercontent.com/DEVKruidenier/LandWard.wiki/master/images/codex-clipboard-8da055b9-7295-4a30-9d72-2dd648fc1ceb.png)

The world’s own PvP setting always remains the upper limit: LandWard cannot enable member PvP in a world where PvP is disabled.

## Members who own a land too

A player may be a member of multiple other lands *and* own one separate land. `/claim` continues to show their create/owned-land flow while providing a read-only **Member lands** route to lands that they do not own. Membership never exposes the owner’s settings.

## Entry and protection feedback

Players receive a concise localized notice when entering a claimed area. Protection feedback explains why an action is blocked. Operators who have `landward.bypass` must deliberately enable it with `/claim bypass`; it switches off automatically when they leave.

---

Next: [Commands](Commands) · [Permissions](Permissions) · [Visual Guide](Visual-Guide)
