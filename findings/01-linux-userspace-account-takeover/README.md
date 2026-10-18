# Finding 1 — `linux-userspace` account/identity takeover via same-name, different-version package

Impact, source citations and the suggested fix are in the root
[`README.md`](../../README.md) §1.

**Severity:** High, live-reproduced.
**Classification:** fully scriptable, no special base image.
**Setup needed:** `docs/SETUP.md` "App-scope setup".

## Reproduction steps

1. Generate a `linux-userspace` filesystem
   (`-Dgenerate-filesystem.appsIsolation=linux-userspace`, see
   `docs/SETUP.md` "Generating a mode's filesystem").
2. Build the two packages. Both declare `info.name=hello-world`, with
   different versions and main classes:
   - Package A is the SDK's own `sdk/examples-apps/hello-world-simple`,
     unmodified (`hello-world-5.1.nmfpack`).
   - Package B is `fixtures/hello-world-package-b/` in this directory
     (`hello-world-5.2-TEST.nmfpack`, main class `DemoHelloWorldSimpleV2`).
3. Stage both into `packages/` and start `docker/linux-userspace/`
   (`docs/SETUP.md`).
4. Install package A, then inspect the account and App directory:
   ```bash
   java -jar runapp-trigger.jar <directoryURI> hello-world hello-world-5.1.nmfpack
   docker exec nmf-sup sh -c 'grep app-hello-world /etc/passwd; ls /nanosat-mo-framework/apps/hello-world'
   ```
5. Install package B without uninstalling A, and inspect again:
   ```bash
   java -jar runapp-trigger.jar <directoryURI> hello-world hello-world-5.2-TEST.nmfpack
   docker exec nmf-sup sh -c 'grep app-hello-world /etc/passwd; ls /nanosat-mo-framework/apps/hello-world; grep -E "^(MAIN_CLASS|JAR_APP)=" /nanosat-mo-framework/apps/hello-world/start_app.sh'
   ```
   The bug: there's still exactly one `app-hello-world` account (same
   uid/gid), both jars sit in the directory, `start_app.sh` now points at
   package B, and both installs log "Package successfully installed".

## Verifying a fix

Re-run step 5. A fixed Supervisor rejects package B's install as a name
collision, or treats it as an explicit upgrade only after a publisher
check. Either way the client gets an error, and `start_app.sh` still
points at package A's `MAIN_CLASS`/`JAR_APP`.

## Observed

**Result: CONFIRMED, live, on `release-5.1` and `v6.0`** (SHAs per the root
`README.md` "Verified against" lines). Same results on both refs.
Environment: `docker/linux-userspace/` image, consumer run as a peer
container on the same Docker bridge network per `docs/SETUP.md`. Supervisor logs: [`evidence/5.1/linux-userspace-f1-f6-f8.log`](../../evidence/5.1/linux-userspace-f1-f6-f8.log) and [`evidence/6.0/linux-userspace-f1-f6-f8.log`](../../evidence/6.0/linux-userspace-f1-f6-f8.log).

- Installed package A (`hello-world-5.1.nmfpack` / `hello-world-6.0-SNAPSHOT.nmfpack`,
  `info.name=hello-world`). Supervisor log: account created via `sudo
  adduser --system --shell /bin/bash --group app-hello-world`; `/etc/passwd`
  showed exactly one entry:
  `app-hello-world:x:101:102::/nonexistent:/bin/bash`. **Checked
  `apps/hello-world/` at this point, before touching package B:** contained
  exactly one jar plus
  `transport.properties`/`provider.properties`/`start_app.sh`. "Package
  successfully installed" logged, no error.
- Installed package B (`hello-world-5.2-TEST.nmfpack`, same `info.name`,
  `info.version=5.2-TEST`) **without uninstalling package A first.**
  Supervisor log: `sudo adduser --system --shell /bin/bash --group
  app-hello-world` was run **again** for the same name, followed immediately
  by a caught-and-logged exception: `The permissions of the User Home
  directory could not be set for username: app-hello-world` (this is the
  broad log-only catch the root `README.md` cites at
  [`NMFPackageManager.java:195-198`](https://github.com/esa/nanosat-mo-framework/blob/3b5920c85185ff55e98a08aaa48b60c1f77bcdc5/core/nmf-package-lib/src/main/java/esa/mo/nmf/nmfpackage/NMFPackageManager.java#L195-L198); it is logged on *both* installs, not
  just the second) — install proceeded anyway. "Package successfully
  installed" logged, no error surfaced to the caller either way.
- **After package B's install:** `/etc/passwd` still showed exactly **one**
  `app-hello-world` entry (same uid/gid, `101:102`) — confirmed account
  reuse, not a second account. `apps/hello-world/` now held **both** jars
  (package A's, still there from before, **and** `hello-world-5.2-TEST.jar`,
  newly added) — confirmed against the pre-install-B listing above, not
  inferred. So the old jar is not deleted, but
  `start_app.sh`/`transport.properties`/`provider.properties` **are**
  regenerated on every install and point at whichever package installed
  most recently (`start_app.sh` switched to
  `MAIN_CLASS=esa.mo.nmf.apps.DemoHelloWorldSimpleV2` /
  `JAR_APP=hello-world-5.2-TEST.jar`), so the App's actual launch target
  silently switches to package B without any uninstall step. The core claim
  holds: no error, no operator-visible warning, same Linux account and app
  directory silently taken over by an unrelated package declaring the same
  name.
- **Sharper on v6.0:** package B's `5.2-TEST` is *lower* than the installed
  `6.0-SNAPSHOT`, and it still took over. So there is no version-ordering
  check either, not just no name check.
- Not tested: whether "same name, different developer" specifically (as
  opposed to same name, same developer doing a version bump) changes
  anything — the install path has no such concept regardless, so it wasn't
  expected to matter and didn't.
