# Review legacy Dipp references in the crate mechanic

Status: ready-for-human

## Context

The crate mechanic migrated from DippGen to SneakyMisc with four legacy identifiers left in place for compatibility:

- `dipp.crate`, the scoreboard tag on placed crate entities
- `dipp.admin`, the permission that allows normal shulker-box placement
- `dipp.debug`, the permission that shows crate debug details
- `dipp.commands.cratecheck`, the permission for `/cratecheck`

SneakyMisc also has an ignored local file at `libs/dippgen-1.0.jar`. The `fileTree("libs")` compile-only dependency in `build.gradle.kts` puts that JAR on the compilation classpath, although the migrated crate code does not import DippGen classes.

## Human decisions needed

- Decide whether the three permissions should remain as compatibility aliases or move to `sneakymisc.*` names.
- Decide whether `dipp.crate` should remain permanent, be replaced, or support both old and new tags during a migration period.
- Check server permission configuration and existing placed crates before changing the identifiers.
- Confirm whether `libs/dippgen-1.0.jar` supports any other SneakyMisc code. Remove it from the local library directory or exclude it from `build.gradle.kts` if it is unused.

## Acceptance criteria

- Each legacy identifier has a documented keep, rename, or compatibility decision.
- Any renamed permission includes the required server permission migration.
- Existing placed crates continue to work after any scoreboard-tag change.
- The DippGen JAR is either removed from SneakyMisc's compile classpath or its remaining purpose is documented.

## Comments
