# Finding 8 — PackageManagement and AppsLauncher key the same App by two different names

**Source of truth:** root `README.md` §8 for full impact, source citations, and
suggested fix. Nothing below restates that.

**Severity (per source):** Minor (UX), live-reproduced.
**Classification:** fully scriptable, simplest finding — good first
smoke-test that the migrated toolchain (`docs/SETUP.md` "App-scope setup")
works at all.

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
- Upstream SHA (per root README.md "Verified against"): `09333029773c1eac7b43e9f1ac841c1fe2ff8e86`
- Environment: `docker/linux-userspace/`, though this finding is
  mode-independent — any of the four modes would show the same result.
