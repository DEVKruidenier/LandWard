# Commands

LandWard keeps the public command surface deliberately small. Players manage land through the GUI; `/land` and `/landward` are friendly aliases for `/claim`.

## Player commands

| Command | Description |
| --- | --- |
| `/claim` | Opens the LandWard GUI. If you do not own a land, it opens the create-land flow and also shows lands where you are a member. |
| `/claim menu` | Opens your owned land’s management GUI. |
| `/claim help` | Shows the short in-game command reference. |
| `/claim info` | Shows the installed version, author and update status. |
| `/claim bypass` | Toggles the temporary admin protection bypass. It requires `landward.bypass` and resets when you leave. |
| `/claim update` | Stages a checksum-verified official GitHub Release JAR for manual installation. Requires `landward.admin`. |

For the complete player flow—creating a land, the native anvil screen, chunk-map colours, membership and owner controls—see [Claim Management](Claim-Management).

## Administration

| Command | Description |
| --- | --- |
| `/claimadmin` | Opens ClaimAdmin: player claims, purple Safe Zones and active-world controls. Requires `landward.admin`. |
| `/claimadmin reload` | Reloads configuration, language files and map layers. Requires `landward.admin`. |
| `/claimadmin migrate <sqlite\|mysql> confirm` | Console-only verified migration to the configured, empty target backend. No players may be online. |
| `/claimadmin storage status <sqlite\|mysql>` | Console-only check of the configured backend and whether its LandWard tables contain data. |
| `/claimadmin storage clear <sqlite\|mysql> confirm` | Console-only maintenance command that clears LandWard tables in the selected backend. Use only after reading [Storage](Storage). |

> **GUI-first:** creating, claiming, unclaiming, renaming, member management and administration maps are handled through the in-game GUI. This keeps confirmations and permissions consistent.

---

Next: [Claim Management](Claim-Management) · [Permissions](Permissions) · [Configuration](Configuration)
