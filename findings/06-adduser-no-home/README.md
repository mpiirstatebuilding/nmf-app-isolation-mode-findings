# Finding 6 — `linux-userspace`'s Debian `adduser()` path never sets `--home`

**Source of truth:** root `README.md` §6 for full impact, source citations, and
suggested fix. Nothing below restates that.

**Severity (per source):** High, live-reproduced.
**Classification:** live, needs a specific base image.
**Setup needed:** `docs/SETUP.md` "App-scope setup".

## Base image requirement

**Use a stock Debian-based image, not Alpine.** The source project's own
benchmarking harness builds a separate Alpine/BusyBox-based Dockerfile for
`linux-userspace` mode specifically to dodge this bug (BusyBox's
`adduser -S`/`-D` creates a real home directory as a side effect; Debian's
`adduser --system` doesn't). Do not carry that Alpine workaround into this
testbed's image — it would silently suppress the bug.

## Reproduction steps

1. Select `linux-userspace` mode on a Debian-based image.
2. Install any App.
3. Confirm account creation logs a `chgrp: cannot access '/nonexistent':
   No such file or directory` failure, with the App failing to launch.

## Observed

**Result: CONFIRMED, live, 2026-09-15** — but the failure surfaced one step
later than the predicted signature, and in two places, not one. Recorded
precisely rather than adjusted to match the prediction (same principle
finding 3 states explicitly).

- Base image used: `eclipse-temurin:21-jre`, actually **Ubuntu 26.04**
  (`ID_LIKE=debian` — Ubuntu inherits the same `adduser` package/behavior
  from Debian, so it still exhibits the bug; not literally "Debian" but
  satisfies the "not Alpine/BusyBox" requirement this finding is actually
  about).
- **Same root cause hit before this finding was even reachable:**
  `setup_linux_userspace.sh` (NMF-generated, a different call site from
  this finding's own `LinuxUsersGroups.java`) creates the Supervisor's own
  `nmf-admin` account the identical way and hit the identical bug — see
  `docs/PROVENANCE.md`'s "New discovery, this testbed" entry for the full
  writeup and why it isn't folded into this finding's own numbering.
- **The finding itself, App account:** installing `hello-world` created
  `app-hello-world:x:101:102::/nonexistent:/bin/bash` — home is
  `/nonexistent`, confirmed. The predicted
  `chgrp: cannot access '/nonexistent': No such file or directory` did
  **not** appear at account-creation time in our run — the account
  creation's own `chgrp`/`chmod` calls target `apps/hello-world/` and
  `logs/app_hello-world/` (real, existing directories), not `$HOME`
  directly, and those succeeded. The actual failure surfaced one step
  later, inside the App's own process (running as `app-hello-world` via
  `sudo su - app-hello-world`): `AppStorage.mkDirAndSetPermissions` tries to
  create `$HOME/.nmf-apps/hello-world/nmf-internal` and fails with
  `java.nio.file.NoSuchFileException: /nonexistent/.nmf-apps/hello-world/nmf-internal`,
  which crashes the App on launch (`App 'hello-world' (id=1) process exited
  with code 1, classified as CRASHED`). Same root cause, same overall
  impact the source `README.md` states ("a linux-userspace-mode App fails
  to launch outright") — just a different log signature than predicted.
- Upstream SHA for this 2026-09-15 run: `0933302` (pre-release `v5.1`
  tip). The current pin is the root `README.md` "Verified against" line.
- Environment: `docker/linux-userspace/`, see findings/01's Observed section
  for the shared setup.

## Re-verified 2026-10-06: 5.1 release and v6.0

**Result: CONFIRMED on both**, live, same steps and environment as above.
The refs tested were `release-5.1` and `v6.0`; see the root `README.md`
"Verified against" lines for the SHAs. Tooling and fixtures were rebuilt
against each version (`docs/SETUP.md`). Raw Supervisor logs are in
`results/reverify-2026-10/`, which is not committed.

- The App still crashes on launch on both refs (`exited with code 1,
  classified as CRASHED`). It crashes through the same
  `AppStorage.mkDirAndSetPermissions` → `/nonexistent/.nmf-apps` chain,
  reached via `OneInstanceLock`. Before that, the log shows a failed write
  of `/nonexistent/.mo-services/providerURIs.properties`.
- **Delta from the 2026-09-15 run:** the originally predicted signature now
  *does* appear, on every install, at account creation:
  ```
  INFO: The permissions of the User Home directory could not be set for username: app-hello-world
   >> sudo chgrp --recursive nmf-admin /nonexistent
  chgrp: cannot access '/nonexistent': No such file or directory
  ```
  The September run recorded this as absent for the first install. The cause
  of the difference wasn't determined. `NMFPackageManager`,
  `LinuxUsersGroups`, `AppStorage` and `OneInstanceLock` are unchanged
  between `0933302` and `release-5.1`. Either way, the root `README.md` §6
  note that the signature varies still covers it.
