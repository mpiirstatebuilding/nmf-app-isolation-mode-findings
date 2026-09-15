# Finding 3 — `bootloader/config.properties` no integrity check at boot, fails open to no isolation

**Source of truth:** root `README.md` §3 for full impact, source citations, and
suggested fix. Nothing below restates that.

**Severity (per source):** High. **Was source-reviewed only in the source
project; now live-confirmed by this testbed** (see Observed below) — the
root `README.md`'s own "Source-reviewed" verification column reflects what
was already sent to César and is intentionally left as-is (that file is a
verbatim copy of the outreach doc, not adapted here); this file is where
the update lives.
**Classification:** none special, but was new live work.

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
finding update to report back to César — don't silently adjust the test to
match the prediction. Record whatever actually happens in Observed below.

## Observed

**Result: CONFIRMED, live, 2026-09-15 — this is now live-reproduced, not
just source-reviewed. Matches the fail-open prediction closely, but via
two genuinely different mechanisms depending on which corruption is
tested — both traced to source, both tested independently.**

Both tests used a filesystem genuinely built for `linux-userspace`
(`docker/linux-userspace/`, same image as findings 1/6/8, `nmf-admin`
already has a real home) with only `bootloader/config.properties`'s
`apps-isolation` line edited afterward — not a fresh `none` build.

**Test A — unrecognized non-empty value (`apps-isolation=totally-bogus-mode`):**
this is the code path the source `README.md` actually describes.
- Boot log: **zero indication of a problem** — `BOOTLOADER CONFIRMATION
  confirmed after 1s`, identical shape to a normal boot. Confirms the
  prediction exactly.
- Confirmed via `ps aux` inside the container that the JVM actually
  received `-Desa.mo.nmf.packagemanager.appsIsolation=totally-bogus-mode`
  verbatim (the shell bootloader does pass through any *non-empty* value
  unmodified).
- Traced the actual fallthrough mechanism precisely: `AppsIsolationMode.getCurrent()`
  (`AppsIsolationMode.java:57-60`) returns the raw string as-is whenever it's
  non-null — it never validates against the four known modes. So
  `isBubblewrap()`/`isDockerContainers()`/`isLinuxUserspace()` (each an
  exact-string `.equals()` check) all evaluate false for
  `"totally-bogus-mode"`. `AppsLauncherProviderServiceImpl.init()`
  (`AppsLauncherProviderServiceImpl.java:136-145`) only branches on
  `isBubblewrap()`/`isDockerContainers()`/Windows — **everything else,
  including both `"none"` and `"linux-userspace"`, falls through to the
  same `else` branch, `new AppsLauncherManagerLinux(...)`.** So
  `linux-userspace` was never a separate branch here at all; the
  isolation/no-isolation distinction for that manager is made *inside*
  `AppsLauncherManagerLinux`/`NMFPackageManager`, gated on
  `isLinuxUserspace()` specifically — which is false for our bogus value,
  same as it would be for a literal `"none"`.
- Installed `hello-world` and confirmed the fail-open directly, three
  ways: **(1)** no `app-hello-world` entry appeared in `/etc/passwd` at
  all; **(2)** the launcher command was plain `[/bin/sh, -c, ...]`, not
  the `[sudo, su, -, app-hello-world, -c, ...]` form findings 1/6 showed
  under real `linux-userspace`; **(3)** `ps aux` showed the App's own JVM
  process running **as `nmf-admin`** — the Supervisor's own account, not
  a dedicated one. The App even launched successfully (no crash) since
  running as `nmf-admin` sidesteps finding 6 entirely.

**Test B — empty value (`apps-isolation=`):** genuinely fails open too,
but the mechanism above is **never reached** — a different, shell-level
default intercepts it first, discovered live.
- `start_supervisor.sh`'s own `initialisation()` reads the config file with
  `_v=$(get_prop apps-isolation "$CONFIG_FILE"); [ -n "$_v" ] &&
  APPS_ISOLATION=$_v` — for an empty value, `[ -n "$_v" ]` is **false**,
  so the override is skipped entirely and `APPS_ISOLATION` stays at the
  script's own hardcoded built-in default (`APPS_ISOLATION=none`, set at
  the top of the script, unconditionally, before any config file is even
  read).
- Confirmed via `ps aux`: the JVM received
  `-Desa.mo.nmf.packagemanager.appsIsolation=none` — a **valid, literal
  `"none"`**, not an empty or corrupted value. `AppsIsolationMode`'s own
  Java-side fallback (the thing this finding is actually about) is never
  exercised in this case — the fail-open happens one layer earlier, by
  accident of a shell script default that happens to point the same
  direction.
- Practical outcome is identical to Test A (no isolation), but the
  *mechanism* differs enough that it's worth reporting as two separate
  observations rather than one, if this goes back to César: the real
  `AppsIsolationMode` fallback (Test A) needs the fix suggested in the
  source `README.md`; the shell-level default (Test B) is a second,
  independent place the same "default to strongest, not weakest" fix
  would need to apply, or the empty-string case will keep silently
  becoming `none` regardless of what the Java-side fix does.
- Not tested: whether `get_prop`'s own parsing (`sed -n
  "s/^$1=//p" "$2" | head -n 1 | tr -d '\r'`) treats a merely-whitespace
  value (e.g. `apps-isolation= `) the same as fully empty — plausible it
  would, not confirmed.
- Upstream SHA (per root README.md "Verified against"): `09333029773c1eac7b43e9f1ac841c1fe2ff8e86`
