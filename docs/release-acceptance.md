# Integrated release acceptance

The first cohesive LandWard test release must verify: SQLite and MySQL storage;
chunk claiming, unclaiming and limits; free, XP and Vault payments; owner and
member permissions; protection for blocks, containers, fluids and entities;
the chunk map and management GUIs; WorldGuard overlap denial; BlueMap, Squaremap
and Pl3xMap markers; all bundled player-facing translations; update checks, metrics and
startup diagnostics. It must also verify the separate player-claims and
admin-claims maps: type separation, purple markers, WorldGuard/spawn overlap
behaviour, trusted members, PvP, mob, container, door and interaction toggles.
A full build, unit tests and a local Paper runtime test are required before the
test JAR is installed.
