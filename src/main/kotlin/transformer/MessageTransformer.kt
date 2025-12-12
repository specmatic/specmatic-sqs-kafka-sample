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
     * Determines the type of incoming order message
     */
    fun determineMessageType(messageBody: String): OrderMessageType {
        return try {
            val jsonNode = objectMapper.readTree(messageBody)

            when {
                // Bulk order: has batchId and orders array
                jsonNode.has("batchId") && jsonNode.has("orders") && jsonNode.get("orders").isArray -> {
                    logger.info("Detected BULK order message")
                    OrderMessageType.BULK
                }
                // Priority order: has priorityLevel and expectedDeliveryDate
                jsonNode.has("priorityLevel") && jsonNode.has("expectedDeliveryDate") -> {
                    logger.info("Detected PRIORITY order message")
                    OrderMessageType.PRIORITY
                }
                // Standard order: has orderId, customerId, items
                jsonNode.has("orderId") && jsonNode.has("customerId") && jsonNode.has("items") -> {
                    logger.info("Detected STANDARD order message")
                    OrderMessageType.STANDARD
                }
                else -> {
                    logger.warn("Unknown message format: {}", messageBody)
                    OrderMessageType.UNKNOWN
                }
            }
        } catch (e: Exception) {
            logger.error("Error parsing message to determine type: ${e.message}", e)
            OrderMessageType.UNKNOWN
        }
    }

    /**
     * Transforms the incoming SQS message to the appropriate Kafka message format
     */
    fun transformMessage(messageBody: String): String? {
        val messageType = determineMessageType(messageBody)

        return when (messageType) {
            OrderMessageType.STANDARD -> transformStandardOrder(messageBody)
            OrderMessageType.PRIORITY -> transformPriorityOrder(messageBody)
            OrderMessageType.BULK -> transformBulkOrder(messageBody)
            OrderMessageType.UNKNOWN -> {
                logger.error("Message does not match any expected schema. Message will not be forwarded to Kafka: {}", messageBody)
                null
            }
        }
    }

    /**
     * Transform Standard Order → WIP status
     */
    private fun transformStandardOrder(messageBody: String): String? {
        return try {
            val standardOrder = objectMapper.readValue(messageBody, StandardOrderMessage::class.java)

            val wipOrder = WipOrderMessage(
                orderId = standardOrder.orderId,
                itemsCount = standardOrder.items.size,
                status = "WIP",
                processingStartedAt = Instant.now().toString()
            )

            logger.info("Processing STANDARD order: ${standardOrder.orderId} with ${standardOrder.items.size} items")

            objectMapper.writeValueAsString(wipOrder)
        } catch (e: Exception) {
            logger.error("Error transforming standard order: ${e.message}. Message will not be forwarded.", e)
            null
        }
    }

    /**
     * Transform Priority Order → DELIVERED status
     */
    private fun transformPriorityOrder(messageBody: String): String? {
        return try {
            val priorityOrder = objectMapper.readValue(messageBody, PriorityOrderMessage::class.java)

            val deliveredOrder = DeliveredOrderMessage(
                orderId = priorityOrder.orderId,
                itemsCount = priorityOrder.items.size,
                status = "DELIVERED",
                deliveredAt = Instant.now().toString(),
                deliveryLocation = "Delivery location from logistics system" // Placeholder
            )

            logger.info("Processing PRIORITY order: ${priorityOrder.orderId} with priority level: ${priorityOrder.priorityLevel}")

            objectMapper.writeValueAsString(deliveredOrder)
        } catch (e: Exception) {
            logger.error("Error transforming priority order: ${e.message}. Message will not be forwarded.", e)
            null
        }
    }

    /**
     * Transform Bulk Order → COMPLETED status
     */
    private fun transformBulkOrder(messageBody: String): String? {
        return try {
            val bulkOrder = objectMapper.readValue(messageBody, BulkOrderMessage::class.java)

            // Calculate total items across all orders in the batch
            val totalItemsCount = bulkOrder.orders.sumOf { order -> order.items.size }

            val completedOrder = CompletedOrderMessage(
                batchId = bulkOrder.batchId,
                itemsCount = totalItemsCount,
                status = "COMPLETED",
                completedAt = Instant.now().toString(),
                customerConfirmation = true
            )

            logger.info("Processing BULK order batch: ${bulkOrder.batchId} - Total orders: ${bulkOrder.orders.size} - Total items: $totalItemsCount")

            objectMapper.writeValueAsString(completedOrder)
        } catch (e: Exception) {
            logger.error("Error transforming bulk order: ${e.message}. Message will not be forwarded.", e)
            null
        }
    }

    /**
     * Extract message key for Kafka partitioning
     */
    fun extractMessageKey(messageBody: String, messageType: OrderMessageType): String {
        return try {
            val jsonNode = objectMapper.readTree(messageBody)
            when (messageType) {
                OrderMessageType.BULK -> jsonNode.get("batchId")?.asText() ?: "unknown"
                else -> jsonNode.get("orderId")?.asText() ?: "unknown"
            }
        } catch (e: Exception) {
            logger.warn("Could not extract message key: ${e.message}")
            "unknown"
        }
    }
}

