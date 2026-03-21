plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.jib)
    java
    application
}

application {
    mainClass = "com.example.catalog.CatalogApplication"
}

jib {
    from { image = "docker://eclipse-temurin:21-jre-alpine" }
    to { image = "catalog"; tags = setOf("latest") }
    container {
        jvmFlags = listOf(
            "--enable-preview",
            "-Dfile.encoding=UTF-8",
            "-XX:+UseZGC",
            "-XX:MaxRAMPercentage=75.0",
            "-XX:+ExitOnOutOfMemoryError"
        )
        ports = listOf("8081")
        creationTime = "USE_CURRENT_TIMESTAMP"
    }
}

// Wire Spring Boot's resolved main class into Jib automatically
tasks.named("jibDockerBuild") {
    dependsOn("resolveMainClassName")
    doFirst {
        jib.container.mainClass = layout.buildDirectory.file("resolvedMainClassName")
            .get().asFile.readText().trim()
    }
}
tasks.named("jib") {
    dependsOn("resolveMainClassName")
    doFirst {
        jib.container.mainClass = layout.buildDirectory.file("resolvedMainClassName")
            .get().asFile.readText().trim()
    }
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("--enable-preview", "-parameters"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("--enable-preview")
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("--enable-preview")
}

// Jib needs the plain jar task enabled to scan for main class
tasks.named<Jar>("jar") {
    enabled = true
}

dependencies {
    // Internal libraries — resolved from GitHub Packages in CI, mavenLocal in local dev
    implementation("com.example:seedwork:0.1.0")
    implementation("com.example:shared-events:0.1.0")

    // Spring Boot
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.validation)

    // Kafka + Avro
    implementation(libs.spring.kafka)
    implementation(libs.kafka.avro.serializer)

    // Database
    runtimeOnly(libs.postgresql)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.database.postgresql)

    // API docs — exposes /v3/api-docs for SwaggerHub sync and PactFlow BDCT
    implementation(libs.springdoc.openapi.starter.webmvc)

    // Observability
    implementation(libs.micrometer.registry.prometheus)

    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    // Test
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.kafka.test)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.rest.assured)
}
