plugins {
    kotlin("jvm") version "2.3.0"
    application
}

group = "io.specmatic.async"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("aws.sdk.kotlin:sqs:1.5.123")
    implementation("aws.smithy.kotlin:http-client-engine-crt:1.6.0")
    implementation("org.apache.kafka:kafka-clients:3.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("ch.qos.logback:logback-classic:1.5.25")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.0")

    testImplementation(kotlin("test"))
    testImplementation("org.testcontainers:testcontainers:2.0.3")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.testcontainers:kafka:1.21.4")
    testImplementation("org.testcontainers:localstack:1.21.4")
    testImplementation("org.assertj:assertj-core:3.27.6")
}

application {
    mainClass.set("io.specmatic.async.MainKt")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true

        // Show standard out and error streams
        showStandardStreams = true
    }
}

// Create a fat JAR with all dependencies
tasks.jar {
    manifest {
        attributes["Main-Class"] = "io.specmatic.async.MainKt"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
