# Provenance — non-target findings from the source project's internal log

The source benchmarking project (`nanosats-framework`) kept an 11-entry
internal findings log (`CESAR_FINDINGS.md`) alongside the 9-finding outreach
doc reproduced in this repo's root `README.md`. Two of those 11 internal
entries are **not** among the 9 and are recorded here only so their absence
doesn't read as an oversight:

- **Internal #1 — bubblewrap App storage-bind crash.** Already fixed and
  merged upstream as
  [PR #192](https://github.com/esa/nanosat-mo-framework/pull/192). Not one
  of the 9 findings this testbed reproduces, but the fix is what makes
  live app-scope verification under `bubblewrap` possible at all (relevant
  background for finding 4's Track B setup — see `docs/SETUP.md`) and is
  useful context on prior good-faith collaboration with César when opening
  new PRs.
- **Internal #7 — `linux-userspace` Apps observed with `gid=0`.**
  **Retracted.** Confirmed to be a harness bug (a process-matching bug in
  the source project's own benchmarking script), not an NMF behavior. Not
  one of the 9, and should not be resurrected as a claim against NMF.

No other internal-log content is carried into this repo — see
`docs/MIGRATION_TO_TESTBED.md` section 3 for the full internal-to-outreach
numbering map, and section 6 for why this stays a two-fact appendix rather
than a fuller migration.
