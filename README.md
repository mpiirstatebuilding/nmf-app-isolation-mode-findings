# Findings from NMF isolation-mode benchmarking

Ten bugs and gaps found while benchmarking NMF's four `appsIsolation`
modes. Each has a reproduction in `findings/`. All but finding 5 are
live-reproduced.

| # | Finding | Severity | Scope | Verification |
|---|---|---|---|---|
| 1 | `linux-userspace` account/identity takeover via a same-named, different-version package | High | App-vs-App | Live-reproduced |
| 2 | Unsanitized package name → path traversal on install | High | Any deployment shape | Live-reproduced |
| 3 | `bootloader/config.properties` has no integrity check, fails open to no isolation | High | Any deployment shape | Live-reproduced |
| 4 | bubblewrap `--ro-bind / /`, no seccomp/AppArmor/cap-drop | High | App-vs-host | Live-reproduced |
| 5 | `docker-containers` sets no `--pids-limit` on the sibling App container | High | App-vs-host | Source-reviewed |
| 6 | `linux-userspace`'s Debian `adduser()` never sets `--home` | High | Any deployment shape | Live-reproduced |
| 7 | bubblewrap `DEFAULT_PATH` is hardcoded, no override | Medium | Any deployment shape | Live-reproduced |
| 8 | PackageManagement/AppsLauncher key the same App by two different names | Minor (UX) | Not isolation | Live-reproduced |
| 9 | `docker-containers` sibling-mount host-path precondition is undocumented | Doc gap | Not isolation | Live-reproduced |
| 10 | bubblewrap: every App failure is reported as exit 0 / `EXITED` | Medium | Any deployment shape | Live-reproduced |

**Scope** says who should care:

- **App-vs-App** (1): needs two Apps colliding on the same declared name.
  It's moot for a single-org mission where one trusted party submits every
  App.
- **App-vs-host** (4, 5): a single App reaches beyond its own sandbox into
  the host filesystem or its resources, however many organizations submit
  Apps.
- **Any deployment shape** (2, 3, 6, 7, 10): Supervisor-level or functional
  bugs that apply to any deployment shape, not just a multi-tenant one.
- **Not isolation** (8, 9): a UX issue and a documentation gap.

Findings 1–9 are ordered by how severe and reachable we think each one is,
most important first. Finding 10 was found later and is appended rather
than renumbering the rest. We'd rank it alongside finding 7.

This repo is a **testbed**. Each finding's reproduction steps, observed
results and a "Verifying a fix" check for your patch are in
`findings/NN-*/`. Shared setup steps are in `docs/SETUP.md`, the trigger
tooling is in `tools/runapp-trigger/`, and the Supervisor logs from the
runs are in `evidence/`. `docs/PROVENANCE.md` covers two related findings
that aren't included, and details the second call site of finding 6.

## Versions tested

**Verified against:** the NMF 5.1 release, tag `release-5.1` @
`3b5920c85185ff55e98a08aaa48b60c1f77bcdc5` (2026-10-06). Upstream's
`master` @ `126d4379d73703a56848170e4c070c068ef0e424` ("Release 5.1 (#193)")
is a squash of that tag with a byte-identical tree, so the line numbers
hold for either. Every source citation below and in `findings/NN-*/` refers
to this exact tree, and links to it on GitHub.

All 10 findings are live-reproduced on this release, except finding 5, which
is source-reviewed and confirmed from its captured launch command (no fork
bomb run, per its "Source-reviewed" status).

**Also re-run live, all 10, on `v6.0`, with the same results** @
`5cc91528a92e4ad37d20271f4ee4d734c8209aa4` (the in-development 6.0 branch,
2026-10-06). None of its commits touch the code behind these findings. One
move affects citations: `nmf-package-lib` was merged into
`nmf-services-impl`. So on `v6.0`, the
`core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/...` files below
live under `core/nmf-services-impl/src/main/java/esa/mo/nmf/nmfpackage/...`.
Their line numbers are unchanged.

