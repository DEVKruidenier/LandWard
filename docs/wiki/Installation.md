# Installation

1. Download the normal `LandWard-<version>.jar` release file. Do not use the `-unshaded` development artifact.
2. Stop the Paper server.
3. Put the JAR in `plugins/`.
4. Start the server once. LandWard creates `plugins/LandWard/config.yml` and, by default, the SQLite database `landward.db`.
5. Configure storage, language and claims as required, then restart the server.

At startup, the LandWard banner reports the active storage backend and the number of claims loaded. A failed storage connection prevents the plugin from enabling; check the full server log before changing data files.

## Updating

Back up `plugins/LandWard/` first. Replace only the plugin JAR while the server is stopped. Never delete the database during an update unless you intentionally want to reset every land.

When an administrator sees an **Outdated** status in `/claim info`, clicking it (or running `/claim update`) can download the official digest-verified JAR into `plugins/LandWard/updates/`. This is a staging area: LandWard deliberately never replaces a JAR while Paper has it loaded. Stop the server, replace the JAR with the staged file, then start Paper normally.
