# Storage

LandWard uses SQLite by default. Use MySQL/MariaDB when you want a separate database server.

> **Before you begin:** migration requires `landward.dbadmin` (operators have it by default). Players may stay online; LandWard briefly pauses claim changes during the migration. LandWard creates and checks its own migration backup automatically.

## SQLite

SQLite is the default. It stores data locally at `plugins/LandWard/landward.db` and needs no separate database service.

```yaml
storage:
  type: sqlite
```

## MySQL

On a new server with no LandWard data, set `type` to `mysql` and fill in the
connection details below. For an existing SQLite server, use the migration steps
instead—do **not** change the storage type by hand first.

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

LandWard creates its own tables automatically. Use a separate database for
LandWard; do not let unrelated plugins use LandWard's tables.

## Migration: SQLite → MySQL / MariaDB

This moves all player lands, chunks, Safe Zones, members and settings from
`landward.db` to MySQL/MariaDB.

1. **Create an empty MySQL/MariaDB database and account.** Do not reuse a database that belongs to another plugin.
2. **Edit only the nested `mysql:` connection details** in `plugins/LandWard/config.yml`—host, port, database, username, password, TLS and pool size. Leave the storage type set to SQLite:

   ```yml
   storage:
     type: sqlite
   ```
3. Reload the configuration:

   ```text
   claimadmin reload
   ```
4. Verify that the MySQL/MariaDB target is empty:

   ```text
   claimadmin storage status mysql
   ```

   Continue only when it reports no LandWard data.
5. Run the live migration:

   ```text
   claimadmin migrate mysql confirm
   ```
6. Wait for the completion message. LandWard creates a verified migration backup, copies and checks the data, then activates MySQL immediately. No restart or player kick is needed.

## Migration: MySQL / MariaDB → SQLite

Use this when moving back to the local `plugins/LandWard/landward.db` file.

1. Leave the storage type set to MySQL; LandWard needs the current backend as its source.

   ```yml
   storage:
     type: mysql
   ```
2. Check the SQLite target:

   ```text
   claimadmin storage status sqlite
   ```

   It must be empty before migration.
3. Run the live migration:

   ```text
   claimadmin migrate sqlite confirm
   ```
4. Wait for the completion message. LandWard creates a verified migration backup, copies and checks the data, then activates SQLite immediately. No restart or player kick is needed.

## If the target is not empty

LandWard refuses to overwrite existing LandWard data. Inspect the target first:

```text
claimadmin storage status mysql
```

If the existing **LandWard tables only** are intentionally disposable, run this while no other players are online:

```text
claimadmin storage clear mysql confirm
```

LandWard first creates a backup, then clears only its own tables. It never deletes a database, database user, server file or another plugin’s tables. Replace `mysql` with `sqlite` when preparing the local target.

> **Safety rule:** never manually change the storage type before a migration. If a migration fails, LandWard keeps the current storage unchanged.

---

Next: [Configuration](Configuration) · [Installation](Installation)
