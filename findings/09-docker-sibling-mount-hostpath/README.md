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

_(fill in after running — pass/fail plus what was actually seen, not assumed)_

- Result:
- Workaround (host-bind-mounting the generated filesystem at a matching
  path) also re-confirmed?
- Upstream SHA (per root README.md "Verified against"):
- Notes:
