# Commands

The planned player-facing command surface is intentionally small.

| Command | Purpose |
| --- | --- |
| `/claim create <name>` | Create a land and claim the current chunk |
| `/claim claim` | Add the current adjacent chunk |
| `/claim unclaim` | Remove the current chunk |
| `/claim list` | Open the centered chunk-map GUI |
| `/claim info` | Show information about the current chunk |
| `/claim menu` | Open land management |
| `/claim rename <name>` | Rename a land |
| `/claim add <player>` | Add an online member |
| `/claim remove <player>` | Remove an online member |
| `/claim rights <player> <build\|containers\|entities> <on\|off>` | Change a member right |

All listed commands are implemented. The member-management GUI provides the same
member and rights controls without typing a command.
