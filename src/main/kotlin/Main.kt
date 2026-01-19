package io.specmatic.async

import io.specmatic.async.transformer.MessageTransformer
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

fun main() {
    val logger = LoggerFactory.getLogger("Main")

    // Configuration - can be moved to config file or environment variables
    // Check system properties first (for tests), then environment variables
    val sqsQueueUrl = System.getProperty("SQS_QUEUE_URL")
        ?: System.getenv("SQS_QUEUE_URL")
        ?: "http://localhost:4566/000000000000/place-order-queue"
    val kafkaTopic = System.getProperty("KAFKA_TOPIC")
        ?: System.getenv("KAFKA_TOPIC")
        ?: "place-order-topic"
    val retryTopic = System.getProperty("RETRY_TOPIC")
        ?: System.getenv("RETRY_TOPIC")
        ?: "place-order-retry-topic"
    val dlqTopic = System.getProperty("DLQ_TOPIC")
        ?: System.getenv("DLQ_TOPIC")
        ?: "place-order-dlq-topic"
    val maxRetries = System.getProperty("MAX_RETRIES")?.toIntOrNull()
        ?: System.getenv("MAX_RETRIES")?.toIntOrNull()
        ?: 3
    val sqsEndpoint = System.getProperty("SQS_ENDPOINT")
        ?: System.getenv("SQS_ENDPOINT")
        ?: "http://localhost:4566"
    val kafkaBootstrapServers = System.getProperty("KAFKA_BOOTSTRAP_SERVERS")
        ?: System.getenv("KAFKA_BOOTSTRAP_SERVERS")
        ?: "localhost:9092"

    logger.info("=".repeat(60))
    logger.info("SQS to Kafka Bridge Application with Retry & DLQ")
    logger.info("=".repeat(60))
    logger.info("Configuration:")
    logger.info("  SQS Queue URL: $sqsQueueUrl")
    logger.info("  SQS Endpoint: $sqsEndpoint")
    logger.info("  Kafka Topic: $kafkaTopic")
    logger.info("  Retry Topic: $retryTopic")
    logger.info("  DLQ Topic: $dlqTopic")
    logger.info("  Max Retries: $maxRetries")
    logger.info("  Kafka Bootstrap Servers: $kafkaBootstrapServers")
    logger.info("=".repeat(60))

    // Shared message transformer
    val messageTransformer = MessageTransformer()

    val bridge = SqsToKafkaBridge(
        sqsQueueUrl = sqsQueueUrl,
        kafkaTopic = kafkaTopic,
        retryTopic = retryTopic,
        sqsEndpoint = sqsEndpoint,
        kafkaBootstrapServers = kafkaBootstrapServers,
        messageTransformer = messageTransformer
    )

    val retryConsumer = RetryConsumer(
        retryTopic = retryTopic,
        mainKafkaTopic = kafkaTopic,
        dlqTopic = dlqTopic,
        maxRetries = maxRetries,
        kafkaBootstrapServers = kafkaBootstrapServers,
        messageTransformer = messageTransformer
    )

    // Graceful shutdown hook
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutdown signal received")
        bridge.close()
        retryConsumer.close()
        logger.info("Application shutdown complete")
    })

    try {
        runBlocking {
            // Start both bridge and retry consumer concurrently
            launch {
                logger.info("Starting SQS to Kafka Bridge...")
                bridge.start()
            }
            launch {
                logger.info("Starting Retry Consumer...")
                retryConsumer.start()
            }
        }
    } catch (e: Exception) {
        logger.error("Fatal error in application", e)
        exitProcess(1)
    }
}