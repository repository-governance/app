FROM eclipse-temurin:21.0.9_10-jdk-noble@sha256:10f263b4b053dddab217626b3607dfdb541830e5fc4d6c5d9bbade31707dd45b

ARG ARCHI_VERSION=5.11.0
ARG ARCHI_SHA256=a13f4f058c16d8653c1307a145b9dd6bddd4226abc952f58c586c46d61db219e
ENV ARCHI_PLUGINS=/opt/archi/plugins
WORKDIR /workspace

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl unzip zip \
    && rm -rf /var/lib/apt/lists/*

# Use the official Linux build and its published checksum list as the compile-time API.
RUN set -eux; \
    archive="Archi-Linux64-${ARCHI_VERSION}.tgz"; \
    base="https://www.archimatetool.com/downloads/archi/archive/${ARCHI_VERSION}"; \
    curl --fail --location --retry 3 "${base}/${archive}" -o "/tmp/${archive}"; \
    curl --fail --location --retry 3 "${base}/Archi-${ARCHI_VERSION}-SHA256.txt" -o /tmp/archi-sha256.txt; \
    published="$(awk -v file="${archive}" '$2 == file { print $1 }' /tmp/archi-sha256.txt)"; \
    test "$published" = "$ARCHI_SHA256"; \
    printf '%s  %s\n' "$ARCHI_SHA256" "/tmp/${archive}" | sha256sum --check --status; \
    mkdir -p /opt/archi; \
    tar -xzf "/tmp/${archive}" -C /opt/archi --strip-components=1; \
    test -d /opt/archi/plugins; \
    rm -f "/tmp/${archive}" /tmp/archi-sha256.txt; \
    mkdir -p /workspace/build /workspace/dist; \
    chown -R 10001:10001 /workspace

USER 10001:10001
ENTRYPOINT ["/bin/sh", "/workspace/scripts/package.sh"]
