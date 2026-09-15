# Finding 1 — `linux-userspace` account/identity takeover via same-name, different-version package

**Source of truth:** root `README.md` §1 for full impact, source citations, and
suggested fix. Nothing below restates that — see `docs/MIGRATION_TO_TESTBED.md`
§3 on why.

**Severity (per source):** High, live-reproduced.
**Classification:** fully scriptable, no special base image.
**Setup needed:** `docs/SETUP.md` "App-scope setup".

## Reproduction steps

1. Generate a `linux-userspace`-mode filesystem (`docs/MIGRATION_TO_TESTBED.md` §5.4).
2. Build two `.nmfpack` packages with identical `info.name` but different
   `info.version` and different content (e.g. different `pack.app.mainclass`).
3. Install the first. Confirm its dedicated Linux account (`app-<name>`) and
   app directory exist with the first package's content.
4. Install the second (same name, different version). Check `/etc/passwd` —
   should still be exactly one account for that name — and the app
   directory, which should now hold the second package's files, with no
   error surfaced during either install.

## Observed

_(fill in after running — pass/fail plus what was actually seen, not assumed)_

- Result:
- Upstream SHA (per root README.md "Verified against"):
- Notes:
