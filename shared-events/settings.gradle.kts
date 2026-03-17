pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal()
        maven { url = uri("https://packages.confluent.io/maven/") }
    }

}

rootProject.name = "shared-events"
