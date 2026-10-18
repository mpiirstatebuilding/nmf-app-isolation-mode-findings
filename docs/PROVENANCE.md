# Provenance — non-target findings from the source project's internal log

The source benchmarking project (`nanosats-framework`) kept an 11-entry
internal findings log alongside the outreach doc that became this repo's
root `README.md`. Two of those 11 internal entries are **not** among the
root README's findings, and are recorded here only so their absence
doesn't read as an oversight:

- **Internal #1 — bubblewrap App storage-bind crash.** Already fixed and
  merged upstream as
  [PR #192](https://github.com/esa/nanosat-mo-framework/pull/192). Not one
  of the findings in the root `README.md`, but the fix is what makes
  live app-scope verification under `bubblewrap` possible at all (relevant
  background for finding 4's Track B setup — see `docs/SETUP.md`).
- **Internal #7 — `linux-userspace` Apps observed with `gid=0`.**
  **Retracted.** Confirmed to be a harness bug (a process-matching bug in
  the source project's own benchmarking script), not an NMF behavior. Not
  one of the findings in the root `README.md`, and should not be resurrected as a claim against NMF.

No other internal-log content is carried into this repo; this is a
two-fact appendix, not a fuller migration.

## New discovery, this testbed (not from the source project's internal log)

- **NMF's own generated `setup_linux_userspace.sh` has the same `adduser`
  gap finding 6 reports, at a second call site, and it's more severe.**
  It's reported as part of finding 6: the root `README.md` §6's suggested
  fix covers both call sites. Recorded here because it isn't one of the
  numbered findings.
  - [`setup_linux_userspace.sh:46`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-linux-maven-plugin/src/main/resources/setup_linux_userspace.sh#L46)
    creates the Supervisor's own `nmf-admin` account with
    `adduser --system --shell ... --group`, with no `--home`. That's the same
    Debian/Ubuntu default-to-`/nonexistent` behavior finding 6 cites at
    [`LinuxUsersGroups.java:98-100`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-services-impl/src/main/java/esa/mo/nmf/environment/LinuxUsersGroups.java#L98-L100).
  - The blast radius is larger: the **Supervisor itself** can't start
    (`AppStorage`/`OneInstanceLock` needs a writable home), not just one
    App. Anyone deploying `linux-userspace` mode on a stock Debian/Ubuntu
    image hits this first, before any App is installed.
  - Confirmed live while building `docker/linux-userspace/`. That Dockerfile
    works around it (explicit `usermod -d`) so the other findings can run.
    To verify a fix to this call site, delete that `RUN` step and check
    that the Supervisor boots.
