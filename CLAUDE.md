# NMF App-Isolation Findings Testbed — Project Context

## Purpose

This repo reproduces, live and independently, the 10 NMF (NanoSat MO
Framework) app-isolation findings already written up for César Coelho
(NMF maintainer/developer, Terma GmbH/ESA) in the root `README.md`. It was
migrated from a separate Phase-1 isolation-benchmarking project
(`nanosats-framework`) per `docs/MIGRATION_TO_TESTBED.md` — that document
is the canonical migration runbook; don't re-derive its scope or manifest
decisions from scratch. This repo's own job is narrower than the source
project's: demonstrate the 10 bugs, not re-run a 4-mode benchmark suite.

## Documentation ownership (avoid bloat)

Each fact type has exactly one owner; everywhere else links to it instead of
restating it:

| Fact type | Owner |
|---|---|
| The 10 findings (impact, source citations, repro, suggested fix) | root `README.md` |
| Pinned upstream SHA (all source citations cite this) | root `README.md`'s "Verified against" line |
| Non-target internal-log findings (fixed / retracted) | `docs/PROVENANCE.md` |
| Shared setup procedures (app-scope trigger, Track B cross-app) | `docs/SETUP.md` |
| Per-finding reproduction attempt + observed result | `findings/NN-*/` |
| Migration decisions and file-by-file manifest | `docs/MIGRATION_TO_TESTBED.md` |

Before writing a fact down, grep for it first
(`grep -rn "<key phrase>" --include="*.md" .`); if it's already written
somewhere, link to that file instead of re-explaining it. This project's
source project hit the same duplication problem twice historically
(commits `43facef`, `b7acf48`) plus a third instance in the same session
that produced the migration runbook (`CESAR_FINDINGS.md` →
`CESAR_OUTREACH.md` → a since-discarded fourth summary doc) — see
`docs/MIGRATION_TO_TESTBED.md` section 3. Don't reintroduce a fourth copy
here.
