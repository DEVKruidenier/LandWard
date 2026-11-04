# Commands

The planned player-facing command surface is intentionally small.

| Command | Purpose |
| --- | --- |
| `/land create <name>` | Create a land and claim the current chunk |
| `/land claim` | Add the current adjacent chunk |
| `/land unclaim` | Remove the current chunk |
| `/land list` | Open the centered chunk-map GUI |
| `/land info` | Show information about the current chunk |
| `/land menu` | Open land management |
| `/land rename <name>` | Rename a land |
| `/land add <player>` | Add an online member |
| `/land remove <player>` | Remove an online member |
| `/land rights <player> <build\|containers\|entities> <on\|off>` | Change a member right |

All listed commands are implemented. The member-management GUI provides the same
member and rights controls without typing a command.
