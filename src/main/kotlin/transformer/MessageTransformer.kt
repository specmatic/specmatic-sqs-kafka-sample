package io.specmatic.async.transformer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.specmatic.async.model.*
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Custom exception for message transformation failures
 */
class MessageTransformationException(message: String, cause: Throwable? = null) : Exception(message, cause)

class MessageTransformer {
    private val logger = LoggerFactory.getLogger(MessageTransformer::class.java)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    // Set of order IDs that should fail transformation (for testing retry/DLQ)
    private val failingOrderIds = mutableSetOf<String>()

    // Track retry attempts for specific order IDs
    private val retryAttempts = mutableMapOf<String, Int>()

    /**
     * Mark an order ID to fail transformation (for testing purposes)
     */
    fun addFailingOrderId(orderId: String) {
        failingOrderIds.add(orderId)
        logger.info("Order ID $orderId marked to fail transformation")
    }

    /**
     * Remove an order ID from the failing list
     */
    fun removeFailingOrderId(orderId: String) {
        failingOrderIds.remove(orderId)
        logger.info("Order ID $orderId removed from failing list")
    }

    /**
     * Clear all failing order IDs
     */
    fun clearFailingOrderIds() {
        failingOrderIds.clear()
        logger.info("Cleared all failing order IDs")
    }

    /**
     * Transforms the incoming SQS message to the appropriate Kafka message format
     * Uses Jackson's polymorphic deserialization based on the orderType discriminator
     *
     * @throws MessageTransformationException if transformation fails
     */
    fun transformMessage(messageBody: String): String? {
        return try {
            // Jackson automatically deserializes to the correct subtype based on orderType
            val orderMessage = objectMapper.readValue(messageBody, OrderMessage::class.java)

            // Check if this order should fail (for testing retry/DLQ)
            val orderId = extractOrderId(orderMessage)
            if (orderId in failingOrderIds) {
                // Track retry attempts
                val attempts = retryAttempts.getOrDefault(orderId, 0) + 1
                retryAttempts[orderId] = attempts

                logger.info("Processing failing order ID: $orderId, attempt: $attempts")

                // For retry scenario: fail on first attempt, succeed on retry
                if (orderId == "ORD-RETRY-90001") {
                    if (attempts == 1) {
                        throw MessageTransformationException("Simulated transformation failure for order: $orderId (first attempt)")
                    } else {
                        logger.info("Order $orderId succeeded on retry attempt $attempts")
                        // Remove from failing list so it succeeds
                        failingOrderIds.remove(orderId)
                    }
                } else {
                    // For DLQ scenario: always fail
                    throw MessageTransformationException("Simulated transformation failure for order: $orderId")
                }
            }

            val transformedMessage = when (orderMessage) {
                is StandardOrderMessage -> transformStandardOrder(orderMessage)
                is PriorityOrderMessage -> transformPriorityOrder(orderMessage)
                is BulkOrderMessage -> transformBulkOrder(orderMessage)
            }

            objectMapper.writeValueAsString(transformedMessage)
        } catch (e: MessageTransformationException) {
            // Log without stack trace to keep test logs clean
            logger.warn("Message transformation failed: ${e.message}")
            throw e
        } catch (e: Exception) {
            logger.error("Error transforming message: ${e.message}")
            throw MessageTransformationException("Failed to transform message", e)
        }
    }

    /**
     * Extract order ID from an order message
     */
    private fun extractOrderId(orderMessage: OrderMessage): String {
        return when (orderMessage) {
            is StandardOrderMessage -> orderMessage.orderId
            is PriorityOrderMessage -> orderMessage.orderId
            is BulkOrderMessage -> orderMessage.batchId
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
        logger.debug("Processing STANDARD order: ${standardOrder.orderId}")

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
        logger.debug("Processing PRIORITY order: ${priorityOrder.orderId}")

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

        logger.debug("Processing BULK order batch: ${bulkOrder.batchId}")

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

