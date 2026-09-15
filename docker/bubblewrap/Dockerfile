# Testbed image for appsIsolation=bubblewrap (findings 4 and 7).
#
# Base is eclipse-temurin:21-jre, unmodified -- no java symlink patch, so
# finding 7's own bug (bubblewrap's hardcoded PATH not including
# /opt/java/openjdk/bin) is free to surface rather than being silently
# worked around. See docs/MIGRATION_TO_TESTBED.md section 2 and this
# finding's own README for why that matters.
#
# Run requirement, not baked into this image (a `docker run` flag, not a
# build-time one): the container needs `--security-opt seccomp=unconfined`
# -- Docker's default seccomp profile blocks pivot_root(2), which bwrap's
# `--ro-bind / /` needs, confirmed live 2026-09-15 (see the finding's own
# README for the exact denial before this flag was added).
#
#   docker build -f docker/bubblewrap/Dockerfile \
#     -t nmf-testbed-bubblewrap \
#     <checkout>/nmf-mission-barebone/barebone-space-filesystem/target/space-filesystem
#   docker run -d --security-opt seccomp=unconfined nmf-testbed-bubblewrap

FROM eclipse-temurin:21-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends bubblewrap \
    && rm -rf /var/lib/apt/lists/*

# The Supervisor refuses to run as root (NanoSatMOSupervisor.init(), a
# separate safety check -- same one hit building docker/docker-containers/).
RUN useradd --system --create-home --home-dir /home/nmf nmf

COPY nanosat-mo-framework/ /nanosat-mo-framework/
RUN chown -R nmf:nmf /nanosat-mo-framework \
    && chmod +x /nanosat-mo-framework/start_supervisor.sh

USER nmf
WORKDIR /nanosat-mo-framework
EXPOSE 1024
ENTRYPOINT ["./start_supervisor.sh"]
