# Migrating this repo's work into the testbed repo

> **Status: completed, historical.** This runbook was carried out in
> September 2026 and is kept as a record of the migration decisions. Since
> then:
> - The canonical findings are the root `README.md`, numbered 1–10 (not
>   `CESAR_OUTREACH.md`'s 1–9). Finding 10 was added on 2026-10-06.
> - Current setup procedures are in `docs/SETUP.md`. They supersede this
>   document's `5.1-SNAPSHOT`/`v5.1` clone steps and its
>   `maltcp://127.0.0.1` directory URI.

**Audience: a fresh Claude Code instance with no memory of the conversation that produced this document**, opened in (or about to be pointed at) the private GitHub repo already created to share isolation-mode findings with César Coelho (NMF maintainer). That repo's next job is to become a **testbed** that reproduces 9 specific findings live for him, ahead of fix PRs upstream to `github.com/esa/nanosat-mo-framework`.

## 1. How to use this document

This is a one-time migration runbook, not ongoing project documentation. Read section 2 (scope) and section 3 (source-of-truth rule) in full before copying or adapting anything — several sections below name things you must deliberately *leave out* or *invert*, and doing the copy in file order without reading those first will re-introduce exactly the workarounds that suppress the bugs you're trying to demonstrate.

## 2. Purpose and non-goals

**Purpose:** get the testbed repo to the point where each of the 9 findings already sent to César is independently reproducible and demonstrable, so he can see each issue live.

**Non-goals — do not port these, and do not rebuild their equivalents:**
- `bench_isolation.sh`'s full 4-mode CSV cost/performance benchmarking loop (startup latency, memory, CPU, storage overhead measurement across `-n` iterations).
- Its fork-bomb safety/containment machinery (`PIDS_LIMIT`, `SUPERVISOR_CPU_LIMIT`, the settle-loop reap, `FORK_BOMB_ALTERNATE_SCOPES`).
- `analyze_results.py`'s cross-mode CSV aggregation, and `isolation_mode_comparison_20260909.md`'s standalone VM benchmark report.

All of the above served Phase 1 benchmarking — a distinct, already-completed goal in the source project. The testbed's job is narrower: demonstrate 9 specific bugs, not re-run a benchmark suite. Build only the specific `appsIsolation` mode(s) each finding below actually needs — most need exactly one, not all four.

## 3. Source of truth for the 9 findings — read this before citing a finding by number

**`CESAR_OUTREACH.md`, as it exists in this repo's working tree, is canonical.** Use its numbering (1–9) everywhere in the testbed repo. Do not use `git show HEAD:CESAR_OUTREACH.md` — at the time this document was written, that file was staged *and* further modified beyond the staged version, so `HEAD`'s copy is stale relative to what's actually on disk.

**`CESAR_FINDINGS.md` is a different, 11-entry internal log — not interchangeable with the outreach numbering.** It includes one finding already FIXED and merged upstream (its own #1, PR #192) and one RETRACTED finding (its own #7, a harness bug, not an NMF behavior) — neither is among the 9 sent to César. Its numbering for the 9 that *are* shared is a near-reverse mapping of the outreach numbering:

| Outreach # (canonical — use this) | `CESAR_FINDINGS.md` # (internal log — do not use for new references) |
|---|---|
| 1 — same-name/different-version account takeover | 11 |
| 2 — path traversal on install | 10 |
| 3 — `config.properties` no integrity check | 9 |
| 4 — bubblewrap `--ro-bind / /` | 8 |
| 5 — no `--pids-limit` | 6 |
| 6 — Debian `adduser()` no `--home` | 2 |
| 7 — `DEFAULT_PATH` hardcoded | 3 |
| 8 — two-names UX | 5 |
| 9 — sibling-mount host-path | 4 |
| *(not among the 9)* | 1 — FIXED, PR #192 merged upstream |
| *(not among the 9)* | 7 — RETRACTED (harness bug) |

If you copy a code comment or doc excerpt that cites `CESAR_FINDINGS.md #N`, translate it through this table to the outreach number before it lands in the testbed repo.

**Do not create a fourth copy of these findings.** This project's own `CLAUDE.md` documents that the same content has already drifted into multiple docs twice (commits `43facef`, `b7acf48`), and a third instance of the same pattern happened during this same session: `CESAR_FINDINGS.md` → `CESAR_OUTREACH.md` → a since-zipped `ISOLATION_MODE_FINDINGS.md`, all describing the same 9 findings. If the testbed repo needs a findings reference, section 7 below *is* that reference — do not write a new summary document restating the same content again.

## 4. File migration manifest

| Source file | Action | Notes |
|---|---|---|
| `CESAR_OUTREACH.md` | **Copy verbatim** | Canonical findings source — see section 3. |
| `RunAppTrigger.java` | **Copy verbatim** | Self-contained headless MAL trigger (`package esa.mo.nmf.bench;`) that drives a running Supervisor like CTT's "Run App" button: `findPackage`+`install` (if given a package filename) → `listApp` → `runApp`. Needed as-is for findings 1, 2, 6, 8, and useful for 4/7's launch step. |
| `pom.xml` | **Adapt** | Copy the `<parent>` block (NMF `5.1-SNAPSHOT` + `nmf-composites` dependency) and the `maven-assembly-plugin` fat-jar config verbatim. **Required edit, not a copy:** `<sourceDirectory>${project.basedir}</sourceDirectory>` only applies if the new repo also keeps `RunAppTrigger.java` at its module root rather than a conventional `src/main/java/...` layout — change it if you use the latter. |
| `CESAR_FINDINGS.md` | **Adapt — provenance appendix only** | Carry only two facts (see section 6's provenance appendix content below), not full prose for the 9 — that's `CESAR_OUTREACH.md`'s job. |
| `CLAUDE.md` | **Adapt — excerpt only** | Pull just the "Documentation ownership (avoid bloat)" convention and a one-paragraph project-purpose summary. The rest is Phase 0–5 benchmarking-project context that doesn't apply to a testbed repo. |
| `FLATSAT_READINESS.md` | **Adapt — excerpt only** | Pull only: fix-history entries relevant to findings 1/2/4/6/8, and the Phase 3.1 "Track B" cross-app design rationale (quoted in section 7 below). Do not carry its cost/performance results tables or completed-phase checklists. |
| `README.md` | **Adapt — excerpt only** | Pull the "Enabling app-scope checks" and "Enabling cross-app isolation checks (Track B)" procedures near-verbatim (quoted in section 7). Do not carry the CLI-flags/env-vars/CSV-columns reference — that documents `bench_isolation.sh`'s benchmarking loop, out of scope here. |
| `target/runapp-trigger-jar-with-dependencies.jar` | **Rebuild, don't copy** | Build artifact. Run `mvn package` in the new repo after the `~/.m2` prerequisite in section 5 is met. |
| `bench_isolation.sh` | **Do not copy the file.** Extract only the specific fragments named in section 7. | Mixes real per-finding preconditions with benchmarking-only machinery and the fork-bomb safety rails this migration excludes. Copying it whole would silently re-suppress several of the bugs it's meant to help demonstrate — see the toggle-pair callouts in section 7. |
| `analyze_results.py`, `isolation_mode_comparison_20260909.md` | **Leave behind** | Served Phase 1 benchmarking/comparison, not this testbed. |
| `MANAGEMENT_QUESTIONS.md`, `NETWORK_ISOLATION_RESEARCH.md` | **Leave behind** | Gitignored personal tracking docs in the source repo, explicitly marked out of scope there. |
| `results/`, `target/`, `.idea/`, `.DS_Store` | **Leave behind** | Build/IDE/OS artifacts. |
| `ISOLATION_MODE_FINDINGS.md.zip`, `isolation_mode_findings.docx`, `isolation_mode_findings.pdf`, `~$olation_mode_findings.docx` | **Leave behind — do not migrate** | Presentation exports of content `CESAR_OUTREACH.md` already owns. Migrating them recreates the duplication problem in section 3. The `~$...docx` is a transient Word lock file regardless. |

## 5. External dependency setup

Every finding below needs a working `nanosat-mo-framework` v5.1 checkout and a populated `~/.m2`.

1. **Do not reuse the source project's existing checkout as-is.** `/Users/User/nanosat-mo-framework/nmf-mission-barebone` (on the machine that produced this document) has its working tree mutated in place by prior benchmarking runs — a patched `barebone-space-filesystem/Dockerfile` (bubblewrap/docker.io/java-symlink added) and a patched generated `start_supervisor.sh` (loopback binding). Either:
   - **Clone fresh** (recommended): `git clone --branch v5.1 https://github.com/esa/nanosat-mo-framework`, or
   - Reuse an existing checkout only after confirming (`git status`/`git diff`) and resetting those two files to pristine.
2. **Record the checkout's commit once**, immediately after cloning/resetting: `git rev-parse HEAD`. Cite that pinned SHA (not bare `File.java:123`-style line references against a moving branch) in every per-finding source citation below and in anything the testbed repo writes about upstream source. (At the time this document was written, the source project's own findings were verified against tip `093330297` on `v5.1` — a reasonable starting point, but re-pin against your own fresh clone rather than assuming that SHA is still current.)
3. **One-time build**, at the checkout root: `mvn clean install`. Populates `~/.m2` with `int.esa.nmf:nanosat-mo-framework:5.1-SNAPSHOT` (parent POM) and `nmf-composites` — required both to generate any isolation-mode filesystem and to compile the migrated `RunAppTrigger.java`/`pom.xml`.
4. **Per-mode filesystem generation** (reused by most findings below): now in `docs/SETUP.md` "Generating a mode's filesystem".

## 6. Target repo layout

Propose, don't mandate — **the destination repo already exists; inspect its current contents (`ls`, `git log`) before laying anything down.** If it's effectively empty, a reasonable default:

```
tools/runapp-trigger/     # migrated Maven module (RunAppTrigger.java + pom.xml)
findings/NN-short-name/   # one directory per finding, NN = outreach number
docs/
  MIGRATION_TO_TESTBED.md # this document, once copied over
  PROVENANCE.md           # the CESAR_FINDINGS.md appendix below
```

**Provenance appendix content** (for `docs/PROVENANCE.md` or equivalent — two facts, not full prose):
- Finding 1 in `CESAR_FINDINGS.md`'s internal numbering (App storage bind missing under bubblewrap) was already fixed and merged upstream as [PR #192](https://github.com/esa/nanosat-mo-framework/pull/192) — evidence of prior good-faith collaboration with César, useful context when opening new PRs, but not one of the 9 findings this testbed reproduces.
- Finding 7 in that same internal numbering (`linux-userspace` Apps observed with `gid=0`) was **retracted** — confirmed to be a harness bug (a process-matching bug in the *source* project's own benchmarking script, not an NMF behavior) — not one of the 9, and should never be resurrected as a claim against NMF.

## 7. Per-finding reproduction specs

Two shared building blocks referenced below, adapted from `README.md`:

**App-scope setup** (needed by findings 1, 2, 6, 8): build `runapp-trigger` (`mvn package`), build a test App's `.nmfpack` (`nmf-mission-barebone`'s `sdk/examples-apps/hello-world-simple` example works), then install + trigger it via `RunAppTrigger`: `java -jar runapp-trigger-jar-with-dependencies.jar maltcp://127.0.0.1:1024/nanosat-mo-supervisor-Directory <appName> [packageFilename]`. Remember: the package's **filename** (PackageManagement's install key) and the App's **metadata name** (AppsLauncher's `listApp`/`runApp` key) are different strings — confirmed even for `hello-world-simple` (`hello-world-5.0.nmfpack` vs. `hello-world`). This distinction *is* finding 8.

