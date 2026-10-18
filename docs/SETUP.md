# Shared setup procedures

Setup steps used by more than one finding. Each `findings/NN-*/README.md`
links here rather than restating them.

## External dependencies

**Pinned upstream SHA:** owned by the root `README.md`'s "Verified against"
line, not restated here. Cite that same line from every source citation in
`findings/NN-*/`.

**Checkouts** of upstream NMF live outside this repo and aren't committed.
Clone `nanosat-mo-framework` once and check out the ref you're testing (the
SHAs and tags are on the root `README.md`'s "Verified against" line); a
`git worktree` per ref lets you keep more than one side by side. The
reproductions were run against worktrees named
`../nanosat-mo-framework-5.1-release` (tag `release-5.1`) and
`../nanosat-mo-framework-v6.0` (branch `v6.0`); any paths are fine as long
as you point the build at them.

Run `mvn clean install` in each. That puts `5.1` and `6.0-SNAPSHOT` side
by side in `~/.m2`. v6.0 needs JDK 21+.

**Match the tooling to the Supervisor's NMF version.** The NMF major
version is also the MO area version, and a provider rejects other area
versions. So a 5.x-built `RunAppTrigger` cannot drive a 6.0 Supervisor.
Observed 2026-10-06: the Supervisor logs `Unsupported Area ...
(51, 5)`, and the client gets back an MO error wrapping a
`NullPointerException`.

1. Run `tools/set-nmf-version.sh <version>` (`5.1`, the committed default,
   or `6.0-SNAPSHOT`). It points `tools/runapp-trigger/` and every
   `findings/*/fixtures/` module at one version.
2. Rebuild each module with `rm -rf target && mvn -o package`. Delete
   `target/` explicitly, because a failed build leaves the previous
   version's jar in place.
3. Tag the testbed images per version too (e.g.
   `nmf-testbed-linux-userspace:5.1` / `:6.0`). The `docker/*/Dockerfile`s
   themselves are version-agnostic.

## Generating a mode's filesystem

Most findings need a filesystem generated for one `appsIsolation` mode.
From `<checkout>/nmf-mission-barebone/barebone-space-filesystem`:

```bash
mvn package -Pgenerate-filesystem -Dgenerate-filesystem.appsIsolation=<mode>
```

where `<mode>` is one of `none`, `linux-userspace`, `bubblewrap`,
`docker-containers`.

## Building and running the Supervisor image per mode

Each mode has its own Dockerfile under `docker/<mode>/`. The Dockerfile
header comments cover the *why* (base image choice, seccomp, etc.); the
build/run commands are here so there's one place to copy them from.

The build context is the generated filesystem's parent directory
(`.../barebone-space-filesystem/target/space-filesystem`), so the
`COPY nanosat-mo-framework/ ...` in each Dockerfile picks it up. For the
App-scope and cross-app findings, **stage the `.nmfpack` test packages into
`nanosat-mo-framework/packages/` inside that context before building** (see
"App-scope setup" below) — the Supervisor does not auto-install from
`packages/`, and `findPackage` only sees files that are there.

```bash
FS=<checkout>/nmf-mission-barebone/barebone-space-filesystem/target/space-filesystem
V=5.1   # or 6.0, matching the filesystem you generated

# linux-userspace (findings 1, 2, 3, 6, 8)
docker build -f docker/linux-userspace/Dockerfile -t nmf-testbed-linux-userspace:$V "$FS"
docker run -d --name nmf-sup nmf-testbed-linux-userspace:$V

# bubblewrap (findings 4, 7, 10) — needs the seccomp opt, plus the AppArmor
# opt on a Linux Docker host; see finding 4's preconditions
docker build -f docker/bubblewrap/Dockerfile -t nmf-testbed-bubblewrap:$V "$FS"
docker run -d --name nmf-sup --security-opt seccomp=unconfined --security-opt apparmor=unconfined nmf-testbed-bubblewrap:$V

# docker-containers (findings 5, 9) — Docker-outside-of-Docker, needs the
# host docker.sock; finding 9's precondition is that the filesystem is
# COPY'd in (no host bind mount of nmfHome), which this image already does.
# On macOS + Docker Desktop the sibling container never starts, even with
# the bind-mount workaround (see finding 9), so verifying a fix to finding 5
# or 9 end-to-end needs a Linux Docker host.
docker build -f docker/docker-containers/Dockerfile -t nmf-testbed-docker-containers:$V "$FS"
docker run -d --name nmf-sup -v /var/run/docker.sock:/var/run/docker.sock nmf-testbed-docker-containers:$V
```

Confirm the Supervisor booted and is in the mode you expect:

```bash
docker logs nmf-sup 2>&1 | grep -m1 "supervisor started"
docker exec nmf-sup sh -c 'ps aux | grep -o "appsIsolation=[a-z-]*" | head -1'
```

Then drive it with `RunAppTrigger` per "Docker networking" below.

## App-scope setup (needed by every finding)

Without a running App to test against, checks can only observe the outer
Supervisor, not whether `appsIsolation` itself works.

1. Build the trigger tool: `mvn package` in `tools/runapp-trigger/` →
   `target/runapp-trigger-jar-with-dependencies.jar`, after
   `tools/set-nmf-version.sh` (see above).
2. Build a test App's `.nmfpack` — `nmf-mission-barebone`'s SDK examples
   ship one (`sdk/examples-apps/hello-world-simple`); building it in a
   checkout produces `target/hello-world-<that NMF version>.nmfpack`, e.g.
   `hello-world-5.1.nmfpack` or `hello-world-6.0-SNAPSHOT.nmfpack`.
   Confirmed working offline (`mvn -o package`) against both the `5.1` and
   `6.0-SNAPSHOT` parents on 2026-10-06; `RunAppTrigger.java` and the
   fixtures compile against either unchanged.
   - **Finding-specific fixtures** live under each finding's `fixtures/`
     (e.g. findings 1 and 4). Build them the same way: run
     `tools/set-nmf-version.sh <version>` first, then `rm -rf target &&
     mvn -o package` in the fixture module. Finding 2's fixture has an
     extra `build-fixture.py` post-step — see its own README.
