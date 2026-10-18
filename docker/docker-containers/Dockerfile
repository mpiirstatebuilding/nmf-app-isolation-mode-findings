# Testbed image for appsIsolation=docker-containers (findings 5 and 9).
#
# Docker-outside-of-Docker (DooD): the Supervisor talks to the HOST's own
# Docker daemon (not a nested/Docker-in-Docker one) to launch each App's
# sibling container, via AppsLauncherManagerDocker's plain `docker run ...`
# (ProcessBuilder, no API client) -- so this image just needs the `docker`
# CLI installed, and the container needs to be run with the host's
# docker.sock bind-mounted in (see docs/SETUP.md "Building and running the
# Supervisor image per mode" for the run command).
#
# Deliberately built with the filesystem COPY'd in at image-build time
# (not a host bind mount) -- that is finding 9's own precondition, not a
# workaround to avoid. Simplification vs. a real deployment: runs as root,
# to sidestep docker.sock group-permission friction entirely (docker-
# containers mode has no linux-userspace-style adduser/sudo mechanics that
# would need a non-root account anyway).
#
#   docker build -f docker/docker-containers/Dockerfile \
#     -t nmf-testbed-docker-containers:<nmf-version> \
#     <checkout>/nmf-mission-barebone/barebone-space-filesystem/target/space-filesystem

FROM eclipse-temurin:21-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends docker.io \
    && rm -rf /var/lib/apt/lists/*

# The Supervisor refuses to run as root (NanoSatMOSupervisor.init(), a
# separate, unrelated safety check) -- confirmed live 2026-09-15. The
# container itself must still start as root, though, to fix up the
# bind-mounted host docker.sock's permissions -- see entrypoint.sh.
RUN useradd --system --create-home --home-dir /home/nmf nmf

COPY nanosat-mo-framework/ /nanosat-mo-framework/
RUN chown -R nmf:nmf /nanosat-mo-framework \
    && chmod +x /nanosat-mo-framework/start_supervisor.sh
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

EXPOSE 1024
ENTRYPOINT ["/entrypoint.sh"]
