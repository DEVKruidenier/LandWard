# LandWard

**Simple land claims. Reliable protection.**

LandWard is an open-source chunk-claiming plugin for Paper survival servers. It is designed around a simple command flow and a visual chunk map instead of claim tools, selection wands, or mandatory economies.

> [!IMPORTANT]
> LandWard is in early development and is not ready for production servers.

## Goals

- Claim nearby chunks with a visual GUI map opened using /claim.
- Manage lands, members and shared member rights in an in-game GUI.
- Show nearby chunks as a color-coded map centered on the player.
- Protect blocks, containers, entities, fluids, pistons, and explosions.
- Support SQLite and MySQL storage.
- Respect vanilla spawn protection and integrate with WorldGuard, BlueMap, Squaremap, Pl3xMap, Plan, PlaceholderAPI and EssentialsX modules when installed.
- Localize every player-facing and administrative message.

## Anonymous metrics

LandWard includes bStats for anonymous, aggregated usage statistics such as server
counts, software versions, and country-level adoption. It never collects player
names, chat, world data, server addresses, or exact locations. Metrics can be
disabled in LandWard's configuration or in the server-wide bStats configuration.

[View LandWard on bStats](https://bstats.org/plugin/bukkit/LandWard/35128)

## Requirements

- Paper 26.2 (tested)
- Java 25 (tested)

Paper 26.3 is a planned compatibility target, not yet a supported release.
Folia is currently unsupported while LandWard is adapted and tested for its
regionised scheduler model. See [Compatibility](docs/compatibility.md).

## Build

```bash
./gradlew build
```

The distributable plugin JAR is written to `build/libs/LandWard-<version>.jar`.
The `-unshaded` JAR is a development artifact and does not include bStats.

## Project status

LandWard is actively developed and not yet production-ready. The current development
build includes persistent SQLite/MySQL storage, GUI-first chunk claims, shared
member settings, anvil-based naming, claim protection, XP/Vault payments, WorldGuard
checks, BlueMap/Squaremap/Pl3xMap markers, PlaceholderAPI placeholders, combat-tag
protection and an optional Plan analytics panel. The final integration and gameplay
test pass is still in progress before the first stable release.

## Documentation

- [Architecture](docs/architecture.md)
- [Commands](docs/commands.md)
- [Configuration](docs/configuration.md)
- [Integrations](docs/integrations.md)
- [Compatibility](docs/compatibility.md)
- [Translations](docs/translations.md)
- [Roadmap](docs/roadmap.md)
- [Public wiki](https://github.com/DEVKruidenier/LandWard/wiki)

## License

Copyright © 2026 DEVKruidenier.

LandWard is licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE).