3. **Stage the package(s) into `packages/`.** Copy each `.nmfpack` into the
   generated filesystem's `nanosat-mo-framework/packages/` directory
   *before* building the Supervisor image (the Dockerfile `COPY`s the whole
   tree in). If the Supervisor is already running, `docker cp
   <pkg>.nmfpack nmf-sup:/nanosat-mo-framework/packages/` works too, as
   long as the file ends up readable by the Supervisor's account. This step
   is required: an App sitting in `packages/` is not auto-installed at boot,
   and `findPackage` only sees what's on disk there.
4. Install + trigger it (see "Docker networking" below for the exact
   `directoryURI` to use when the Supervisor runs in a container, which is
   every mode in this testbed):
   ```bash
   java -jar runapp-trigger-jar-with-dependencies.jar \
     maltcp://<supervisor-address>:1024/nanosat-mo-supervisor-Directory \
     hello-world hello-world-5.1.nmfpack
   ```
   **Remember the two-names gotcha (this distinction is finding 8 itself):**
   the package's **filename** (PackageManagement's install key,
   `hello-world-5.1.nmfpack`) is not the same string as the App's
   **metadata name** (AppsLauncher's `listApp`/`runApp` key, `hello-world`).
   The 3rd argument above (the package filename) makes `RunAppTrigger` run
   `findPackage`+`install` before `listApp`/`runApp` — an App sitting in
   `packages/` is not auto-installed at boot, so this step is required, not
   optional, for `listApp` to find it at all.

## Docker networking (containerized Supervisor)

Every mode in this testbed runs the Supervisor in a container. **Run
`RunAppTrigger` (and any other consumer) as a container on the same Docker
network as the Supervisor, and address the Supervisor by its real bridge
IP.** Get the IP with:

```bash
docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' <container>
```

Leave the Supervisor's default `autohost` alone, and run the consumer on
the same (default bridge) network, e.g.:

```bash
docker run --rm \
  -v /path/to/runapp-trigger-jar-with-dependencies.jar:/work/runapp-trigger.jar:ro \
  -w /work eclipse-temurin:21-jre \
  java -jar runapp-trigger.jar maltcp://<supervisor-bridge-ip>:1024/nanosat-mo-supervisor-Directory \
  hello-world hello-world-5.1.nmfpack
```

The finding READMEs shorten this whole wrapper to
`java -jar runapp-trigger.jar <directoryURI> <appName> [packageFileName]`.

Confirmed live for all three modes (`linux-userspace`, `bubblewrap`,
`docker-containers`) on macOS + Docker Desktop. Each mode needs its own
Supervisor container invocation, but the consumer side is the same for all
three:

- finding 9 requires that the Supervisor's filesystem is NOT
  host-bind-mounted
- findings 4, 7 and 10 need `--security-opt seccomp=unconfined` (plus
  `--security-opt apparmor=unconfined` on a Linux Docker host) and a
  bubblewrap-capable host

**Tried and rejected:**

- **Reaching the Supervisor from the macOS host directly.** The
  Supervisor's `autohost` advertises its bridge IP (e.g. `172.17.0.2`) as
  its provider URI. That's unreachable from the macOS host's network stack
  even with `-p 1024:1024` published. Later PackageManagement/AppsLauncher
  calls dial whatever host:port the Directory response embeds, not
  necessarily the path the first published-port connection used.
- **Forcing loopback** (`-Dorg.ccsds.moims.mo.mal.transport.tcpip.host=127.0.0.1`
  etc., patched into `start_supervisor.sh`'s JVM invocation). The initial
  Directory lookup still succeeds, but the following `findPackage`/`install`
  calls stalled. They were killed after ~150s with no response; this
  wasn't diagnosed as a permanent hang. Suspected cause: the async
  response path needs the Supervisor container to dial back to the
  client's advertised address, which a host-side client's address isn't
  reachable at from inside the container. Not root-caused further once the
  peer-container approach worked.
- **A `transport.properties` file in the generated filesystem's root.**
  `start_supervisor.sh` doesn't pick it up, because it isn't on the JVM's
  classpath. If a transport property must be forced at all, patch it into
  the JVM invocation as a system property.

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

With this testbed's fixtures (`findings/04-*/fixtures/track-b-app-a/` and
`track-b-app-b/`), stage both `.nmfpack`s into `packages/`, then run
`RunAppTrigger` twice, in this order (same `docker run` wrapper as above):

```bash
java -jar runapp-trigger-jar-with-dependencies.jar <directoryURI> track-b-app-a track-b-app-a-1.0.0.nmfpack
java -jar runapp-trigger-jar-with-dependencies.jar <directoryURI> track-b-app-b track-b-app-b-1.0.0.nmfpack
```

The first run makes App A write its marker. The second makes App B try to
read, write and delete it, and print `TrackBAppB results:` with one line
per outcome to its App log under `logs/app_track-b-app-b/`.

Avoid giving App B a name that's a substring of App A's name — process/
container name-matching can otherwise merge the two Apps into one handle.

The App-scope and Track B procedures were validated live end-to-end in the
source project (all four `appsIsolation` modes, 2026-08-29) before this
testbed was built. See `docs/PROVENANCE.md` for PR #192, the upstream fix
that made live `bubblewrap` app-scope verification possible at all.
