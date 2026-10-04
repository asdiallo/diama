# ============================================================
#  Diama — build natif GraalVM multi-stage
#  Stage 1 : compilation native (Mandrel + Gradle)
#  Stage 2 : image d'exécution minimale (ubi9-minimal)
#
#  Build :  docker build -t zalamtech/diama-api:native .
#  Run   :  docker run -i --rm -p 8080:8080 zalamtech/diama-api:native
# ============================================================

# ---- Stage 1 : native build --------------------------------
FROM quay.io/quarkus/ubi-quarkus-mandrel-builder-image:jdk-21 AS build
WORKDIR /project

# Wrapper Gradle + config racine (copiés en premier pour le cache de couches)
COPY --chown=quarkus:quarkus gradlew gradle.properties settings.gradle.kts build.gradle.kts ./
COPY --chown=quarkus:quarkus gradle ./gradle

# Modules (l'ordre reflète le graphe de dépendances)
COPY --chown=quarkus:quarkus diama-common ./diama-common
COPY --chown=quarkus:quarkus diama-domain ./diama-domain
COPY --chown=quarkus:quarkus diama-infra  ./diama-infra
COPY --chown=quarkus:quarkus diama-api    ./diama-api

USER quarkus
# Compilation native du module applicatif uniquement.
# (équivalent hérité : -Pquarkus.package.type=native)
# Tests exclus : ils exigent Docker (Testcontainers), indisponible ici.
RUN ./gradlew :diama-api:build -x test \
      -Dquarkus.native.enabled=true \
      -Dquarkus.package.jar.enabled=false \
      --no-daemon --stacktrace

# ---- Stage 2 : runtime -------------------------------------
FROM registry.access.redhat.com/ubi9/ubi-minimal:9.4
WORKDIR /work/

RUN chown 1001 /work \
    && chmod "g+rwX" /work \
    && chown 1001:root /work

# Binaire natif produit par le module diama-api
COPY --from=build --chown=1001:root /project/diama-api/build/*-runner /work/application

EXPOSE 8080
USER 1001

ENTRYPOINT ["./application", "-Dquarkus.http.host=0.0.0.0"]
