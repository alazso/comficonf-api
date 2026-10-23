plugins {
    java
}

description = "ComfiConfDemo: reference voucher plugin showing a ComfiConf integration"

dependencies {
    // Provided at runtime by the ComfiConf bridge when it is installed; never shade it.
    compileOnly(project(":api"))
    compileOnly(libs.paper.api)

    testImplementation(project(":api"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testImplementation(libs.paper.api)
    testRuntimeOnly(libs.junit.platform.launcher)
}

base {
    archivesName.set("ComfiConfDemo")
}

tasks.processResources {
    val properties = mapOf("version" to project.version.toString())
    inputs.properties(properties)
    filesMatching("plugin.yml") {
        expand(properties)
    }
}
