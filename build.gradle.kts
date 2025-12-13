plugins {
    kotlin("jvm") version "2.2.21"
    application
}

group = "io.specmatic.async"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("aws.sdk.kotlin:sqs:1.0.0")
    implementation("aws.smithy.kotlin:http-client-engine-crt:1.0.0")
    implementation("org.apache.kafka:kafka-clients:3.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")

    testImplementation(kotlin("test"))
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
    testImplementation("org.testcontainers:kafka:1.21.3")
    testImplementation("org.testcontainers:localstack:1.21.3")
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