# Findings from NMF isolation-mode benchmarking

**Verified against:** `origin/v5.1`

This repo is a **testbed**: each finding below is reproduced live and
independently under `findings/NN-*/`, with setup steps in `docs/SETUP.md`
and the migrated trigger tooling in `tools/runapp-trigger/`. See
`docs/MIGRATION_TO_TESTBED.md` for how this was assembled and
`docs/PROVENANCE.md` for two related-but-not-included findings.

Ordered by how severe/reachable we think each one is, most important first.

Only finding 1 is specifically an App-vs-App concern — it requires two
Apps colliding on the same declared name, so it's moot for a single-org
mission where one trusted party submits every App. Findings 4 and 5 are
App-vs-host, not App-vs-App: they let a single App reach beyond its own
sandbox into the host filesystem or its resources, which matters
regardless of how many organizations submit Apps. Findings 2, 3, 6, and 7
are Supervisor-level or functional bugs — an unsanitized-name path
traversal, a fail-open boot config, a broken account-creation flow, and a
hardcoded PATH — that apply to any deployment shape, not just our own
multi-tenant one. Findings 8 and 9 aren't isolation issues and apply
either way.

| # | Finding | Severity | Verification |
|---|---|---|---|
| 1 | `linux-userspace` account/identity takeover via a same-named, different-version package | High | Live-reproduced |
| 2 | Unsanitized package name → path traversal on install | High | Live-reproduced |
| 3 | `bootloader/config.properties` has no integrity check, fails open to no isolation | High | Source-reviewed |
| 4 | bubblewrap `--ro-bind / /`, no seccomp/AppArmor/cap-drop | High | Live-reproduced |
| 5 | `docker-containers` sets no `--pids-limit` on the sibling App container | High | Source-reviewed |
| 6 | `linux-userspace`'s Debian `adduser()` never sets `--home` | High | Live-reproduced |
| 7 | bubblewrap `DEFAULT_PATH` is hardcoded, no override | Medium | Live-reproduced |
| 8 | PackageManagement/AppsLauncher key the same App by two different names | Minor (UX) | Live-reproduced |
| 9 | `docker-containers` sibling-mount host-path precondition is undocumented | Doc gap | Live-reproduced |

## 1. `linux-userspace`: a same-named package with a different version silently takes over another App's account and files

Any App installed under `linux-userspace` gets a dedicated Linux account
derived straight from its declared name (`app-<info.name>`). Nothing checks
that a package now claiming that name is the same one — or the same
developer — that claimed it before. The one guard that looks like it should
catch this only blocks an *exact* name-and-version match (it exists to let
a `SNAPSHOT` rebuild override itself); a second, unrelated package that
declares the same name with any different version sails straight through
it. The second install reuses the first App's existing Linux account
(same uid/gid) and silently overwrites its installed files and start
script in place — no error, no operator-visible warning, "Package
successfully installed" logged both times. It doesn't even need a
deliberately crafted attack — two developers picking the same App name by
coincidence would trigger it.

**Impact:** under `linux-userspace`, any actor able to get a package
installed can take over an already-installed App belonging to a different
developer or organization — same uid/gid, files and start script silently
overwritten in place. This is a full identity/account takeover, not just a
shared-resource leak, and requires no privilege beyond ordinary package
submission.

**Where:**
- `core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/NMFPackageManager.java:136` — `packageName = metadata.getPackageName()`
- `NMFPackageManager.java:144` — `generateUsername(packageName)`, gated on `linux-userspace`
- `NMFPackageManager.java:157` — the unconditional `adduser()` call
- `NMFPackageManager.java:195-198` — the broad, log-only `catch`
- `NMFPackageManager.java:489-546` — `isFinalVersionInstalled()`
- `NMFPackageManager.java:543-545` — the exact name-and-version equality check, the actual gap:
  ```java
  return installedMetadata != null
          && packageName.equals(installedMetadata.getPackageName())
          && version.equals(installedMetadata.getPackageVersion());
  ```
- `NMFPackageManager.java:833-834` — `generateUsername()`'s definition
- `core/nmf-services-impl/src/main/java/esa/mo/sm/impl/provider/PackageManagementProviderServiceImpl.java:214` — where that guard is invoked at the real install-service entry point
- `core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/NMFPackagePMBackend.java:169-189` — `checkPackageIntegrity()`, the integrity-check stub — live code, unconditionally `return true` (it's the *signing* code it would call into that's commented out, see below)
- `core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/NMFPackageBuilder.java:164-192` — the commented-out package-signing block itself

**Verification:** live-reproduced.

**How to replicate:** on a Linux host with `linux-userspace` selected
(`esa.mo.nmf.packagemanager.appsIsolation=linux-userspace`), build two
packages that both declare `info.name=shared-name` in their
`package-metadata.properties` but different `info.version` values and
different content (e.g. different `pack.app.mainclass`). Install the first,
confirm the `app-shared-name` Linux account and its `apps/shared-name/`
directory now exist with the first package's content. Install the second.
Check `/etc/passwd` — still exactly one `app-shared-name` entry — and
`apps/shared-name/` — now holding the second package's files and start
script, with no error surfaced during either install.

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
something like `../../outside-target/pwned-app`, and `install()` will
write real files outside the intended install directories. This is a
Supervisor-level gap — it applies before any `appsIsolation` mode takes
effect, so it undermines filesystem boundaries regardless of which
isolation mode is active.

