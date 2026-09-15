# Finding 3 — `bootloader/config.properties` no integrity check at boot, fails open to no isolation

**Source of truth:** root `README.md` §3 for full impact, source citations, and
suggested fix. Nothing below restates that.

**Severity (per source):** High. **Source-reviewed only in the source
project — not yet live-confirmed end-to-end.** This testbed's attempt is the
first live confirmation; treat it as new verification work, not a rerun.
**Classification:** none special, but new live work.

## Reproduction steps

1. Generate a filesystem for any `appsIsolation` mode.
2. Edit the generated `bootloader/config.properties`, setting its
   `apps-isolation` key to an empty string or an unrecognized value.
3. Boot the Supervisor.
4. Trace whether every subsequently launched App runs with no isolation
   (the fail-open direction the source-only analysis predicts) and whether
   the boot log gives any indication of the problem.

**If the actual observed behavior differs from this prediction** (e.g. the
Supervisor fails to start instead of failing open), that is a genuine
finding update to report back to César — don't silently adjust the test to
match the prediction. Record whatever actually happens in Observed below.

## Observed

_(required — this finding's live-vs-source-reviewed disposition after this
testbed's attempt must be recorded explicitly, per
`docs/MIGRATION_TO_TESTBED.md` §9)_

- Result:
- Did observed behavior match the fail-open prediction?
- Upstream SHA (per root README.md "Verified against"):
- Notes:
