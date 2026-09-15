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

_(fill in after running — pass/fail plus what was actually seen, not assumed)_

- Result:
- Upstream SHA (per root README.md "Verified against"):
- Notes:
