plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.jib)
    java
    application
}

application {
    mainClass = "com.example.order.OrderApplication"
}

jib {
    from { image = "docker://eclipse-temurin:21-jre-alpine" }
    to { image = "order"; tags = setOf("latest") }
    container {
        jvmFlags = listOf(
            "--enable-preview",
            "-Dfile.encoding=UTF-8",
            "-XX:+UseZGC",
            "-XX:MaxRAMPercentage=75.0",
            "-XX:+ExitOnOutOfMemoryError"
        )
        ports = listOf("8082")
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
    useJUnitPlatform {
        // CI passes -PexcludeTags=contract to skip Pact tests in the main test job
        // (contract tests run in their own dedicated CI job)
        val excludeTags = project.findProperty("excludeTags")?.toString()
        if (excludeTags != null) excludeTags(excludeTags)
    }
    jvmArgs("--enable-preview")
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("--enable-preview")
}

tasks.named<Jar>("jar") {
    enabled = true
}

configurations.all {
    resolutionStrategy.force("org.apache.commons:commons-lang3:3.17.0")
}

// Override Spring Boot BOM's commons-lang3 pin so commons-compress:1.28.0 gets the version it needs
extra["commons-lang3.version"] = "3.17.0"

dependencies {
    // Internal libraries — resolved from GitHub Packages in CI, mavenLocal in local dev
    implementation("com.example:seedwork:0.1.0")
    implementation("com.example:shared-events:0.1.0")

    // Spring Boot
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.data.elasticsearch)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.validation)

    // HTTP client (to call catalog service)
    implementation(libs.spring.boot.starter.webflux)

    // Kafka + Avro
    implementation(libs.spring.kafka)
    implementation(libs.kafka.avro.serializer)

    // Database
    runtimeOnly(libs.postgresql)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.database.postgresql)

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
    testImplementation(libs.testcontainers.elasticsearch)
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation(libs.rest.assured)

}
