# Changelog

All notable changes to LandWard will be documented here.

## [Unreleased]

### Changed / Fixed — 1.0.6.0-beta2 (development preview)

- Keep both protected door halves synchronized when a permitted player opens or closes them.
- Support copper chests, including oxidized/waxed variants, and personal locks on levers/buttons.
- Allow the personal owner to expand a protected chest and break their own protected storage;
  retain the original owner/sharing on a double chest's remaining half.
- Allow separate adjacent chests, end invalid block selections and close the menu after
  successfully saving private protection. Update the denial text in all 32 languages.

### Added — 1.0.6.0-beta1 (development preview)

- Optional built-in personal protection for storage blocks, doors, trapdoors and
  fence gates, managed through private/shared access GUIs in all 32 languages.
- UUID-based block placement ownership, paired chest/door locks, land-owner
  management and a separate personal-lock administrator bypass.
- Transactional lock/share cleanup when members leave, ownership changes, land is
  deleted or chunks are unclaimed; storage migrations and backups retain lock data.
- Localized leave/removal/transfer warnings, owner filtering and paginated lists.
- Same-owner hopper automation with protected output inventories and inherited
  source access checks; foreign/unrecorded hoppers cannot drain private storage.
- Conservative automation safeguards and asynchronous personal-lock writes with
  fail-closed diagnostics. Existing installations explicitly opt in; disabling
  personal protection preserves data and leaves normal claim permissions active.

## [1.0.5.0] - 2026-11-18

### Added

- CraftBukkit 26.3 support, preserving
  clickable update/author links, hover hints and actionbar notifications without
  requiring Spigot chat classes or a separate chat plugin.
- Folia 26.3 support using player, region and
  global schedulers while retaining the same universal Paper/Spigot JAR.
- Region-owned destination checks and asynchronous Folia teleports, including
  collision checks spanning chunk borders and indoor warp landings.

### Changed

- Warp warmups follow the player and discard stale or cancelled requests before
  teleporting; callbacks stop when the player disconnects or the plugin shuts down.
- Shared menu state, configuration/locale publication and environment toggles
  support concurrent regions. Migration verification and activation serialize
  with claim mutations. Asynchronous placeholders use immutable player snapshots.

### Compatibility

- Modrinth loader metadata now includes Bukkit and Folia alongside Paper and
  Spigot; the same universal JAR is used on each supported backend.

## [1.0.4.1] - 2026-11-18

### Fixed

- Preserve exact warp yaw/pitch when reading existing MySQL/MariaDB FLOAT
  columns, preventing false migration verification failures for normal angles.
- Resume a previously imported target only when every snapshot field matches
  the active source exactly; never overwrite a nonmatching target.
- Migration errors identify the failed phase, record counts and the first
  differing field, plus backup and active-storage state. SQL errors include
  SQLState/vendor codes without dumping connection credentials.

## [1.0.4.0] - 2026-11-18

### Added

- Nineteen complete language catalogs (32 total), including Nordic, European
  and Asian languages, Arabic and Traditional Chinese. Recognize Java Bokmål
  `no_no` as `nb_NO`; unsupported clients use the configured default language.

- Bounded chunk-map navigation for player lands, Safe Zones and remote admin
  player-land maps. Four footer controls pan one chunk per click without travel;
  keep the viewport after claiming, unclaiming or cancelling confirmations.
  Configure the maximum center offset with `claims.map-navigation-limit` (30).

- Anonymous bStats feature-usage charts for storage backend, configured default
  language, payment mode, integration availability and grouped counts of player
  lands, claimed chunks, Safe Zones and named warps. Both opt-outs are respected;
  reloading LandWard applies its metrics switch without restarting the server.

## [1.0.3.0] - 2026-11-17

### Added

- Per-land environment controls for fire spread/burning, fall,
  fire/lava/explosion player damage, separate TNT/other explosion block damage,
  mob block changes and internal water/lava flow, for player lands and Safe Zones.
- Named player-land warps with owner management, member/admin
  travel, explicit rank limits, cancellable warmups and checked destinations.
- Persist the new settings and warps in SQLite/MySQL and version-4 migration
  backups, including automatic warp cleanup when chunks or lands are removed.

### Changed

- Fire spread and block burning are intentionally denied in upgraded claims.
  Existing survival damage behavior is retained until changed per land.
- Add adjacent categories for access/members and environment/damage;
  retain the existing nine access controls and their ordering in their submenu.

### Fixed

- Put Warps directly in the land main menu, with dedicated warp rename/teleport
  labels and concise localized travel hints.
- Check precise block collision shapes for indoor, carpet and slab destinations
  while retaining support, obstruction, liquid and hazard checks.

## [1.0.2.2] - 2026-11-16

### Fixed

- Make missing claim, owned-land and owner PlaceholderAPI text configurable through language keys, empty by default. Translate Safe Zone owner labels instead of showing an internal UUID.
- Explain /landward update, its required permission and download directory in all 13 languages.
- Preserve verified GitHub asset filenames, isolate temporary downloads and never overwrite a different existing update.

## [1.0.2.1] - 2026-11-15

### Changed

- Reuse the SQLite and MySQL JDBC drivers already supplied by supported Paper
  and Spigot servers instead of bundling duplicate drivers and SQLite natives.
  No extra plugin, manual library installation or LandWard runtime download is required.

### Fixed

- Open the full administrator player-land menu on selection. View the selected
  claim map remotely without teleporting; teleportation has its own button.
- Keep player-land owners able to use their own doors, trapdoors, signs, buttons
  and levers when the corresponding member interaction settings are disabled.
- Make legacy schema upgrades work on MySQL as well as MariaDB by checking
  columns before adding them instead of using MariaDB-only ALTER TABLE syntax.

## [1.0.2.0] - 2026-11-11

### Added

- Colored, localized update summaries in the console and on login for operators
  and players with `landward.pladmin`, controlled by `updates.notify-on-join`.

### Fixed

- Compare all four release-version components and refresh GitHub metadata before
  showing update information or selecting a download.
- Restore clickable update status on Paper and Spigot and target LandWard's
  namespaced command so another plugin's `/claim` cannot intercept the click.
- Follow GitHub download redirects and select the verified universal release
  JAR rather than a reduced Hangar artifact or stale release metadata.

## [1.0.1.6] - 2026-11-11

### Added

- Separate building and entity-interaction controls for public Safe Zone access.
- Localized hover explanations for settings and menu actions in all 13 languages.

### Changed

- Compact, aligned player and administrator menus with consistent back/close
  navigation and clearly positioned settings, member management and delete controls.
- Paginated world, land and member lists with red/blue page controls, remembered
  pages and valid-page recovery after entries are removed.
- PvP controls show a short unavailable explanation when world or WorldGuard
  rules prohibit PvP; clicking cannot override those rules or change the stored setting.

### Fixed

- Native anvil land creation, renaming and owner transfer now use the entered
  text on Paper and Spigot. Confirmation labels can no longer be saved as names.
- Bundle the Adventure text API needed by standalone Spigot installations.
- Honor effective WorldGuard PvP flags, including the global region, when
  displaying and handling PvP controls, independently of operator bypass.
- Keep the bundled config version synchronized with the release version.

## [1.0.1.5] - 2026-11-08

### Fixed

- Added a green confirmation control to native anvil screens, so land creation,
  renaming and owner transfer work on Spigot even when its output slot cannot
  render a result.

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
