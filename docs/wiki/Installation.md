# Installation

1. Download the latest `LandWard-<version>.jar` from the GitHub Releases page.
2. Stop the Paper server.
3. Put the JAR in `plugins/`.
4. Start the server once. LandWard creates its configuration and, by default, an SQLite database.
5. Change language, claim limits or storage only if needed, then restart the server.

At startup, the LandWard banner shows the active storage type and the number of loaded claims. If the database cannot be reached, check the server log before changing any data files.

## Updating

Stop the server, back up `plugins/LandWard/`, then replace only the plugin JAR. Never delete the database during an update unless you intentionally want to remove every land.

When `/claim info` shows **Outdated**, an administrator can click it or run `/claim update`. LandWard downloads the verified JAR into `plugins/LandWard/updates/`; it does not replace the running plugin. Stop the server, replace the JAR with the downloaded file, then start Paper normally.

---

Next: [Configuration](Configuration) · [Claim Management](Claim-Management)
