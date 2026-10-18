# Finding 3 — `bootloader/config.properties` no integrity check at boot, fails open to no isolation

Impact, source citations and the suggested fix are in the root
[`README.md`](../../README.md) §3.

**Severity:** High, live-reproduced. It was source-reviewed
only in the source project; this testbed was the first to confirm it live.
**Classification:** fully scriptable, mode-independent.
**Setup needed:** `docs/SETUP.md` "App-scope setup", to launch an App after
boot.

## Reproduction steps

1. Generate a `linux-userspace` filesystem. Any mode works, but
   `linux-userspace` makes the fail-open easy to see, because a real
   isolated launch creates an `app-<name>` account.
2. **Before building the image** (the Dockerfile `COPY`s the filesystem
   in), edit `$FS/nanosat-mo-framework/bootloader/config.properties`:
   - Test A: `apps-isolation=totally-bogus-mode`
   - Test B: `apps-isolation=` (empty)
3. Build and start `docker/linux-userspace/` with `hello-world-5.1.nmfpack`
   staged (`docs/SETUP.md`), then install and launch it with
   `RunAppTrigger`.
4. Check the boot log for any warning, then check how the App ran:
   ```bash
   docker exec nmf-sup sh -c 'ps aux | grep -o "appsIsolation=[^ ]*" | head -1'
   docker exec nmf-sup sh -c 'grep app-hello-world /etc/passwd; ps -o user,args -C java'
   ```
   The bug: the boot log is clean, no `app-hello-world` account exists, and
   the App's JVM runs as `nmf-admin`, the Supervisor's own account.

## Verifying a fix

Repeat Tests A and B. A fixed Supervisor either boots into the strongest
available mode (here, `linux-userspace`, so `app-hello-world` is created
and the App runs as that account), or logs an explicit warning naming the
invalid value. Check Test B separately: `start_supervisor.sh`'s own
`APPS_ISOLATION=none` default must also change, or the empty value still
reaches the JVM as a literal `none`.

## Observed

**Result: CONFIRMED, live, on `release-5.1` and `v6.0`** (SHAs per the root
`README.md` "Verified against" lines), identical on both refs. Both
corruptions fail open, as predicted, but through two different mechanisms.
Both were traced to source and tested separately. Supervisor logs: [`evidence/5.1/f3-testA.log`](../../evidence/5.1/f3-testA.log) and [`evidence/6.0/f3-testA.log`](../../evidence/6.0/f3-testA.log); [`evidence/5.1/f3-testB.log`](../../evidence/5.1/f3-testB.log) and [`evidence/6.0/f3-testB.log`](../../evidence/6.0/f3-testB.log).

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
  - `AppsIsolationMode.getCurrent()` ([`AppsIsolationMode.java:57-60`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-services-impl/src/main/java/esa/mo/nmf/environment/AppsIsolationMode.java#L57-L60))
    returns the raw string whenever it's non-null. It never checks it
    against the four known modes.
  - So `isBubblewrap()`/`isDockerContainers()`/`isLinuxUserspace()`, each
    an exact-string `.equals()` check, all return false for
    `"totally-bogus-mode"`.
  - `AppsLauncherProviderServiceImpl.init()`
    ([`AppsLauncherProviderServiceImpl.java:136-145`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-services-impl/src/main/java/esa/mo/sm/impl/provider/AppsLauncherProviderServiceImpl.java#L136-L145)) only branches on
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
