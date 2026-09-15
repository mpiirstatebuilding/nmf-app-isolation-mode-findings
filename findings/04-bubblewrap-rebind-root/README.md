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

_(fill in after running — pass/fail plus what was actually seen, not assumed)_

- Read result:
- Write result (expected: blocked):
- Delete result (expected: blocked):
- Upstream SHA (per root README.md "Verified against"):
- Notes:
