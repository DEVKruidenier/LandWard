# Commands

All player commands begin with `/claim`.

| Command | Description |
| --- | --- |
| `/claim` | Open the LandWard management GUI. Players without a land see the create-land flow. |
| `/claim help` | Show command help. |
| `/claim info` | Show the installed LandWard version and author. |
| `/claim menu` | Open the land-management GUI. |
| `/claimadmin` | Open administration: claim moderation and active-world controls. Requires `landward.admin`. |
| `/claimadmin reload` | Reload config, language files and map layers. Requires `landward.admin`. |

Only the land owner can add members, remove members or change rights. Members may belong to multiple lands. Legacy typed mutation subcommands redirect to the GUI so every action follows the same confirmation and permission flow.

The admin reload does not replace the active storage backend. Change `storage.type` or its database connection settings only while the server is stopped.
