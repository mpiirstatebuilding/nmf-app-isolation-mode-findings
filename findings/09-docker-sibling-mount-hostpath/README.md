# Finding 9 — `docker-containers`'s sibling-container mount silently requires a host-bind-mounted Supervisor filesystem

**Source of truth:** root `README.md` §9 for full impact, source citations, and
suggested fix. Nothing below restates that.

**Severity (per source):** Doc gap, live-reproduced; workaround also
confirmed live.
**Classification:** live, needs Docker daemon.

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

**Result: CONFIRMED, live, 2026-09-15 — the core bug reproduces exactly as
predicted, byte-for-byte. The workaround did NOT reproduce, though — a
real, host-specific discrepancy, recorded precisely rather than forced to
match.**

- Deployed the Supervisor's filesystem via plain `COPY` at image-build
  time (`docker/docker-containers/Dockerfile`), no host bind mount at
  `/nanosat-mo-framework`. Selected `docker-containers`, installed and
  triggered `hello-world`. The sibling `docker run` exited with code 125
  and (reproduced manually to capture the exact daemon message, since NMF
  doesn't log the child process's own stderr anywhere):
  ```
  docker: Error response from daemon: mounts denied:
  The path /nanosat-mo-framework is not shared from the host and is not
  known to Docker.
  You can configure shared paths from Docker -> Preferences... -> Resources
  -> File Sharing.
  ```
  Matches the predicted error **verbatim**, down to the exact wording.
- **Workaround test — does NOT resolve it on this host, unlike the source
  project's own Linux host.** Re-ran with the Supervisor's own generated
  filesystem host-bind-mounted at the identical container-internal path
  (`-v <host-path>:/nanosat-mo-framework`, matching the source project's
  described fix exactly) — the sibling `docker run` **still** failed with
  the **identical** "mounts denied" error, unchanged.
- **Root cause of the discrepancy, traced (not just observed):** this is
  macOS + Docker Desktop specific. Docker Desktop enforces its own,
  separate File Sharing allowlist (Preferences → Resources → File
  Sharing, default roughly `/Users`, `/Volumes`, `/private`, `/tmp`) on
  top of the generic "is this bind-mounted" precondition the source
  project's workaround addresses. `/nanosat-mo-framework` is never a real
  macOS filesystem path — it only exists inside containers — so no amount
  of bind-mounting *my own* host directory at that container-internal
  path makes Docker Desktop's daemon recognize `/nanosat-mo-framework`
  itself as a shared *host* path when the **sibling** container's own,
  separate `docker run -v /nanosat-mo-framework:/nanosat-mo-framework`
  request is evaluated against the daemon's allowlist. A real Linux
  Docker host (no such allowlist layer) would very plausibly not hit
  this — consistent with the source project's own workaround having
  worked there. **Host-dependent finding, not a retraction:** the doc-gap
  bug itself (finding 9's core claim) is fully confirmed; only the
  suggested workaround's *portability* is now known to be host-specific,
  worth a one-line caveat if this goes back to César.
- Upstream SHA (per root README.md "Verified against"): `09333029773c1eac7b43e9f1ac841c1fe2ff8e86`
