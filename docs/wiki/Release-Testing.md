# Release testing

Run this checklist against the shaded `LandWard.jar` on a clean Paper 26.2 test server before a public release.

## Core flow

1. Start the server and check the LandWard startup panel for version, storage type, loaded claims, language, metrics and update state.
2. Create a land through `/claim`, claim and unclaim chunks through the GUI, rename through the anvil screen, and manage members and their rights.
3. Verify the current chunk remains at slot 22, coordinates and owners are visible, and every GUI back arrow is in the lower-right slot.
4. Test block, container, entity, fluid, piston, explosion and fire-spread protection as a non-member.
5. Test entry/exit notifications, `/land` and `/landward` aliases, `/claim info`, and `/claim bypass`.

## Admin claims

1. Keep player claims and purple admin claims separate in ClaimAdmin.
2. Verify only the corresponding map can remove its claim type.
3. Create an admin claim over a WorldGuard region; verify a player claim is rejected there.
4. Test each admin-claim protection toggle: PvP, hostile/passive mobs, hostile spawning, containers, doors, signs/buttons/levers and trusted members.

## Storage and integrations

1. Test free, XP and Vault payments, including failed-payment/refund paths.
2. Migrate SQLite → MySQL → SQLite with `/claimadmin migrate <target> confirm` while no players are online. Check the target first with `/claimadmin storage status <target>`; verify all lands, chunks, members, permissions and admin claims after both restarts.
3. Verify WorldGuard and vanilla spawn protection deny normal claims.
4. Verify exact 16×16 green player and purple admin markers in BlueMap, Squaremap and Pl3xMap.
5. Verify the documented PlaceholderAPI placeholders and Plan statistics when those optional plugins are installed.
6. Test LandWard's fallback combat tag and installed supported combat taggers.

## Release safeguards

1. Verify `/claim update` only stages a checksum-verified GitHub Release JAR and never replaces the running JAR.
2. Run `./gradlew test shadowJar --no-daemon --console=plain` successfully and retain its output with the release artifacts.
