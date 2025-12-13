package io.specmatic.async.model

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

// Input Message Types (from SQS)

data class OrderItem(
    val productId: String,
    val quantity: Int,
    val price: Double
)

/**
 * Base interface for polymorphic order messages using Jackson's type discrimination
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "orderType"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = StandardOrderMessage::class, name = "STANDARD"),
    JsonSubTypes.Type(value = PriorityOrderMessage::class, name = "PRIORITY"),
    JsonSubTypes.Type(value = BulkOrderMessage::class, name = "BULK")
)
sealed interface OrderMessage {
    val orderType: String
}

data class StandardOrderMessage(
    override val orderType: String = "STANDARD",
    val orderId: String,
    val customerId: String,
    val items: List<OrderItem>,
    val totalAmount: Double,
    val orderDate: String
) : OrderMessage

data class PriorityOrderMessage(
    override val orderType: String = "PRIORITY",
    val orderId: String,
    val customerId: String,
    val items: List<OrderItem>,
    val totalAmount: Double,
    val orderDate: String,
    val priorityLevel: String,
    val expectedDeliveryDate: String
) : OrderMessage

data class BulkOrderItem(
    val orderId: String,
    val items: List<OrderItem>,
    val totalAmount: Double
)

data class BulkOrderMessage(
    override val orderType: String = "BULK",
    val batchId: String,
    val customerId: String,
    val orders: List<BulkOrderItem>,
    val totalOrderCount: Int,
    val batchTotalAmount: Double,
    val orderDate: String
) : OrderMessage

// Output Message Types (to Kafka)

data class WipOrderMessage(
    val orderId: String,
    val itemsCount: Int,
    val status: String = "WIP",
    val processingStartedAt: String
)

data class DeliveredOrderMessage(
    val orderId: String,
    val itemsCount: Int,
    val status: String = "DELIVERED",
    val deliveredAt: String,
    val deliveryLocation: String
)

data class CompletedOrderMessage(
    val batchId: String,
    val itemsCount: Int,
    val status: String = "COMPLETED",
    val completedAt: String,
    val customerConfirmation: Boolean
)

// Enum for message types
enum class OrderMessageType {
    STANDARD,
    PRIORITY,
    BULK
}
