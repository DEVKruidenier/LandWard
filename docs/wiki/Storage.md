# Storage

LandWard supports SQLite out of the box and MySQL for shared or remote storage.

> **Before you begin:** back up `plugins/LandWard/` and ensure no one is online. Migration and maintenance commands are deliberately console-only.

## SQLite

SQLite is the default. It stores data locally at `plugins/LandWard/landward.db` and needs no separate database service.

```yaml
storage:
  type: sqlite
```

## MySQL

Set `storage.type` to `mysql`, then configure a database and account with permission to create and alter LandWard's tables.

```yaml
storage:
  type: mysql
  mysql:
    host: localhost
    port: 3306
    database: landward
    username: minecraft
    password: change-me
    ssl: false
    pool-size: 10
```

LandWard uses a connection pool and creates the `lands`, `claims`, `admin_lands` and `land_members` tables automatically. Use InnoDB and UTF-8 (`utf8mb4`); do not point multiple unrelated plugins at LandWard's tables.

## Safe migration

LandWard migrates all lands, claimed chunks, member rights and administrative claims between SQLite and MySQL. It is intentionally a maintenance action.

1. Configure the target MySQL connection in `config.yml` first, but do **not** change `storage.type` yourself.
2. With no players online, execute this from the server console:

   ```text
   claimadmin migrate mysql confirm
   ```

   Use `sqlite` instead when moving back to the local database.
3. LandWard validates the source, writes a complete versioned backup in `plugins/LandWard/migration-backups/`, verifies it, requires an empty target and imports it in one transaction.
4. It compares the complete source and target snapshot. Only then does it set `storage.type`; restart the server to activate the migrated backend.

If a check, backup, import or comparison fails, the active storage remains unchanged. A non-empty target is always refused rather than overwritten.

## Inspecting or clearing a target

Before a migration, query whether a configured backend already has LandWard data:

```text
claimadmin storage status mysql
```

If you intentionally want to discard the **LandWard tables only** in a target backend, first make your own backup and then use:

```text
claimadmin storage clear mysql confirm
```

The command is console-only and requires no players online. It does not delete databases, database users, server files or tables belonging to other plugins. It exists solely to prepare a target after the administrator has explicitly decided its existing LandWard data can be removed.

---

Next: [Configuration](Configuration) · [Installation](Installation)
