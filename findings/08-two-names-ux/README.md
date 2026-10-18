# Finding 8 — PackageManagement and AppsLauncher key the same App by two different names

Impact, source citations and the suggested fix are in the root
[`README.md`](../../README.md) §8.

**Severity:** Minor (UX), live-reproduced.
**Classification:** fully scriptable, mode-independent. The simplest
finding, so it's a good first smoke test that the toolchain works at all.
**Setup needed:** `docs/SETUP.md` "App-scope setup".

## Reproduction steps

Any mode works. With `hello-world-5.1.nmfpack` staged:

1. Pass the package filename as the App name too:
   `java -jar runapp-trigger.jar <directoryURI> hello-world-5.1.nmfpack hello-world-5.1.nmfpack`.
   The install succeeds, then `listApp` fails with `errorNumber=65551`.
2. Pass the metadata name instead:
   `java -jar runapp-trigger.jar <directoryURI> hello-world hello-world-5.1.nmfpack`.
   `listApp` resolves it and `runApp` is submitted.

## Verifying a fix

The suggested fix is documentation only. Check that the SDK docs that
describe package install and App launch together state which name each
service expects.

## Observed

**Result: CONFIRMED, live, on `release-5.1` and `v6.0`** (SHAs per the root
`README.md` "Verified against" lines), identical on both refs. Environment:
`docker/linux-userspace/`, though this finding is mode-independent — any of
the four modes would show the same result. The `listApp` failure is client-side output, so it isn't in the Supervisor
log. [`evidence/5.1/linux-userspace-f1-f6-f8.log`](../../evidence/5.1/linux-userspace-f1-f6-f8.log)
only shows the metadata-name calls succeeding (`runApp received`).

- Called `RunAppTrigger` with the package filename
  (`hello-world-5.1.nmfpack` / `hello-world-6.0-SNAPSHOT.nmfpack`) as the
  `appName` argument to `listApp`: failed with a `MALInteractionException`
  from the provider (`errorNumber=65551`) — `listApp` does not recognize the
  filename at all.
- Called it again with `hello-world` (the metadata `info.name`) instead:
  `listApp` resolved it and `runApp` was submitted successfully
  (`objInstIds=[1]`) — consistent with findings 1 and 6's own testing, where
  every install used the filename and every `listApp`/`runApp` call used the
  metadata name.
- This finding doubled as the toolchain smoke test: a successful end-to-end
  `RunAppTrigger` call (build, install, listApp, runApp submit) against each
  per-version Supervisor confirmed the whole chain before the other findings
  were attempted.
