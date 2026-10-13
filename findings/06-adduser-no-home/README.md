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

**Result: CONFIRMED, live, 2026-09-15.** The App crashes on launch as
predicted. This run recorded the failure as appearing one step later than
the predicted signature, and in two places, not one. (Corrected below:
the predicted `chgrp` line *does* appear at account creation; this run
missed it.)

- **Base image:** `eclipse-temurin:21-jre`, which is actually
  **Ubuntu 26.04** (`ID_LIKE=debian`). Ubuntu inherits Debian's `adduser`
  package and behavior, so it still shows the bug. It isn't literally
  Debian, but it meets the "not Alpine/BusyBox" requirement that matters
  here.
- **The same root cause hit earlier, before this finding was reachable.**
  NMF's generated `setup_linux_userspace.sh`, a different call site from
  this finding's `LinuxUsersGroups.java`, creates the Supervisor's own
  `nmf-admin` account the same way and hits the same bug. See
  `docs/PROVENANCE.md`'s "New discovery, this testbed" entry for the full
  write-up and why it isn't numbered as part of this finding.
- **The finding itself, App account:**
  - Installing `hello-world` created
    `app-hello-world:x:101:102::/nonexistent:/bin/bash`, so the home is
    `/nonexistent`.
  - This run recorded the predicted
    `chgrp: cannot access '/nonexistent': No such file or directory` as
    **not** appearing at account creation (corrected below). It
    attributed that to the account-creation `chgrp`/`chmod` calls
    targeting `apps/hello-world/` and `logs/app_hello-world/`, which
    exist, rather than `$HOME`.
  - The App's own process (running as `app-hello-world` via
    `sudo su - app-hello-world`) failed next.
    `AppStorage.mkDirAndSetPermissions` tries to create
    `$HOME/.nmf-apps/hello-world/nmf-internal` and fails with
    `java.nio.file.NoSuchFileException: /nonexistent/.nmf-apps/hello-world/nmf-internal`.
  - That crashes the App on launch: `App 'hello-world' (id=1) process
    exited with code 1, classified as CRASHED`.
  - Same root cause, and the same impact the root `README.md` states ("a
    linux-userspace-mode App fails to launch outright").
- Upstream SHA for this 2026-09-15 run: `0933302` (pre-release `v5.1`
  tip). The current pin is the root `README.md` "Verified against" line.
- Environment: `docker/linux-userspace/`. See finding 1's Observed section
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
- **Correction to the 2026-09-15 write-up above:** the originally predicted
  signature *does* appear, on every install, at account creation:
  ```
  INFO: The permissions of the User Home directory could not be set for username: app-hello-world
   >> sudo chgrp --recursive nmf-admin /nonexistent
  chgrp: cannot access '/nonexistent': No such file or directory
  ```
  The September run recorded this as absent. Re-running the September
  image itself (`nmf-testbed-linux-userspace:latest`, built 2026-09-15,
  `jars-nmf/5.1-SNAPSHOT`) shows the same three lines on its first install.
  So the September write-up missed the line; the behavior hasn't changed
  between versions. Root `README.md` §6 is updated to describe both
  symptoms.