**Cross-app ("Track B") setup** (needed by finding 4): install a *second* App distinct from the first (avoid a name that's a substring of the first App's name — the harness locates App processes by name-matching and can otherwise merge the two). Have App A write a marker into its own storage, then have App B attempt to **read**, **write**, and **delete** that marker — score these as three independent outcomes, not one. Under `bubblewrap`, App B is *expected* to succeed at reading App A's marker (that's the bug — `--ro-bind / /` exposes it) while write/delete should fail; scoring only write/delete would miss the read-exposure gap entirely.

---

### 1. `linux-userspace` account/identity takeover via same-name, different-version package
**Severity/status:** High, live-reproduced. **Classification:** fully scriptable, no special base image.
Build two `.nmfpack` packages with identical `info.name` but different `info.version` and different content. Generate a `linux-userspace`-mode filesystem, install the first package, confirm its dedicated Linux account and app directory. Install the second package (same name). Check `/etc/passwd` — still exactly one account for that name — and the app directory, now holding the second package's files, with no error surfaced during either install.

### 2. Unsanitized package name → path traversal on install
**Severity/status:** High, live-reproduced. **Classification:** fully scriptable, mode-independent (Supervisor-level bug — any mode works).
Build a `.nmfpack` with `info.name=../../outside-target/pwned-app`. Install it. Confirm a receipt file lands one level outside the installations-tracker directory, with a clean "Package successfully installed" log and no exception.

