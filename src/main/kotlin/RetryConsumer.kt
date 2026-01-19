package io.specmatic.async

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.specmatic.async.model.RetryableMessage
import io.specmatic.async.model.DlqMessage
import io.specmatic.async.transformer.MessageTransformer
import io.specmatic.async.transformer.MessageTransformationException
import kotlinx.coroutines.*
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.*

/**
 * Kafka consumer that processes messages from the retry topic
 * Attempts to reprocess messages by transforming and sending to main Kafka topic
 * If transformation still fails, sends back to retry topic or to DLQ if max retries exceeded
 */
class RetryConsumer(
    private val retryTopic: String,
    private val mainKafkaTopic: String,
    private val dlqTopic: String,
    private val maxRetries: Int = 3,
    private val kafkaBootstrapServers: String = "localhost:9092",
    val messageTransformer: MessageTransformer = MessageTransformer()
) {
    private val logger = LoggerFactory.getLogger(RetryConsumer::class.java)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val consumerLock = Any()

    @Volatile
    private var running = true

    private val kafkaConsumer: KafkaConsumer<String, String> by lazy {
        val props = Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers)
            put(ConsumerConfig.GROUP_ID_CONFIG, "retry-consumer-group")
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
            put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10")
        }
        KafkaConsumer<String, String>(props)
    }

    private val kafkaProducer: KafkaProducer<String, String> by lazy {
        val props = Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.ACKS_CONFIG, "all")
            put(ProducerConfig.RETRIES_CONFIG, 3)
        }
        KafkaProducer<String, String>(props)
    }

    suspend fun start() = coroutineScope {
        logger.info("Starting Retry Consumer...")
        logger.info("Retry Topic: $retryTopic")
        logger.info("Main Kafka Topic (for reprocessing): $mainKafkaTopic")
        logger.info("DLQ Topic: $dlqTopic")
        logger.info("Max Retries: $maxRetries")

        synchronized(consumerLock) {
            kafkaConsumer.subscribe(listOf(retryTopic))
        }

        while (isActive && running) {
            try {
                processRetryMessages()
            } catch (e: Exception) {
                logger.error("Error in retry consumer loop", e)
                delay(5000)
            }
        }
    }

    private suspend fun processRetryMessages() {
        val records = synchronized(consumerLock) {
            kafkaConsumer.poll(Duration.ofSeconds(10))
        }

        if (records.isEmpty) {
            logger.debug("No retry messages to process")
            return
        }

        logger.info("Processing ${records.count()} retry message(s)")

        for (record in records) {
            try {
                val retryableMessage = objectMapper.readValue<RetryableMessage>(record.value())
                logger.info("Processing retry message - Key: ${record.key()}, Retry Count: ${retryableMessage.retryCount}")

                processRetryMessage(retryableMessage)
            } catch (e: Exception) {
                logger.error("Error processing retry record at offset ${record.offset()}", e)
            }
        }

        synchronized(consumerLock) {
            kafkaConsumer.commitSync()
        }
    }

    private suspend fun processRetryMessage(retryableMessage: RetryableMessage) {
        // Wait before retry (exponential backoff)
        val delayMs = calculateBackoff(retryableMessage.retryCount)
        logger.info("Waiting ${delayMs}ms before retry attempt ${retryableMessage.retryCount + 1}")
        delay(delayMs)

        // Check if max retries exceeded
        if (retryableMessage.retryCount >= maxRetries) {
            logger.warn("Max retries ($maxRetries) exceeded for message key: ${retryableMessage.messageKey}. Sending to DLQ")
            sendToDlq(retryableMessage)
            return
        }

        // Attempt to transform and send to main Kafka topic
        try {
            val transformedMessage = messageTransformer.transformMessage(retryableMessage.originalMessage)

            if (transformedMessage != null) {
                sendToMainKafkaTopic(transformedMessage, retryableMessage.messageKey)
                logger.info("Successfully reprocessed message on retry attempt ${retryableMessage.retryCount + 1}. Message key: ${retryableMessage.messageKey}")
            } else {
                logger.warn("Message transformation returned null on retry. Sending back to retry topic.")
                val error = MessageTransformationException("Transformation returned null")
                sendBackToRetryTopic(retryableMessage, error)
            }
        } catch (e: MessageTransformationException) {
            logger.warn("Message transformation failed on retry attempt ${retryableMessage.retryCount + 1}. Sending back to retry topic. Message key: ${retryableMessage.messageKey}")
            sendBackToRetryTopic(retryableMessage, e)
        } catch (e: Exception) {
            logger.error("Unexpected error during retry processing. Sending back to retry topic.", e)
            sendBackToRetryTopic(retryableMessage, e)
        }
    }

    private fun sendToMainKafkaTopic(transformedMessage: String, messageKey: String) {
        val record = ProducerRecord(mainKafkaTopic, messageKey, transformedMessage)

        kafkaProducer.send(record) { metadata, exception ->
            if (exception != null) {
                logger.error("Failed to send reprocessed message to main Kafka topic", exception)
                throw exception
            } else {
                logger.info("Reprocessed message sent to Kafka - Topic: ${metadata.topic()}, Partition: ${metadata.partition()}, Offset: ${metadata.offset()}")
            }
        }.get() // Wait for acknowledgment
    }

    private fun sendBackToRetryTopic(retryableMessage: RetryableMessage, error: Exception) {
        val newRetryCount = retryableMessage.retryCount + 1

        val updatedRetryMessage = retryableMessage.copy(
            retryCount = newRetryCount,
            lastAttemptTimestamp = Instant.now().toString(),
            errorMessage = error.message,
            errorStackTrace = error.stackTraceToString()
        )

        val messageJson = objectMapper.writeValueAsString(updatedRetryMessage)
        val record = ProducerRecord(retryTopic, retryableMessage.messageKey, messageJson)

        kafkaProducer.send(record) { metadata, exception ->
            if (exception != null) {
                logger.error("Failed to send message back to retry topic: ${exception.message}")
            } else {
                logger.debug("Message re-queued to retry topic - Retry count: $newRetryCount")
            }
        }.get()
    }

    private fun sendToDlq(retryableMessage: RetryableMessage) {
        val dlqMessage = DlqMessage(
            originalMessage = retryableMessage.originalMessage,
            messageKey = retryableMessage.messageKey,
            totalRetries = retryableMessage.retryCount,
            firstAttemptTimestamp = retryableMessage.firstAttemptTimestamp,
            failedTimestamp = Instant.now().toString(),
            finalErrorMessage = retryableMessage.errorMessage ?: "Max retries exceeded",
            finalErrorStackTrace = retryableMessage.errorStackTrace
        )

        val messageJson = objectMapper.writeValueAsString(dlqMessage)
        val record = ProducerRecord(dlqTopic, retryableMessage.messageKey, messageJson)

        kafkaProducer.send(record) { metadata, exception ->
            if (exception != null) {
                logger.error("Failed to send message to DLQ - THIS IS CRITICAL!: ${exception.message}")
                // In production, this should trigger an alert
            } else {
                logger.info("Message sent to DLQ - Key: ${retryableMessage.messageKey}, Total retries: ${retryableMessage.retryCount}")
            }
        }.get()
    }

    /**
     * Calculate exponential backoff delay
     */
    private fun calculateBackoff(retryCount: Int): Long {
        // Exponential backoff: 1s, 2s, 4s, 8s, etc. (capped at 30s)
        val baseDelayMs = 1000L
        val exponentialDelay = baseDelayMs * (1 shl retryCount.coerceAtMost(5))
        return exponentialDelay.coerceAtMost(30000L)
    }

    fun close() {
        logger.info("Shutting down Retry Consumer...")
        running = false
        synchronized(consumerLock) {
            kafkaConsumer.close()
        }
        kafkaProducer.close()
    }
}

