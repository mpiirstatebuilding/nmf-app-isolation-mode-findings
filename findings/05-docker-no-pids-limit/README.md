# Finding 5 — `docker-containers` sets no `--pids-limit` on the sibling App container

**Source of truth:** root `README.md` §5 for full impact, source citations, and
suggested fix. Nothing below restates that.

**Severity (per source):** High, source-reviewed, **not live-confirmed** (the
source project's own harness deliberately declined to run a live fork bomb
here, since there's nothing to bound it against).
**Classification: inspection-only — do not attempt a live fork bomb against
the sibling container in this testbed either.**

This is about the **sibling per-App container's own** `docker run`
invocation (built by `AppsLauncherManagerDocker.assembleCommand()`) — a
different code path from any `--pids-limit` a testbed's own outer
Supervisor container might set on itself. Don't conflate the two.

## Reproduction steps

1. Trigger an App launch under `docker-containers` mode (see
   `docs/SETUP.md` "App-scope setup" for the trigger tooling).
2. Cite/print the actual constructed `docker run` argument list for the
   sibling container (source citation, pinned SHA).
3. Point out the absence of `--pids-limit`/`--memory`/`--cpus` anywhere in
   it.

## Observed

_(fill in after running — pass/fail plus what was actually seen, not assumed)_

- Constructed `docker run` args observed:
- Upstream SHA (per root README.md "Verified against"):
- Notes:
