pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        // Local development and PR CI publish seedwork and shared-events first.
        mavenLocal()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/${
                System.getenv("GITHUB_REPOSITORY_OWNER")
                    ?: providers.gradleProperty("githubOwner").orNull
                    ?: "YOUR_GITHUB_ORG"
            }/*")
            content {
                includeGroup("com.example")
            }
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                    ?: providers.gradleProperty("githubActor").orNull ?: ""
                password = System.getenv("GITHUB_TOKEN")
                    ?: providers.gradleProperty("githubToken").orNull ?: ""
            }
        }
        mavenCentral()
        maven { url = uri("https://packages.confluent.io/maven/") }
    }
}

rootProject.name = "catalog"
