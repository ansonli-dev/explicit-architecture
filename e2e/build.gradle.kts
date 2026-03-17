plugins {
    java
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("--enable-preview")

    // URLs can be overridden via:
    //   Gradle property:  ./gradlew test -Pe2e.catalog.base-url=http://staging:8081
    //   Environment var:  CATALOG_BASE_URL=http://staging:8081 ./gradlew test
    fun prop(key: String, envVar: String, default: String): String =
        project.findProperty(key) as String?
            ?: System.getenv(envVar)
            ?: default

    systemProperty("e2e.catalog.base-url",      prop("e2e.catalog.base-url",      "CATALOG_BASE_URL",      "http://localhost:8081"))
    systemProperty("e2e.order.base-url",         prop("e2e.order.base-url",         "ORDER_BASE_URL",         "http://localhost:8082"))
    systemProperty("e2e.notification.base-url",  prop("e2e.notification.base-url",  "NOTIFICATION_BASE_URL",  "http://localhost:8083"))
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("io.rest-assured:rest-assured:5.5.0")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("org.awaitility:awaitility:4.2.2")
    testImplementation("org.hamcrest:hamcrest:2.2")

    testCompileOnly("org.projectlombok:lombok:1.18.34")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.34")
}
