# Configuration

`plugins/LandWard/config.yml` controls the installed server.

## Claims

```yaml
claims:
  require-adjacent-chunks: true
  maximum-lands-per-player: 1
  maximum-chunks-per-land: 25 # -1 means unlimited
  # Example: disabled-worlds: [myworld]
  disabled-worlds: []
```

LandWard is active in every world except those explicitly listed under `disabled-worlds`.

## Admin claims

```yaml
admin-claims:
  default-name: "" # empty uses the configured language, e.g. Safe Zone / Veilige Zone
  protection:
    allow-pvp: false
    allow-hostile-mob-kills: false
    allow-passive-mob-kills: true
    allow-containers: false
    allow-doors: false
    allow-interactions: false
    allow-hostile-mob-spawns: false
```

Admin claims are server-owned purple areas, designed for spawn and other server facilities. They are created only from the **Admin claims** map and may overlap WorldGuard and vanilla spawn-protection regions. Trusted members added through Admin claim settings always have full access. The public-access toggles can also be changed in that GUI.

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

WorldGuard, BlueMap, Dynmap and Plan settings are in the `integrations` section. With `integrations.worldguard.deny-any-overlap: true`, LandWard refuses a chunk if it overlaps any WorldGuard region, including spawn regions. `integrations.plan.enabled: true` makes land statistics available in Plan's web panel when Plan is installed. An integration setting does not install the dependency; it only enables LandWard's integration when that plugin is present and supported.
