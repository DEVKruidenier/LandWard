# Permissions

| Permission | Default | Purpose |
| --- | --- | --- |
| `landward.use` | Everyone | Use `/claim` (also `/land` and `/landward`). |
| `landward.admin` | Operators | Reserved for LandWard administration features. |
| `landward.bypass` | Operators | Bypass LandWard's claim-protection checks. |

Land membership is not a Bukkit permission. A land owner chooses, per member, whether that member can build, use containers, or place and interact with protected entities in that land.

`landward.command` remains a compatibility alias during the pre-release period; new permission configurations should use `landward.use`.
