package io.specmatic.async.model

// Input Message Types (from SQS)

data class OrderItem(
    val productId: String,
    val quantity: Int,
    val price: Double
)

data class StandardOrderMessage(
    val orderId: String,
    val customerId: String,
    val items: List<OrderItem>,
    val totalAmount: Double,
    val orderDate: String
)

data class PriorityOrderMessage(
    val orderId: String,
    val customerId: String,
    val items: List<OrderItem>,
    val totalAmount: Double,
    val orderDate: String,
    val priorityLevel: String,
    val expectedDeliveryDate: String
)

data class BulkOrderItem(
    val orderId: String,
    val items: List<OrderItem>,
    val totalAmount: Double
)

data class BulkOrderMessage(
    val batchId: String,
    val customerId: String,
    val orders: List<BulkOrderItem>,
    val totalOrderCount: Int,
    val batchTotalAmount: Double,
    val orderDate: String
)

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
    BULK,
    UNKNOWN
}

