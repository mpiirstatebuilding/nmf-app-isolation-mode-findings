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

Fixtures used: `fixtures/hello-world-package-b/` in this directory (package
B — `info.name=hello-world`, `info.version=5.2-TEST`, distinct main class/
content). Package A is the SDK's own `hello-world-simple` example,
unmodified (its `info.version` is the NMF version it was built from, e.g.
`5.1`).

## Observed

**Result: CONFIRMED, live, 2026-09-15.**

- Installed package A (`hello-world-5.1-SNAPSHOT.nmfpack`, `info.name=hello-world`).
  Supervisor log: account created via `sudo adduser --system --shell /bin/bash
  --group app-hello-world`; `/etc/passwd` showed exactly one entry:
  `app-hello-world:x:101:102::/nonexistent:/bin/bash`. **Checked
  `apps/hello-world/` at this point, before touching package B:** contained
  exactly one jar, `hello-world-5.1-SNAPSHOT.jar`, plus
  `transport.properties`/`provider.properties`/`start_app.sh`. "Package
  successfully installed in 141 ms" logged, no error.
- Installed package B (`hello-world-5.2-TEST.nmfpack`, same `info.name`,
  `info.version=5.2-TEST`) **without uninstalling package A first.**
  Supervisor log: `sudo adduser --system --shell /bin/bash --group
  app-hello-world` was run **again** for the same name, followed immediately
  by a caught-and-logged exception: `The permissions of the User Home
  directory could not be set for username: app-hello-world` (this is the
  broad log-only catch the source `README.md` cites at
  `NMFPackageManager.java:195-198`) — install proceeded anyway.
  "Package successfully installed in 104 ms" logged, no error surfaced to
  the caller either way.
- **After package B's install:** `/etc/passwd` still showed exactly **one**
  `app-hello-world` entry (same uid/gid, `101:102`) — confirmed account
  reuse, not a second account. `apps/hello-world/` now held **both** jars
  (`hello-world-5.1-SNAPSHOT.jar`, still there from before, **and**
  `hello-world-5.2-TEST.jar`, newly added) — confirmed against the
  pre-install-B listing above, not inferred. This is a nuance worth
  recording precisely rather than restating the source `README.md`'s
  "silently overwrites in place" wording verbatim: the old jar is not
  deleted, but `start_app.sh`/`transport.properties`/`provider.properties`
  **are** regenerated on every install and point at whichever package
  installed most recently, so the App's actual launch target silently
  switches to package B without any uninstall step. Either way, the core
  claim holds: no error, no operator-visible warning, same Linux account
  and app directory silently taken over by an unrelated package declaring
  the same name.
- Not tested here: whether "same name, different developer" specifically
  (as opposed to same name, same developer doing a version bump) changes
  anything — the install path has no such concept regardless, so this
  wasn't expected to matter and didn't.
- Upstream SHA for this 2026-09-15 run: `0933302` (pre-release `v5.1`
  tip). The current pin is the root `README.md` "Verified against" line.
- Environment: `docker/linux-userspace/` image (see that Dockerfile's own
  comments for the nmf-admin-home and networking notes), consumer run as a
  peer container on the same Docker bridge network per `docs/SETUP.md`.

## Re-verified 2026-10-06: 5.1 release and v6.0

**Result: CONFIRMED on both**, live, same steps and environment as above.
The refs tested were `release-5.1` and `v6.0`; see the root `README.md`
"Verified against" lines for the SHAs. Tooling and fixtures were rebuilt
against each version (`docs/SETUP.md`). Raw Supervisor logs are in
`results/reverify-2026-10/`, which is not committed.

- On both refs, the same results as before:
  - one `app-hello-world` entry (`101:102`) before and after package B
  - both jars left in `apps/hello-world/`
  - `start_app.sh` switched to
    `MAIN_CLASS=esa.mo.nmf.apps.DemoHelloWorldSimpleV2` /
    `JAR_APP=hello-world-5.2-TEST.jar`
  - "Package successfully installed" logged twice, and no error reached the
    caller
- Package A is now `hello-world-5.1.nmfpack` or
  `hello-world-6.0-SNAPSHOT.nmfpack`, matching its build.
- **Sharper on v6.0:** package B's `5.2-TEST` is *lower* than the installed
  `6.0-SNAPSHOT`, and it still took over. So there is no version-ordering
  check either, not just no name check.
- Correction to the September write-up, explained in finding 6's
  section: "The permissions of the User Home directory could not be set" is
  logged on *both* installs, not just the second. Re-running the September
  image shows the same, so the earlier write-up was incomplete; the
  behavior didn't change between versions.
