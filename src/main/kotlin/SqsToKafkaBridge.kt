package io.specmatic.async

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.sqs.SqsClient
import aws.sdk.kotlin.services.sqs.model.DeleteMessageRequest
import aws.sdk.kotlin.services.sqs.model.ReceiveMessageRequest
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.net.url.Url
import io.specmatic.async.transformer.MessageTransformer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.coroutineScope
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.util.Properties

class SqsToKafkaBridge(
    private val sqsQueueUrl: String,
    private val kafkaTopic: String,
    private val sqsEndpoint: String = "http://localhost:4566",
    private val kafkaBootstrapServers: String = "localhost:9092"
) {
    private val logger = LoggerFactory.getLogger(SqsToKafkaBridge::class.java)
    private val messageTransformer = MessageTransformer()
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
        }

        val response = sqsClient.receiveMessage(receiveRequest)
        val messages = response.messages ?: emptyList()

        if (messages.isEmpty()) {
            logger.debug("No messages received from SQS")
            return
        }

        logger.info("Received ${messages.size} message(s) from SQS")

        messages.forEach { message ->
            try {
                val messageBody = message.body ?: ""
                logger.info("Received message from SQS: $messageBody")
                val messageType = messageTransformer.determineMessageType(messageBody)
                val transformedMessage = messageTransformer.transformMessage(messageBody)

                if (transformedMessage != null) {
                    val messageKey = messageTransformer.extractMessageKey(messageBody, messageType)
                    sendToKafka(transformedMessage, messageKey)
                    logger.info("Successfully processed and forwarded message to Kafka")
                } else {
                    logger.warn("Message was not processed and will not be forwarded to Kafka. MessageId: ${message.messageId}")
                }
            } catch (e: Exception) {
                logger.error("Error processing message: ${message.messageId}", e)
            } finally {
                deleteMessage(sqsClient, message.receiptHandle ?: "")
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

