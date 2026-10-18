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
     `etc/installations-tracker/../../outside-target/pwned-app.receipt`.
     On a Supervisor that has never installed anything, it fails with a
     `FileNotFoundException`, which `install()`'s broad catch logs as
     `SEVERE`. If any package was installed first, it succeeds, and the
     receipt lands at `/nanosat-mo-framework/outside-target/pwned-app.receipt`
     (see "Re-run 2026-10-18 on a fresh setup").
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
  logged (not silent) exception instead.** This holds only on a Supervisor
  with no earlier install; see "Re-run 2026-10-18 on a fresh setup" below
  for what happens after one.
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
  - This is deterministic as long as `etc/installations-tracker/` doesn't
    exist yet, which is the case until the first ordinary install.
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
- **No file *content* escaped on this fresh Supervisor.** The aux-file writes to
  `apps/../../../../tmp/nmf-escape/` are logged as "Creating file on: ..."
  but swallowed by `AuxFilesGenerator.writeFile()` (the directory doesn't
  exist yet when they run — `logDir.mkdirs()` runs after), and the receipt
  write throws `FileNotFoundException` on
  `etc/installations-tracker/../../../../tmp/nmf-escape.receipt`. The
  receipt write only fails because `installations-tracker/` didn't exist
  yet. After any earlier install it succeeds (see the fresh-setup re-run
  below).

**2. Does the uninstall path also traverse? The code does. It was
unreachable here, but only because no receipt had been written.**

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
  three canaries survived untouched. That only holds while the receipt is
  missing. The fresh-setup re-run below shows the receipt being written.

Supervisor log excerpts:
[`evidence/5.1/f2-deeptraversal-escape.log`](../../evidence/5.1/f2-deeptraversal-escape.log)
and [`evidence/6.0/f2-deeptraversal-escape.log`](../../evidence/6.0/f2-deeptraversal-escape.log).

## Re-run 2026-10-18 on a fresh setup: the receipt escapes after any earlier install

The runs above each installed a traversal package on a Supervisor that had
never installed anything. A clean-room re-run of the whole testbed on
`release-5.1` (fresh NMF clone, fresh testbed clone, empty Maven
repository, image `nmf-testbed-linux-userspace:5.1-fresh`) installed
`hello-world` first, for findings 8, 1 and 6, and then both traversal
packages in the same container. `v6.0` hasn't been re-run this way.

- Both installs logged `Package successfully installed`, with no `SEVERE`
  line, and the client got "Package ... installed." for each.
- Both log directories escaped as before:
  `/nanosat-mo-framework/logs/outside-target/pwned-app/` and
  `/tmp/nmf-escape/`, both empty.
- **Both receipts were written:**
  ```
  -rw-r--r-- 1 nmf-admin nmf-admin 481 /nanosat-mo-framework/outside-target/pwned-app.receipt
  -rw-r--r-- 1 nmf-admin nmf-admin 477 /tmp/nmf-escape.receipt
  ```
  `/tmp/nmf-escape.receipt` is outside the NMF root. Its contents are the
  package's metadata as a properties file, starting `#NMF Package
  Metadata` and including `info.name=../../../../tmp/nmf-escape`. So file
  contents the attacker writes do land outside today. The file name
  always ends in `.receipt`.
- Why: `hello-world`'s install had created `etc/installations-tracker/`.
  With that directory present, the OS resolves
  `etc/installations-tracker/../../../../tmp/nmf-escape.receipt` normally,
  and `Metadata.store()` succeeds. The `mkdirs()` quirk described in
  Observed only bites while that directory is missing.
- Consequence for uninstall, read in source but **not run live**
  (`RunAppTrigger` has no uninstall call): `isPackageInstalled()`
  ([`NMFPackageManager.java:432-440`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/NMFPackageManager.java#L432-L440)) only checks that the
  receipt exists at that same traversed path, so it now returns true for
  the traversal package. Uninstall would then reach the unguarded
  `removeAuxiliaryFiles()` and delete `start_app.sh`,
  `provider.properties` and `transport.properties` under `/tmp/nmf-escape/`.
- The aux files themselves were still silently not written.

**Net:** a crafted `info.name` escapes the NMF root. It creates
directories, and after any earlier install it also writes its receipt
there, while the client is told the install succeeded. Severity stays
High. Supervisor log:
[`evidence/5.1/fresh-linux-userspace-f8-f1-f6-f2.log`](../../evidence/5.1/fresh-linux-userspace-f8-f1-f6-f2.log).

## Verifying a fix

On a fresh container, install `hello-world-5.1.nmfpack` first, so
`etc/installations-tracker/` exists and the receipt case is covered. Then
install both traversal packages (`pwned-app-1.0.0-fixed.nmfpack` and
`pwned-deep-1.0.0.nmfpack`). A fixed Supervisor rejects each one as an
invalid package name before creating anything, and the client gets an
error, not "Package ... installed.". Check that nothing was created:

```bash
docker exec nmf-sup sh -c 'ls -d /tmp/nmf-escape /tmp/nmf-escape.receipt /nanosat-mo-framework/logs/outside-target /nanosat-mo-framework/outside-target 2>&1'
```

All four should report "No such file or directory".
