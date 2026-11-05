# Compatibility

LandWard intentionally lists only configurations that have been exercised on a
local server and in the automated build. This avoids presenting a platform as
supported merely because the plugin happens to load.

## Supported runtime

| Server software | Minecraft version | Java | Status |
| --- | --- | --- | --- |
| Paper | 26.2 | 25 | Tested locally |

The current verification server uses Paper 26.2 with Java 25. Run the complete
[release test matrix](release-test-matrix.md) again before adding a Paper
version to this table or publishing it as compatible on a download platform.

## Planned Paper support

Paper 26.3 is available, but is not yet listed as supported. It needs a fresh
server test with a copied production-like configuration, the full release test
matrix and the optional integrations enabled where available. Keep a backup of
the test world: Paper warns that worlds upgraded to 26.3 cannot be downgraded.

## Folia

Folia is **not supported** in the current release line. LandWard must first be
adapted and tested for Folia's regionised threading model, including region and
entity schedulers for all location- and player-bound work. Do not add
`folia-supported: true` to `plugin.yml` until that work and dedicated Folia
acceptance tests have passed.

PaperMC explicitly notes that the metadata flag alone does not make a plugin
Folia-compatible; platform-aware schedulers are required. This is why LandWard
fails safely by making no Folia compatibility claim today.

## Other platforms

LandWard is built for Paper. It is not currently supported on Folia, Spigot,
Purpur, Bukkit, proxy platforms or mod loaders. A server may load the JAR on a
related platform, but that is not a compatibility guarantee.
