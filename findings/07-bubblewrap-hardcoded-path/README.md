# Finding 7 — bubblewrap's `DEFAULT_PATH` is hardcoded, no override

**Source of truth:** root `README.md` §7 for full impact, source citations, and
suggested fix. Nothing below restates that.

**Severity (per source):** Medium, live-reproduced.
**Classification:** live, needs a specific base image.
**Setup needed:** `docs/SETUP.md` "App-scope setup".

## Base image requirement

**Use an `eclipse-temurin`-based image, and omit any `java` symlink patch.**
The source project patches a
`RUN ln -s "$(command -v java)" /usr/local/bin/java` line into its
Supervisor Dockerfile specifically to work around this bug. Do not carry
that symlink into this testbed's image — it would silently suppress the
bug. `eclipse-temurin` puts Java under `/opt/java/openjdk/bin`, which is
not on bubblewrap's hardcoded `/usr/local/bin:/usr/bin:/bin`.

## Reproduction steps

1. Launch an App under `bubblewrap` mode on the (unpatched) `eclipse-temurin`
   image.
2. Confirm the App's own `java` invocation fails with `java: not found`.

## Observed

**Result: CONFIRMED, live, 2026-09-15.** `docker/bubblewrap/` (unpatched
`eclipse-temurin:21-jre`) with `track-b-app-a` installed and triggered.

- App's own log (`logs/app_track-b-app-a/*.log`) shows
  `./start_app.sh: 46: java: not found`.
- Confirmed the *real* underlying exit code (the sidecar `.exit` file
  `start_app.sh` writes to survive its own `tee` pipeline, per that
  script's own comment) was **127** ("command not found"), even though the
  App process as reported to the Supervisor was recorded differently —
  see finding 4's Observed section, which re-triggered the same App after
  fixing this specific issue (by symlinking `java` onto the sandbox's
  hardcoded `PATH`, live, in a *separate* run — not baked into this
  finding's own image) in order to test Track B; that confirms this really
  is the blocking factor, not a red herring.
- Upstream SHA (per root README.md "Verified against"): `09333029773c1eac7b43e9f1ac841c1fe2ff8e86`
- Environment: `docker/bubblewrap/`, run with `--security-opt
  seccomp=unconfined` (see finding 4's Observed section for that
  precondition's own discovery — without it, `bwrap` itself can't even
  start, a different, earlier failure than this one).
