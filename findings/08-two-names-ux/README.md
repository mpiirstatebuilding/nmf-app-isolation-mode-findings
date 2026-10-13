# Finding 8 — PackageManagement and AppsLauncher key the same App by two different names

**Source of truth:** root `README.md` §8 for full impact, source citations, and
suggested fix. Nothing below restates that.

**Severity (per source):** Minor (UX), live-reproduced.
**Classification:** fully scriptable, mode-independent. The simplest
finding, so it's a good first smoke test that the toolchain works at all.
**Setup needed:** `docs/SETUP.md` "App-scope setup".

## Reproduction steps

1. Install a package and note its filename (PackageManagement's install
   key).
2. Call `listApp`/`runApp` using that filename — it won't match.
3. Call again using the App's metadata-derived name instead (AppsLauncher's
   key) — it succeeds.

## Observed

**Result: CONFIRMED, live, 2026-09-15.**

- Called `RunAppTrigger` with `hello-world-5.1-SNAPSHOT.nmfpack` (the
  package filename) as the `appName` argument to `listApp`: failed with a
  `MALInteractionException` from the provider (`errorNumber=65551`) —
  `listApp` does not recognize the filename at all.
- Called it again with `hello-world` (the metadata `info.name`) instead:
  `listApp` resolved it and `runApp` was submitted successfully
  (`objInstIds=[1]`) — confirmed live throughout findings 1 and 6's own
  testing in this same session, where every install used the filename and
  every `listApp`/`runApp` call used the metadata name.
- This finding doubled as the toolchain smoke test as intended — first
  successful end-to-end `RunAppTrigger` call of this migration confirmed
  the whole chain (build, install, listApp, runApp submit) works before
  findings 1/6 were attempted.
- Upstream SHA for this 2026-09-15 run: `0933302` (pre-release `v5.1`
  tip). The current pin is the root `README.md` "Verified against" line.
- Environment: `docker/linux-userspace/`, though this finding is
  mode-independent — any of the four modes would show the same result.

## Re-verified 2026-10-06: 5.1 release and v6.0

**Result: CONFIRMED on both**, live, same steps and environment as above.
The refs tested were `release-5.1` and `v6.0`; see the root `README.md`
"Verified against" lines for the SHAs. Tooling and fixtures were rebuilt
against each version (`docs/SETUP.md`). Raw Supervisor logs are in
`results/reverify-2026-10/`, which is not committed.

- Identical on both refs. Passing the package filename
  (`hello-world-5.1.nmfpack` / `hello-world-6.0-SNAPSHOT.nmfpack`) to
  `listApp` fails with `errorNumber=65551`. Passing `hello-world` succeeds
  (`objInstIds=[1]`).
- This again served as the toolchain smoke test: the per-version
  `RunAppTrigger` worked against each Supervisor.
