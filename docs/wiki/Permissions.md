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

Land membership is not a Bukkit permission. The owner configures the shared member rules in the land’s **Settings** menu. Every current and future member receives the same rules for that land:

- build and block breaking/placing;
- containers;
- protected entities;
- doors and interaction blocks;
- hostile/passive mob kills and hostile spawning;
- PvP between members, subject to the world’s own PvP setting.

Owners always retain full control. Members can be added or removed from the members menu, may belong to multiple lands, and may still own their own separate land.

---

Next: [Claim Management](Claim-Management) · [Commands](Commands)
