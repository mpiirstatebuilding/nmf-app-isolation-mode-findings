# Finding 2 — unsanitized package name → path traversal on install

**Source of truth:** root `README.md` §2 for full impact, source citations, and
suggested fix. Nothing below restates that.

**Severity (per source):** High, live-reproduced.
**Classification:** fully scriptable, mode-independent (Supervisor-level bug —
any `appsIsolation` mode works).
**Setup needed:** none beyond a scratch NMF root — this is a Supervisor-level
bug, reachable before any isolation mode takes effect.

## Reproduction steps

1. Build a `.nmfpack` with `info.name=../../outside-target/pwned-app`
   (metadata otherwise valid).
2. Install it against a scratch NMF root.
3. Confirm the traversal write succeeds and no error is surfaced to the
   caller.

Fixture: `fixtures/pwned-package/` (Maven module) +
`fixtures/build-fixture.py` (post-processing script) in this directory —
see the script's own docstring for the exact build steps. **Important
build note, discovered live:** the package's own **zip-internal file entry
paths** are separately guarded by an existing, working traversal check
(`HelperNMFPackage.resolveInside()`, called from
`NMFPackageManager.touchedBaselineDirs()`/`rejectIfTargetsFactory()` at
`NMFPackageManager.java:125`, *before* the vulnerable `info.name`-based
code at line 136+ is ever reached) — if the zip entries themselves also
contain `../`, install() aborts immediately with a clean `IOException`
before the vulnerability under test is exercised at all. The Maven plugin
derives its zip entry paths from the same `info.name`, so its raw output
needs `build-fixture.py`'s post-processing to decouple them: clean,
non-traversing zip-internal paths, while `info.name` alone carries the
traversal string — this is also the *only* shape a real attacker could
use, since a traversing zip entry is independently blocked regardless of
`info.name`.

## Observed

**Result: CONFIRMED — the core claim holds (unvalidated `info.name` writes
outside intended directories, no error surfaced to the caller) — but two
concrete details in the prediction did not reproduce as written, both
recorded precisely below rather than adjusted to match, per the same
principle finding 3 states explicitly.**

Live, 2026-09-15, `docker/linux-userspace/` (any mode would do — this is
Supervisor-level — `linux-userspace` was just what was already running).

- **Log-directory traversal: confirmed exactly as predicted.**
  `Deployment.getLogsDirForApp(packageName); logDir.mkdirs();`
  (`NMFPackageManager.java:150-151`) created a real, empty directory at
  `/nanosat-mo-framework/logs/outside-target/pwned-app/` — one level
  outside the intended `logs/app_<name>/` location.
- **App-directory content: traversal target directory was never created,
  a *different*, silent bug — not the finding as written.** The log
  claimed success (`>> Creating file on:
  /nanosat-mo-framework/apps/../../outside-target/pwned-app/start_app.sh`,
  same for `transport.properties`/`provider.properties`), but
  `/nanosat-mo-framework/outside-target/` was confirmed **empty** on disk
  afterward — no `pwned-app/` subdirectory, no files. Traced to
  `AuxFilesGenerator.writeFile()` (`AuxFilesGenerator.java:269-278`):
  it unconditionally prints `"Creating file on: " + file` *before*
  attempting the write, then wraps the actual `FileWriter` call in
  `catch (IOException e) { // Handle the exception }` — a **fully silent,
  unlogged swallow**, not even the broad log-only catch finding 1 cites.
  Nothing in this code path ever calls `appDir.mkdirs()`; a normal package
  gets away with this because its own (traversal-checked, therefore
  non-traversing) zip entries happen to extract into the same directory
  first — but this fixture's zip entries deliberately land at a *different*,
  clean path (see above), so `outside-target/pwned-app/` is never
  materialized and every aux-file write silently no-ops. This isn't an
  artifact of this one test run: **any** hand-crafted malicious package hits
  the identical gap, because a traversing zip entry is independently
  blocked (see "Important build note" above) — there is no package shape
  that avoids both problems at once.
- **Install receipt: also did not land where predicted — install() aborted
  with a logged (not silent) exception instead.** Expected
  `<nmf-root>/outside-target/pwned-app.receipt`; observed
  `java.io.FileNotFoundException:
  /nanosat-mo-framework/etc/installations-tracker/../../outside-target/pwned-app.receipt`
  from `Metadata.store()` (`NMFPackageManager.java:206`), logged `SEVERE`
  by `NMFPackagePMBackend.install()`'s own catch (the same broad,
  install()-level catch finding 1 cites). Root cause traced precisely:
  `Metadata.store()` (`Metadata.java:253-261`) calls
  `new File(file.getParent()).mkdirs()` before opening the
  `FileOutputStream` — but `file.getParent()` is the *textual* parent
  (`.../installations-tracker/../../outside-target`, `../` un-resolved as
  a string), and `mkdirs()` checks `exists()` on that exact string first —
  which the **OS** resolves correctly (`.. ` and all) to
  `/nanosat-mo-framework/outside-target`, a directory that already exists
  by this point in the same `install()` call (created moments earlier, as
  a side effect of the logDir step above landing in the same parent).
  Since the resolved target already exists, `mkdirs()` short-circuits and
  never creates the literal intermediate `installations-tracker/`
  directory — so the later `FileOutputStream` open fails on that missing
  literal path component. This is deterministic given the code's own
  ordering (log-dir creation always runs before receipt storage in every
  `install()` call), not a fluke of this run.
- **The sharpest form of the core claim, arguably sharper than
  predicted:** despite the receipt-write throwing and being caught as
  `SEVERE`, and despite the app-directory aux files never being written at
  all, the MAL client (`RunAppTrigger`) received a clean **success**
  response (`installResponseReceived`, "Package ... installed.") — no
  error surfaced to the caller even though the install did not actually
  complete. A subsequent `isFinalVersionInstalled()`/`findPackage()` call
  would very likely report this package as not installed (no receipt was
  ever written) despite the caller having been told it succeeded — not
  verified further this session.
- **Unrelated, mode-specific side effect, not part of this finding's own
  claim:** the `linux-userspace`-only `adduser` step also failed, for an
  unrelated reason — `Usernames must be no more than 32 bytes in length`
  (`app-../../outside-target/pwned-app` is 34 bytes) — caught by the same
  broad catch finding 1 cites. Orthogonal to the traversal bug; would not
  occur under `none`/`bubblewrap`/`docker-containers`.
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
