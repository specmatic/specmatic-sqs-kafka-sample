package io.specmatic.async

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

fun main() {
    val logger = LoggerFactory.getLogger("Main")

    // Configuration - can be moved to config file or environment variables
    val sqsQueueUrl = System.getenv("SQS_QUEUE_URL")
        ?: "http://localhost:4566/000000000000/test-queue"
    val kafkaTopic = System.getenv("KAFKA_TOPIC") ?: "test-topic"
    val sqsEndpoint = System.getenv("SQS_ENDPOINT") ?: "http://localhost:4566"
    val kafkaBootstrapServers = System.getenv("KAFKA_BOOTSTRAP_SERVERS") ?: "localhost:9092"

    logger.info("=".repeat(60))
    logger.info("SQS to Kafka Bridge Application")
    logger.info("=".repeat(60))
    logger.info("Configuration:")
    logger.info("  SQS Queue URL: $sqsQueueUrl")
    logger.info("  SQS Endpoint: $sqsEndpoint")
    logger.info("  Kafka Topic: $kafkaTopic")
    logger.info("  Kafka Bootstrap Servers: $kafkaBootstrapServers")
    logger.info("=".repeat(60))

    val bridge = SqsToKafkaBridge(
        sqsQueueUrl = sqsQueueUrl,
        kafkaTopic = kafkaTopic,
        sqsEndpoint = sqsEndpoint,
        kafkaBootstrapServers = kafkaBootstrapServers
    )

    // Graceful shutdown hook
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutdown signal received")
        bridge.close()
        logger.info("Application shutdown complete")
    })

    try {
        runBlocking {
            bridge.start()
        }
    } catch (e: Exception) {
        logger.error("Fatal error in application", e)
        exitProcess(1)
    }
}