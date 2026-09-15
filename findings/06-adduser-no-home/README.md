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
- Upstream SHA (per root README.md "Verified against"): `09333029773c1eac7b43e9f1ac841c1fe2ff8e86`
- Environment: `docker/linux-userspace/`, see findings/01's Observed section
  for the shared setup.
