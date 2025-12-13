package io.specmatic.async.transformer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.specmatic.async.model.*
import org.slf4j.LoggerFactory
import java.time.Instant

class MessageTransformer {
    private val logger = LoggerFactory.getLogger(MessageTransformer::class.java)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    /**
     * Transforms the incoming SQS message to the appropriate Kafka message format
     * Uses Jackson's polymorphic deserialization based on the orderType discriminator
     */
    fun transformMessage(messageBody: String): String? {
        return try {
            // Jackson automatically deserializes to the correct subtype based on orderType
            val orderMessage = objectMapper.readValue(messageBody, OrderMessage::class.java)

            val transformedMessage = when (orderMessage) {
                is StandardOrderMessage -> transformStandardOrder(orderMessage)
                is PriorityOrderMessage -> transformPriorityOrder(orderMessage)
                is BulkOrderMessage -> transformBulkOrder(orderMessage)
            }

            objectMapper.writeValueAsString(transformedMessage)
        } catch (e: Exception) {
            logger.error("Error transforming message: ${e.message}. Message will not be forwarded to Kafka.", e)
            logger.debug("Message body: {}", messageBody)
            null
        }
    }

    /**
     * Determines the message type from a deserialized order message
     * Useful for extracting message keys
     */
    fun getMessageType(orderMessage: OrderMessage): OrderMessageType {
        return when (orderMessage) {
            is StandardOrderMessage -> OrderMessageType.STANDARD
            is PriorityOrderMessage -> OrderMessageType.PRIORITY
            is BulkOrderMessage -> OrderMessageType.BULK
        }
    }

    /**
     * Transform Standard Order → WIP status
     */
    private fun transformStandardOrder(standardOrder: StandardOrderMessage): WipOrderMessage {
        logger.info("Processing STANDARD order: ${standardOrder.orderId} with ${standardOrder.items.size} items")

        return WipOrderMessage(
            orderId = standardOrder.orderId,
            itemsCount = standardOrder.items.size,
            status = "WIP",
            processingStartedAt = Instant.now().toString()
        )
    }

    /**
     * Transform Priority Order → DELIVERED status
     */
    private fun transformPriorityOrder(priorityOrder: PriorityOrderMessage): DeliveredOrderMessage {
        logger.info("Processing PRIORITY order: ${priorityOrder.orderId} with priority level: ${priorityOrder.priorityLevel}")

        return DeliveredOrderMessage(
            orderId = priorityOrder.orderId,
            itemsCount = priorityOrder.items.size,
            status = "DELIVERED",
            deliveredAt = Instant.now().toString(),
            deliveryLocation = "Delivery location from logistics system" // Placeholder
        )
    }

    /**
     * Transform Bulk Order → COMPLETED status
     */
    private fun transformBulkOrder(bulkOrder: BulkOrderMessage): CompletedOrderMessage {
        // Calculate total items across all orders in the batch
        val totalItemsCount = bulkOrder.orders.sumOf { order -> order.items.size }

        logger.info("Processing BULK order batch: ${bulkOrder.batchId} - Total orders: ${bulkOrder.orders.size} - Total items: $totalItemsCount")

        return CompletedOrderMessage(
            batchId = bulkOrder.batchId,
            itemsCount = totalItemsCount,
            status = "COMPLETED",
            completedAt = Instant.now().toString(),
            customerConfirmation = true
        )
    }

    /**
     * Extract message key for Kafka partitioning from the order message
     */
    fun extractMessageKey(orderMessage: OrderMessage): String {
        return when (orderMessage) {
            is BulkOrderMessage -> orderMessage.batchId
            is StandardOrderMessage -> orderMessage.orderId
            is PriorityOrderMessage -> orderMessage.orderId
        }
    }

    /**
     * Extract message key for Kafka partitioning from raw JSON
     * This is a convenience method for cases where we need to extract the key before deserialization
     */
    fun extractMessageKeyFromJson(messageBody: String): String {
        return try {
            val orderMessage = objectMapper.readValue(messageBody, OrderMessage::class.java)
            extractMessageKey(orderMessage)
        } catch (e: Exception) {
            logger.warn("Could not extract message key: ${e.message}")
            "unknown"
        }
    }
}

