# Commands

LandWard keeps the public command surface deliberately small. Players manage land through the GUI; `/land` and `/landward` are friendly aliases for `/claim`.

## Player commands

| Command | Description |
| --- | --- |
| `/claim` | Opens the LandWard GUI. If you do not own a land, it opens the create-land flow and also shows lands where you are a member. |
| `/claim menu` | Opens your owned land’s management GUI. |
| `/claim help` | Shows the short in-game command reference. |
| `/claim info` | Shows the installed version, author and update status. |
| `/claim bypass` | Turns the temporary admin protection bypass on or off. It requires `landward.bypass` and resets when you log out. |
| `/claim update` | Downloads the latest verified release so you can install it after stopping the server. Requires `landward.admin`. |

For the complete player flow—creating a land, the native anvil screen, chunk-map colours, membership and owner controls—see [Claim Management](Claim-Management).

## Administration

| Command | Description |
| --- | --- |
| `/claimadmin` | Opens ClaimAdmin: player claims, purple Safe Zones and active-world controls. Requires `landward.admin`. |
| `/claimadmin reload` | Reloads configuration, language files and map layers. Requires `landward.admin`. |
| `/claimadmin migrate <sqlite\|mysql> confirm` | Console-only live migration to the configured, empty target backend. Players stay online; LandWard briefly pauses claim changes. Requires `landward.dbadmin`. |
| `/claimadmin storage status <sqlite\|mysql>` | Console-only check of the configured backend and whether its LandWard tables contain data. Requires `landward.dbadmin`. |
| `/claimadmin storage clear <sqlite\|mysql> confirm` | Console-only maintenance command that clears LandWard tables in the selected backend. Requires `landward.dbadmin`; use only after reading [Storage](Storage). |

> **GUI-first:** creating, claiming, unclaiming, renaming, member management and administration maps are handled through the in-game GUI. This keeps confirmations and permissions consistent.

---

Next: [Claim Management](Claim-Management) · [Permissions](Permissions) · [Configuration](Configuration)
