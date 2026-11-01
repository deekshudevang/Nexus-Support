# ADR 0007: Gating Destructive Database Migrations

## Status
Accepted

## Context
During early development, Room was configured with `fallbackToDestructiveMigration(dropAllTables = true)`. This allowed rapid iteration on the `AppDatabase` schema without manually writing migration scripts for every structural change. 

However, as the app moves toward production readiness, retaining this flag presents a severe data-loss risk. If an end-user updates the app, and a bug in the migration logic causes it to fail, Room will silently wipe the entire database—including the identity keys, saved messages, and cached contact verification states. In an emergency offline communication app, data persistence is critical, and a crash is vastly preferable to silent data annihilation.

## Decision
We will gate `fallbackToDestructiveMigration` strictly to debug builds using the `BuildConfig.DEBUG` flag.
- **Production Builds (`DEBUG = false`):** If a migration fails or is missing, the application will crash with an `IllegalStateException`. The user's data remains intact on disk, allowing us to ship a patch update with the corrected migration script.
- **Debug Builds (`DEBUG = true`):** Developers will continue to benefit from automatic destructive migrations during local schema iteration.

## Consequences
- **Positive:** No silent data loss for end-users on botched upgrades.
- **Negative:** Developers must be rigorous about writing and testing migrations (like `MIGRATION_13_14`) before pushing to production, as missed migrations will cause immediate crashes on app launch. We rely on the `MigrationTest` suite to catch these before merge.
