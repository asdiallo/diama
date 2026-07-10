plugins {
    // Le plugin Quarkus n'est appliqué QUE dans le module applicatif (diama-api).
    // Ici on le déclare sans l'appliquer pour le mettre sur le classpath du build.
    id("io.quarkus") apply false
}

// Versions centralisées (gradle.properties)
val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project
val lombokVersion: String by project
val mapstructVersion: String by project
val lombokMapstructBindingVersion: String by project

allprojects {
    group = "com.zalamtech"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
        mavenLocal()
    }
}

subprojects {
    apply(plugin = "java")

    // --- Toolchain Java 21 + UTF-8 -----------------------------------------
    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        // -parameters : requis par Quarkus (injection de config, JAX-RS)
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    }

    // --- Dépendances partagées à tous les modules --------------------------
    dependencies {
        // BOM Quarkus : aligne toutes les versions d'extensions
        "implementation"(enforcedPlatform("$quarkusPlatformGroupId:$quarkusPlatformArtifactId:$quarkusPlatformVersion"))

        // Coeur CDI + configuration YAML (application.yaml)
        "implementation"("io.quarkus:quarkus-arc")
        "implementation"("io.quarkus:quarkus-config-yaml")

        // Extensions partagées (spec Diama — point 2).
        // NB : noms d'artefacts Quarkus 3.x actuels — les anciens
        // (resteasy-reactive-*, rest-client-reactive-*, smallrye-reactive-
        // messaging-kafka, keycloak-admin-client-reactive) ont été renommés.
        "implementation"("io.quarkus:quarkus-rest-jackson")               // ex resteasy-reactive-jackson
        "implementation"("io.quarkus:quarkus-hibernate-orm-panache")
        "implementation"("io.quarkus:quarkus-jdbc-postgresql")
        "implementation"("io.quarkus:quarkus-oidc")
        "implementation"("io.quarkus:quarkus-rest-client-oidc-filter")    // OAuth2 client_credentials pour REST clients sortants (Swan)
        "implementation"("io.quarkus:quarkus-smallrye-jwt")
        "implementation"("io.quarkus:quarkus-redis-client")
        "implementation"("io.quarkus:quarkus-redis-cache")               // @CacheResult/@CacheInvalidate backés par Redis
        "implementation"("io.quarkus:quarkus-messaging-kafka")            // ex smallrye-reactive-messaging-kafka
        "implementation"("io.quarkus:quarkus-flyway")
        "implementation"("io.quarkus:quarkus-rest-client-jackson")        // ex rest-client-reactive(-jackson)
        "implementation"("io.quarkus:quarkus-keycloak-admin-rest-client") // ex keycloak-admin-client-reactive
        "implementation"("io.quarkus:quarkus-smallrye-openapi")
        "implementation"("io.quarkus:quarkus-smallrye-health")
        "implementation"("io.quarkus:quarkus-hibernate-validator")
        "implementation"("io.quarkus:quarkus-hibernate-search-orm-elasticsearch")
        "implementation"("io.quarkus:quarkus-micrometer-registry-prometheus")

        // Lombok + MapStruct (annotation processing)
        "compileOnly"("org.projectlombok:lombok:$lombokVersion")
        "annotationProcessor"("org.projectlombok:lombok:$lombokVersion")
        "implementation"("org.mapstruct:mapstruct:$mapstructVersion")
        "annotationProcessor"("org.mapstruct:mapstruct-processor:$mapstructVersion")
        "annotationProcessor"("org.projectlombok:lombok-mapstruct-binding:$lombokMapstructBindingVersion")

        // Tests
        "testImplementation"("io.quarkus:quarkus-junit5")
        "testImplementation"("io.rest-assured:rest-assured")
        "testCompileOnly"("org.projectlombok:lombok:$lombokVersion")
        "testAnnotationProcessor"("org.projectlombok:lombok:$lombokVersion")
    }
}
