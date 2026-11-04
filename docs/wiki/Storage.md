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

LandWard uses a connection pool and creates the `lands`, `claims` and `land_members` tables automatically. Use InnoDB and UTF-8 (`utf8mb4`); do not point multiple unrelated plugins at LandWard's tables.
