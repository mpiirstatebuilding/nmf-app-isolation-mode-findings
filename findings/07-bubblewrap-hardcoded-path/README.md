# Finding 7 — bubblewrap's `DEFAULT_PATH` is hardcoded, no override

Impact, source citations and the suggested fix are in the root
[`README.md`](../../README.md) §7.

**Severity:** Medium, live-reproduced.
**Classification:** live, needs a specific base image.
**Setup needed:** `docs/SETUP.md` "App-scope setup".

## Base image requirement

**Use an `eclipse-temurin`-based image with no `java` symlink.** A
`ln -s "$(command -v java)" /usr/local/bin/java` in the image hides this
bug, so `docker/bubblewrap/` deliberately has none. `eclipse-temurin` puts Java under `/opt/java/openjdk/bin`, which is
not on bubblewrap's hardcoded `/usr/local/bin:/usr/bin:/bin`.

## Reproduction steps

1. Start `docker/bubblewrap/` with `--security-opt seccomp=unconfined` (plus
   `--security-opt apparmor=unconfined` on a Linux Docker host, see
   finding 4's preconditions) and
   `track-b-app-a-1.0.0.nmfpack` staged (`docs/SETUP.md`).
2. Install and launch it:
   `java -jar runapp-trigger.jar <directoryURI> track-b-app-a track-b-app-a-1.0.0.nmfpack`.
3. Check the App's log:
   ```bash
   docker exec nmf-sup sh -c 'grep -h "java: not found" /nanosat-mo-framework/logs/app_track-b-app-a/*.log'
   ```
   The bug: `./start_app.sh: 46: java: not found`.

## Verifying a fix

On a fresh container (no symlink), re-run steps 1–3. A fixed Supervisor
puts `java.home`'s `bin` (or the override property's value) on the
sandbox `PATH`. The `--setenv, PATH, ...` value in the logged `Using
launcher command:` line should include `/opt/java/openjdk/bin`, and the
App log should have no `java: not found`. Because of finding 10, the
Supervisor reports exit 0 either way, so check the App log or the `.exit`
file, not the Supervisor's exit code.

## Observed

**Result: CONFIRMED, live, on `release-5.1` and `v6.0`** (SHAs per the root
`README.md` "Verified against" lines), identical on both refs.
`docker/bubblewrap/` (unpatched `eclipse-temurin:21-jre`, run with
`--security-opt seccomp=unconfined`) with `track-b-app-a` installed and
triggered. Supervisor logs: [`evidence/5.1/bubblewrap-f4-f7.log`](../../evidence/5.1/bubblewrap-f4-f7.log) and [`evidence/6.0/bubblewrap-f4-f7.log`](../../evidence/6.0/bubblewrap-f4-f7.log).

- The App's own log (`logs/app_track-b-app-a/*.log`) shows
  `./start_app.sh: 46: java: not found`.
- The real exit code was **127** ("command not found"). It's in the
  sidecar `.exit` file that `start_app.sh` writes so the code survives its
  own `tee` pipeline (per that script's comment).
- The Supervisor logs this same run as `process exited with code 0,
  classified as EXITED` — so the launch failure is reported to the operator
  as a clean exit. That masking is a separate bug of its own (traced to
  `/dev/null` not being writable in the sandbox, defeating `start_app.sh`'s
  exit-code re-export, and affecting every App failure under bubblewrap):
  see `findings/10-bubblewrap-exit-code-masked/` and root `README.md` §10.
- This really is the blocking factor. Finding 4's Observed section
  re-triggered the same App after symlinking `java` onto the sandbox's
  hardcoded `PATH`, and it ran. That symlink was applied live in a
  separate run, not baked into this finding's image.
- Without the `--security-opt seccomp=unconfined` flag, `bwrap` itself
  can't start, which is a different, earlier failure (see finding 4's
  Observed section).
