// ============================================================
//  diama-api — module APPLICATIF Quarkus (JAX-RS resources)
//  Seul module qui applique le plugin io.quarkus et produit
//  l'artefact exécutable / l'image native.
//  Packages : resource, filter, mapper
// ============================================================

plugins {
    // Version fournie par pluginManagement (settings.gradle.kts)
    id("io.quarkus")
}

dependencies {
    // Agrégation de tous les modules internes
    implementation(project(":diama-common"))
    implementation(project(":diama-domain"))
    implementation(project(":diama-infra"))

    // Extension de build d'image (Docker / natif GraalVM) —
    // spécifique au module applicatif.
    implementation("io.quarkus:quarkus-container-image-docker")

    // Scheduler (@Scheduled) — refresh quotidien des taux de change.
    implementation("io.quarkus:quarkus-scheduler")

    // Tests de sécurité (@TestSecurity + JWT claims)
    testImplementation("io.quarkus:quarkus-test-security")
    testImplementation("io.quarkus:quarkus-test-security-jwt")
}
