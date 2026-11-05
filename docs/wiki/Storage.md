# Storage

LandWard supports SQLite out of the box and MySQL for shared or remote storage.

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

LandWard can transfer all lands, claimed chunks, member rights and administrative claims between SQLite and MySQL without hand-editing tables. This is intentionally a maintenance action:

1. Configure the **target** database connection in `config.yml` first. Do not change `storage.type` yourself.
2. Stop normal play so no players are online, then run this from the server console:

   ```text
   claimadmin migrate mysql confirm
   ```

   Use `sqlite` instead to move from MySQL back to the local database.
3. LandWard validates the source, writes a complete versioned backup in `plugins/LandWard/migration-backups/`, verifies that backup, requires an empty target and imports the target in a single transaction.
4. It compares the complete source and target snapshot. Only after that succeeds it stores the new `storage.type`; restart the server to use it.

If any validation, backup, import or comparison fails, the active source backend remains unchanged. A non-empty target is always refused rather than overwritten.
