# Finding 9 — `docker-containers`'s sibling-container mount silently requires a host-bind-mounted Supervisor filesystem

**Source of truth:** root `README.md` §9 for full impact, source citations, and
suggested fix. Nothing below restates that.

**Severity (per source):** Doc gap, live-reproduced; workaround also
confirmed live.
**Classification:** live, needs Docker daemon.
**Setup needed:** `docs/SETUP.md` "App-scope setup", on
`docker/docker-containers/`.

## Reproduction steps

1. Deploy the Supervisor's own filesystem via `COPY` at image-build time
   rather than a host bind mount (i.e. give the container's `nmfHome` no
   matching host-side directory).
2. Select `docker-containers` mode and launch any App (`docs/SETUP.md`
   "App-scope setup").
3. Confirm the sibling `docker run` fails immediately with an error
   resembling:
   ```
   docker: Error response from daemon: mounts denied:
   The path <nmfHome> is not shared from the host and is not known to Docker.
   ```

## Observed

**Result: CONFIRMED, live, 2026-09-15.** The core bug reproduces exactly as
predicted, byte-for-byte. The workaround did **not** work on this host,
which is a real, host-specific difference, recorded as observed.

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
  confirmed. Only the workaround's *portability* turned out to depend on
  the host. The root `README.md` §9 notes this.
- Upstream SHA for this 2026-09-15 run: `0933302` (pre-release `v5.1`
  tip). The current pin is the root `README.md` "Verified against" line.

## Re-verified 2026-10-06: 5.1 release and v6.0

**Result: CONFIRMED on both**, live, same steps and environment as above.
The refs tested were `release-5.1` and `v6.0`; see the root `README.md`
"Verified against" lines for the SHAs. Tooling and fixtures were rebuilt
against each version (`docs/SETUP.md`). Raw Supervisor logs are in
`results/reverify-2026-10/`, which is not committed.

- The core bug is identical on both refs. The sibling `docker run` exits
  `125`, and a manual re-run inside the Supervisor container gives the same
  "mounts denied: The path /nanosat-mo-framework is not shared from the host
  and is not known to Docker." error.
- The bind-mount workaround test (macOS + Docker Desktop specific, see
  above) was not repeated this round.
