# Finding 5 — `docker-containers` sets no `--pids-limit` on the sibling App container

**Source of truth:** root `README.md` §5 for full impact, source citations, and
suggested fix. Nothing below restates that.

**Severity (per source):** High. **Was source-reviewed only; now
live-confirmed** — not by running a fork bomb (still not attempted, same
as the source project), but by capturing the actual constructed command
live rather than relying on reading the source alone. See Observed below.
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

**Result: CONFIRMED, live, 2026-09-15.** Absence of any resource-cap flag
confirmed directly from the actual constructed command, not just source
inspection — no live fork bomb attempted, per this finding's own
classification.

- **Constructed `docker run` args, captured from the Supervisor's own log**
  (`AppsLauncherManager.startAppProcess()` logs the assembled command
  before executing it, `AppsLauncherManager.java:505-507`):
  ```
  [docker, run, --rm, --name, nmf-app-hello-world, --network, host,
   -v, /nanosat-mo-framework:/nanosat-mo-framework,
   -w, /nanosat-mo-framework/apps/hello-world,
   -e, JAVA_OPTS=-Desa.mo.nmf.centralDirectoryURI=maltcp://172.17.0.2:1024/nanosat-mo-supervisor-Directory,
   eclipse-temurin:21-jre, /bin/sh, -c, ./start_app.sh]
  ```
  Confirms `AppsLauncherManagerDocker.assembleCommand()`
  (`AppsLauncherManagerDocker.java:111-141`) exactly: `--rm`, `--name`,
  `--network`, `-v`, `-w`, `-e` (one per env var), image, then the launch
  command — **no `--pids-limit`, `--memory`, or `--cpus` anywhere in the
  list**, confirming the finding directly from a live-captured command
  rather than source reading alone.
- This particular run's sibling container never actually started (see
  finding 9 — a `docker-containers`-mode host precondition this testbed
  environment doesn't meet), but that's irrelevant to this finding: the
  command is fully assembled and logged *before* `ProcessBuilder.start()`
  is even called, so the missing flags are confirmed regardless of
  whether the sibling container goes on to run successfully.
- Upstream SHA (per root README.md "Verified against"): `09333029773c1eac7b43e9f1ac841c1fe2ff8e86`
