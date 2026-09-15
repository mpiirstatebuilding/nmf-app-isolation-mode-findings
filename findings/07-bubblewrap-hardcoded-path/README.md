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

_(fill in after running — pass/fail plus what was actually seen, not assumed)_

- Result:
- Upstream SHA (per root README.md "Verified against"):
- Notes:
