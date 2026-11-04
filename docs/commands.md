# Commands

LandWard is GUI-first: players normally only need /claim.

| Command | Purpose |
| --- | --- |
| /claim | Opens land management. Players without a land get the create-land GUI. |
| /claim info | Shows the installed LandWard version and author. |
| /claim menu | Opens the same land management GUI explicitly. |
| /claim help | Shows the compact command reference. |
| /claimadmin | Opens administration: separate player-claim moderation, admin claims and active-world controls; requires landward.admin. |
| /claimadmin reload | Reloads config, language files and map layers; requires landward.admin. |

## In-game flow

- **Create:** choose *Create your land* and enter the name in the anvil GUI.
- **Claim:** open the chunk map and click an available grey chunk.
- **Unclaim:** click an owned chunk in the map and confirm. Removing the final
  chunk also removes that land and its member rights.
- **Rename:** click the name tag in land management, enter a name in the anvil GUI,
  then click the result item.
- **Members and rights:** use the member and rights screens in land management.

Former typed mutation subcommands are intentionally redirected to the GUI, so every
player uses the same confirmation and permission flow.

## Administrative claims

The **Admin claims** card is reserved for server-owned protected areas such as spawn. Its map uses purple tiles. Clicking an unclaimed chunk creates or extends the server-owned administrative area; it intentionally bypasses WorldGuard and vanilla spawn-overlap checks. The separate **Player claims** card can only review or remove player claims, and the Admin claims card can only remove administrative claims.

The admin reload does not replace the active storage backend. Change `storage.type`
or its database connection settings only while the server is stopped.
