# Finding 10 — under bubblewrap, every App failure is reported as a clean exit

**Source of truth:** root `README.md` §10 for full impact, source citations,
and suggested fix. Nothing below restates that.

**Severity (per source):** Medium, live-reproduced.
**Classification:** live, environment-dependent (same `bubblewrap`
preconditions as findings 4 and 7).
**Setup needed:** `docs/SETUP.md` "App-scope setup", on `docker/bubblewrap/`
run with `--security-opt seccomp=unconfined`.

Found 2026-10-06 while re-running finding 7, so it was not among the nine
findings originally sent to César.

## Reproduction steps

1. Under `bubblewrap`, install and launch an App that exits non-zero. Two
   ways:
   - finding 7's own failure: unpatched image, so `java: not found` (127)
   - an ordinary crash: symlink `java` onto the sandbox `PATH` (as in
     finding 4), then point the installed App's `start_app.sh` at a
     `MAIN_CLASS` that doesn't exist (1)
2. Compare the Supervisor log's `process exited with code N, classified as
   ...` line with the sidecar `logs/app_<name>/<name>_<date>.log.exit` file.
3. To see the mechanism, run the exact logged `bwrap` launcher command by
   hand with `/bin/sh -x ./start_app.sh` in place of `./start_app.sh`.

## Observed

**Result: CONFIRMED, live, 2026-10-06, on `release-5.1` and `v6.0`** (SHAs
per the root `README.md` "Verified against" lines). App `track-b-app-a`
(finding 4's fixture).

- **Missing `java` (finding 7's case):** the Supervisor logged `process
  exited with code 0, classified as EXITED`, and the `.exit` file held
  `127`.
- **Ordinary crash** (`MAIN_CLASS=does.not.Exist`, with `java` reachable):
  the App log shows `Caused by: java.lang.ClassNotFoundException:
  does.not.Exist`. The Supervisor logged `exited with code 0, classified as
  EXITED`, and the `.exit` file held `1`. Same result on both refs.
- **Trace inside the sandbox** (`sh -x`, 5.1):
  ```
  ./start_app.sh: 46: java: not found
  + echo 127
  ./start_app.sh: 52: cannot create /dev/null: Permission denied
  + echo 0
  + exit 0
  ```
- **The script itself is fine outside the sandbox:** the same
  `start_app.sh` run directly in the container returns `127`.
- **bwrap itself passes exit codes through:** `bwrap --unshare-all
  --ro-bind / / --die-with-parent ... /bin/sh -c "exit 127"` returns 127.
  So the loss is the `/dev/null` redirection, not bwrap.
- **Fix direction confirmed:** inside `bwrap --unshare-all --ro-bind / /`,
  `/dev/null` is not writable. Adding `--dev /dev` makes it writable.
- Environment: `docker/bubblewrap/` images `nmf-testbed-bubblewrap:5.1` and
  `:6.0`, bubblewrap 0.11.1. Logs:
  `results/reverify-2026-10/*-bubblewrap-exitcode-masking.supervisor.log`
  (not committed).