**Where:**
- `core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/NMFPackageManager.java:136,147` — the unvalidated name flowing straight into the install path:
  ```java
  String packageName = metadata.getPackageName();
  ...
  File appDir = new File(Deployment.getAppsDir(), packageName);
  ```
- `NMFPackageManager.java:205` — same pattern for the receipt filename
- `NMFPackageManager.java:247-248` — same pattern again for the uninstall path
- `core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/utils/HelperNMFPackage.java:168` — `resolveInside()`, the existing traversal guard, never applied to the package name
- `core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/metadata/Metadata.java:156-158` — `getPackageName()`, the raw, unvalidated read

**Verification:** live-reproduced.

**How to replicate:** build a package whose `package-metadata.properties`
declares `info.name=../../outside-target/pwned-app` (metadata otherwise
valid, no files need to be included). Install it against a scratch NMF
root. A receipt file lands one level outside the installations-tracker
directory, at `<nmf-root>/outside-target/pwned-app.receipt`, with a clean
"Package successfully installed" log line and no exception. (An absolute
path in `info.name` doesn't escape further in our testing — only the
relative `..`-traversal form does.)

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
- `core/nmf-linux-maven-plugin/src/main/java/esa/mo/nmf/filesystem/BootloaderGenerator.java:108-115` — writes `config.properties`, with `apps-isolation` set at line 109
- `BootloaderGenerator.java:121-124` — the checksum manifest is written only for the jar directories, never for `config.properties`
- `core/nmf-linux-maven-plugin/src/main/resources/start_supervisor.sh:89` — the boot-time check, readable but not validated:
  ```sh
  if [ -r "$CONFIG_FILE" ]; then
  ```
- `core/nmf-services-impl/src/main/java/esa/mo/nmf/environment/AppsIsolationMode.java:57-59` — `getCurrent()`, defaults to `NONE` on anything unset/unrecognized
- `AppsIsolationMode.java:76-95` — `isLinuxUserspace()`/`isDockerContainers()`/`isBubblewrap()`, each an exact-string match against the unvalidated value

**Verification:** source-reviewed — we haven't booted a live Supervisor
against a deliberately corrupted `apps-isolation` value to confirm the
fallthrough end-to-end; this is a trace through `AppsIsolationMode`'s code
path, not an observed boot log.

**How to replicate:** generate a filesystem with any real
`appsIsolation` mode, then edit `bootloader/config.properties` to set
`apps-isolation` to an empty string or an unrecognized value, and boot the
Supervisor. Tracing `AppsIsolationMode.getCurrent()`'s fallthrough, every
subsequently launched App should run with no isolation and nothing in the
boot log should call this out as a problem — worth confirming live if
that's useful on your end.

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
This is separate from the storage-bind bug you already fixed: it's a live
hardening gap in the isolation boundary itself, not a functional bug.

**Impact:** a sandboxed App can read (though not write) essentially the
entire host filesystem, and runs with the full default Linux syscall
surface and capability set available to it.

**Where:**
- `core/nmf-services-impl/src/main/java/esa/mo/sm/impl/provider/AppsLauncherManagerBubblewrap.java:72` — `assembleCommand()`
- `AppsLauncherManagerBubblewrap.java:84` — the `--ro-bind / /` build:
  ```java
  ret.add("--ro-bind");
  ret.add("/");
  ret.add("/");
  ```

**Verification:** live-reproduced — the read-exposure half specifically
(write/delete are correctly blocked by the narrower per-App binds).

**How to replicate:** launch two Apps under `bubblewrap` and have one try
to read a file inside the other's per-App storage directory (not write —
writes and deletes are correctly blocked by the narrower per-App binds
`assembleCommand()` sets up). The read succeeds, confirming the read
exposure is real in practice, not just implied by the `--ro-bind / /` flag
in source.

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
- `core/nmf-services-impl/src/main/java/esa/mo/sm/impl/provider/AppsLauncherManagerDocker.java:112` — `assembleCommand()`
- `AppsLauncherManagerDocker.java:120-128` — the `docker run` flag list, `--rm` onward, with no `--pids-limit`/`--memory`/`--cpus` anywhere in it:
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

**Verification:** source-reviewed, not live-confirmed. Our own benchmark
harness declines to run a live fork bomb against this specific
mode+scope combination for exactly this reason — no `--pids-limit` to
bound it against — and reports an inconclusive result instead of
attempting one. So we've confirmed from the flag list that nothing would
stop a resource-exhausting payload here, but we haven't watched one run
unbounded in this container ourselves.

**How to replicate:** inspect the `docker run` flag list `assembleCommand()`
builds for the sibling container — no `--pids-limit`/`--memory`/`--cpus`
appears anywhere in it, unlike the Supervisor's own container.

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
- `core/nmf-services-impl/src/main/java/esa/mo/nmf/environment/LinuxUsersGroups.java:74` — `adduser()`
- `LinuxUsersGroups.java:98-100` — the non-BusyBox `cmd2` branch, missing `--home`:
  ```java
  cmd2 = new String[]{"sudo", "adduser", "--system",
      "--shell", DEFAULT_SHELL, withGroup ? "--group" : "",
      username};
  ```

**Verification:** live-reproduced.

**How to replicate:** select `linux-userspace` on a Debian-based image and
install any App. Account creation logs a `chgrp: cannot access
'/nonexistent': No such file or directory` failure and the App fails to
launch.

**Suggested fix:** pass `--home /home/<username>` (or equivalent)
explicitly in both branches, rather than relying on each platform's
differing default.

## 7. bubblewrap's `DEFAULT_PATH` is hardcoded, no override

`DEFAULT_PATH = "/usr/local/bin:/usr/bin:/bin"` is set inside the sandbox
regardless of where the JVM/App binaries actually live, with no system
property to override it.

**Impact:** a launched App's own `java` invocation fails outright on any
base image where Java isn't symlinked onto that specific three-directory
list — a portability/deployability gap, not just a benchmarking
inconvenience.

**Where:**
- `core/nmf-services-impl/src/main/java/esa/mo/sm/impl/provider/AppsLauncherManagerBubblewrap.java:55` — the `DEFAULT_PATH` constant:
  ```java
  private static final String DEFAULT_PATH = "/usr/local/bin:/usr/bin:/bin";
  ```
- `AppsLauncherManagerBubblewrap.java:135` — its use in `assembleCommand()`

**Verification:** live-reproduced.

**How to replicate:** launch an App under `bubblewrap` on an
`eclipse-temurin`-based image (Java installed under
`/opt/java/openjdk/bin`, not on the sandboxed `PATH`). The App's own
`java` invocation fails with `java: not found` unless something symlinks
it onto one of those three directories.

**Suggested fix:** derive the sandboxed `PATH` from the launching JVM's own
`java.home`, or expose it as an overridable system property.

## 8. PackageManagement and AppsLauncher key the same App by two different names

`findPackage`/`install` (PackageManagement) key off the package's
**filename** (e.g. `hello-world-5.0.nmfpack`); `listApp`/`runApp`
(AppsLauncher) key off the **App name** from the package's own metadata
(`info.name`, e.g. `hello-world`) — usually not the same string.

**Impact:** not a functional bug, but genuinely confusing when building
any tooling against both services together — easy to burn real debugging
time on, since a value that works for one service silently doesn't resolve
anything via the other.

**Where:**
- `core/nmf-services-impl/src/main/java/esa/mo/sm/impl/provider/PackageManagementProviderServiceImpl.java:120` — `findPackage`, filename-keyed
- `PackageManagementProviderServiceImpl.java:183` — `install`, filename-keyed
- `core/nmf-services-impl/src/main/java/esa/mo/sm/impl/provider/AppsLauncherProviderServiceImpl.java:288` — `runApp`, metadata-name-keyed
- `AppsLauncherProviderServiceImpl.java:502` — `listApp`, metadata-name-keyed

**Verification:** live-reproduced.

**How to replicate:** install a package and note the filename you used;
try to look it up via `listApp`/`runApp` using that same filename — it
won't match, because those calls expect the metadata-derived App name
instead.

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
- `core/nmf-services-impl/src/main/java/esa/mo/sm/impl/provider/AppsLauncherManagerDocker.java:115` — `nmfHome` resolved dynamically, not hardcoded:
  ```java
  final String nmfHome = Deployment.getNMFRootDir().getAbsolutePath();
  ```
- `AppsLauncherManagerDocker.java:127-128` — the sibling-container mount built from it:
  ```java
  ret.add("-v");
  ret.add(nmfHome + ":" + nmfHome);
  ```

**Verification:** live-reproduced; a harness-side workaround (bind-mounting
our generated filesystem at a host path that satisfies the precondition,
no NMF code change) is also confirmed live.

**How to replicate:** deploy the Supervisor's own filesystem via `COPY` at
image-build time rather than a host bind mount (i.e. give the container's
`nmfHome` no matching host-side directory), select `docker-containers`,
and launch any App. The sibling `docker run` fails immediately with the
mount-denied error above.

**Suggested fix:** no code defect to fix — `nmfHome` is already resolved
dynamically, which is the right approach. This is a documentation-only
gap: call out the host-bind-mount precondition explicitly next to the
`docker-containers` mode / Docker deployment instructions.

## Known limitation, for context — not an NMF bug

Separately from the above: bubblewrap currently can't launch an App at all
on one of our Ubuntu-based test hosts. We traced this to an Ubuntu
23.10+ AppArmor policy restricting unprivileged user-namespace creation
(`kernel.apparmor_restrict_unprivileged_userns`) — a host-config issue, not
an NMF or bubblewrap bug (it works fine for us on macOS/Docker Desktop).
It's directly relevant to finding 4's scope above (same isolation
boundary), so we're mentioning it for context rather than raising it as
something for you to act on.
