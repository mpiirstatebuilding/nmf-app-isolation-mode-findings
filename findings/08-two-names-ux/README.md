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

_(fill in after running — pass/fail plus what was actually seen, not assumed)_

- Result:
- Upstream SHA (per root README.md "Verified against"):
- Notes:
