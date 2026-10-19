plugins {
    java
}

group = "me.sparklee"
version = "1.0.0"
description = "Advanced fake-player population plugin for Paper and Folia"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

val paperApiVersion = "1.21.11-R0.1-SNAPSHOT"

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")

    testImplementation("io.papermc.paper:paper-api:$paperApiVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    filteringCharset = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.jar {
    archiveBaseName.set("PhantomPlayers")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    manifest {
        attributes("Implementation-Title" to "PhantomPlayers", "Implementation-Version" to project.version)
    }
}

/*
 * Verification source sets compile the same main sources against the other
 * supported API stacks. They are wiring for `check`; no separate artifacts
 * are produced or shipped.
 */
val verifyTargets = listOf(
    "VerifyPaper121" to "io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT",
    "VerifyFolia121" to "dev.folia:folia-api:1.21.11-R0.1-SNAPSHOT",
    "VerifyPaper263" to "io.papermc.paper:paper-api:26.3.build.206-stable",
    "VerifyFolia263" to "dev.folia:folia-api:26.3.build.3-alpha",
)

val verifySourceSets = verifyTargets.associate { (name, platformDependency) ->
    val sourceSet = sourceSets.create(name) {
        java.setSrcDirs(listOf("src/main/java"))
    }
    dependencies.add(sourceSet.compileOnlyConfigurationName, platformDependency)
    name to sourceSet
}

listOf("VerifyPaper121", "VerifyFolia121").forEach { name ->
    tasks.named<JavaCompile>(verifySourceSets.getValue(name).compileJavaTaskName) {
        options.release.set(21)
    }
}
listOf("VerifyPaper263", "VerifyFolia263").forEach { name ->
    tasks.named<JavaCompile>(verifySourceSets.getValue(name).compileJavaTaskName) {
        javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(25)) })
        options.release.set(25)
    }
}

tasks.named("check") {
    dependsOn(verifySourceSets.values.map { it.compileJavaTaskName })
}
