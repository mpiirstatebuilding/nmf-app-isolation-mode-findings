# Finding 2 — unsanitized package name → path traversal on install

**Source of truth:** root `README.md` §2 for full impact, source citations, and
suggested fix. Nothing below restates that.

**Severity (per source):** High, live-reproduced.
**Classification:** fully scriptable, mode-independent (Supervisor-level bug —
any `appsIsolation` mode works).
**Setup needed:** none beyond a scratch NMF root — this is a Supervisor-level
bug, reachable before any isolation mode takes effect.

## Reproduction steps

1. Build a `.nmfpack` with `info.name=../../outside-target/pwned-app`
   (metadata otherwise valid, no files need to be included).
2. Install it against a scratch NMF root.
3. Confirm a receipt file lands one level outside the installations-tracker
   directory (`<nmf-root>/outside-target/pwned-app.receipt`), with a clean
   "Package successfully installed" log line and no exception.

## Observed

_(fill in after running — pass/fail plus what was actually seen, not assumed)_

- Result:
- Upstream SHA (per root README.md "Verified against"):
- Notes:
