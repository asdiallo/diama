/**
 * Sondes SmallRye Health.
 *
 * <p>Infrastructure vitale (PostgreSQL, Redis, Kafka) en {@code @Readiness}
 * ({@code /q/health/ready}, consommé par Kubernetes) ; disponibilité des
 * partenaires externes (Thunes, Keycloak) en {@code @Wellness}
 * ({@code /q/health/well}) — informatif, un incident partenaire ne doit pas
 * sortir les pods du load-balancing.</p>
 */
package com.zalamtech.diama.infra.health;
