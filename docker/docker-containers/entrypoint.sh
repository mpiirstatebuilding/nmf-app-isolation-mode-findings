#!/bin/sh
# Runs as root (the image's default) so it can fix up the bind-mounted host
# docker.sock's permissions, then drops to the non-root "nmf" user the
# Supervisor insists on (NanoSatMOSupervisor.init() refuses to run as root)
# before exec'ing the real entrypoint.
if [ -S /var/run/docker.sock ]; then
    chmod 666 /var/run/docker.sock
fi
cd /nanosat-mo-framework

# su resets PATH to Debian's login.defs default regardless of the caller's
# environment (confirmed live) -- re-prepend java's real location, or the
# bootloader's own java-runtime integrity check fails (exit 127).
JAVA_DIR=$(dirname "$(command -v java)")
export JAVA_DIR
exec su nmf -s /bin/sh -c 'export PATH="$JAVA_DIR:$PATH"; exec ./start_supervisor.sh'
