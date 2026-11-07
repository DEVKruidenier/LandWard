# LandWard

**Simple land claims. Reliable protection.**

LandWard is an open-source chunk-claiming plugin for Paper survival servers. It
uses a visual in-game map instead of claim tools, selection wands, or mandatory
economies.

## Documentation

The [LandWard Wiki](https://github.com/DEVKruidenier/LandWard/wiki) is the
central and current documentation source. It covers installation, commands,
claim management, configuration, integrations, permissions, storage migration
and updates.

Completed features, fixes and behaviour changes are listed in the
[Changelog](CHANGELOG.md).

## Anonymous metrics

LandWard includes bStats for anonymous, aggregated usage statistics such as server
counts, software versions, and country-level adoption. It never collects player
names, chat, world data, server addresses, or exact locations. Metrics can be
disabled in LandWard's configuration or in the server-wide bStats configuration.

[View LandWard on bStats](https://bstats.org/plugin/bukkit/LandWard/35128)

## Build

```bash
./gradlew build
```

The distributable plugin JAR is written to `build/libs/LandWard-<version>.jar`.
The `-unshaded` JAR is a development artifact and does not include bStats.

## License

Copyright © 2026 DEVKruidenier.

LandWard is licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE).
