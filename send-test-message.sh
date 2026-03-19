#!/bin/bash

set -e

KAFKA_BROKER="${KAFKA_BROKER:-localhost:9092}"
TOPIC="${TOPIC:-place-order-topic}"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-kafka}"
SQS_QUEUE_URL="${SQS_QUEUE_URL:-http://localhost:4566/000000000000/place-order-queue}"

echo "======================================"
echo "Sending Test Order Messages to Kafka"
echo "======================================"
echo ""

echo "1. Sending STANDARD order..."
echo '{"orderType":"STANDARD","orderId":"ORD-90001","customerId":"CUST-44556","items":[{"productId":"PROD-111","quantity":1,"price":899.99},{"productId":"PROD-222","quantity":2,"price":129.50}],"totalAmount":1158.99,"orderDate":"2025-12-09T14:20:00Z"}' | docker exec -i "$KAFKA_CONTAINER" kafka-console-producer --bootstrap-server "$KAFKA_BROKER" --topic "$TOPIC"
echo "✓ STANDARD order sent (expected SQS status: WIP)"
echo ""

echo "2. Sending PRIORITY order..."
echo '{"orderType":"PRIORITY","orderId":"ORD-PRIORITY-90002","customerId":"CUST-77889","items":[{"productId":"PROD-555","quantity":3,"price":249.99},{"productId":"PROD-666","quantity":1,"price":599.00}],"totalAmount":1348.97,"orderDate":"2025-12-09T08:00:00Z","priorityLevel":"URGENT","expectedDeliveryDate":"2025-12-09T20:00:00Z"}' | docker exec -i "$KAFKA_CONTAINER" kafka-console-producer --bootstrap-server "$KAFKA_BROKER" --topic "$TOPIC"
echo "✓ PRIORITY order sent (expected SQS status: DELIVERED)"
echo ""

echo "3. Sending BULK order..."
echo '{"orderType":"BULK","batchId":"BATCH-90003","customerId":"CUST-99001","orders":[{"orderId":"ORD-BULK-001","items":[{"productId":"PROD-333","quantity":10,"price":45.50}],"totalAmount":455.00},{"orderId":"ORD-BULK-002","items":[{"productId":"PROD-444","quantity":5,"price":89.99},{"productId":"PROD-555","quantity":3,"price":249.99}],"totalAmount":1199.92},{"orderId":"ORD-BULK-003","items":[{"productId":"PROD-666","quantity":20,"price":12.75}],"totalAmount":255.00}],"totalOrderCount":3,"batchTotalAmount":1909.92,"orderDate":"2025-12-07T12:00:00Z"}' | docker exec -i "$KAFKA_CONTAINER" kafka-console-producer --bootstrap-server "$KAFKA_BROKER" --topic "$TOPIC"
echo "✓ BULK order sent (expected SQS status: COMPLETED)"
echo ""

echo "======================================"
echo "All test messages sent"
echo "======================================"
echo ""
echo "Read transformed queue messages with:"
echo "  ./consume-sqs-messages.sh \"$SQS_QUEUE_URL\""
