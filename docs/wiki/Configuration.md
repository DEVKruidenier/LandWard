# Configuration

`plugins/LandWard/config.yml` is grouped by concern: language and storage first, then claims, notifications, payments and integrations. Every generated setting includes an English explanation in the file itself.

> **Safe updates:** LandWard retains administrator-made values. On an update it adds genuinely new defaults, preserves values that were changed by the server owner, creates a configuration baseline/backup for comparison, and removes obsolete layout keys when the new structure replaces them. Review the generated file after each major update.

## Language and storage

```yaml
language:
  default: en_US
  use-client-locale: true

storage:
  type: sqlite
```

Language packs live in `plugins/LandWard/lang/`. LandWard includes English, Dutch, German, French, Spanish, Brazilian Portuguese, Italian, Polish, Turkish, Russian, Simplified Chinese, Japanese and Korean. Every player-facing message is loaded from these files; `use-client-locale` selects the matching bundled pack when possible, then falls back to the `default` value in the language section above.

See [Storage](Storage) before changing the backend.

## Claims and worlds

```yaml
claims:
  require-adjacent-chunks: true
  maximum-lands-per-player: 1
  maximum-chunks-per-land: 25 # -1 means unlimited
  # LandWard is active everywhere except worlds listed here.
  disabled-worlds:
    - myworld
```

LandWard is active in every world by default. Keep only worlds where claims should be unavailable under `disabled-worlds`; the included `myworld` is an example and can be removed. The setting scopes claiming, protection, entry/exit notices and map markers.

## Payments and rename cost

```yaml
payments:
  mode: free # free, experience or vault
  experience-points-per-chunk: 150
  vault-money-per-chunk: 100.0

rename:
  experience-level-cost: 0
```

- `free` charges nothing.
- `experience` charges total experience points for each newly claimed chunk.
- `vault` uses Vault’s active economy provider, such as EssentialsX Economy.
- `experience-level-cost: 0` keeps renaming free; a positive value charges that many XP levels after a successful anvil confirmation.

## Admin claims

```yaml
admin-claims:
  default-name: "" # empty uses Safe Zone / Veilige Zone from the selected language
  protection:
    allow-pvp: false
    allow-hostile-mob-kills: false
    allow-passive-mob-kills: true
    allow-containers: false
    allow-doors: false
    allow-interactions: false
    allow-hostile-mob-spawns: false
```

Admin claims are purple server-owned Safe Zones for spawn and facilities. They are managed only through the separate **Admin claims** map. Public behaviour can be adjusted in ClaimAdmin; trusted admin-claim members always have full access.

## Integrations and updates

The remaining sections configure WorldGuard, vanilla spawn protection, maps, PlaceholderAPI, Plan, combat-tag providers, metrics and release checks. Enabling a switch never installs a dependency: LandWard only activates an integration when the companion plugin is present and compatible.

See [Integrations](Integrations) for what each bridge does.

---

Next: [Storage](Storage) · [Integrations](Integrations) · [Installation](Installation)
