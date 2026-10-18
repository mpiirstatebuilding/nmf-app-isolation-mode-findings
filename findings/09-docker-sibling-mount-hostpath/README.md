# Finding 9 — `docker-containers`'s sibling-container mount silently requires a host-bind-mounted Supervisor filesystem

Impact, source citations and the suggested fix are in the root
[`README.md`](../../README.md) §9.

**Severity:** Doc gap, live-reproduced. The bind-mount workaround worked
on the source project's Linux host, but not on macOS + Docker Desktop (see
Observed).
**Classification:** live, needs Docker daemon.
**Setup needed:** `docs/SETUP.md` "App-scope setup", on
`docker/docker-containers/`.

## Reproduction steps

1. Start `docker/docker-containers/` with `hello-world-5.1.nmfpack` staged
   (`docs/SETUP.md`). That image `COPY`s the filesystem in, so the
   container's `nmfHome` has no matching host directory.
2. Install and launch the App with `RunAppTrigger`. The Supervisor logs
   `process exited with code 125, classified as CRASHED`.
3. NMF doesn't log the child's stderr, so re-run the logged `docker run`
   command by hand inside the Supervisor container to see the daemon
   error. It resembles:
   ```
   docker: Error response from daemon: mounts denied:
   The path <nmfHome> is not shared from the host and is not known to Docker.
   ```

## Observed

**Result: CONFIRMED, live, on `release-5.1` and `v6.0`** (SHAs per the root
`README.md` "Verified against" lines), identical on both refs. The core bug
reproduces exactly as predicted, byte-for-byte. The workaround did **not**
work on this host, which is a real, host-specific difference, recorded as
observed. Supervisor logs: [`evidence/5.1/docker-containers-f5-f9.log`](../../evidence/5.1/docker-containers-f5-f9.log) and [`evidence/6.0/docker-containers-f5-f9.log`](../../evidence/6.0/docker-containers-f5-f9.log).

- **The bug:**
  - The Supervisor's filesystem was deployed via plain `COPY` at
    image-build time (`docker/docker-containers/Dockerfile`), with no host
    bind mount at `/nanosat-mo-framework`.
  - With `docker-containers` selected, `hello-world` was installed and
    triggered. The sibling `docker run` exited with code 125.
  - NMF doesn't log the child process's stderr, so the command was re-run
    manually to capture the daemon message:
    ```
    docker: Error response from daemon: mounts denied:
    The path /nanosat-mo-framework is not shared from the host and is not
    known to Docker.
    You can configure shared paths from Docker -> Preferences... -> Resources
    -> File Sharing.
    ```
  - This matches the predicted error **verbatim**.
- **The workaround does NOT fix it on this host, unlike the source
  project's Linux host.** The Supervisor's generated filesystem was
  host-bind-mounted at the identical container path
  (`-v <host-path>:/nanosat-mo-framework`, exactly the source project's
  fix). The sibling `docker run` **still** failed with the identical
  "mounts denied" error.
- **Why, traced:** this is specific to macOS + Docker Desktop.
  - Docker Desktop enforces its own File Sharing allowlist (Preferences →
    Resources → File Sharing, default roughly `/Users`, `/Volumes`,
    `/private`, `/tmp`). That's a separate layer on top of the generic
    "is this bind-mounted" precondition the workaround addresses.
  - `/nanosat-mo-framework` is never a real macOS path. It only exists
    inside containers.
  - The sibling container's own
    `docker run -v /nanosat-mo-framework:/nanosat-mo-framework` request is
    checked against the daemon's allowlist. Bind-mounting a host directory
    into the Supervisor container at that path doesn't make Docker Desktop
    treat `/nanosat-mo-framework` as a shared *host* path.
  - A real Linux Docker host has no such allowlist, so it very plausibly
    wouldn't hit this. That's consistent with the workaround having worked
    in the source project.
- **Host-dependent, not a retraction.** The doc-gap bug itself is fully
  confirmed (the sibling `docker run` exits `125`; a manual re-run inside
  the Supervisor container gives the same "mounts denied" error). Only the
  workaround's *portability* turned out to depend on the host — that
  bind-mount workaround test is macOS + Docker Desktop specific. The root
  `README.md` §9 notes this.

## Verifying a fix

The suggested fix is documentation only. Check that the `docker-containers`
mode docs state the host-bind-mount precondition, plus the extra Docker
Desktop File Sharing caveat if you want to cover macOS developers.
