# Shared setup procedures

Two building blocks used by multiple findings below, adapted from the
source project's `README.md`. Neither is finding-specific — each
`findings/NN-*/README.md` links back here rather than restating these
steps.

## External dependencies

See `docs/MIGRATION_TO_TESTBED.md` section 5 for the one-time
`nanosat-mo-framework` v5.1 checkout + `mvn clean install` prerequisite,
and section 4 for the `tools/runapp-trigger` build (`mvn package` in that
directory, once the checkout has populated `~/.m2`).

**Pinned upstream SHA:** owned by the root `README.md`'s "Verified against"
line, not restated here — cite that same line from every source citation in
`findings/NN-*/`. Fresh checkout cloned 2026-09-15 at
`/Users/User/IdeaProjects/nanosat-mo-framework-v5.1` (outside this repo,
not committed — a plain `git clone --branch v5.1` of upstream). Clean
working tree, confirmed at the same tip the source project's findings were
originally verified against.

## App-scope setup (needed by findings 1, 2, 6, 8)

Without a running App to test against, checks can only observe the outer
Supervisor, not whether `appsIsolation` itself works.

1. Build the trigger tool: `mvn package` in `tools/runapp-trigger/` →
   `target/runapp-trigger-jar-with-dependencies.jar`.
2. Build a test App's `.nmfpack` — `nmf-mission-barebone`'s SDK examples
   ship one (`sdk/examples-apps/hello-world-simple`); building it produces
   `target/hello-world-5.0.nmfpack`.
   Confirmed working locally, offline, against this repo's own `~/.m2`
   (`int.esa.nmf:nanosat-mo-framework:5.1-SNAPSHOT` parent already
   populated) — `mvn -o package` in `tools/runapp-trigger/` succeeds and
   produces a runnable fat jar.
3. Install + trigger it (see "Docker networking" below for the exact
   `directoryURI` to use when the Supervisor runs in a container, which is
   every mode in this testbed):
   ```bash
   java -jar runapp-trigger-jar-with-dependencies.jar \
     maltcp://<supervisor-address>:1024/nanosat-mo-supervisor-Directory \
     hello-world hello-world-5.0.nmfpack
   ```
   **Remember the two-names gotcha (this distinction is finding 8 itself):**
   the package's **filename** (PackageManagement's install key,
   `hello-world-5.0.nmfpack`) is not the same string as the App's
   **metadata name** (AppsLauncher's `listApp`/`runApp` key, `hello-world`).
   The 3rd argument above (the package filename) makes `RunAppTrigger` run
   `findPackage`+`install` before `listApp`/`runApp` — an App sitting in
   `packages/` is not auto-installed at boot, so this step is required, not
   optional, for `listApp` to find it at all.

## Docker networking (containerized Supervisor)

**Confirmed live 2026-09-15 for `linux-userspace` only**, macOS + Docker
Desktop — not yet exercised for the other three modes, which need
different container invocations anyway (finding 9 specifically requires
the Supervisor's filesystem NOT be host-bind-mounted; findings 4/7 need
`--security-opt seccomp=unconfined` and a bubblewrap-capable host path).
Treat this as a starting point for those, not an assumption that it
transfers unchanged.
**run `RunAppTrigger` (and any consumer) as a container on the same Docker
network as the Supervisor, and address it by its real bridge IP** (`docker
inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}'
<container>`) — do not try to reach the Supervisor from the macOS host
directly, and do not force the transport to advertise/bind `127.0.0.1`:

- The Supervisor's default `autohost` behavior advertises its own bridge IP
  (e.g. `172.17.0.2`) as its provider URI. That's unreachable from the
  macOS host's own network stack even with `-p 1024:1024` published,
  because subsequent PackageManagement/AppsLauncher calls dial whatever
  host:port string the Directory response embeds — not necessarily the
  same path the initial published-port connection went through.
- Forcing loopback instead (`-Dorg.ccsds.moims.mo.mal.transport.tcpip.host=127.0.0.1`
  etc., patched into `start_supervisor.sh`'s JVM invocation) was tried and
  abandoned: the initial Directory lookup still succeeds, but the
  subsequent `findPackage`/`install` calls stalled — killed after ~150s
  with no response, not a diagnosed permanent hang. Suspected cause: the
  async response path needs the Supervisor container to dial back to the
  client's own advertised address, and a host-side client's address isn't
  reachable from inside the container's namespace either — but this wasn't
  root-caused further once the peer-container approach worked instead.
- A plain `transport.properties` file dropped into the generated
  filesystem's root is **not** picked up by `start_supervisor.sh` (it
  isn't on the JVM's classpath) — don't rely on it; use system properties
  patched directly into the JVM invocation if a transport property must be
  forced at all.
- What works: leave the Supervisor's default `autohost` alone, run the
  consumer in a container on the same (default bridge) network, e.g.:
  ```bash
  docker run --rm \
    -v /path/to/runapp-trigger-jar-with-dependencies.jar:/work/runapp-trigger.jar:ro \
    -w /work eclipse-temurin:21-jre \
    java -jar runapp-trigger.jar maltcp://<supervisor-bridge-ip>:1024/nanosat-mo-supervisor-Directory \
    hello-world hello-world-5.0.nmfpack
  ```

## Cross-app ("Track B") setup (needed by finding 4)

Install a **second** App, distinct from the first. Have App A write a
marker into its own storage (a copy of `hello-world-simple` with one line
added to its constructor — `esa.mo.nmf.AppStorage.getAppCacheDir();` — is
enough to make it touch its own storage). Have App B attempt to **read**,
**write**, and **delete** that marker, scored as three independent
outcomes, not one:

- Under `bubblewrap`, App B is *expected* to **succeed** at reading App
  A's marker — that's finding 4 itself (`--ro-bind / /` exposes it) — while
  write and delete should correctly fail. Scoring only write/delete would
  miss the read-exposure gap entirely.

Avoid giving App B a name that's a substring of App A's name — process/
container name-matching can otherwise merge the two Apps into one handle.

Both procedures above were validated live end-to-end in the source project
(all four `appsIsolation` modes, 2026-08-29) before migration; see
`docs/PROVENANCE.md` for the one piece of upstream context (PR #192) that
made live `bubblewrap` app-scope verification possible at all. Full
harness-side fix history lives in the source project's
`FLATSAT_READINESS.md` and isn't re-carried here — it documents a
benchmarking harness this testbed deliberately doesn't reuse (see
migration doc section 2).
