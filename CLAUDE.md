# NMF App-Isolation Findings Testbed — Project Context

## Purpose

This repo reproduces, live and independently, the 10 NMF (NanoSat MO
Framework) app-isolation findings already written up for César Coelho
(NMF maintainer/developer, Terma GmbH/ESA) in the root `README.md`. It was
migrated from a separate Phase-1 isolation-benchmarking project
(`nanosats-framework`). This repo's own job is narrower than the source
project's: demonstrate the 10 bugs, not re-run a 4-mode benchmark suite.

## Documentation ownership (avoid bloat)

Each fact type has exactly one owner; everywhere else links to it instead of
restating it:

| Fact type | Owner |
|---|---|
| The 10 findings (impact, source citations, suggested fix) | root `README.md` |
| Pinned upstream SHA (all source citations cite this) | root `README.md`'s "Verified against" line |
| Non-target internal-log findings (fixed / retracted), testbed-found extras | `docs/PROVENANCE.md` |
| Shared setup procedures (app-scope trigger, Track B cross-app) | `docs/SETUP.md` |
| Per-finding reproduction steps, observed result, "Verifying a fix" check | `findings/NN-*/` |
| Supervisor-log evidence from the re-verification runs | `evidence/` (index in `evidence/README.md`) |

Before writing a fact down, grep for it first
(`grep -rn "<key phrase>" --include="*.md" .`); if it's already written
somewhere, link to that file instead of re-explaining it. This project's
source project repeatedly hit the same duplication problem (one summary doc
superseding another), so keep each fact in its one owner above rather than
restating it.