### 3. `bootloader/config.properties` no integrity check at boot — fails open to no isolation
**Severity/status:** High, **source-reviewed only** in the source project — not yet live-confirmed end-to-end. **Classification: this migration can be the first live confirmation; treat it as new verification work, not a rerun.**
Generate a filesystem for any mode. Edit the generated `bootloader/config.properties`, setting its isolation-mode key to an empty string or an unrecognized value. Boot the Supervisor. Trace whether every subsequently launched App runs with no isolation (the fail-open direction the source-only analysis predicts) and whether the boot log gives any indication of the problem. **If the actual observed behavior differs from this prediction (e.g. the Supervisor fails to start instead of failing open) — that is a genuine finding update to report back to César, not a broken repro. Don't silently adjust the test to match the prediction.**

### 4. bubblewrap exposes the full host filesystem read-only, with no seccomp/AppArmor/cap-drop
**Severity/status:** High, live-reproduced (read-exposure half only — write/delete are correctly blocked). **Classification:** live, environment-dependent.
Use the Track B two-App setup above under `bubblewrap` mode. App B should succeed at reading App A's storage marker.
**Environment preconditions, not workarounds to invert — both are real:**
- The Supervisor's own container needs `--security-opt seccomp=unconfined`: Docker's default seccomp profile blocks `pivot_root(2)`, which bubblewrap's `--ro-bind / /` needs, regardless of what capabilities are granted.
- If verifying via in-namespace inspection (e.g. `nsenter`) rather than just the read/write/delete file test, the Supervisor's container also needs `--cap-add=SYS_PTRACE --cap-add=SYS_ADMIN`.
- Separately, bubblewrap itself needs a host where unprivileged user-namespace creation isn't blocked — known-blocked on Ubuntu 23.10+ (an AppArmor policy default, `kernel.apparmor_restrict_unprivileged_userns`), known-fine on macOS/Docker Desktop.

### 5. `docker-containers` sets no `--pids-limit` on the sibling App container
**Severity/status:** High, source-reviewed, **not live-confirmed** in the source project (its own harness deliberately declined to run a live fork bomb here, since there's nothing to bound it against). **Classification: inspection-only — do not attempt a live fork bomb against the sibling container in this testbed either.**
This is about the **sibling per-App container's own** `docker run` invocation (built by NMF's `AppsLauncherManagerDocker.assembleCommand()`), a different code path from any `--pids-limit` a testbed's own outer Supervisor container might set on itself — don't conflate the two. Demonstrate by citing/printing the actual constructed `docker run` argument list for the sibling container (source citation, pinned SHA) and pointing out the absence of `--pids-limit`/`--memory`/`--cpus` anywhere in it.

### 6. `linux-userspace`'s Debian `adduser()` path never sets `--home`
**Severity/status:** High, live-reproduced. **Classification:** live, needs a specific base image.
**Toggle-pair callout:** the source project's `bench_isolation.sh` builds a *separate Alpine/BusyBox-based Dockerfile* specifically for `linux-userspace` mode, explicitly to dodge this exact bug (BusyBox's `adduser -S`/`-D` creates a real home directory as a side effect; Debian's `adduser --system` doesn't). **Use a stock Debian-based image instead of an Alpine one** to let the bug surface. Select `linux-userspace` mode, install any App, and confirm account creation logs a `chgrp: cannot access '/nonexistent'` failure with the App failing to launch.

### 7. bubblewrap's `DEFAULT_PATH` is hardcoded, no override
**Severity/status:** Medium, live-reproduced. **Classification:** live, needs a specific base image.
**Toggle-pair callout:** the source project patches a `RUN ln -s "$(command -v java)" /usr/local/bin/java` line into its Supervisor Dockerfile specifically to work around this. **Omit that symlink** and use a `java`-having base image where `java` is *not* on bubblewrap's hardcoded `/usr/local/bin:/usr/bin:/bin` (e.g. an `eclipse-temurin` image, where Java lives under `/opt/java/openjdk/bin`). Launch an App under `bubblewrap` and confirm its own `java` invocation fails with `java: not found`.

### 8. PackageManagement and AppsLauncher key the same App by two different names
**Severity/status:** Minor (UX), live-reproduced. **Classification:** fully scriptable, simplest finding — good first smoke-test that the migrated toolchain (App-scope setup above) works at all.
Install a package and note its filename. Call `listApp`/`runApp` using that filename — it won't match. Call again using the App's metadata-derived name instead (see the App-scope setup note above) — it succeeds.

### 9. `docker-containers`'s sibling-container mount silently requires the Supervisor's own filesystem to be host-bind-mounted at the same path
**Severity/status:** Doc gap, live-reproduced, workaround also confirmed live. **Classification:** live, needs Docker daemon.
**Toggle-pair callout:** the source project's own Supervisor-launch step adds `-v "$fs_path:$fs_path" -w "$fs_path"` to the Supervisor's `docker run`, explicitly commented there as "path-matching fix" for this exact bug. **Omit that bind-mount flag** when starting the Supervisor container for `docker-containers` mode (let the Supervisor run on just its image-baked-in filesystem, no host-side directory at that same path). Select `docker-containers` mode and launch any App — the sibling container's own `docker run` should fail with a Docker daemon error resembling `mounts denied: The path <nmfHome> is not shared from the host and is not known to Docker.`

## 8. Host/environment capability matrix

| Finding | Base image | Host requirement | Classification |
|---|---|---|---|
| 1, 2, 8 | any | none special | fully scriptable |
| 3 | any | none special, but new live work | live — new verification, report surprises |
| 4 | any (Track B second App) | bubblewrap-capable host (blocked on Ubuntu 23.10+ AppArmor default; fine on macOS/Docker Desktop); Supervisor container needs `--security-opt seccomp=unconfined` (+ `SYS_PTRACE`/`SYS_ADMIN` for in-namespace verification) | live, read-exposure half only |
| 5 | any | none (no live exploit attempted) | inspection-only |
| 6 | Debian-based (stock, not Alpine) | none special | live |
| 7 | `eclipse-temurin` (java-symlink patch omitted) | none special | live |
| 9 | any (deployed without the path-matching bind-mount) | Docker daemon | live |

## 9. Acceptance criteria / verification checklist

- [ ] `mvn clean install` succeeds at the fresh `nanosat-mo-framework` checkout root; `~/.m2` contains `int.esa.nmf:nanosat-mo-framework:5.1-SNAPSHOT` and `nmf-composites`.
- [ ] The migrated `runapp-trigger` module builds (`mvn package`) and produces a runnable `*-jar-with-dependencies.jar`.
- [ ] Each of the 9 findings has been executed at least once in the testbed repo, with an **observed** result recorded (not assumed) — pass/fail plus what was actually seen.
- [ ] Finding 3 specifically: its live-vs-source-reviewed disposition after this testbed's attempt is explicitly recorded, along with whether the observed behavior matched the fail-open prediction.
- [ ] No fourth findings document was created; `CESAR_OUTREACH.md` (or this document's section 7) remains the single reference the testbed's own writeups link back to.
- [ ] None of the "leave behind" items in section 4 were copied into the testbed repo.
- [ ] Every upstream source citation used in the testbed repo is pinned to the recorded commit SHA (section 5, step 2), not a bare line number against a moving branch.

## 10. Out of scope for this document

Opening fix PRs against `github.com/esa/nanosat-mo-framework` is the next phase, once each finding is demonstrable — not planned by this document.

## 11. Open questions / assumptions

This document makes defaults explicit rather than leaving them implicit, so a fresh instance (or the user) can quickly confirm or override them rather than re-deriving from scratch:

- **Host:** assumed macOS + Docker Desktop, the source project's own known-good environment for bubblewrap-related findings (4, 7). Confirm this matches the testbed's actual host before treating findings 4/7 as guaranteed-live rather than "attempt live, fall back to inspection."
- **Finding 3:** defaulted to attempting live confirmation (an upgrade beyond source-reviewed) since it's cheap to try — but this is optional/stretch, not required for a minimal demo of the other 8.
- **`CESAR_FINDINGS.md`'s non-target entries** (internal #1 fixed, internal #7 retracted): defaulted to a two-line provenance note only (section 6), not fuller migration.
- **Upstream ref for citations:** defaulted to `v5.1` (matches everything already verified in the source project) — worth a quick check whether upstream has moved past it before the eventual PR-authoring phase.
- **Destination repo structure:** unknown from the source project's side — the first action after this document is copied over should be inspecting what's already in the destination repo, not assuming it's empty.
