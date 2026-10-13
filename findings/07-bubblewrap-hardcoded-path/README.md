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

- The App's own log (`logs/app_track-b-app-a/*.log`) shows
  `./start_app.sh: 46: java: not found`.
- The real exit code was **127** ("command not found"). It's in the
  sidecar `.exit` file that `start_app.sh` writes so the code survives its
  own `tee` pipeline (per that script's comment).
- The Supervisor recorded the App's exit differently. (Corrected below:
  it recorded exit 0. That masking is now finding 10.)
- This really is the blocking factor. Finding 4's Observed section
  re-triggered the same App after symlinking `java` onto the sandbox's
  hardcoded `PATH`, and it ran. That symlink was applied live in a
  separate run, not baked into this finding's image.
- Upstream SHA for this 2026-09-15 run: `0933302` (pre-release `v5.1`
  tip). The current pin is the root `README.md` "Verified against" line.
- Environment: `docker/bubblewrap/`, run with `--security-opt
  seccomp=unconfined`. Without that flag, `bwrap` itself can't start,
  which is a different, earlier failure (see finding 4's Observed section).

## Re-verified 2026-10-06: 5.1 release and v6.0

**Result: CONFIRMED on both**, live, same steps and environment as above.
The refs tested were `release-5.1` and `v6.0`; see the root `README.md`
"Verified against" lines for the SHAs. Tooling and fixtures were rebuilt
against each version (`docs/SETUP.md`). Raw Supervisor logs are in
`results/reverify-2026-10/`, which is not committed.

- Identical on both refs: the App log shows `./start_app.sh: 46: java: not
  found`, and the sidecar `.exit` file holds `127`.
- Recorded more precisely than before: the Supervisor logs this same run as
  `process exited with code 0, classified as EXITED`. So the launch failure
  is reported to the operator as a clean exit. Traced to `/dev/null` not
  being writable inside the sandbox, which defeats `start_app.sh`'s
  exit-code re-export. It affects every App failure under bubblewrap, not
  just this one, so it is now its own finding: see
  `findings/10-bubblewrap-exit-code-masked/` and root `README.md` §10.
