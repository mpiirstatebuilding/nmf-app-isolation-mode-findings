# Finding 4 — bubblewrap exposes the full host filesystem read-only, no seccomp/AppArmor/cap-drop

Impact, source citations and the suggested fix are in the root
[`README.md`](../../README.md) §4.

**Severity:** High, live-reproduced (read-exposure half only —
write/delete are correctly blocked).
**Classification:** live, environment-dependent.
**Setup needed:** `docs/SETUP.md` "Cross-app (Track B) setup".

## Environment preconditions

- The Supervisor's own container needs `--security-opt seccomp=unconfined`.
  Docker's default seccomp profile blocks bubblewrap's user-namespace
  creation (see Observed), regardless of granted capabilities.
- If verifying via in-namespace inspection (e.g. `nsenter`) rather than just
  the read/write/delete file test, the Supervisor's container also needs
  `--cap-add=SYS_PTRACE --cap-add=SYS_ADMIN`.
- Separately, bubblewrap itself needs a host where unprivileged
  user-namespace creation isn't blocked — known-blocked on Ubuntu 23.10+
  (an AppArmor policy default, `kernel.apparmor_restrict_unprivileged_userns`),
  known-fine on macOS/Docker Desktop. A quick check inside the Supervisor
  container is `bwrap --unshare-all --ro-bind / / /bin/echo hello`.

## Reproduction steps

1. Use the Track B two-App setup (`docs/SETUP.md`) under `bubblewrap` mode.
2. Work around finding 7 so the Apps can launch at all (an App that can't
   start can't write a marker to read). `eclipse-temurin` keeps `java`
   under `/opt/java/openjdk/bin`, off the sandbox's hardcoded `PATH`; put it
   on that `PATH` live, in the running container, **not** baked into the
   image (baking it in would suppress finding 7):
   ```bash
   docker exec -u root nmf-sup ln -s /opt/java/openjdk/bin/java /usr/local/bin/java
   ```
   Confirm finding 7 independently *first*, before this symlink — it is a
   real, separate bug, not a precondition to invert.
3. Run App A, then App B, as in `docs/SETUP.md`'s Track B section.
4. Read App B's results:
   ```bash
   docker exec nmf-sup sh -c 'grep -h -A3 "TrackBAppB results" /nanosat-mo-framework/logs/app_track-b-app-b/*.log'
   ```
   The bug: `READ: SUCCESS content=[secret-from-A]`. `WRITE` and `DELETE`
   fail, as they should.

## Verifying a fix

Rebuild the image with the patched Supervisor, re-apply the `java`
symlink (or the finding 7 fix), and re-run steps 3–4. With an allowlist in
place of `--ro-bind / /`, App B's `READ` line reports `FAILED`, and App A
still launches and writes its marker. Also check the `Using launcher
command:` line in the Supervisor log for the new seccomp and `--cap-drop`
arguments.

## Observed

**Result: CONFIRMED, live, on `release-5.1` and `v6.0`** (SHAs per the root
`README.md` "Verified against" lines), identical on both refs — clean match
with the prediction, all three outcomes exactly as expected.

Fixtures: `fixtures/track-b-app-a/` and `fixtures/track-b-app-b/` in this
directory (two new Maven modules, not copied from anywhere — App A writes
a marker into `AppStorage.getAppCacheDir()`; App B hardcodes the path
`$HOME/.nmf-apps/track-b-app-a/cache/marker.txt` and attempts read, then
write, then delete). Environment: `docker/bubblewrap/`. Supervisor logs: [`evidence/5.1/bubblewrap-f4-f7.log`](../../evidence/5.1/bubblewrap-f4-f7.log) and [`evidence/6.0/bubblewrap-f4-f7.log`](../../evidence/6.0/bubblewrap-f4-f7.log).

- **`--security-opt seccomp=unconfined` precondition confirmed real and
  necessary** — without it, even a bare `bwrap --unshare-all ... /bin/echo
  hello` failed outright:
  `bwrap: No permissions to create a new namespace, likely because the
  kernel does not allow non-privileged user namespaces.` Note on the
  mechanism: what blocks the bare invocation here is the **user-namespace
  creation itself** (`unshare`/`clone(CLONE_NEWUSER)`) under Docker's
  default seccomp profile, more than `pivot_root(2)` specifically —
  `pivot_root` may be a second thing the same flag unblocks, not verified
  separately. With the flag applied, the same bare invocation succeeded
  immediately.
- **Host confirmed in the "known-fine" bucket** — macOS + Docker Desktop.
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
- The seccomp precondition was confirmed on Docker Desktop 29.7.2. Without
  `--security-opt seccomp=unconfined`, a bare `bwrap --unshare-all
  --ro-bind / / /bin/echo hello` fails with the same "No permissions to
  create a new namespace" error. With the flag, it prints `hello`.
