# Provenance — non-target findings from the source project's internal log

The source benchmarking project (`nanosats-framework`) kept an 11-entry
internal findings log (`CESAR_FINDINGS.md`) alongside the 9-finding outreach
doc reproduced in this repo's root `README.md`. Two of those 11 internal
entries are **not** among the 9 and are recorded here only so their absence
doesn't read as an oversight:

- **Internal #1 — bubblewrap App storage-bind crash.** Already fixed and
  merged upstream as
  [PR #192](https://github.com/esa/nanosat-mo-framework/pull/192). Not one
  of the findings in the root `README.md`, but the fix is what makes
  live app-scope verification under `bubblewrap` possible at all (relevant
  background for finding 4's Track B setup — see `docs/SETUP.md`) and is
  useful context on prior good-faith collaboration with César when opening
  new PRs.
- **Internal #7 — `linux-userspace` Apps observed with `gid=0`.**
  **Retracted.** Confirmed to be a harness bug (a process-matching bug in
  the source project's own benchmarking script), not an NMF behavior. Not
  one of the findings in the root `README.md`, and should not be resurrected as a claim against NMF.

No other internal-log content is carried into this repo — see
`docs/MIGRATION_TO_TESTBED.md` section 3 for the full internal-to-outreach
numbering map, and section 6 for why this stays a two-fact appendix rather
than a fuller migration.

## New discovery, this testbed (not from the source project's internal log)

- **NMF's own generated `setup_linux_userspace.sh` has the same `adduser`
  gap finding 6 reports, at a different call site, and it's more severe.**
  Confirmed live 2026-09-15 building `docker/linux-userspace/`: the script
  creates the Supervisor's own `nmf-admin` account via
  `adduser --system --shell ... --group`, no `--home` — same Debian/Ubuntu
  default-to-`/nonexistent` behavior finding 6 cites at
  `LinuxUsersGroups.java:98-100`, but this is a *different* code path
  (`nmf-linux-maven-plugin`'s generated `setup_linux_userspace.sh`, not
  `LinuxUsersGroups.java`) and a different blast radius: the **Supervisor
  itself** can't start at all (`AppStorage`/`OneInstanceLock` needs a
  writable home), not just one App. This is the first thing anyone
  deploying `linux-userspace` mode on a stock Debian/Ubuntu image would
  hit, before any App is even installed. Worked around at the
  infrastructure level in `docker/linux-userspace/Dockerfile` (explicit
  `usermod -d`) so the testbed's own findings could proceed — not one of
  the findings sent to César, and not yet decided whether it's worth
  reporting to him. **Flagged here for Mari's call, not decided
  unilaterally** — see `findings/06-adduser-no-home/README.md`'s Observed
  section for the full live evidence.
