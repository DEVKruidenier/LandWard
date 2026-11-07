# LandWard Project Standards

This document records product decisions that apply to all future LandWard work.
When a new feature conflicts with one of these rules, update this document and
the relevant documentation in the same change.

## Documentation

- The repository documentation in `docs/` and the public GitHub Wiki describe
  the same product and must be updated together.
- Configuration is shown as complete, nested YAML sections. Do not document
  settings only as dotted paths such as `section.option`.
- Write every Minecraft command with its leading `/` in prose, tables and code
  blocks, for example `/claimadmin reload`.
- Documentation, configuration comments, commands, permissions and GUI text
  must reflect the shipped behaviour. Remove obsolete integrations and flows
  rather than leaving historical instructions behind.
- Every player-facing feature needs an English explanation. The GitHub Wiki is
  concise and task-oriented; the repository documentation can add release and
  test details.

## Product and interface

- LandWard is GUI-first. `/claim` opens the relevant menu; normal claim
  creation, management and confirmation should not require typed mutation
  commands.
- The plugin protects 16×16 chunks. The current chunk is clearly indicated in
  the map, maps are centred on it, and destructive actions always ask for
  confirmation.
- Player lands and server-owned Safe Zones are separate concepts, maps and
  moderation flows. Safe Zones use the selected language’s default name.
- Admin bypass is opt-in through `/claim bypass`, requires
  `landward.bypass`, and turns off when the player logs out.
- Keep menus compact, logically grouped, and consistently place the back arrow
  in the lower-right slot.

## Configuration, language and storage

- All configurable player-facing text belongs in language files. The bundled
  languages are English, Dutch, German, French, Spanish, Brazilian Portuguese,
  Italian, Polish, Turkish, Russian, Simplified Chinese, Japanese and Korean.
- New configuration entries belong in the logical existing section and include
  clear English comments. Every comment must state what the setting controls,
  who or what it applies to, its practical effect, and any important exception
  or interaction (for example, player claims versus ClaimAdmin Safe Zones).
  Language and storage stay at the top of `config.yml`.
- Before publishing a configuration change, verify that its wording matches the
  shipped behavior in `config.yml`, repository documentation and the public
  GitHub Wiki. Avoid ambiguous words such as “claims” when only player claims
  or only Safe Zones are affected.
- Configuration updates preserve administrator changes while adding new
  defaults; language files are refreshed without discarding custom additions.
- SQLite and MySQL/MariaDB are supported. Migration is explicit, validated,
  backed up, and never silently overwrites an existing target.

## Compatibility and integrations

- Paper is the primary platform. Compatibility claims are made only after a
  clean test run on that specific Paper version.
- Optional integrations must use soft dependencies and gracefully do nothing
  when the companion plugin is absent. Supported maps are BlueMap, Squaremap
  and Pl3xMap; Dynmap is not supported.
- Any remaining legacy Dynmap code or configuration must be removed before a
  public release.
- Claiming honours WorldGuard regions, vanilla spawn protection and EssentialsX
  AntiBuild where applicable. Administrative Safe Zones may deliberately cover
  protected spawn areas.
- Vault/EssentialsX economy, Plan, PlaceholderAPI, bStats and configured combat
  taggers remain optional. No player should need to install an extra plugin
  merely to run LandWard.

## Quality and releases

- Do not ship temporary fixes. Implement the actual durable behaviour, test it,
  and keep test-only helpers out of the release JAR.
- Restarting the local test server is allowed when needed for a reliable test;
  preserve user data unless a reset is explicitly requested.
- A release update is staged and verified before a manual restart; it never
  replaces a running plugin JAR. Live-update feasibility remains an explicit
  future investigation.
- Every new feature, adjustment, correction or cleanup follows this order:
  1. create or update an English GitHub Project item;
  2. move the Project item to **In Progress**;
  3. implement the change, including a logically placed and clearly commented
     configuration entry when configuration is needed;
  4. update every bundled language file for new or changed player/admin text;
  5. update the GitHub Wiki and the matching repository documentation together;
  6. run relevant tests;
  7. publish the change;
  8. verify the actual GitHub repository documentation and
     GitHub Wiki for stale, contradictory or superseded information;
  9. move the Project item to **Done** only after the implementation,
     configuration, translations, documentation, published-GitHub verification
     and relevant tests are complete.

## Storage administration

- Storage migration and maintenance require `landward.dbadmin` (operators have
  it by default). Regular ClaimAdmin access is not enough.
- A verified migration may switch between SQLite and MySQL/MariaDB while Paper
  stays online. LandWard temporarily pauses claim changes, creates a verified
  backup, checks the destination and activates the new backend only after a
  successful check. Players are not kicked and a restart is not required.
