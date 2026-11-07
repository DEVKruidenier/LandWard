# Configuration

`plugins/LandWard/config.yml` contains all LandWard settings. Each setting has an English explanation in the file itself.

> **Safe updates:** Your changed values are kept when LandWard updates. New settings are added automatically; check the file after a major update.

## Language and storage

```yaml
language:
  default: en_US
  use-client-locale: true

storage:
  type: sqlite
```

Language packs live in `plugins/LandWard/lang/`. LandWard includes English,
Dutch, German, French, Spanish and several other common languages. With
`use-client-locale: true`, players see a matching installed language when
available; otherwise LandWard uses the `default` language above.

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

LandWard is active in every world by default. List only worlds where it should
be off, such as creative or minigame worlds. `myworld` is only an example; you
can remove it.

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
  # Keep this as "" to use the name from the selected LandWard language.
  # Custom example: default-name: "Spawn"
  default-name: ""
  protection:
    allow-pvp: false
    allow-hostile-mob-kills: false
    allow-passive-mob-kills: true
    allow-containers: false
    allow-doors: false
    allow-interactions: false
    allow-hostile-mob-spawns: false
```

Admin claims are purple server-owned Safe Zones, for example at spawn. Manage
them through the separate **Admin claims** map. Public protection can be changed
in ClaimAdmin; trusted members always have full access.

## Integrations and updates

The remaining sections configure optional integrations, metrics and release
checks. Turning on a setting does not install another plugin; LandWard simply
uses that integration when the other plugin is installed.

See [Integrations](Integrations) for what each bridge does.

```yaml
updates:
  enabled: true
  maximum-download-megabytes: 32
```

When an official release is newer, `/claim update` can download a verified JAR
to `plugins/LandWard/updates/`. It is deliberately staged only: Paper does not
provide a safe live replacement for a running plugin's classes, listeners,
integrations and open storage connections. Stop the server, replace the plugin
JAR with the staged file, then start Paper normally.

---

Next: [Storage](Storage) · [Integrations](Integrations) · [Installation](Installation)
