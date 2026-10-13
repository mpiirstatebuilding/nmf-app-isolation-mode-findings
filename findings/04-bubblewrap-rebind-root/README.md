# Finding 4 — bubblewrap exposes the full host filesystem read-only, no seccomp/AppArmor/cap-drop

**Source of truth:** root `README.md` §4 for full impact, source citations, and
suggested fix. Nothing below restates that.

**Severity (per source):** High, live-reproduced (read-exposure half only —
write/delete are correctly blocked).
**Classification:** live, environment-dependent.
**Setup needed:** `docs/SETUP.md` "Cross-app (Track B) setup".

## Environment preconditions (real, not workarounds to invert)

- The Supervisor's own container needs `--security-opt seccomp=unconfined`:
  Docker's default seccomp profile blocks `pivot_root(2)`, which
  bubblewrap's `--ro-bind / /` needs, regardless of granted capabilities.
- If verifying via in-namespace inspection (e.g. `nsenter`) rather than just
  the read/write/delete file test, the Supervisor's container also needs
  `--cap-add=SYS_PTRACE --cap-add=SYS_ADMIN`.
- Separately, bubblewrap itself needs a host where unprivileged
  user-namespace creation isn't blocked — known-blocked on Ubuntu 23.10+
  (an AppArmor policy default, `kernel.apparmor_restrict_unprivileged_userns`),
  known-fine on macOS/Docker Desktop. Confirm which applies to this host
  before treating this finding as guaranteed-live rather than
  "attempt live, fall back to inspection" (`docs/MIGRATION_TO_TESTBED.md` §11).

## Reproduction steps

1. Use the Track B two-App setup (`docs/SETUP.md`) under `bubblewrap` mode.
2. Have App B attempt to read App A's storage marker (not write — writes and
   deletes are correctly blocked by the narrower per-App binds).
3. The read is expected to succeed — that's the bug.

## Observed

**Result: CONFIRMED, live, 2026-09-15 — clean match with the prediction,
all three outcomes exactly as expected.**

Fixtures: `fixtures/track-b-app-a/` and `fixtures/track-b-app-b/` in this
directory (two new Maven modules, not copied from anywhere — App A writes
a marker into `AppStorage.getAppCacheDir()`; App B hardcodes the path
`$HOME/.nmf-apps/track-b-app-a/cache/marker.txt` and attempts read, then
write, then delete). Environment: `docker/bubblewrap/`.

- **`--security-opt seccomp=unconfined` precondition confirmed real and
  necessary** — without it, even a bare `bwrap --unshare-all ... /bin/echo
  hello` failed outright:
  `bwrap: No permissions to create a new namespace, likely because the
  kernel does not allow non-privileged user namespaces.` One correction to
  the predicted mechanism: `docs/MIGRATION_TO_TESTBED.md` §7 attributes
  this flag's necessity specifically to `pivot_root(2)` being blocked; what was
  actually observed here is the **user-namespace creation itself**
  (`unshare`/`clone(CLONE_NEWUSER)`) being blocked by Docker's default
  seccomp profile — `pivot_root` may be a second thing the same flag
  unblocks, not verified separately. With the flag applied, the same bare
  invocation succeeded immediately.
- **Host confirmed in the "known-fine" bucket** — macOS + Docker Desktop,
  per `docs/MIGRATION_TO_TESTBED.md` §11's assumption. Confirmed, not just
  assumed.
- **Getting Apps to actually run at all under `bubblewrap` first required
  working around finding 7 (unrelated bug, tested independently in its own
  right — see that finding's Observed section) for *this* test run only:**
  symlinked `java` onto the sandbox's hardcoded `PATH`
  (`/usr/local/bin/java -> /opt/java/openjdk/bin/java`), live inside the
  running container via `docker exec -u root`, **not** baked into
  `docker/bubblewrap/Dockerfile`. This is necessary infrastructure to reach
  Track B at all (an App that can't launch can't write a marker to read),
  not a suppression of finding 7 — finding 7 itself was independently
  confirmed first, on the same image, *before* this symlink was applied.
- **App A:** launched successfully once `java` was reachable; confirmed
  `/home/nmf/.nmf-apps/track-b-app-a/cache/marker.txt` contains
  `secret-from-A`.
- **App B, all three outcomes exactly as predicted:**
  ```
  READ: SUCCESS content=[secret-from-A]
  WRITE: FAILED java.io.FileNotFoundException: /home/nmf/.nmf-apps/track-b-app-a/cache/marker.txt (Read-only file system)
  DELETE: FAILED (delete() returned false)
  ```
  Confirmed on disk afterward too: `marker.txt` still reads
  `secret-from-A`, unmodified — the read-exposure is real, and write/delete
  really are blocked by the narrower per-App binds, exactly as the root
  `README.md` states.
- Upstream SHA for this 2026-09-15 run: `0933302` (pre-release `v5.1`
  tip). The current pin is the root `README.md` "Verified against" line.

## Re-verified 2026-10-06: 5.1 release and v6.0

**Result: CONFIRMED on both**, live, same steps and environment as above.
The refs tested were `release-5.1` and `v6.0`; see the root `README.md`
"Verified against" lines for the SHAs. Tooling and fixtures were rebuilt
against each version (`docs/SETUP.md`). Raw Supervisor logs are in
`results/reverify-2026-10/`, which is not committed.

- App B's three outcomes were identical on both refs:
  ```
  READ: SUCCESS content=[secret-from-A]
  WRITE: FAILED java.io.FileNotFoundException: /home/nmf/.nmf-apps/track-b-app-a/cache/marker.txt (Read-only file system)
  DELETE: FAILED (delete() returned false)
  ```
  The marker on disk was unchanged afterwards.
- The seccomp precondition was re-confirmed on Docker Desktop 29.7.2.
  Without `--security-opt seccomp=unconfined`, a bare `bwrap --unshare-all
  --ro-bind / / /bin/echo hello` still fails with the same "No permissions
  to create a new namespace" error. With the flag, it prints `hello`.
- Finding 7 was again confirmed first. Then the same live-only `java`
  symlink was applied to reach Track B.
