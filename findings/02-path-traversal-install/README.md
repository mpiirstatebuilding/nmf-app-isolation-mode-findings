# Finding 2 — unsanitized package name → path traversal on install

Impact, source citations and the suggested fix are in the root
[`README.md`](../../README.md) §2.

**Severity:** High, live-reproduced.
**Classification:** fully scriptable, mode-independent (Supervisor-level bug).
**Setup needed:** `docs/SETUP.md` "App-scope setup", in any `appsIsolation`
mode. The bug is reached before any isolation mode takes effect.

## Reproduction steps

1. Build `pwned-app-1.0.0-fixed.nmfpack`, which has
   `info.name=../../outside-target/pwned-app` and clean zip entries (see
   the build note below):
   ```bash
   cd fixtures && mkdir -p outside-target
   (cd pwned-package && rm -rf target && mvn -o package)
   python3 build-fixture.py
   ```
2. Stage it into `packages/` of any mode's Supervisor (this testbed used
   `docker/linux-userspace/`) and install it:
   ```bash
   java -jar runapp-trigger.jar <directoryURI> pwned-app pwned-app-1.0.0-fixed.nmfpack
   ```
   Only the install result matters here; ignore what the
   `listApp`/`runApp` half reports.
3. Confirm:
   - the App's log directory is created one level up from the intended
     `logs/app_<name>/`, at `<nmf-root>/logs/outside-target/pwned-app/`
     ([`NMFPackageManager.java:150-151`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/NMFPackageManager.java#L150-L151)). This two-dot fixture only climbs
     one level inside `logs/`; the "Re-tested 2026-10-18" section below
     shows a deeper name escaping the NMF root entirely.
   - the receipt write targets
     `etc/installations-tracker/../../outside-target/pwned-app.receipt`
     and fails with a `FileNotFoundException`, which `install()`'s broad
     catch logs as `SEVERE`
   - the aux files (`start_app.sh` and the `.properties` files) are logged
     as "Creating file on: .../apps/../../outside-target/pwned-app/..." but
     never written, because `AuxFilesGenerator.writeFile()` swallows the
     `IOException`
   - the installing client still gets a success response ("Package ...
     installed.")

Fixture: `fixtures/pwned-package/` (Maven module) plus
`fixtures/build-fixture.py` (post-processing script) in this directory. The
script's docstring has the exact build steps.

**Build note, discovered live:** the zip entries must not traverse.

- The package's zip-internal file entry paths are separately guarded by a
  working traversal check: `HelperNMFPackage.resolveInside()`, called from
  `NMFPackageManager.touchedBaselineDirs()`/`rejectIfTargetsFactory()` at
  [`NMFPackageManager.java:125`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/NMFPackageManager.java#L125).
- That check runs *before* the vulnerable `info.name`-based code at line
  136+. If the zip entries also contain `../`, `install()` aborts with a
  clean `IOException` before the bug is exercised.
- The Maven plugin derives zip entry paths from the same `info.name`, so
  `build-fixture.py` rewrites its output: clean zip entries, and the
  traversal string only in `info.name`.
- This is also the only shape a real attacker could use, since a
  traversing zip entry is blocked regardless of `info.name`.

## Observed

**Result: CONFIRMED, live, on `release-5.1` and `v6.0`** (SHAs per the root
`README.md` "Verified against" lines), identical on both. The core claim
holds: an unvalidated `info.name` writes outside the intended directories,
and no error reaches the caller. Two details of the original prediction did
not reproduce as written; they're recorded below as observed, not adjusted
to match.

Environment: `docker/linux-userspace/` (any mode would do, since this is
Supervisor-level). Supervisor logs: [`evidence/5.1/linux-userspace-f2.log`](../../evidence/5.1/linux-userspace-f2.log) and [`evidence/6.0/linux-userspace-f2.log`](../../evidence/6.0/linux-userspace-f2.log).

- **Log-directory traversal: confirmed as predicted.**
  - `Deployment.getLogsDirForApp(packageName); logDir.mkdirs();`
    ([`NMFPackageManager.java:150-151`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/NMFPackageManager.java#L150-L151)) created a real, empty directory at
    `/nanosat-mo-framework/logs/outside-target/pwned-app/`.
  - That's one level outside the intended `logs/app_<name>/` location.
- **App directory: never created. This is a different, silent bug, not
  the finding as written.**
  - The log claimed success: `>> Creating file on:
    /nanosat-mo-framework/apps/../../outside-target/pwned-app/start_app.sh`,
    and the same for `transport.properties`/`provider.properties`.
  - On disk, `/nanosat-mo-framework/outside-target/` was **empty**
    afterward: no `pwned-app/` subdirectory, no files.
  - Cause: `AuxFilesGenerator.writeFile()` ([`AuxFilesGenerator.java:269-278`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/utils/AuxFilesGenerator.java#L269-L278))
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
    from `Metadata.store()` ([`NMFPackageManager.java:206`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/NMFPackageManager.java#L206)), logged `SEVERE`
    by `NMFPackagePMBackend.install()`'s own catch (the same broad catch
    finding 1 cites).
  - Cause: `Metadata.store()` ([`Metadata.java:253-261`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/metadata/Metadata.java#L253-L261)) calls
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

## Re-tested 2026-10-18: deeper traversal escapes the NMF root, and the uninstall path

The original fixture's `info.name` only climbs one
level inside `logs/`, so it never actually proved an escape *out of* the
NMF root. Re-tested live on both `release-5.1` and `v6.0`
(`docker/linux-userspace/` images `:5.1` and `:6.0`), two questions:

**1. Does a deeper `..` chain escape the NMF root entirely? Yes.**

A variant package with `info.name=../../../../tmp/nmf-escape` and a clean
zip entry was installed via `RunAppTrigger`. Build it with
`python3 build-fixture.py --name ../../../../tmp/nmf-escape --out
outside-target/pwned-deep-1.0.0.nmfpack`.

- On **both** refs, `logDir.mkdirs()`
  ([`NMFPackageManager.java:150-151`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/NMFPackageManager.java#L150-L151)) created an **empty directory at
  `/tmp/nmf-escape`** — fully outside `/nanosat-mo-framework`, confirmed
  with `find / -name '*nmf-escape*'` returning only the `/tmp` path.
  `getLogsDirForApp()` prepends `app_`, so the first path segment is the
  literal `app_..`; the remaining `../../../..` still climb past the root
  and `/tmp/nmf-escape` is created. So the escape is real and reaches an
  arbitrary `..`-reachable absolute path, not just one level inside
  `logs/`.
- The install was still reported to the client as success
  (`install() acknowledged` → `Package ... installed.`), while the
  Supervisor log shows `SEVERE: The package ... could not be installed!`.
- **No file *content* escaped, in current code.** The aux-file writes to
  `apps/../../../../tmp/nmf-escape/` are logged as "Creating file on: ..."
  but swallowed by `AuxFilesGenerator.writeFile()` (the directory doesn't
  exist yet when they run — `logDir.mkdirs()` runs after), and the receipt
  write throws `FileNotFoundException` on
  `etc/installations-tracker/../../../../tmp/nmf-escape.receipt`. So today
  the escape produces an empty directory, not attacker-chosen file
  contents. That limit is incidental: it rests on two unrelated bugs (the
  swallowed aux write and the failing receipt write), not on any check of
  `info.name`. Fixing either without sanitizing `info.name` would turn
  this into an arbitrary-file write.

**2. Does the uninstall path also traverse? The code does, but it's
currently unreachable.**

- `uninstall()` calls `removeAuxiliaryFiles(new File(getAppsDir(),
  packageName), ...)` ([`NMFPackageManager.java:247-248`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/NMFPackageManager.java#L247-L248)), which deletes
  `start_app.sh`/`provider.properties`/`transport.properties` under that
  directory. That path is built from the raw `packageName` and is **not**
  passed through `resolveInside()` (unlike `removeFiles()`, which is
  guarded). So for a traversal name it would delete files at the escaped
  location.
- But the uninstall service is gated by `isPackageInstalled()`
  (`NMFPackagePMBackend`/`NMFPackageManager`), which returns false unless a
  **receipt** exists — and the receipt write fails for any traversal name
  (above). Tested live: three canary files were placed at
  `/tmp/nmf-escape/`, then `PackageManagement.uninstall` was called for
  `pwned-deep-1.0.0.nmfpack`. It was refused with `response ERROR errorNumber=1`
  (`InvalidArgumentException` — "The package is not installed!"), and all
  three canaries survived untouched. So the unguarded delete exists in the
  code but the same receipt bug keeps it unreachable through the service.

**Net:** the finding stands and is sharpened — a crafted `info.name`
escapes the NMF root (directory creation at an arbitrary absolute path) and
the install is still reported as success. The headline in root `README.md`
§2 is corrected to say *directories* escape today, with arbitrary file
write as the latent risk, rather than implying file contents already land
outside. Severity stays High. Supervisor log excerpts:
[`evidence/5.1/f2-deeptraversal-escape.log`](../../evidence/5.1/f2-deeptraversal-escape.log)
and [`evidence/6.0/f2-deeptraversal-escape.log`](../../evidence/6.0/f2-deeptraversal-escape.log).

## Verifying a fix

Install both packages again (`pwned-app-1.0.0-fixed.nmfpack` and
`pwned-deep-1.0.0.nmfpack`). A fixed Supervisor rejects each one as an
invalid package name before creating anything, and the client gets an
error, not "Package ... installed.". Check that neither directory exists:

```bash
docker exec nmf-sup sh -c 'ls -d /tmp/nmf-escape /nanosat-mo-framework/logs/outside-target 2>&1'
```

Both should report "No such file or directory".
