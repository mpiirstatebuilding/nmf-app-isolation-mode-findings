# Finding 6 — `linux-userspace`'s Debian `adduser()` path never sets `--home`

Impact, source citations and the suggested fix are in the root
[`README.md`](../../README.md) §6.

**Severity:** High, live-reproduced.
**Classification:** live, needs a specific base image.
**Setup needed:** `docs/SETUP.md` "App-scope setup".

## Base image requirement

**Use a stock Debian-based image, not Alpine.** BusyBox's `adduser -S`
creates a real home directory as a side effect, and Debian's
`adduser --system` doesn't, so an Alpine image hides this bug.
`docker/linux-userspace/` is Debian-based.

## Reproduction steps

1. Start `docker/linux-userspace/` with `hello-world-5.1.nmfpack` staged
   (`docs/SETUP.md`).
2. Install and launch it:
   `java -jar runapp-trigger.jar <directoryURI> hello-world hello-world-5.1.nmfpack`.
3. Check `docker exec nmf-sup grep app-hello-world /etc/passwd` (home is
   `/nonexistent`), and confirm that account creation logs a `chgrp: cannot access '/nonexistent':
   No such file or directory` failure, with the App then failing to launch
   with a `NoSuchFileException` under `/nonexistent/.nmf-apps/`.

## Observed

**Result: CONFIRMED, live, on `release-5.1` and `v6.0`** (SHAs per the root
`README.md` "Verified against" lines), identical on both refs. Both
symptoms appear, in order: the `chgrp` failure at account creation, then the
App crash on launch. Environment: `docker/linux-userspace/` (see finding 1's
Observed section for the shared setup). Supervisor logs: [`evidence/5.1/linux-userspace-f1-f6-f8.log`](../../evidence/5.1/linux-userspace-f1-f6-f8.log) and [`evidence/6.0/linux-userspace-f1-f6-f8.log`](../../evidence/6.0/linux-userspace-f1-f6-f8.log).

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
- **At account creation**, installing `hello-world` created
  `app-hello-world:x:101:102::/nonexistent:/bin/bash` (home is
  `/nonexistent`), and on every install the Supervisor logs:
  ```
  INFO: The permissions of the User Home directory could not be set for username: app-hello-world
   >> sudo chgrp --recursive nmf-admin /nonexistent
  chgrp: cannot access '/nonexistent': No such file or directory
  ```
- **At launch**, the App's own process (running as `app-hello-world` via
  `sudo su - app-hello-world`) fails next. `AppStorage.mkDirAndSetPermissions`
  (reached via `OneInstanceLock`) tries to create
  `$HOME/.nmf-apps/hello-world/nmf-internal` and fails with
  `java.nio.file.NoSuchFileException: /nonexistent/.nmf-apps/hello-world/nmf-internal`;
  before that, the log shows a failed write of
  `/nonexistent/.mo-services/providerURIs.properties`. That crashes the App:
  `App 'hello-world' (id=1) process exited with code 1, classified as
  CRASHED` — the same impact the root `README.md` states ("a
  linux-userspace-mode App fails to launch outright").

## Verifying a fix

Rebuild and re-run steps 1–3 on a fresh container (the account must not
exist yet). `/etc/passwd` should show `app-hello-world` with a real home
such as `/home/app-hello-world`, the install should log no `chgrp` error,
and the Supervisor should no longer log `exited with code 1, classified as
CRASHED` straight after launch.

The second call site, `setup_linux_userspace.sh`, is covered in
`docs/PROVENANCE.md`. To check that fix, delete the `usermod -d` `RUN` step
in `docker/linux-userspace/Dockerfile` and confirm the Supervisor still
boots.
