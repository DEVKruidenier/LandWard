# Architecture

LandWard is organized around independent domain, storage, protection, GUI, localization, and integration layers. Bukkit and Paper objects must not leak into storage records.

## Planned modules

- **domain** — lands, chunks, memberships, roles, and permission decisions
- **storage** — a shared repository contract with SQLite and MySQL implementations
- **protection** — event listeners and cross-boundary protection rules
- **gui** — inventory menus and the chunk map
- **locale** — per-player translations with an English fallback
- **integration** — optional WorldGuard, BlueMap, and Dynmap adapters

Database work runs asynchronously. World and entity access remains on Paper's server thread or an appropriate scheduler.
