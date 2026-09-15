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
unmodified (`info.version=5.1-SNAPSHOT`).

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
- Upstream SHA (per root README.md "Verified against"): `09333029773c1eac7b43e9f1ac841c1fe2ff8e86`
- Environment: `docker/linux-userspace/` image (see that Dockerfile's own
  comments for the nmf-admin-home and networking notes), consumer run as a
  peer container on the same Docker bridge network per `docs/SETUP.md`.
