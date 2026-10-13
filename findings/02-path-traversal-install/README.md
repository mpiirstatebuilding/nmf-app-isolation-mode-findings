# Finding 2 — unsanitized package name → path traversal on install

**Source of truth:** root `README.md` §2 for full impact, source citations, and
suggested fix. Nothing below restates that.

**Severity (per source):** High, live-reproduced.
**Classification:** fully scriptable, mode-independent (Supervisor-level bug).
**Setup needed:** `docs/SETUP.md` "App-scope setup", in any `appsIsolation`
mode. The bug is reached before any isolation mode takes effect.

## Reproduction steps

1. Build a `.nmfpack` with `info.name=../../outside-target/pwned-app`
   (metadata otherwise valid).
2. Install it against a scratch NMF root.
3. Confirm the traversal write succeeds and no error is surfaced to the
   caller.

Fixture: `fixtures/pwned-package/` (Maven module) plus
`fixtures/build-fixture.py` (post-processing script) in this directory. The
script's docstring has the exact build steps.

**Build note, discovered live:** the zip entries must not traverse.

- The package's zip-internal file entry paths are separately guarded by a
  working traversal check: `HelperNMFPackage.resolveInside()`, called from
  `NMFPackageManager.touchedBaselineDirs()`/`rejectIfTargetsFactory()` at
  `NMFPackageManager.java:125`.
- That check runs *before* the vulnerable `info.name`-based code at line
  136+. If the zip entries also contain `../`, `install()` aborts with a
  clean `IOException` before the bug is exercised.
- The Maven plugin derives zip entry paths from the same `info.name`, so
  `build-fixture.py` rewrites its output: clean zip entries, and the
  traversal string only in `info.name`.
- This is also the only shape a real attacker could use, since a
  traversing zip entry is blocked regardless of `info.name`.

## Observed

**Result: CONFIRMED, live, 2026-09-15.** The core claim holds: an
unvalidated `info.name` writes outside the intended directories, and no
error reaches the caller. Two details of the prediction did not reproduce
as written. They're recorded below as observed, not adjusted to match.

Environment: `docker/linux-userspace/`. Any mode would do, since this is
Supervisor-level; this was the one already running.

- **Log-directory traversal: confirmed as predicted.**
  - `Deployment.getLogsDirForApp(packageName); logDir.mkdirs();`
    (`NMFPackageManager.java:150-151`) created a real, empty directory at
    `/nanosat-mo-framework/logs/outside-target/pwned-app/`.
  - That's one level outside the intended `logs/app_<name>/` location.
- **App directory: never created. This is a different, silent bug, not
  the finding as written.**
  - The log claimed success: `>> Creating file on:
    /nanosat-mo-framework/apps/../../outside-target/pwned-app/start_app.sh`,
    and the same for `transport.properties`/`provider.properties`.
  - On disk, `/nanosat-mo-framework/outside-target/` was **empty**
    afterward: no `pwned-app/` subdirectory, no files.
  - Cause: `AuxFilesGenerator.writeFile()` (`AuxFilesGenerator.java:269-278`)
    prints `"Creating file on: " + file` *before* the write, then wraps the
    `FileWriter` call in `catch (IOException e) { // Handle the exception }`.
    That's a **fully silent, unlogged swallow**, not even the broad
    log-only catch finding 1 cites.
  - Nothing in this code path calls `appDir.mkdirs()`. A normal package
    gets away with that because its zip entries extract into the same
    directory first. This fixture's zip entries land at a different, clean
    path (see the build note), so `outside-target/pwned-app/` never
    exists and every aux-file write silently no-ops.
  - This applies to **any** hand-crafted malicious package, not just this
    run. A traversing zip entry is blocked separately (see the build note),
    so no package shape avoids both problems.
- **Install receipt: not written where predicted. `install()` hit a
  logged (not silent) exception instead.**
  - Expected: `<nmf-root>/outside-target/pwned-app.receipt`.
  - Observed: `java.io.FileNotFoundException:
    /nanosat-mo-framework/etc/installations-tracker/../../outside-target/pwned-app.receipt`
    from `Metadata.store()` (`NMFPackageManager.java:206`), logged `SEVERE`
    by `NMFPackagePMBackend.install()`'s own catch (the same broad catch
    finding 1 cites).
  - Cause: `Metadata.store()` (`Metadata.java:253-261`) calls
    `new File(file.getParent()).mkdirs()` before opening the
    `FileOutputStream`. `file.getParent()` is the *textual* parent,
    `.../installations-tracker/../../outside-target`, with `../`
    unresolved.
  - `mkdirs()` first checks `exists()` on that string, which the OS
    resolves to `/nanosat-mo-framework/outside-target`. That directory
    already exists, created moments earlier as a side effect of the
    log-dir step.
  - So `mkdirs()` returns early and never creates the literal
    `installations-tracker/` path component, and the `FileOutputStream`
    open fails on it.
  - This is deterministic: log-dir creation always runs before receipt
    storage in `install()`.
- **The caller was told it succeeded, which sharpens the core claim.**
  - The receipt write threw and was logged `SEVERE`, and the aux files
    were never written.
  - Yet the MAL client (`RunAppTrigger`) received a clean **success**
    response (`installResponseReceived`, "Package ... installed.").
  - A later `isFinalVersionInstalled()`/`findPackage()` call would very
    likely report the package as not installed, since no receipt exists.
    Not verified further.
- **Unrelated, mode-specific side effect, not part of this finding:** the
  `linux-userspace`-only `adduser` step also failed, with `Usernames must
  be no more than 32 bytes in length` (`app-../../outside-target/pwned-app`
  is 34 bytes). It was caught by the same broad catch finding 1 cites, and
  would not occur under `none`/`bubblewrap`/`docker-containers`.
- Upstream SHA for this 2026-09-15 run: `0933302` (pre-release `v5.1`
  tip). The current pin is the root `README.md` "Verified against" line.

## Re-verified 2026-10-06: 5.1 release and v6.0

**Result: CONFIRMED on both**, live, same steps and environment as above.
The refs tested were `release-5.1` and `v6.0`; see the root `README.md`
"Verified against" lines for the SHAs. Tooling and fixtures were rebuilt
against each version (`docs/SETUP.md`). Raw Supervisor logs are in
`results/reverify-2026-10/`, which is not committed.

- Identical on both refs, including the two discrepancies recorded above:
  - the log-dir traversal created `logs/outside-target/pwned-app/`
  - `outside-target/` stayed empty, because `AuxFilesGenerator` silently
    swallows its write failures even though it printed "Creating file on:
    .../apps/../../outside-target/pwned-app/..."
  - the receipt write failed with the same `FileNotFoundException` on
    `etc/installations-tracker/../../outside-target/pwned-app.receipt`,
    logged `SEVERE`
- `RunAppTrigger` was still told "Package ... installed."
- The `linux-userspace`-only 32-byte username failure also recurred.
