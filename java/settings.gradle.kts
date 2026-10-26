pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") {
            name = "papermc"
        }
        if (file("bridge").isDirectory) {
            maven("https://repo.faststats.dev/releases") {
                name = "faststats"
                content {
                    includeGroup("dev.faststats.metrics")
                }
            }
        }
    }
}

rootProject.name = "comficonf-java"

include("api", "demo-plugin")
project(":demo-plugin").projectDir = file("../examples/demo-plugin")

// The bridge is closed source and not part of the public API repository.
if (file("bridge").isDirectory) {
    include("bridge")
}
