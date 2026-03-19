package io.specmatic.async

import io.specmatic.async.transformer.MessageTransformer
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

data class AppConfig(
    val kafkaTopic: String,
    val sqsQueueUrl: String,
    val retryTopic: String,
    val dlqTopic: String,
    val maxRetries: Int,
    val sqsEndpoint: String,
    val kafkaBootstrapServers: String
) {
    companion object {
        fun load(): AppConfig {
            return AppConfig(
                kafkaTopic = System.getProperty("KAFKA_TOPIC")
                    ?: System.getenv("KAFKA_TOPIC")
                    ?: "place-order-topic",
                sqsQueueUrl = System.getProperty("SQS_QUEUE_URL")
                    ?: System.getenv("SQS_QUEUE_URL")
                    ?: "http://localhost:4566/000000000000/place-order-queue",
                retryTopic = System.getProperty("RETRY_TOPIC")
                    ?: System.getenv("RETRY_TOPIC")
                    ?: "place-order-retry-topic",
                dlqTopic = System.getProperty("DLQ_TOPIC")
                    ?: System.getenv("DLQ_TOPIC")
                    ?: "place-order-dlq-topic",
                maxRetries = System.getProperty("MAX_RETRIES")?.toIntOrNull()
                    ?: System.getenv("MAX_RETRIES")?.toIntOrNull()
                    ?: 3,
                sqsEndpoint = System.getProperty("SQS_ENDPOINT")
                    ?: System.getenv("SQS_ENDPOINT")
                    ?: "http://localhost:4566",
                kafkaBootstrapServers = System.getProperty("KAFKA_BOOTSTRAP_SERVERS")
                    ?: System.getenv("KAFKA_BOOTSTRAP_SERVERS")
                    ?: "localhost:9092"
            )
        }
    }
}

class BridgeApplication(
    private val config: AppConfig = AppConfig.load(),
    val messageTransformer: MessageTransformer = MessageTransformer()
) {
    private val logger = LoggerFactory.getLogger(BridgeApplication::class.java)

    private val bridge = KafkaToSqsBridge(
        kafkaTopic = config.kafkaTopic,
        sqsQueueUrl = config.sqsQueueUrl,
        retryTopic = config.retryTopic,
        dlqTopic = config.dlqTopic,
        sqsEndpoint = config.sqsEndpoint,
        kafkaBootstrapServers = config.kafkaBootstrapServers,
        messageTransformer = messageTransformer
    )

    private val retryConsumer = RetryConsumer(
        retryTopic = config.retryTopic,
        sqsQueueUrl = config.sqsQueueUrl,
        dlqTopic = config.dlqTopic,
        maxRetries = config.maxRetries,
        sqsEndpoint = config.sqsEndpoint,
        kafkaBootstrapServers = config.kafkaBootstrapServers,
        messageTransformer = messageTransformer
    )

    private var mainBridgeThread: Thread? = null
    private var retryConsumerThread: Thread? = null

    fun logConfiguration() {
        logger.info("=".repeat(60))
        logger.info("Kafka to SQS Bridge Application with Retry & DLQ")
        logger.info("=".repeat(60))
        logger.info("Configuration:")
        logger.info("  Kafka Topic (Input): ${config.kafkaTopic}")
        logger.info("  SQS Queue URL (Output): ${config.sqsQueueUrl}")
        logger.info("  SQS Endpoint: ${config.sqsEndpoint}")
        logger.info("  Retry Topic: ${config.retryTopic}")
        logger.info("  DLQ Topic: ${config.dlqTopic}")
        logger.info("  Max Retries: ${config.maxRetries}")
        logger.info("  Kafka Bootstrap Servers: ${config.kafkaBootstrapServers}")
        logger.info("=".repeat(60))
    }

    fun runBlocking() {
        runBlocking {
            launch {
                logger.info("Starting Kafka to SQS Bridge...")
                bridge.start()
            }
            launch {
                logger.info("Starting Retry Consumer...")
                retryConsumer.start()
            }
        }
    }

    fun startAsync() {
        check(mainBridgeThread == null && retryConsumerThread == null) {
            "Application already started"
        }

        mainBridgeThread = Thread {
            runBlocking {
                bridge.start()
            }
        }.apply {
            isDaemon = true
            name = "MainBridge"
            start()
        }

        retryConsumerThread = Thread {
            runBlocking {
                retryConsumer.start()
            }
        }.apply {
            isDaemon = true
            name = "RetryConsumer"
            start()
        }
    }

    fun close(joinTimeoutMs: Long = 5000) {
        logger.info("Shutting down application...")
        bridge.close()
        retryConsumer.close()
        mainBridgeThread?.takeIf { it.isAlive }?.join(joinTimeoutMs)
        retryConsumerThread?.takeIf { it.isAlive }?.join(joinTimeoutMs)
    }
}

fun main() {
    val logger = LoggerFactory.getLogger("Main")
    val application = BridgeApplication()

    application.logConfiguration()

    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutdown signal received")
        application.close()
        logger.info("Application shutdown complete")
    })

    try {
        application.runBlocking()
    } catch (e: Exception) {
        logger.error("Fatal error in application", e)
        exitProcess(1)
    }
}
