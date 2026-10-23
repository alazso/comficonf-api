plugins {
    `java-library`
    `maven-publish`
    signing
}

description = "ComfiConf developer API for Paper plugins"

dependencies {
    compileOnly(libs.paper.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testImplementation(libs.paper.api)
    testRuntimeOnly(libs.junit.platform.launcher)
}

java {
    withSourcesJar()
    withJavadocJar()
}

base {
    archivesName.set("comficonf-api")
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "ComfiConf API",
            "Implementation-Version" to project.version,
        )
    }
}

// POM metadata that Maven Central requires, from java/gradle.properties or -P options.
val pomMetadata = linkedMapOf(
    "comficonf.repositoryUrl" to providers.gradleProperty("comficonf.repositoryUrl").orNull,
    "comficonf.licenseName" to providers.gradleProperty("comficonf.licenseName").orNull,
    "comficonf.licenseUrl" to providers.gradleProperty("comficonf.licenseUrl").orNull,
    "comficonf.developerName" to providers.gradleProperty("comficonf.developerName").orNull,
).mapValues { (_, value) -> value?.trim()?.ifEmpty { null } }
val repositoryUrl = pomMetadata["comficonf.repositoryUrl"]
val licenseName = pomMetadata["comficonf.licenseName"]
val licenseUrl = pomMetadata["comficonf.licenseUrl"]
val developerName = pomMetadata["comficonf.developerName"]

val stagingDir = layout.buildDirectory.dir("staging-deploy")

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "comficonf-api"
            from(components["java"])
            pom {
                name.set("ComfiConf API")
                description.set(project.description)
                if (repositoryUrl != null) {
                    url.set(repositoryUrl)
                    scm {
                        url.set(repositoryUrl)
                        connection.set("scm:git:$repositoryUrl.git")
                        developerConnection.set("scm:git:$repositoryUrl.git")
                    }
                }
                if (licenseName != null) {
                    licenses {
                        license {
                            name.set(licenseName)
                            if (licenseUrl != null) {
                                url.set(licenseUrl)
                            }
                        }
                    }
                }
                if (developerName != null) {
                    developers {
                        developer {
                            name.set(developerName)
                        }
                    }
                }
            }
        }
    }
    repositories {
        maven {
            name = "staging"
            url = uri(stagingDir)
        }
    }
}

val checkCentralMetadata = tasks.register("checkCentralMetadata") {
    description = "Fails when POM metadata required by Maven Central is not set."
    val missing = pomMetadata.filterValues { it == null }.keys
    doLast {
        if (missing.isNotEmpty()) {
            throw GradleException("Set ${missing.joinToString()} in java/gradle.properties "
                    + "(or pass them with -P) before building the Maven Central bundle.")
        }
    }
}

tasks.named { it == "publishMavenPublicationToStagingRepository" }.configureEach {
    dependsOn(checkCentralMetadata)
    doFirst {
        stagingDir.get().asFile.deleteRecursively()
    }
}

val centralBundle = tasks.register<Zip>("centralBundle") {
    group = "publishing"
    description = "Builds the signed upload bundle for the Maven Central Portal in build/distributions."
    dependsOn("publishMavenPublicationToStagingRepository")
    from(stagingDir) {
        include("com/comficonf/comficonf-api/${project.version}/**")
    }
    archiveFileName.set("comficonf-api-${project.version}-central-bundle.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}

signing {
    // An ASCII-armored secret key in signingKey (for example ORG_GRADLE_PROJECT_signingKey) takes precedence
    // over the signing.keyId, signing.password and signing.secretKeyRingFile properties.
    val signingKey = providers.gradleProperty("signingKey").orNull
    if (signingKey != null) {
        useInMemoryPgpKeys(signingKey, providers.gradleProperty("signingPassword").getOrElse(""))
    }
    // A missing key fails the Central bundle; other publishing tasks then skip signing.
    setRequired({ gradle.taskGraph.hasTask(centralBundle.get()) })
    sign(publishing.publications["maven"])
}
