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
    // AWS SDK for SQS
    implementation("aws.sdk.kotlin:sqs:1.0.0")
    implementation("aws.smithy.kotlin:http-client-engine-crt:1.0.0")

    // Kafka Client
    implementation("org.apache.kafka:kafka-clients:3.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // JSON processing
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("io.specmatic.async.MainKt")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}