# Release test matrix

Run this matrix against the shaded `LandWard.jar` on a clean, supported Paper
test server before creating the first public release. Record the Paper build,
Java version, server log and each pass/fail result with the release notes.

## Runtime compatibility

- [ ] Verify the tested runtime is Paper 26.2 with Java 25.
- [ ] For every additional Paper release (including 26.3), use a copied test
  world and run this complete matrix before declaring it supported.
- [ ] Do not advertise Folia compatibility or add `folia-supported: true` until
  LandWard has dedicated regionised-threading support and Folia acceptance tests.

## Startup and configuration

- [ ] Start with a fresh `plugins/LandWard` directory. Confirm the startup panel
  identifies the version, storage type, loaded-claim count, selected language,
  metrics state and update-check state.
- [ ] Confirm `config.yml` contains documented English comments, `myworld` in
  the following list, and every bundled language file:

  ```yml
  claims:
    disabled-worlds:
      - myworld
  ```
- [ ] Remove `myworld`, restart, then use **ClaimAdmin → Active worlds** to
  disable and re-enable a world. Confirm the setting persists without removing
  claims in that world.
- [ ] Change a safe setting, restart and confirm configuration defaults were
  merged without overwriting the changed value.

## Player land workflow

- [ ] With `landward.use`, run `/claim`, create a named land through the GUI,
  and claim an adjacent grey tile from the chunk map.
- [ ] Confirm the current chunk stays centred on slot 22, chunk coordinates and
  owners are shown, and the back arrow is in the lower-right slot.
- [ ] Verify unclaim confirmation, final-chunk deletion, anvil rename (including
  rename cost `0`), member addition/removal and shared member build/container/entity
  settings.
- [ ] Verify `/land` and `/landward` route to `/claim`, while `/claim info`
  shows the version, author and update state.
- [ ] Verify entry/exit notifications contain only the owner/land message.

## Protection and admin claims

- [ ] As a non-member, verify block changes, containers, entities, fluids,
  pistons, explosions and fire spread cannot damage another player's claim.
- [ ] Verify `/claim bypass` requires `landward.bypass`, is opt-in and is
  cleared on logout.
- [ ] In **ClaimAdmin**, verify player claims and purple admin claims are kept
  separate. Ensure an admin cannot delete one type from the other map.
- [ ] Create an admin claim over a WorldGuard region and verify the player claim
  map still rejects that overlap.
- [ ] Test admin-claim PvP, hostile/passive mob kills, hostile spawning,
  containers, doors, signs/buttons/levers and trusted admin members against each
  corresponding config switch.

## Payments, storage and integrations

- [ ] Test the following `mode` values; confirm failed
  claims refund XP/money and an unavailable Vault provider is reported safely.

  ```yml
  payments:
    mode: free # then experience and vault
  ```
- [ ] With players online, run `/claimadmin migrate mysql confirm` from the
  console using a dedicated MySQL database. Confirm claim changes pause only
  during the migration, no player is kicked and MySQL becomes active without a
  restart. Validate every land, chunk, member, permission and admin-claim
  marker, then migrate back to SQLite.
- [ ] Before migration, use `/claimadmin storage status mysql`. Verify that
  migration and maintenance require `landward.dbadmin`. If a target must be
  deliberately reset, verify its backup and test `/claimadmin storage clear mysql confirm` with no players online.
- [ ] Verify WorldGuard overlap denial and vanilla spawn-protection denial.
- [ ] Verify BlueMap, Squaremap and Pl3xMap show normal claims in green and
  admin claims in purple, at exact 16×16 chunk boundaries.
- [ ] With PlaceholderAPI, verify every documented `%landward_*%` placeholder.
  With Plan installed, verify the read-only LandWard statistics panel.
- [ ] Test LandWard's fallback combat tag: after a real PvP hit, both players
  are tagged; tagged players cannot abandon claims, and tagged combatants can
  continue their PvP encounter inside a protected claim until expiry.
- [ ] For every externally installed combat tagger (CombatLogX, DeluxeCombat,
  PvPManager or EternalCombat), install its matching PlaceholderAPI expansion
  and repeat the fallback test. An unresolved placeholder must never tag a
  player or block a command.

## Release safeguards

- [ ] Check `/claim update` accepts only a verified GitHub Release asset, stages
  it without replacing the running JAR, and rejects an unexpected checksum.
- [ ] Confirm bStats remains optional and does not require a separate plugin.
- [ ] Run `./gradlew test shadowJar --no-daemon --console=plain` and preserve a
  successful log with the release artifacts.
