# Commands

All player commands begin with `/claim`.

| Command | Description |
| --- | --- |
| `/claim` | Open the LandWard management GUI. Players without a land see the create-land flow. |
| `/claim help` | Show command help. |
| `/claim info` | Show the installed LandWard version and author. |
| `/claim menu` | Open the land-management GUI. |
| `/claim bypass` | Toggle the temporary protection bypass. Requires `landward.bypass`; it resets when the player leaves. |
| `/claim update` | Stage a checksum-verified official GitHub Release JAR for manual installation. Requires `landward.admin`. |
| `/claimadmin` | Open administration: separate player-claim moderation, admin claims and active-world controls. Requires `landward.admin`. |
| `/claimadmin reload` | Reload config, language files and map layers. Requires `landward.admin`. |
| `/claimadmin migrate <sqlite\|mysql> confirm` | Console-only migration to an empty configured target backend. Requires no online players; creates and verifies a full backup before changing `storage.type` for the next restart. |

Only the land owner can add members, remove members or change rights. Members may belong to multiple lands. Legacy typed mutation subcommands redirect to the GUI so every action follows the same confirmation and permission flow.

## Administrative claims

The **Admin claims** card is for server-owned protected areas such as spawn. Its map uses purple tiles. Clicking an unclaimed chunk creates or extends the server-owned administrative area; it intentionally bypasses WorldGuard and vanilla spawn-overlap checks. The **Player claims** card can only review or remove player claims, and the Admin claims card can only remove administrative claims.

The admin reload does not replace the active storage backend. Change `storage.type` or its database connection settings only while the server is stopped.
