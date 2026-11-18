# LandWard

LandWard 1.0.3.0 adds per-land environmental protection settings and named land
warps. See the [documentation](https://github.com/DEVKruidenier/LandWard/wiki/Protection-and-Warps)
for defaults, permissions, migration behavior and safe teleport rules.

Update staging and PlaceholderAPI behavior are documented in the
[Wiki](https://github.com/DEVKruidenier/LandWard/wiki).

**Simple land claims. Reliable protection.**

LandWard is an open-source chunk-claiming plugin for Paper and Spigot survival servers. It
uses a visual in-game map instead of claim tools, selection wands, or mandatory
economies.

## Servers using LandWard

- **play.martialcraft.nl**
- Want your server listed here? Join **play.martialcraft.nl** and use
  `/mail send Kruidenier <yourServerIP>`.

## Documentation

**Upcoming 1.0.4.0-beta2:** player and admin chunk maps can be moved with four
footer controls without teleporting. The configurable center limit defaults to
30 chunks per direction. See the [map guide](https://github.com/DEVKruidenier/LandWard/wiki/Claim-Management).

The [LandWard Wiki](https://github.com/DEVKruidenier/LandWard/wiki) is the
central and current documentation source. It covers installation, commands,
claim management, configuration, integrations, permissions, storage migration
and updates.

Completed features, fixes and behaviour changes are listed in the
[Changelog](CHANGELOG.md).

## Anonymous metrics

LandWard includes bStats for anonymous, aggregated usage statistics such as server
counts, software versions, and country-level adoption. It never collects player
names, chat, world names or coordinates, server addresses, or exact locations. Metrics can be
disabled in LandWard's configuration or in the server-wide bStats configuration.

**Upcoming 1.0.4.0 (beta1):** custom charts add storage type, configured default language,
payment mode, integration availability and grouped counts of player lands,
claimed chunks, Safe Zones and named warps. Counts use categories (0, 1–10,
11–50, 51–100, 101–500, 501+), never names or individual land records.
These charts are not included in release 1.0.3.0.

[View LandWard on bStats](https://bstats.org/plugin/bukkit/LandWard/35128)

## Build

```bash
./gradlew build
```

The distributable plugin JAR is written to `build/libs/LandWard-<version>.jar`.
The `-unshaded` JAR is a development artifact and omits bundled runtime libraries.

Since 1.0.2.1, the same distributable is used for every publication
platform. SQLite and MySQL drivers come from the supported Paper/Spigot server;
no extra installation or LandWard library download is required. The plugin still
includes its own connection pool, metrics client and Adventure support for Spigot.

## License

Copyright © 2026 DEVKruidenier.

LandWard is licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE).
