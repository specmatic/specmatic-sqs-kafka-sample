package io.specmatic.async

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.sqs.SqsClient
import aws.sdk.kotlin.services.sqs.model.SendMessageRequest
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.net.url.Url
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.specmatic.async.model.RetryableMessage
import io.specmatic.async.transformer.MessageTransformer
import io.specmatic.async.transformer.MessageTransformationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
import java.util.Properties

class KafkaToSqsBridge(
    private val kafkaTopic: String,
    private val sqsQueueUrl: String,
    private val retryTopic: String,
    private val sqsEndpoint: String = "http://localhost:4566",
    private val kafkaBootstrapServers: String = "localhost:9092",
    val messageTransformer: MessageTransformer = MessageTransformer()
) {
    private val logger = LoggerFactory.getLogger(KafkaToSqsBridge::class.java)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val consumerLock = Any()

    @Volatile
    private var running = true

    private val kafkaConsumer: KafkaConsumer<String, String> by lazy {
        val props = Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers)
            put(ConsumerConfig.GROUP_ID_CONFIG, "kafka-to-sqs-bridge-group")
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

    private lateinit var sqsClient: SqsClient

    suspend fun start() = coroutineScope {
        logger.info("Starting Kafka to SQS bridge...")
        logger.info("Kafka Topic: $kafkaTopic")
        logger.info("SQS Queue URL: $sqsQueueUrl")
        logger.info("Retry Topic: $retryTopic")

        sqsClient = SqsClient {
            region = "us-east-1"
            endpointUrl = Url.parse(sqsEndpoint)
            credentialsProvider = StaticCredentialsProvider(
                Credentials(
                    accessKeyId = "test",
                    secretAccessKey = "test"
                )
            )
        }

        synchronized(consumerLock) {
            kafkaConsumer.subscribe(listOf(kafkaTopic))
        }

        while (isActive && running) {
            try {
                pollAndProcessMessages()
            } catch (e: Exception) {
                logger.error("Error processing messages", e)
                delay(5000) // Wait before retrying
            }
        }

        sqsClient.close()
    }

    private suspend fun pollAndProcessMessages() {
        val records = synchronized(consumerLock) {
            kafkaConsumer.poll(Duration.ofSeconds(10))
        }

        if (records.isEmpty) {
            logger.debug("No messages received from Kafka")
            return
        }

        logger.info("Received ${records.count()} message(s) from Kafka")

        for (record in records) {
            try {
                val messageBody = record.value()
                logger.info("Received message from Kafka: $messageBody")

                try {
                    val transformedMessage = messageTransformer.transformMessage(messageBody)

                    if (transformedMessage != null) {
                        val messageKey = messageTransformer.extractMessageKeyFromJson(messageBody)
                        sendToSqs(transformedMessage, messageKey)
                        logger.info("Successfully processed and forwarded message to SQS")
                    } else {
                        logger.warn("Message transformation returned null. Key: ${record.key()}")
                    }
                } catch (e: MessageTransformationException) {
                    // Transformation failed - send to retry topic
                    logger.warn("Message transformation failed, sending to retry topic. Key: ${record.key()}")
                    sendToRetryTopic(messageBody, 0, null, e)
                }
            } catch (e: Exception) {
                logger.error("Error processing message: ${record.key()}", e)
            }
        }

        synchronized(consumerLock) {
            kafkaConsumer.commitSync()
        }
    }

    private suspend fun sendToSqs(message: String, messageKey: String) {
        val sendRequest = SendMessageRequest {
            queueUrl = sqsQueueUrl
            messageBody = message
            messageGroupId = messageKey // For FIFO queues, if applicable
        }

        try {
            val response = sqsClient.sendMessage(sendRequest)
            logger.info("Message sent to SQS - MessageId: ${response.messageId}")
        } catch (e: Exception) {
            logger.error("Failed to send message to SQS", e)
            throw e
        }
    }

    private fun sendToRetryTopic(originalMessage: String, currentRetryCount: Int, firstAttemptTimestamp: String?, error: Exception) {
        try {
            val messageKey = messageTransformer.extractMessageKeyFromJson(originalMessage)
            val now = Instant.now().toString()

            val retryableMessage = RetryableMessage(
                originalMessage = originalMessage,
                messageKey = messageKey,
                retryCount = currentRetryCount,
                firstAttemptTimestamp = firstAttemptTimestamp ?: now,
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

    fun close() {
        logger.info("Shutting down Kafka to SQS bridge...")
        running = false
        synchronized(consumerLock) {
            kafkaConsumer.close()
        }
        kafkaProducer.close()
    }
}

