# Permissions

| Permission | Default | Purpose |
| --- | --- | --- |
| `landward.use` | Everyone | Use `/claim`, `/land` and `/landward`. |
| `landward.admin` | Operators | Use ClaimAdmin, reload and release staging. |
| `landward.bypass` | Operators | Allows `/claim bypass`; it does **not** bypass claims until the holder deliberately enables it. |
| `landward.dbadmin` | Operators | Use storage migration and storage maintenance. |

## The admin bypass

`landward.bypass` is permission to *toggle* the bypass, not a permanent bypass. An operator must run `/claim bypass` and receives clear feedback when it is enabled or disabled. The state resets when that player leaves the server, preventing accidental edits in another player’s land.

## Land membership

Land membership is not a Bukkit permission. The configuration supplies the
initial build, container and protected-entity rights for new lands. In the
land's **Settings** menu, the owner can change those defaults and can select a
member to set those three rights for that person individually. An individual
member choice takes priority over the land default. Changing the default affects
future members only; it does not silently replace existing individual choices.

- build and block breaking/placing;
- containers;
- protected entities;

Doors, interaction blocks, hostile/passive mob rules, hostile spawning and PvP
between members are settings for the whole player land. PvP always remains
subject to the world’s own PvP setting. Safe Zone administrators have the same
per-member build, container and entity controls in ClaimAdmin.

Owners always retain full control. Members can be added or removed from the members menu, may belong to multiple lands, and may still own their own separate land.

---

Next: [Claim Management](Claim-Management) · [Commands](Commands)
