pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        // Local dev: run `./gradlew publishToMavenLocal` in seedwork/ and shared-events/
        // CI: artifacts resolved from GitHub Packages below
        mavenLocal()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/${
                System.getenv("GITHUB_REPOSITORY_OWNER")
                    ?: providers.gradleProperty("githubOwner").orNull
                    ?: "YOUR_GITHUB_ORG"
            }/*")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                    ?: providers.gradleProperty("githubActor").orNull ?: ""
                password = System.getenv("GITHUB_TOKEN")
                    ?: providers.gradleProperty("githubToken").orNull ?: ""
            }
        }
        maven { url = uri("https://packages.confluent.io/maven/") }
        mavenCentral()
    }
}

rootProject.name = "order"
