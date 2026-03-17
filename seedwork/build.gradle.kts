plugins {
    `java-library`
    `maven-publish`
}

group = "com.example"
version = "0.1.0"

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
}

dependencies {
    // ResolvableType needed by SpringCommandBus / SpringQueryBus.
    // Consumer services already have spring-context transitively via spring-boot-starter-web;
    // Gradle's conflict resolution will apply the consumer BOM version at runtime.
    implementation("org.springframework:spring-context:6.1.13")

    // SLF4J for bus logging — consumers provide the implementation (Logback via Spring Boot)
    implementation("org.slf4j:slf4j-api:2.0.13")

    // Outbox pattern — compileOnly: consumers bring these via spring-boot-starter-data-jpa
    compileOnly("org.springframework.data:spring-data-jpa:3.3.5")
    compileOnly("org.springframework:spring-tx:6.1.13")
    compileOnly("jakarta.persistence:jakarta.persistence-api:3.1.0")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:3.3.5")

    // Metrics — compileOnly: consumers bring micrometer-core via spring-boot-starter-actuator
    compileOnly("io.micrometer:micrometer-core:1.13.5")

    // Web exception handler — compileOnly: consumers bring these via spring-boot-starter-web
    // and spring-boot-starter-validation
    compileOnly("org.springframework:spring-web:6.1.13")       // HttpStatus, HttpRequestMethodNotAllowedException, @ExceptionHandler, etc.
    compileOnly("org.springframework:spring-webmvc:6.1.13")    // @RestControllerAdvice, DispatcherServlet, NoResourceFoundException, MethodArgumentTypeMismatchException
    compileOnly("jakarta.servlet:jakarta.servlet-api:6.0.0")   // HttpServletRequest
    compileOnly("jakarta.validation:jakarta.validation-api:3.0.2") // ConstraintViolationException

    // Kafka send — compileOnly: consumers bring spring-kafka via their own dependency
    compileOnly("org.springframework.kafka:spring-kafka:3.2.4")

    // Avro SpecificRecord interface — compileOnly: consumers bring avro via shared-events
    compileOnly("org.apache.avro:avro:1.12.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("net.jqwik:jqwik:1.9.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")
    testCompileOnly("org.projectlombok:lombok:1.18.34")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.34")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
    repositories {
        mavenLocal()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/${
                System.getenv("GITHUB_REPOSITORY") ?: "YOUR_GITHUB_ORG/seedwork"
            }")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
    }
}
