plugins {
    `java-library`
    alias(libs.plugins.avro)
    `maven-publish`
}

group = "com.example"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Avro runtime
    api("org.apache.avro:avro:1.12.1")
    // Confluent Kafka Avro serializer (exposed as api so consumers don't need to redeclare)
    api("io.confluent:kafka-avro-serializer:7.6.0")
}

avro {
    isCreateSetters.set(false)
    fieldVisibility.set("PRIVATE")
    isEnableDecimalLogicalType.set(true)
    outputCharacterEncoding.set("UTF-8")
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
                System.getenv("GITHUB_REPOSITORY") ?: "YOUR_GITHUB_ORG/shared-events"
            }")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
    }
}
