# Architecture

LandWard is organized around independent domain, storage, protection, GUI, localization, and integration layers. Bukkit and Paper objects must not leak into storage records.

## Modules

- **domain** — lands, chunks, memberships and shared land-right decisions
- **storage** — a shared repository contract with SQLite and MySQL implementations
- **protection** — event listeners and cross-boundary protection rules
- **gui** — inventory menus and the chunk map
- **locale** — per-player translations with an English fallback
- **integration** — optional WorldGuard; map plugins (BlueMap, Squaremap and Pl3xMap); Vault economy; Plan analytics; PlaceholderAPI; and combat-tag adapters

Database work runs asynchronously. World and entity access remains on Paper's server thread or an appropriate scheduler.
