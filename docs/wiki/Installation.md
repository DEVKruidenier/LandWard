# Installation

1. Download the normal `LandWard-<version>.jar` release file. Do not use the `-unshaded` development artifact.
2. Stop the Paper server.
3. Put the JAR in `plugins/`.
4. Start the server once. LandWard creates `plugins/LandWard/config.yml` and, by default, the SQLite database `landward.db`.
5. Configure storage, language and claims as required, then restart the server.

At startup, the LandWard banner reports the active storage backend and the number of claims loaded. A failed storage connection prevents the plugin from enabling; check the full server log before changing data files.

## Updating

Back up `plugins/LandWard/` first. Replace only the plugin JAR while the server is stopped. Never delete the database during an update unless you intentionally want to reset every land.
