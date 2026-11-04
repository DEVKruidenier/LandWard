# Configuration

`plugins/LandWard/config.yml` controls the installed server.

## Claims

```yaml
claims:
  require-adjacent-chunks: true
  maximum-lands-per-player: 1
  maximum-chunks-per-land: 25 # -1 means unlimited
  enabled-worlds: []
  disabled-worlds: []
```

An empty `enabled-worlds` list permits every world except those explicitly listed under `disabled-worlds`.

## Payments

```yaml
payments:
  mode: free
  experience-points-per-chunk: 150
  vault-money-per-chunk: 100.0
```

`free` does not charge players. `experience` charges the configured number of total experience points for every new claimed chunk. `vault` charges the active Vault economy provider (such as EssentialsX Economy); LandWard refuses the claim when Vault or its economy provider is unavailable.

## Language and integrations

`language.default` selects the fallback language. With `language.use-client-locale: true`, a player's Minecraft locale is used when a bundled translation is available.

WorldGuard, BlueMap and Dynmap settings are in the `integrations` section. With `integrations.worldguard.deny-any-overlap: true`, LandWard refuses a chunk if it overlaps any WorldGuard region, including spawn regions. An integration setting does not install the dependency; it only enables LandWard's integration when that plugin is present and supported.
