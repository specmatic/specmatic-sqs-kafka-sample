package io.specmatic.async

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.sqs.SqsClient
import aws.sdk.kotlin.services.sqs.model.DeleteMessageRequest
import aws.sdk.kotlin.services.sqs.model.ReceiveMessageRequest
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.net.url.Url
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.specmatic.async.model.RetryableMessage
import io.specmatic.async.transformer.MessageTransformer
import io.specmatic.async.transformer.MessageTransformationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.coroutineScope
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.Properties

class SqsToKafkaBridge(
    private val sqsQueueUrl: String,
    private val kafkaTopic: String,
    private val retryTopic: String,
    private val sqsEndpoint: String = "http://localhost:4566",
    private val kafkaBootstrapServers: String = "localhost:9092",
    val messageTransformer: MessageTransformer = MessageTransformer()
) {
    private val logger = LoggerFactory.getLogger(SqsToKafkaBridge::class.java)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @Volatile
    private var running = true

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
        logger.info("Starting SQS to Kafka bridge...")
        logger.info("SQS Queue URL: $sqsQueueUrl")
        logger.info("Kafka Topic: $kafkaTopic")
        logger.info("Retry Topic: $retryTopic")

        val sqsClient = SqsClient {
            region = "us-east-1"
            endpointUrl = Url.parse(sqsEndpoint)
            credentialsProvider = StaticCredentialsProvider(
                Credentials(
                    accessKeyId = "test",
                    secretAccessKey = "test"
                )
            )
        }

        sqsClient.use { client ->
            while (isActive && running) {
                try {
                    pollAndProcessMessages(client)
                } catch (e: Exception) {
                    logger.error("Error processing messages", e)
                    delay(5000) // Wait before retrying
                }
            }
        }
    }

    private suspend fun pollAndProcessMessages(sqsClient: SqsClient) {
        val receiveRequest = ReceiveMessageRequest {
            queueUrl = sqsQueueUrl
            maxNumberOfMessages = 10
            waitTimeSeconds = 20 // Long polling
            messageAttributeNames = listOf("All") // Get all message attributes including retry metadata
        }

        val response = sqsClient.receiveMessage(receiveRequest)
        val messages = response.messages ?: emptyList()

        if (messages.isEmpty()) {
            logger.debug("No messages received from SQS")
            return
        }

        logger.info("Received ${messages.size} message(s) from SQS")

        messages.forEach { message ->
            var shouldDeleteMessage = true
            try {
                val messageBody = message.body ?: ""
                logger.info("Received message from SQS: $messageBody")

                // Extract retry metadata from message attributes (if this is a retry)
                val currentRetryCount = message.messageAttributes?.get("RetryCount")?.stringValue?.toIntOrNull() ?: 0
                val firstAttemptTimestamp = message.messageAttributes?.get("FirstAttemptTimestamp")?.stringValue

                try {
                    val transformedMessage = messageTransformer.transformMessage(messageBody)

                    if (transformedMessage != null) {
                        val messageKey = messageTransformer.extractMessageKeyFromJson(messageBody)
                        sendToKafka(transformedMessage, messageKey)
                        logger.info("Successfully processed and forwarded message to Kafka")
                    } else {
                        logger.warn("Message transformation returned null. MessageId: ${message.messageId}")
                        shouldDeleteMessage = false
                    }
                } catch (e: MessageTransformationException) {
                    // Transformation failed - send to retry topic
                    logger.warn("Message transformation failed, sending to retry topic. MessageId: ${message.messageId}, Current retry count: $currentRetryCount")
                    sendToRetryTopic(messageBody, currentRetryCount, firstAttemptTimestamp, e)
                    // Still delete from SQS since we've moved it to retry
                }
            } catch (e: Exception) {
                logger.error("Error processing message: ${message.messageId}", e)
                shouldDeleteMessage = false
            } finally {
                if (shouldDeleteMessage) {
                    deleteMessage(sqsClient, message.receiptHandle ?: "")
                } else {
                    logger.warn("Message not deleted from SQS due to processing error: ${message.messageId}")
                }
            }
        }
    }

    private fun sendToKafka(message: String, messageKey: String) {
        val record = ProducerRecord(kafkaTopic, messageKey, message)

        kafkaProducer.send(record) { metadata, exception ->
            if (exception != null) {
                logger.error("Failed to send message to Kafka", exception)
                throw exception
            } else {
                logger.info("Message sent to Kafka - Topic: ${metadata.topic()}, Partition: ${metadata.partition()}, Offset: ${metadata.offset()}")
            }
        }.get() // Wait for acknowledgment
    }

    private fun sendToRetryTopic(originalMessage: String, currentRetryCount: Int, firstAttemptTimestamp: String?, error: Exception) {
        try {
            val messageKey = messageTransformer.extractMessageKeyFromJson(originalMessage)
            val now = Instant.now().toString()

            val retryableMessage = RetryableMessage(
                originalMessage = originalMessage,
                messageKey = messageKey,
                retryCount = currentRetryCount, // Use the current retry count from SQS attributes
                firstAttemptTimestamp = firstAttemptTimestamp ?: now, // Use existing or set new
                lastAttemptTimestamp = now,
                errorMessage = error.message,
                errorStackTrace = error.stackTraceToString()
            )

            val messageJson = objectMapper.writeValueAsString(retryableMessage)
            val record = ProducerRecord(retryTopic, messageKey, messageJson)

            kafkaProducer.send(record) { metadata, exception ->
                if (exception != null) {
                    logger.error("Failed to send message to retry topic - THIS IS CRITICAL!", exception)
                } else {
                    logger.debug("Message sent to retry topic - Partition: ${metadata.partition()}, Offset: ${metadata.offset()}")
                }
            }.get()
        } catch (e: Exception) {
            logger.error("Failed to create retry message", e)
        }
    }

    private suspend fun deleteMessage(sqsClient: SqsClient, receiptHandle: String) {
        val deleteRequest = DeleteMessageRequest {
            queueUrl = sqsQueueUrl
            this.receiptHandle = receiptHandle
        }
        sqsClient.deleteMessage(deleteRequest)
        logger.debug("Message deleted from SQS")
    }

    fun close() {
        logger.info("Shutting down SQS to Kafka bridge...")
        running = false
        kafkaProducer.close()
    }
}

