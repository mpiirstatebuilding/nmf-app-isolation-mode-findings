# Finding 3 — `bootloader/config.properties` no integrity check at boot, fails open to no isolation

**Source of truth:** root `README.md` §3 for full impact, source citations, and
suggested fix. Nothing below restates that.

**Severity (per source):** High, live-reproduced. It was source-reviewed
only in the source project. This testbed was the first to confirm it live,
and the root `README.md` table was updated in `01eab4e`.
**Classification:** fully scriptable, mode-independent.
**Setup needed:** `docs/SETUP.md` "App-scope setup", to launch an App after
boot.

## Reproduction steps

1. Generate a filesystem for any `appsIsolation` mode.
2. Edit the generated `bootloader/config.properties`, setting its
   `apps-isolation` key to an empty string or an unrecognized value.
3. Boot the Supervisor.
4. Trace whether every subsequently launched App runs with no isolation
   (the fail-open direction the source-only analysis predicts) and whether
   the boot log gives any indication of the problem.

**If the actual observed behavior differs from this prediction** (e.g. the
Supervisor fails to start instead of failing open), that is a genuine
finding update to report back to César. Don't silently adjust the test to
match the prediction. Record whatever actually happens in Observed below.

## Observed

**Result: CONFIRMED, live, 2026-09-15.** Both corruptions fail open, as
predicted, but through two different mechanisms. Both were traced to
source and tested separately.

Both tests used a filesystem built for `linux-userspace`
(`docker/linux-userspace/`, the same image as findings 1/6/8, where
`nmf-admin` already has a real home). Only the `apps-isolation` line in
`bootloader/config.properties` was edited afterward. It was not a fresh
`none` build.

**Test A — unrecognized non-empty value (`apps-isolation=totally-bogus-mode`).**
This is the code path the root `README.md` describes.

- **Boot log: no indication of a problem.** It shows
  `BOOTLOADER CONFIRMATION confirmed after 1s`, the same shape as a normal
  boot.
- The JVM received `-Desa.mo.nmf.packagemanager.appsIsolation=totally-bogus-mode`
  verbatim (checked with `ps aux` inside the container). The shell
  bootloader passes any *non-empty* value through unmodified.
- The fallthrough, traced in source:
  - `AppsIsolationMode.getCurrent()` (`AppsIsolationMode.java:57-60`)
    returns the raw string whenever it's non-null. It never checks it
    against the four known modes.
  - So `isBubblewrap()`/`isDockerContainers()`/`isLinuxUserspace()`, each
    an exact-string `.equals()` check, all return false for
    `"totally-bogus-mode"`.
  - `AppsLauncherProviderServiceImpl.init()`
    (`AppsLauncherProviderServiceImpl.java:136-145`) only branches on
    `isBubblewrap()`/`isDockerContainers()`/Windows. **Everything else,
    including both `"none"` and `"linux-userspace"`, falls through to the
    same `else` branch, `new AppsLauncherManagerLinux(...)`.**
  - `linux-userspace` isolation is decided later, inside
    `AppsLauncherManagerLinux`/`NMFPackageManager`, gated on
    `isLinuxUserspace()`. That's false for the bogus value, the same as
    for a literal `"none"`.
- After installing `hello-world`, the fail-open was confirmed three ways:
  1. No `app-hello-world` entry appeared in `/etc/passwd`.
  2. The launcher command was plain `[/bin/sh, -c, ...]`, not the
     `[sudo, su, -, app-hello-world, -c, ...]` form findings 1/6 showed
     under real `linux-userspace`.
  3. `ps aux` showed the App's JVM running **as `nmf-admin`**, the
     Supervisor's own account.
- The App even launched successfully, because running as `nmf-admin`
  sidesteps finding 6.

**Test B — empty value (`apps-isolation=`).** This also fails open, but
the Test A mechanism is **never reached**. A shell-level default
intercepts it first, discovered live.

- `start_supervisor.sh`'s `initialisation()` reads the config with
  `_v=$(get_prop apps-isolation "$CONFIG_FILE"); [ -n "$_v" ] &&
  APPS_ISOLATION=$_v`.
- For an empty value, `[ -n "$_v" ]` is false, so the override is skipped.
  `APPS_ISOLATION` keeps the script's hardcoded default,
  `APPS_ISOLATION=none`, set unconditionally at the top of the script.
- `ps aux` confirmed the JVM received
  `-Desa.mo.nmf.packagemanager.appsIsolation=none`: a valid, literal
  `"none"`, not an empty or corrupted value.
- So `AppsIsolationMode`'s Java-side fallback is never exercised here. The
  fail-open happens one layer earlier, from a shell default that happens
  to point the same way.
- The outcome is the same as Test A (no isolation), but the mechanism
  differs, so it's worth reporting as a separate observation:
  - Test A needs the fix suggested in the root `README.md`.
  - Test B is a second, independent place the same "default to strongest,
    not weakest" fix must apply. Otherwise the empty value keeps silently
    becoming `none`, whatever the Java-side fix does.
- Not tested: whether `get_prop`'s parsing (`sed -n
  "s/^$1=//p" "$2" | head -n 1 | tr -d '\r'`) treats a whitespace-only
  value (e.g. `apps-isolation= `) as empty. Plausible, not confirmed.
- Upstream SHA for this 2026-09-15 run: `0933302` (pre-release `v5.1`
  tip). The current pin is the root `README.md` "Verified against" line.

## Re-verified 2026-10-06: 5.1 release and v6.0

**Result: CONFIRMED on both**, live, same steps and environment as above.
The refs tested were `release-5.1` and `v6.0`; see the root `README.md`
"Verified against" lines for the SHAs. Tooling and fixtures were rebuilt
against each version (`docs/SETUP.md`). Raw Supervisor logs are in
`results/reverify-2026-10/`, which is not committed.

- **Test A** (`totally-bogus-mode`), identical on both refs:
  - the JVM got `appsIsolation=totally-bogus-mode`
  - the launcher was plain `[/bin/sh, -c, ...]`
  - no `app-hello-world` account was created
  - the App ran as `nmf-admin`
- **Test B** (empty value), identical on both refs: the JVM got
  `appsIsolation=none`, with the same no-isolation outcome.
- In both tests, the boot log has no line mentioning the isolation value.
