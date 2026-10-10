# Diama

Plateforme de transfert d'argent **Europe ↔ Afrique de l'Ouest**.

Backend Java 21 / Quarkus 3.x (natif GraalVM) — PostgreSQL 16, Redis, Kafka
(pattern outbox), Keycloak 24 (OIDC, realm `diama-transfer-app`), Thunes v2 (payout),
Swan.io (collecte EUR, IBAN dédiés), Didit (KYC).

## Prérequis

- **Java 21** (toolchain Gradle — installée automatiquement si absente)
- **Docker** (stack locale + Testcontainers pour les tests)

## Démarrage rapide

```bash
# 1. Variables d'environnement
cp .env.example .env            # puis renseigner les secrets partenaires

# 2. Stack locale (Postgres 5433, Redis, Kafka, Elasticsearch)
docker compose up -d

# 3. API en mode dev (hot reload)
./gradlew quarkusDev
```

Le schéma PostgreSQL est appliqué par **Flyway** au démarrage
(`diama-api/src/main/resources/db/migration`).

L'authentification (dev et phase pilote prod) passe par le Keycloak mutualisé
<https://auth.zalamtech.fr>, realm `diama-transfer-app` ; renseigner
`KEYCLOAK_API_SECRET` (client `quarkus-api`) dans `.env`. Le service account
`quarkus-api` doit disposer de `manage-users` pour la synchro `kyc_level`.

## Commandes

| Commande | Effet |
|---|---|
| `./gradlew quarkusDev` | Mode dev, hot reload, Dev UI sur `/q/dev/` |
| `./gradlew test` | Tests (Testcontainers : Docker requis) |
| `./gradlew build` | Build JVM (`build/quarkus-app/`) |
| `./gradlew build -Dquarkus.native.enabled=true` | Build **natif** GraalVM |
| `docker build -t zalamtech/diama-api:native .` | Image native multi-stage |

## URLs locales

| Service | URL |
|---|---|
| API | <http://localhost:8080> |
| Swagger UI (dev) | <http://localhost:8080/q/swagger-ui> |
| Health (readiness / wellness) | <http://localhost:8080/q/health> · `/q/health/well` |
| Métriques Prometheus | <http://localhost:8080/q/metrics> |
| Keycloak | <https://auth.zalamtech.fr/realms/diama-transfer-app> |
| Kafka UI | <http://localhost:8090> |

## Structure (Gradle multi-modules, Kotlin DSL)

| Module | Contenu |
|---|---|
| `diama-api` | Resources JAX-RS, schedulers (outbox, réconciliation), sécurité |
| `diama-domain` | Entités Panache, services métier, FSM, ports (hexagonal) |
| `diama-infra` | Adaptateurs REST partenaires, consumers Kafka, health checks |
| `diama-common` | DTOs, exceptions, utilitaires |

## Tests

- **Unitaires / intégration** (`@QuarkusTest` + DevServices Postgres/Redis) :
  FSM, frais, taux (cache Redis), devis (TTL 15 min), sécurité par rôle.
- **Flux de bout en bout** (`*FlowIT`) : transfert complet (inscription →
  devis → collecte Swan → payout Thunes → DELIVERED), montée KYC (Didit →
  Keycloak + provisionnement Swan), cycle RFI. Les webhooks sont signés HMAC
  et les sauts Kafka rejoués via l'outbox (broker non requis).
- **WireMock** : adaptateurs REST Thunes/Didit exercés contre stubs HTTP.
