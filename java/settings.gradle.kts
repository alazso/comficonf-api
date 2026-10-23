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
    }
}

rootProject.name = "comficonf-java"

include("api", "demo-plugin")
project(":demo-plugin").projectDir = file("../examples/demo-plugin")

// The public repository (scripts/export-public.mjs) contains no bridge.
if (file("bridge").isDirectory) {
    include("bridge")
}
