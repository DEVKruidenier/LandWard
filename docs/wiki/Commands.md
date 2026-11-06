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

### Player flow

1. Run `/claim`.
2. Choose **Create land** and enter its name through the native Minecraft anvil screen.
3. Open the **Chunk map** and click an eligible adjacent unclaimed chunk.
4. Use the land menu for members, settings, renaming and unclaim confirmation.

> **Ownership:** A player may own one land and be a member of any number of other lands. Being a member never grants ownership controls.

## Administration

| Command | Description |
| --- | --- |
| `/claimadmin` | Opens ClaimAdmin: player claims, purple Safe Zones and active-world controls. Requires `landward.admin`. |
| `/claimadmin reload` | Reloads configuration, language files and map layers. Requires `landward.admin`. |
| `/claimadmin migrate <sqlite\|mysql> confirm` | Console-only verified migration to the configured, empty target backend. No players may be online. |
| `/claimadmin storage status <sqlite\|mysql>` | Console-only check of the configured backend and whether its LandWard tables contain data. |
| `/claimadmin storage clear <sqlite\|mysql> confirm` | Console-only maintenance command that clears LandWard tables in the selected backend. Use only after reading [Storage](Storage). |

## Native anvil naming

Land names are entered through Minecraft’s normal anvil interface. The name is only saved when the output slot is clicked. When `rename.experience-level-cost` is `0`, renaming is free—even if vanilla still displays its cosmetic “Enchantment Cost: 1” label.

## What is intentionally GUI-only?

Creating a land, claiming or unclaiming chunks, renaming, members, land settings and administration maps are GUI actions. This prevents accidental changes, makes the flow discoverable, and keeps confirmation screens consistent.

---

Next: [Claim Management](Claim-Management) · [Permissions](Permissions) · [Configuration](Configuration)
