# Finding 5 — `docker-containers` sets no `--pids-limit` on the sibling App container

Impact, source citations and the suggested fix are in the root
[`README.md`](../../README.md) §5.

**Severity:** High, source-reviewed. The constructed command
was also captured live (see Observed), but no fork bomb was run, so the
impact itself isn't live-confirmed.
**Classification:** inspection-only. No fork bomb is run against the
sibling container in this testbed.
**Setup needed:** `docs/SETUP.md` "App-scope setup", on
`docker/docker-containers/`.

This is about the **sibling per-App container's own** `docker run`
invocation (built by `AppsLauncherManagerDocker.assembleCommand()`) — a
different code path from any `--pids-limit` a testbed's own outer
Supervisor container might set on itself. Don't conflate the two.

## Reproduction steps

1. Trigger an App launch under `docker-containers` mode (see
   `docs/SETUP.md` "App-scope setup" for the trigger tooling).
2. Read the sibling container's constructed `docker run` argument list
   straight from the Supervisor log — `AppsLauncherManager.startAppProcess()`
   logs the full assembled command before executing it
   ([`AppsLauncherManager.java:505-507`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-services-impl/src/main/java/esa/mo/sm/impl/provider/AppsLauncherManager.java#L505-L507) on `release-5.1`). For example:
   `docker logs nmf-sup 2>&1 | grep -m1 '\[docker, run,'`.
3. Note the absence of `--pids-limit`/`--memory`/`--cpus` anywhere in
   it. The command is logged before
   `ProcessBuilder.start()`, so the missing flags show up whether or not the
   sibling container then goes on to run (it doesn't, in this testbed — see
   finding 9).

## Observed

**Result: launch command captured live on `release-5.1` and `v6.0`;
impact not run.** Identical on both refs (SHAs per the root `README.md`
"Verified against" lines). The missing resource-cap flags are confirmed
from the actual constructed command, not just source inspection. No fork
bomb was run, so the finding stays "Source-reviewed" in the root table. Supervisor logs: [`evidence/5.1/docker-containers-f5-f9.log`](../../evidence/5.1/docker-containers-f5-f9.log) and [`evidence/6.0/docker-containers-f5-f9.log`](../../evidence/6.0/docker-containers-f5-f9.log).

- **Constructed `docker run` args, captured from the Supervisor's own log**
  (`AppsLauncherManager.startAppProcess()` logs the assembled command
  before executing it, [`AppsLauncherManager.java:505-507`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-services-impl/src/main/java/esa/mo/sm/impl/provider/AppsLauncherManager.java#L505-L507) on `release-5.1`;
  `:503-505` on `v6.0`, the code unchanged with two lines removed above it):
  ```
  [docker, run, --rm, --name, nmf-app-hello-world, --network, host,
   -v, /nanosat-mo-framework:/nanosat-mo-framework,
   -w, /nanosat-mo-framework/apps/hello-world,
   -e, JAVA_OPTS=-Desa.mo.nmf.centralDirectoryURI=maltcp://172.17.0.2:1024/nanosat-mo-supervisor-Directory,
   eclipse-temurin:21-jre, /bin/sh, -c, ./start_app.sh]
  ```
  Confirms `AppsLauncherManagerDocker.assembleCommand()`
  ([`AppsLauncherManagerDocker.java:111-141`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-services-impl/src/main/java/esa/mo/sm/impl/provider/AppsLauncherManagerDocker.java#L111-L141)) exactly: `--rm`, `--name`,
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

## Verifying a fix

Re-run steps 1–2. The logged `[docker, run, ...]` command should now
contain `--pids-limit` (and `--memory`/`--cpus` if added), with the
default value, or the override when the new system property is set. To see
the limit hold at runtime, the sibling container has to start. That needs
a Linux Docker host, because on Docker Desktop finding 9 stops it first.
There, `docker inspect -f '{{.HostConfig.PidsLimit}}' nmf-app-hello-world`
shows the applied limit.
