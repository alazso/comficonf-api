plugins {
    base
}

subprojects {
    apply(plugin = "java")

    group = rootProject.group
    version = rootProject.version

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(25)
        options.compilerArgs.addAll(listOf("-Xlint:all,-processing,-serial", "-parameters"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("failed", "skipped")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).apply {
            encoding = "UTF-8"
            addStringOption("Xdoclint:all,-missing", "-quiet")
        }
    }
}

val releaseVersion = version.toString()
val releaseDir = layout.buildDirectory.dir("release/$releaseVersion")
val releaseFiles = mapOf(
    ":api" to listOf(
        "comficonf-api-$releaseVersion.jar",
        "comficonf-api-$releaseVersion-sources.jar",
        "comficonf-api-$releaseVersion-javadoc.jar",
    ),
    ":bridge" to listOf("ComfiConf-Bridge-$releaseVersion.jar"),
    ":demo-plugin" to listOf("ComfiConfDemo-$releaseVersion.jar"),
).filterKeys { findProject(it) != null }

tasks.register<Sync>("release") {
    group = "distribution"
    description = "Builds and tests every project, then collects the release JARs and their SHA-256 checksums " +
            "in build/release/<version>."
    dependsOn(releaseFiles.keys.map { "$it:build" })
    releaseFiles.forEach { (path, names) ->
        from(project(path).layout.buildDirectory.dir("libs")) {
            include(names)
        }
    }
    into(releaseDir)
    doLast {
        val dir = releaseDir.get().asFile
        val expected = releaseFiles.values.flatten()
        val missing = expected.filterNot { dir.resolve(it).isFile }
        if (missing.isNotEmpty()) {
            throw GradleException("Release files missing from $dir: ${missing.joinToString()}")
        }
        val sums = expected.sorted().joinToString(separator = "") { name ->
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(dir.resolve(name).readBytes())
            "${digest.joinToString(separator = "") { "%02x".format(it) }}  $name\n"
        }
        dir.resolve("SHA256SUMS").writeText(sums)
    }
}
