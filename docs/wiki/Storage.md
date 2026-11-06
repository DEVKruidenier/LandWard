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

For a brand-new server with no existing LandWard data, set `type` to `mysql` in the section below, then configure a database and account with permission to create and alter LandWard's tables. For an existing SQLite server, follow the migration procedure below instead—do **not** change the storage type by hand first.

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

## Migration: SQLite → MySQL / MariaDB

This procedure moves every player land, chunk, Safe Zone, member and setting from the local `landward.db` to MySQL/MariaDB.

1. **Stop normal player activity.** Keep the server running only long enough to use its console; every player must be offline.
2. **Make a manual backup.** Copy the complete `plugins/LandWard/` directory somewhere safe.
3. **Create an empty MySQL/MariaDB database and account.** The account needs permission to create and alter tables in that database. Do not reuse a database that belongs to another plugin.
4. **Edit only the nested `mysql:` connection details** in `plugins/LandWard/config.yml`—host, port, database, username, password, TLS and pool size. Leave the storage type set to SQLite:

   ```yml
   storage:
     type: sqlite
   ```
5. In the **server console**, verify that the target is empty:

   ```text
   claimadmin storage status mysql
   ```

   Continue only when it reports no LandWard data.
6. Run the migration from the **server console**:

   ```text
   claimadmin migrate mysql confirm
   ```
7. Wait for the success message. LandWard writes and verifies a `.lwbak` snapshot in `plugins/LandWard/migration-backups/`, imports the data atomically, compares the complete source and target snapshot, and then changes the storage type to MySQL.
8. **Restart Paper normally.** The startup panel must report `MYSQL` as the active storage.
9. Validate the result before reopening the server: run `/claim`, inspect a player land, a member, a Safe Zone and several chunk claims. Keep the original SQLite file and migration backup until you are satisfied.

## Migration: MySQL / MariaDB → SQLite

Use this when moving back to the local `plugins/LandWard/landward.db` file.

1. Ensure every player is offline and make a manual backup of `plugins/LandWard/`.
2. Keep the existing MySQL connection settings in `config.yml`. Leave the storage type set to MySQL; LandWard needs the current backend as its source.

   ```yml
   storage:
     type: mysql
   ```
3. In the **server console**, check the SQLite target:

   ```text
   claimadmin storage status sqlite
   ```

   It must be empty before migration.
4. Run:

   ```text
   claimadmin migrate sqlite confirm
   ```
5. Wait for LandWard to create and verify the migration backup, import and compare the data. It then changes the storage type to SQLite.
6. Restart Paper normally. The startup panel must report `SQLITE`, then validate lands, members, Safe Zones and claimed chunks as above.

## If the target is not empty

LandWard refuses to overwrite existing LandWard data. Inspect the target first:

```text
claimadmin storage status mysql
```

If the existing **LandWard tables only** are intentionally disposable, run this from the server console with nobody online:

```text
claimadmin storage clear mysql confirm
```

LandWard first creates and verifies a `.lwbak` backup, then clears only its own tables and verifies the empty result. It never deletes a database, database user, server file or another plugin’s tables. Replace `mysql` with `sqlite` when preparing the local target.

> **Safety rule:** never run a storage command as a player, never migrate with players online, and never manually flip the storage type before a migration. If backup, validation, import or comparison fails, LandWard leaves the active backend unchanged.

---

Next: [Configuration](Configuration) · [Installation](Installation)
