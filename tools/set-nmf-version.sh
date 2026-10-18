#!/bin/sh
# Points every testbed Maven module (RunAppTrigger + the finding fixtures) at
# one NMF version, by rewriting the nanosat-mo-framework parent <version> in
# each pom.xml. The NMF major version is also the MO area version, and a
# provider rejects consumers of another area version, so the tooling must be
# built against the same NMF version as the Supervisor it is run against.
#
# Maven (3.10) refuses a property in a parent version, hence a rewrite rather
# than -Drevision. Run `mvn clean package` in each module afterwards -- and
# check the clean actually ran, or a stale jar from another version survives.
#
#   tools/set-nmf-version.sh 5.1           # the release (committed default)
#   tools/set-nmf-version.sh 6.0-SNAPSHOT  # the v6.0 branch
set -eu
[ $# -eq 1 ] || { echo "usage: $0 <nmf-version>" >&2; exit 2; }
cd "$(dirname "$0")/.."
for pom in tools/runapp-trigger/pom.xml findings/*/fixtures/*/pom.xml; do
    perl -0pi -e 's|(<artifactId>nanosat-mo-framework</artifactId>\s*<version>)[^<]*(</version>)|${1}'"$1"'${2}|' "$pom"
    printf '%s -> %s\n' "$pom" "$(perl -0ne 'print $1 if m|<artifactId>nanosat-mo-framework</artifactId>\s*<version>([^<]*)|' "$pom")"
done
