# LandWard

LandWard 1.0.5.0 adds Folia and CraftBukkit support in one universal JAR,
with region-safe warps and preserved clickable chat. Compatible with Velocity
proxy networks. See the [Wiki](https://github.com/DEVKruidenier/LandWard/wiki)
for configuration and guidance.

Update staging and PlaceholderAPI behavior are documented in the
[Wiki](https://github.com/DEVKruidenier/LandWard/wiki).

**Simple land claims. Reliable protection.**

LandWard is an open-source chunk-claiming plugin for Paper, Spigot, CraftBukkit and Folia survival servers. It
uses a visual in-game map instead of claim tools, selection wands, or mandatory
economies.

## Servers using LandWard

- **play.martialcraft.nl**
- Want your server listed here? Join **play.martialcraft.nl** and use
  `/mail send Kruidenier <yourServerIP>`.

**Supported servers:** Paper 26.2/26.3, Spigot 26.2/26.3, CraftBukkit 26.3 and Folia 26.3. Java 25 required.

**Compatible with Velocity proxy networks** — tested with Velocity 4.3.1 and a Paper 26.2 backend. Install LandWard on the backend server.

## Languages

**English is the default language.** LandWard automatically uses a supported
Minecraft client language when enabled. Server owners can change the default
and console language in `config.yml` and customize all text in `lang/<locale>.yml`.

**32 supported languages:** Arabic, Brazilian Portuguese, Czech, Danish, Dutch, English, Faroese, Filipino, Finnish, French, German, Hindi, Hungarian, Icelandic, Indonesian, Italian, Japanese, Korean, Malay, Norwegian Bokmål, Norwegian Nynorsk, Polish, Romanian, Russian, Simplified Chinese, Spanish, Swedish, Thai, Traditional Chinese, Turkish, Ukrainian and Vietnamese.

See [Languages and locale codes](https://github.com/DEVKruidenier/LandWard/wiki/Languages) for setup and automatic fallback behavior.

## Documentation

**Since 1.0.4.0:** player and admin chunk maps can be moved with four
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

**Since 1.0.4.0:** custom charts add storage type, configured default language,
payment mode, integration availability and grouped counts of player lands,
claimed chunks, Safe Zones and named warps. Counts use categories (0, 1–10,
11–50, 51–100, 101–500, 501+), never names or individual land records.

[View LandWard on bStats](https://bstats.org/plugin/bukkit/LandWard/35128)

## Folia and CraftBukkit

Since **1.0.5.0**, the universal JAR supports Folia 26.3 and CraftBukkit 26.3.
Scheduling and chat delivery are selected automatically. Optional integrations
require compatible builds for the selected server and Minecraft version.

## Build

```bash
./gradlew build
```

The distributable plugin JAR is written to `build/libs/LandWard-<version>.jar`.
The `-unshaded` JAR is a development artifact and omits bundled runtime libraries.

Since 1.0.2.1, the same distributable is used for every publication
platform. SQLite and MySQL drivers come from the supported server;
no extra installation or LandWard library download is required. The plugin still
includes its own connection pool, metrics client and Adventure support for Spigot and CraftBukkit.

## License

Copyright © 2026 DEVKruidenier.

LandWard is licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE).
