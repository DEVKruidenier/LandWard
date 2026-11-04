# LandWard

**Simple land claims. Reliable protection.**

LandWard is an open-source chunk-claiming plugin for Paper survival servers. It is designed around a simple command flow and a visual chunk map instead of claim tools, selection wands, or mandatory economies.

> [!IMPORTANT]
> LandWard is in early development and is not ready for production servers.

## Goals

- Claim the current chunk with straightforward `/land` commands.
- Manage lands, members, roles, and permissions in an in-game GUI.
- Show nearby chunks as a color-coded map centered on the player.
- Protect blocks, containers, entities, fluids, pistons, and explosions.
- Support SQLite and MySQL storage.
- Integrate with WorldGuard, BlueMap, and Dynmap when installed.
- Localize every player-facing and administrative message.

## Requirements

- Paper 26.2 or newer
- Java 25 or newer

## Build

```bash
./gradlew build
```

The plugin JAR is written to `build/libs/`.

## Project status

The repository currently contains the project foundation, localization loader, configuration contract, and documentation skeleton. Claiming and protection behavior will be implemented incrementally with tests.

## Documentation

- [Architecture](docs/architecture.md)
- [Commands](docs/commands.md)
- [Configuration](docs/configuration.md)
- [Integrations](docs/integrations.md)
- [Translations](docs/translations.md)

## License

Copyright © 2026 DEVKruidenier.

LandWard is licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE).
