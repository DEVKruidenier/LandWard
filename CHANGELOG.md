# Changelog

All notable changes to LandWard will be documented here.

## [Unreleased]

## [1.0.1.4] - 2026-11-08

### Fixed

- Made native anvil confirmation read directly from Spigot's rename field,
  restoring land creation and admin owner entry when the output slot is not
  rendered by Spigot.

## [1.0.1.3] - 2026-11-08

### Fixed

- Restored native anvil text entry for creating, renaming and transferring
  lands on Spigot by replacing Paper-only `Player#openAnvil` calls.

## [1.0.1.2] - 2026-11-08

### Fixed

- Made the version value in the packaged `plugin.yml` a Gradle build input, so
  every LandWard JAR reports its exact release version after a version change.

## [1.0.1.1] - 2026-11-08

### Fixed

- Restored `/claim` and client-language selection on Spigot by using its
  cross-platform player locale API.
- Removed the internal `.config-defaults.yml` file; configuration updates now
  merge new defaults directly from the JAR while preserving existing values.

## [1.0.1.0] - 2026-11-08

### Added

- Player-menu controls to teleport to the first claimed chunk after a configurable
  delay (two seconds by default), and to delete the player's complete land with
  a confirmation screen.

## [1.0.0.2] - 2026-11-08

### Fixed

- Replaced the remaining Paper-only GUI, item metadata and player-message API
  calls with Bukkit/Spigot-safe equivalents. ClaimAdmin and all LandWard menus
  now work on Spigot 26.2 as well as Paper.

## [1.0.0.1] - 2026-11-08

### Fixed

- Added verified Spigot 26.2 compatibility while retaining Paper support.
- Replaced Paper-only logger and plugin-metadata calls with safe cross-platform
  fallbacks.

## [1.0.0.0] - 2026-11-08

First public LandWard release for Paper 26.2+ and Java 25.

### Added

- Paper and Java 25 project foundation with SQLite storage, optional
  MySQL/MariaDB storage and live, validated storage migration tools.
- GUI-first player lands: create a named land, claim and unclaim chunks from a
  centred chunk map, rename lands through an anvil interface, manage members,
  and configure land settings.
- Separate ClaimAdmin Safe Zones for spawn and server areas, including their
  own map, settings, member management and player-claim administration tools.
- ClaimAdmin removal controls for individual player-claim chunks, complete
  player lands and all Safe Zones, each protected by a confirmation screen.
- Individual member permissions for building, containers and entities, with
  configurable default permissions for both player lands and Safe Zones.
- Claim protection for building, containers, entities, fire spread, PvP rules,
  doors, interactions, hostile mobs and passive mobs where configured.
- World enable/disable controls, owner-only land limits, optional adjacent
  claiming, configurable XP/Vault economy prices and claim payment messages.
- `/claim` as the primary command, with `/land` and `/landward` aliases for
  every Claim command; `/claimadmin` for administration and `/claim bypass`
  for opt-in administrator bypass.
- Localized language files, automatic client-language selection, structured
  coloured startup diagnostics and configurable claim-entry messages.
- Optional integrations for WorldGuard, vanilla spawn protection, EssentialsX
  AntiBuild, Vault, EssentialsX Economy, BlueMap, Squaremap, Pl3xMap, Plan,
  PlaceholderAPI, bStats and configured combat taggers.
- PlaceholderAPI placeholders, Plan statistics, public GitHub Wiki, release
  update checks and a staged checksum-verified update download.

### Changed

- Configuration updates preserve administrator values while adding new defaults
  and refreshing bundled language files.
- `config.yml` now begins with an automatically maintained `plugin-version`
  reference for the installed JAR.
- Documentation is organised around the GitHub Wiki as the administrator-facing
  source, with matching repository documentation for technical details.

### Fixed

- Corrected BlueMap chunk geometry and refreshed markers after claim changes.
- Prevented temporary anvil rename items from entering player inventories.
- Corrected the Plan statistics layout and kept it in Plan's standard Plugins
  Overview instead of adding a duplicate LandWard navigation item.