## 1. `linux-userspace`: a same-named package with a different version silently takes over another App's account and files

Any App installed under `linux-userspace` gets a dedicated Linux account
derived straight from its declared name (`app-<info.name>`). Nothing checks
that a package now claiming that name is the same one — or the same
developer — that claimed it before. The one guard that looks like it should
catch this only blocks an *exact* name-and-version match (it exists to let
a `SNAPSHOT` rebuild override itself); a second, unrelated package that
declares the same name with any different version sails straight through
it. There's no version-ordering check either: on `v6.0`, a package
declaring a *lower* version than the installed one still took over.

The second install reuses the first App's existing Linux account (same
uid/gid) and writes its files into the same App directory. The first
package's jar is left in place, but `start_app.sh`, `transport.properties`
and `provider.properties` are regenerated to point at the second package,
so the App now launches the second package's code. There's no error and
no operator-visible warning, and "Package successfully installed" is
logged both times. It doesn't even need a
deliberately crafted attack — two developers picking the same App name by
coincidence would trigger it.

**Impact:** under `linux-userspace`, any actor able to get a package
installed can take over an already-installed App belonging to a different
developer or organization: same uid/gid, same App directory, and a start
script silently switched to the new package. This is a full identity/account takeover, not just a
shared-resource leak, and requires no privilege beyond ordinary package
submission.

