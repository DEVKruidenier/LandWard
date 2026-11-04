# LandWard

**Simple land claims. Reliable protection.**

LandWard is an open-source chunk-claiming plugin for Paper survival servers. It is designed around a simple command flow and a visual chunk map instead of claim tools, selection wands, or mandatory economies.

> [!IMPORTANT]
> LandWard is in early development and is not ready for production servers.

## Goals

- Claim the current chunk with straightforward `/claim` commands.
- Manage lands, members, roles, and permissions in an in-game GUI.
- Show nearby chunks as a color-coded map centered on the player.
- Protect blocks, containers, entities, fluids, pistons, and explosions.
- Support SQLite and MySQL storage.
- Respect vanilla spawn protection and integrate with WorldGuard, BlueMap, Dynmap, Plan, and EssentialsX modules when installed.
- Localize every player-facing and administrative message.

## Anonymous metrics

LandWard includes bStats for anonymous, aggregated usage statistics such as server
counts, software versions, and country-level adoption. It never collects player
names, chat, world data, server addresses, or exact locations. Metrics can be
disabled in LandWard's configuration or in the server-wide bStats configuration.

[View LandWard on bStats](https://bstats.org/plugin/bukkit/LandWard/35128)

## Requirements

- Paper 26.2 or newer
- Java 25 or newer

## Build

```bash
./gradlew build
```

The distributable plugin JAR is written to `build/libs/LandWard-<version>.jar`.
The `-unshaded` JAR is a development artifact and does not include bStats.

## Project status

LandWard is actively developed and not yet production-ready. The current development
build includes persistent SQLite/MySQL storage, chunk claims, member management GUI,
claim protection, XP/Vault payments, WorldGuard checks, BlueMap/Dynmap markers and
an optional Plan analytics panel. The final integration and gameplay test pass is still
in progress before the first stable release.

## Documentation

- [Architecture](docs/architecture.md)
- [Commands](docs/commands.md)
- [Configuration](docs/configuration.md)
- [Integrations](docs/integrations.md)
- [Translations](docs/translations.md)

## License

Copyright © 2026 DEVKruidenier.

LandWard is licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE).