**Where:**
- [`core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/NMFPackageManager.java:136`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/NMFPackageManager.java#L136) — `packageName = metadata.getPackageName()`
- [`NMFPackageManager.java:144`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/NMFPackageManager.java#L144) — `generateUsername(packageName)`, gated on `linux-userspace`
- [`NMFPackageManager.java:157`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/NMFPackageManager.java#L157) — the unconditional `adduser()` call
- [`NMFPackageManager.java:195-198`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/NMFPackageManager.java#L195-L198) — the broad, log-only `catch`
- [`NMFPackageManager.java:489-546`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/NMFPackageManager.java#L489-L546) — `isFinalVersionInstalled()`
- [`NMFPackageManager.java:543-545`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/NMFPackageManager.java#L543-L545) — the exact name-and-version equality check, the actual gap:
  ```java
  return installedMetadata != null
          && packageName.equals(installedMetadata.getPackageName())
          && version.equals(installedMetadata.getPackageVersion());
  ```
- [`NMFPackageManager.java:833-834`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/NMFPackageManager.java#L833-L834) — `generateUsername()`'s definition
- [`core/nmf-services-impl/src/main/java/esa/mo/sm/impl/provider/PackageManagementProviderServiceImpl.java:214`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-services-impl/src/main/java/esa/mo/sm/impl/provider/PackageManagementProviderServiceImpl.java#L214) — where that guard is invoked at the real install-service entry point
- [`core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/NMFPackagePMBackend.java:169-189`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/NMFPackagePMBackend.java#L169-L189) — `checkPackageIntegrity()`, the integrity-check stub — live code, unconditionally `return true` (it's the *signing* code it would call into that's commented out, see below)
- [`core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/NMFPackageBuilder.java:171-199`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/NMFPackageBuilder.java#L171-L199) — the commented-out package-signing block itself

**Verification:** live-reproduced.

**How to replicate:** see [`findings/01-linux-userspace-account-takeover/`](findings/01-linux-userspace-account-takeover/README.md#reproduction-steps).

**Suggested fix:** before creating the OS account or writing into the App
directory, check whether a package with this name already exists
*regardless of version* (an installation receipt already does, so the data
needed for this check exists today) and either reject the install as a
name collision, or gate it as an explicit upgrade on some form of publisher
identity check. There's a natural anchor point for the latter already in
the code: package signing is stubbed out (`NMFPackageBuilder`'s signing
block is commented out, and the digital-signature class has no callers) —
wiring that up would close this and give you a real answer to "is this the
same publisher" instead of just "is this the same name."

## 2. Package name isn't sanitized — a submitted package can write files outside its intended install directories

Same root cause as finding 1 — the package's declared `info.name` is
attacker-controlled and never validated — but a different exploitation path
and fix, so closing one doesn't close the other. `install()`/`uninstall()`
build the App's install directory and its installation-receipt filename
directly from the package's own declared name, with no validation. There's
already a well-written guard against exactly this kind of thing for the
*files inside* a package (rejects `..`, dot-only segments, and absolute
paths) — it's just never applied to the package name itself, which is an
equally attacker-controlled field.

**Impact:** any developer able to submit a package can set `info.name` to
a `..`-traversal string and make `install()` create a directory outside
the intended install directories — confirmed escaping the NMF root entirely
(`info.name=../../../../tmp/nmf-escape` created `/tmp/nmf-escape`, live on
`release-5.1` and `v6.0`; see `findings/02-*`). The caller is still told the
install succeeded.

File content escapes too, as soon as any other package has been installed
before. Re-tested on `release-5.1` and `v6.0` from a fresh setup: with
`etc/installations-tracker/` already created by an ordinary install, the
traversal package's installation receipt was written to
`/tmp/nmf-escape.receipt`, outside the NMF root. The file name always ends
in `.receipt`, but its contents are the package's own metadata, which the
attacker writes. On a Supervisor that has never installed anything, the
receipt write fails instead, and only empty directories escape. That's
the case our first runs hit. The App's aux files (`start_app.sh` and the
`.properties` files) are silently not written either way. Neither limit
comes from any validation of `info.name`.

Once the receipt exists, uninstall is reachable too. Its only gate checks
for that same receipt, and its `removeAuxiliaryFiles()` call would delete
those three file names at the traversed path. We've read this in the
source but haven't run it live. This is a
Supervisor-level gap — it applies before any `appsIsolation` mode takes
effect, so it undermines filesystem boundaries regardless of which
isolation mode is active. Only the relative `..`-traversal form escapes; an
absolute path in `info.name` didn't in our testing.

**Where:**
- [`core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/NMFPackageManager.java:136,147`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/NMFPackageManager.java#L136) — the unvalidated name flowing straight into the install path:
  ```java
  String packageName = metadata.getPackageName();
  ...
  File appDir = new File(Deployment.getAppsDir(), packageName);
  ```
- [`NMFPackageManager.java:205`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/NMFPackageManager.java#L205) — same pattern for the receipt filename
- [`NMFPackageManager.java:247-248`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/NMFPackageManager.java#L247-L248) — same pattern again for the uninstall path (`removeAuxiliaryFiles`, unguarded by `resolveInside`, so it would delete outside the tree; reachable whenever the traversed receipt was written, see `findings/02-*`)
- [`NMFPackageManager.java:432-440`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/NMFPackageManager.java#L432-L440) — `isPackageInstalled()`, the uninstall gate, which only checks that the receipt exists at that same traversed path
- [`core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/utils/HelperNMFPackage.java:168`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/utils/HelperNMFPackage.java#L168) — `resolveInside()`, the existing traversal guard, never applied to the package name
- [`core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/metadata/Metadata.java:156-158`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/metadata/Metadata.java#L156-L158) — `getPackageName()`, the raw, unvalidated read

**Verification:** live-reproduced.

**How to replicate:** see [`findings/02-path-traversal-install/`](findings/02-path-traversal-install/README.md#reproduction-steps).

**Suggested fix:** apply the same traversal check already used for
in-package file entries to the package name itself, at the point it's read
out of the metadata — reject anything that isn't a single plain filename
component.

## 3. `bootloader/config.properties` has no integrity check at boot — a corrupted or tampered value silently falls back to no isolation

The file holding the `apps-isolation` value is written with no checksum,
unlike jar directories, which get a SHA256SUMS manifest. At boot, the only
check is that the file is readable, not that its content is valid — an
unrecognized or corrupted `apps-isolation` value makes every
`AppsIsolationMode` mode-check return false, and
`AppsLauncherProviderServiceImpl` falls through to its default: no
isolation at all (`AppsLauncherManagerLinux`).

**Impact:** this is a fail-open failure mode, not a fail-safe one — the
*direction* of the default is the gap, not just the missing check. A
config value that's merely wrong (not even maliciously tampered) degrades
silently to the weakest of the four modes, with nothing in the boot log to
distinguish that from an intentional `none` configuration.

**Where:**
- [`core/nmf-linux-maven-plugin/src/main/java/esa/mo/nmf/filesystem/BootloaderGenerator.java:120-127`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-linux-maven-plugin/src/main/java/esa/mo/nmf/filesystem/BootloaderGenerator.java#L120-L127) — writes `config.properties`, with `apps-isolation` set at line 121
- [`BootloaderGenerator.java:133-136`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-linux-maven-plugin/src/main/java/esa/mo/nmf/filesystem/BootloaderGenerator.java#L133-L136) — the checksum manifest is written only for the jar directories, never for `config.properties`
- [`core/nmf-linux-maven-plugin/src/main/resources/start_supervisor.sh:89`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-linux-maven-plugin/src/main/resources/start_supervisor.sh#L89) — the boot-time check, readable but not validated:
  ```sh
  if [ -r "$CONFIG_FILE" ]; then
  ```
- [`core/nmf-services-impl/src/main/java/esa/mo/nmf/environment/AppsIsolationMode.java:57-59`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-services-impl/src/main/java/esa/mo/nmf/environment/AppsIsolationMode.java#L57-L59) — `getCurrent()`, defaults to `NONE` on anything unset/unrecognized
- [`AppsIsolationMode.java:76-95`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-services-impl/src/main/java/esa/mo/nmf/environment/AppsIsolationMode.java#L76-L95) — `isLinuxUserspace()`/`isDockerContainers()`/`isBubblewrap()`, each an exact-string match against the unvalidated value

**Verification:** live-reproduced, upgraded from source-reviewed. This
testbed booted a real Supervisor against two separately corrupted
`apps-isolation` values. Full evidence is in
`findings/03-config-integrity-fail-open/README.md`.

- An unrecognized non-empty value (e.g. `totally-bogus-mode`) fails open
  exactly as described above. The boot log gives no indication of a
  problem, no App-specific Linux account gets created, and the App
  process runs directly as the Supervisor's own account.
- An **empty** value fails open too, but through a different, earlier
  mechanism. `start_supervisor.sh` has a hardcoded `apps-isolation=none`
  default that silently wins whenever the config value is empty. This
  happens before the JVM starts, so the `AppsIsolationMode` fallback is
  never reached. The suggested fix below **would need to apply in both
  places**, not just inside `AppsIsolationMode`, to close the empty-value
  case too.

**How to replicate:** see [`findings/03-config-integrity-fail-open/`](findings/03-config-integrity-fail-open/README.md#reproduction-steps).

**Suggested fix, two independent pieces:** (1) add `config.properties` to
the existing SHA256SUMS manifest, for auditability — we understand your
boot sequence deliberately never aborts on an integrity failure (the jar
SHA256SUMS check is log-only), so this should stay log-only too, not
introduce a new abort path; and (2) separately fix the fail-open direction
itself — validate `apps-isolation` against the four known modes at the read
site, and on anything unrecognized, default to the *strongest available*
mode rather than `none`. (1) alone doesn't close the gap by itself; (2) is
the piece that actually removes it. This felt like more of a boot-time
trust design call for your team than something we should hand you as a
diff, so we're flagging it rather than attaching a patch.

## 4. bubblewrap exposes the full host filesystem read-only, with no seccomp, AppArmor, or capability-dropping

Every `bwrap` invocation is built with `--ro-bind / /` — the entire host
filesystem, read-only, rather than an explicit allowlist of what the App
actually needs — with no seccomp filter, no AppArmor profile, and no
capability-dropping beyond bubblewrap's own default namespace unsharing.
This is separate from the storage-bind bug you already fixed
([PR #192](https://github.com/esa/nanosat-mo-framework/pull/192)): it's a
live hardening gap in the isolation boundary itself, not a functional bug.

**Impact:** a sandboxed App can read (though not write) essentially the
entire host filesystem, and runs with the full default Linux syscall
surface and capability set available to it.

**Where:**
- [`core/nmf-services-impl/src/main/java/esa/mo/sm/impl/provider/AppsLauncherManagerBubblewrap.java:72`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-services-impl/src/main/java/esa/mo/sm/impl/provider/AppsLauncherManagerBubblewrap.java#L72) — `assembleCommand()`
- [`AppsLauncherManagerBubblewrap.java:84`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-services-impl/src/main/java/esa/mo/sm/impl/provider/AppsLauncherManagerBubblewrap.java#L84) — the `--ro-bind / /` build:
  ```java
  ret.add("--ro-bind");
  ret.add("/");
  ret.add("/");
  ```

**Verification:** live-reproduced — the read-exposure half specifically
(write/delete are correctly blocked by the narrower per-App binds).

**How to replicate:** see [`findings/04-bubblewrap-rebind-root/`](findings/04-bubblewrap-rebind-root/README.md#reproduction-steps).

**Suggested fix:** replace `--ro-bind / /` with an explicit allowlist of
the paths an App genuinely needs (its own runtime, shared libraries, and
the already-correctly-scoped per-App storage/work/log binds), and add a
seccomp filter plus capability-dropping (`--cap-drop ALL` plus only what
the JVM needs) to the `bwrap` argument list.

## 5. `docker-containers` sets no `--pids-limit` (or any resource cap) on the sibling App container

The `docker run` invocation for an App's sibling container sets `--rm`,
`--name`, `--network`, bind mounts, and env vars — but no `--pids-limit`,
`--memory`, or `--cpus`. Since the App runs in a fully independent
container from the Supervisor, whatever resource caps the Supervisor's own
container might have don't apply to it at all.

**Impact:** a misbehaving or malicious App under `docker-containers`
isolation has no per-App process/memory/CPU ceiling — source-reviewed: no
such flag exists anywhere in the `docker run` invocation, so nothing in
this container boundary would stop one.

**Where:**
- [`core/nmf-services-impl/src/main/java/esa/mo/sm/impl/provider/AppsLauncherManagerDocker.java:112`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-services-impl/src/main/java/esa/mo/sm/impl/provider/AppsLauncherManagerDocker.java#L112) — `assembleCommand()`
- [`AppsLauncherManagerDocker.java:120-128`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-services-impl/src/main/java/esa/mo/sm/impl/provider/AppsLauncherManagerDocker.java#L120-L128) — the `docker run` flag list, `--rm` onward, with no `--pids-limit`/`--memory`/`--cpus` anywhere in it:
  ```java
  ret.add("--rm");
  ret.add("--name");
  ret.add(containerName(appName));
  ret.add("--network");
  ret.add(network());
  ...
  ret.add("-v");
  ret.add(nmfHome + ":" + nmfHome);
  ```

**Verification:** source-reviewed. The constructed `docker run` command
was also captured live from the Supervisor's own log, and it has none of
these flags (see `findings/05-docker-no-pids-limit/README.md`).

No fork bomb has been run against this container, so the impact itself
isn't live-confirmed. The source benchmarking project (`nanosats-framework`)
deliberately declines to run one in this mode, because there's no
`--pids-limit` to bound it, and reports an inconclusive result instead.
So we've confirmed nothing would stop a resource-exhausting payload here,
but we haven't watched one run unbounded.

**How to replicate:** see [`findings/05-docker-no-pids-limit/`](findings/05-docker-no-pids-limit/README.md#reproduction-steps).

**Suggested fix:** add `--pids-limit` (and ideally `--memory`/`--cpus`) to
the sibling container's `docker run` invocation, with a sensible default
and an overridable system property, matching the shape of the class's
existing `docker.image`/`docker.network` properties.

## 6. `linux-userspace`'s Debian `adduser()` path never sets `--home`

The non-BusyBox branch of the user-creation code runs `sudo adduser
--system --shell /bin/bash --group <name>` with no `--home` flag. Debian's
`adduser` then defaults that account's home to `/nonexistent`, and every
subsequent `chgrp`/`chmod` against it fails. This only happens to work
today on BusyBox/Alpine, where `adduser -S` creates a real home directory
as a side effect — an accidental workaround, not something the code does on
purpose.

**Impact:** on any Debian-based deployment, a `linux-userspace`-mode App
fails to launch outright — this is the upstream code path itself, not
specific to any one image or deployment.

**Where:**
- [`core/nmf-services-impl/src/main/java/esa/mo/nmf/environment/LinuxUsersGroups.java:74`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-services-impl/src/main/java/esa/mo/nmf/environment/LinuxUsersGroups.java#L74) — `adduser()`
- [`LinuxUsersGroups.java:98-100`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-services-impl/src/main/java/esa/mo/nmf/environment/LinuxUsersGroups.java#L98-L100) — the non-BusyBox `cmd2` branch, missing `--home`:
  ```java
  cmd2 = new String[]{"sudo", "adduser", "--system",
      "--shell", DEFAULT_SHELL, withGroup ? "--group" : "",
      username};
  ```

**Verification:** live-reproduced (see
`findings/06-adduser-no-home/README.md`).
Both symptoms appear, in order. At account creation, the Supervisor logs
`chgrp: cannot access '/nonexistent': No such file or directory`, from
`NMFPackageManager`'s broad catch, and the install still succeeds. At
launch, the App itself crashes in its `AppStorage` initialization, because
it tries to create directories under `/nonexistent`.

**How to replicate:** see [`findings/06-adduser-no-home/`](findings/06-adduser-no-home/README.md#reproduction-steps).

**Suggested fix:** pass `--home /home/<username>` (or equivalent)
explicitly in both branches, rather than relying on each platform's
differing default. Note the **same missing `--home` exists at a second call
site**: the generated `setup_linux_userspace.sh`
([`setup_linux_userspace.sh:46`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-linux-maven-plugin/src/main/resources/setup_linux_userspace.sh#L46),
in `nmf-linux-maven-plugin`) creates the Supervisor's own `nmf-admin`
account the same way, so it hits the identical bug one step earlier — see
`docs/PROVENANCE.md`'s "New discovery, this testbed" entry. Fix both, or
the Supervisor account stays broken even once the App-account path is
fixed.

## 7. bubblewrap's `DEFAULT_PATH` is hardcoded, no override

`DEFAULT_PATH = "/usr/local/bin:/usr/bin:/bin"` is set inside the sandbox
regardless of where the JVM/App binaries actually live, with no system
property to override it.

**Impact:** a launched App's own `java` invocation fails outright on any
base image where Java isn't symlinked onto that specific three-directory
list — a portability/deployability gap, not just a benchmarking
inconvenience.

**Where:**
- [`core/nmf-services-impl/src/main/java/esa/mo/sm/impl/provider/AppsLauncherManagerBubblewrap.java:55`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-services-impl/src/main/java/esa/mo/sm/impl/provider/AppsLauncherManagerBubblewrap.java#L55) — the `DEFAULT_PATH` constant:
  ```java
  private static final String DEFAULT_PATH = "/usr/local/bin:/usr/bin:/bin";
  ```
- [`AppsLauncherManagerBubblewrap.java:135`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-services-impl/src/main/java/esa/mo/sm/impl/provider/AppsLauncherManagerBubblewrap.java#L135) — its use in `assembleCommand()`

**Verification:** live-reproduced.

**How to replicate:** see [`findings/07-bubblewrap-hardcoded-path/`](findings/07-bubblewrap-hardcoded-path/README.md#reproduction-steps).

**Suggested fix:** derive the sandboxed `PATH` from the launching JVM's own
`java.home`, or expose it as an overridable system property.

## 8. PackageManagement and AppsLauncher key the same App by two different names

`findPackage`/`install` (PackageManagement) key off the package's
**filename** (e.g. `hello-world-5.1.nmfpack`); `listApp`/`runApp`
(AppsLauncher) key off the **App name** from the package's own metadata
(`info.name`, e.g. `hello-world`) — usually not the same string.

**Impact:** not a functional bug, but genuinely confusing when building
any tooling against both services together — easy to burn real debugging
time on, since a value that works for one service silently doesn't resolve
anything via the other.

**Where:**
- [`core/nmf-services-impl/src/main/java/esa/mo/sm/impl/provider/PackageManagementProviderServiceImpl.java:120`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-services-impl/src/main/java/esa/mo/sm/impl/provider/PackageManagementProviderServiceImpl.java#L120) — `findPackage`, filename-keyed
- [`PackageManagementProviderServiceImpl.java:183`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-services-impl/src/main/java/esa/mo/sm/impl/provider/PackageManagementProviderServiceImpl.java#L183) — `install`, filename-keyed
- [`core/nmf-services-impl/src/main/java/esa/mo/sm/impl/provider/AppsLauncherProviderServiceImpl.java:288`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-services-impl/src/main/java/esa/mo/sm/impl/provider/AppsLauncherProviderServiceImpl.java#L288) — `runApp`, metadata-name-keyed
- [`AppsLauncherProviderServiceImpl.java:502`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-services-impl/src/main/java/esa/mo/sm/impl/provider/AppsLauncherProviderServiceImpl.java#L502) — `listApp`, metadata-name-keyed

**Verification:** live-reproduced.

**How to replicate:** see [`findings/08-two-names-ux/`](findings/08-two-names-ux/README.md#reproduction-steps).

**Suggested fix:** worth a one-line callout in the SDK docs wherever
package install and App launch are described together — it cost us real
debugging time the first time we hit it.

## 9. `docker-containers`'s sibling-container mount silently requires the Supervisor's own filesystem to be host-bind-mounted at the same path

`assembleCommand()` mounts each App's sibling container with
`-v <nmfHome>:<nmfHome>`, where `nmfHome` is
`Deployment.getNMFRootDir().getAbsolutePath()` — resolved dynamically at
runtime, not hardcoded. Because the Supervisor talks to the host's Docker
daemon directly (sibling containers, not Docker-in-Docker), that mount
source is always resolved against the real host filesystem, whatever
`nmfHome` happens to be. This only works if the Supervisor container's own
filesystem at `nmfHome` is itself a bind-mount from a host directory at
that identical path — not a filesystem baked in via `COPY` at
image-build time.

**Impact:** `docker-containers` mode silently fails to launch any App
whenever that precondition isn't met, with a generic Docker daemon error
rather than anything NMF-specific:
```
docker: Error response from daemon: mounts denied:
The path /nanosat-mo-framework is not shared from the host and is not known to Docker.
```
This is arguably by design — the sibling-container pattern requires a
shared host path — but it isn't documented anywhere alongside
`docker-containers` mode, so it reads as a deployment bug until it's
traced back to this.

**Where:**
- [`core/nmf-services-impl/src/main/java/esa/mo/sm/impl/provider/AppsLauncherManagerDocker.java:115`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-services-impl/src/main/java/esa/mo/sm/impl/provider/AppsLauncherManagerDocker.java#L115) — `nmfHome` resolved dynamically, not hardcoded:
  ```java
  final String nmfHome = Deployment.getNMFRootDir().getAbsolutePath();
  ```
- [`AppsLauncherManagerDocker.java:127-128`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-services-impl/src/main/java/esa/mo/sm/impl/provider/AppsLauncherManagerDocker.java#L127-L128) — the sibling-container mount built from it:
  ```java
  ret.add("-v");
  ret.add(nmfHome + ":" + nmfHome);
  ```

**Verification:** live-reproduced in the source benchmarking project,
and again in this testbed on macOS + Docker Desktop with the identical
error message.

The workaround is to bind-mount the generated filesystem from a host path
that satisfies the precondition, with no NMF code change. It works on the
Linux host used by the source benchmarking project (`nanosats-framework`).
It does **not** work on Docker Desktop, which has its own File Sharing
allowlist on top of this precondition. Full trace in
`findings/09-docker-sibling-mount-hostpath/README.md`.

**How to replicate:** see [`findings/09-docker-sibling-mount-hostpath/`](findings/09-docker-sibling-mount-hostpath/README.md#reproduction-steps).

**Suggested fix:** no code defect to fix — `nmfHome` is already resolved
dynamically, which is the right approach. This is a documentation-only
gap: call out the host-bind-mount precondition explicitly next to the
`docker-containers` mode / Docker deployment instructions.

## 10. Under bubblewrap, every App failure is reported to the Supervisor as a clean exit

Found on 2026-10-06 while re-running finding 7, after the first nine went
to you. When finding 7's launch fails, the Supervisor records the App as
`process exited with code 0, classified as EXITED`, although the real exit
code was 127. This isn't specific to the missing `java`: an ordinary crash
(a `ClassNotFoundException`, real exit code 1) is recorded as exit 0 the
same way.

The cause is in the last line of the generated `start_app.sh`. It is
meant to re-export the App's real exit code from a sidecar `.exit` file:

```sh
exit "$(cat "$LOG_PATH/$FILENAME.exit" 2>/dev/null || echo 0)"
```

The bubblewrap sandbox has no `--dev`, so `/dev/null` there is the host
device node on the read-only `--ro-bind / /` mount. Writing to it fails with
`cannot create /dev/null: Permission denied`. The `2>/dev/null` redirection
fails, so `cat` never runs, the `|| echo 0` fallback fires, and the script
exits 0. The `.exit` file itself does hold the right code, because the logs
directory is bind-mounted writable.

**Impact:** under `bubblewrap`, the Supervisor and anything that monitors
App state (including the AppStopped event it archives) can't tell a crashed
App from a cleanly exited one.

**Where:**
- [`core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/utils/AuxFilesGenerator.java:118`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/utils/AuxFilesGenerator.java#L118) — the `exit "$(cat ... 2>/dev/null || echo 0)"` line, which defaults to 0 when it can't read the code
- [`AppsLauncherManagerBubblewrap.java:84`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-services-impl/src/main/java/esa/mo/sm/impl/provider/AppsLauncherManagerBubblewrap.java#L84) — `--ro-bind / /`, with no `--dev /dev` anywhere in `assembleCommand()`

**Verification:** live-reproduced on `release-5.1` and `v6.0`. A
`start_app.sh` traced with `sh -x` inside the exact sandbox shows `echo 127`
into the `.exit` file, then `cannot create /dev/null: Permission denied`,
then `exit 0`.

**How to replicate:** see [`findings/10-bubblewrap-exit-code-masked/`](findings/10-bubblewrap-exit-code-masked/README.md#reproduction-steps).

**Suggested fix:** add `--dev /dev` to the bubblewrap arguments, which
gives the sandbox a working `/dev/null` (confirmed live). Separately, the
script shouldn't default to 0 when it can't read the code. A non-zero
default, such as `|| echo 1`, would report "unknown" as a failure instead
of a success.

## Known limitation, for context — not an NMF bug

Separately from the above: bubblewrap currently can't launch an App at all
on one of our Ubuntu-based test hosts. We traced this to an Ubuntu
23.10+ AppArmor policy restricting unprivileged user-namespace creation
(`kernel.apparmor_restrict_unprivileged_userns`) — a host-config issue, not
an NMF or bubblewrap bug (it works fine for us on macOS/Docker Desktop).
It's directly relevant to finding 4's scope above (same isolation
boundary), so we're mentioning it for context rather than raising it as
something for you to act on.
