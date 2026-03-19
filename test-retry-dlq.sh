#!/bin/bash

set -e

KAFKA_BROKER="${KAFKA_BROKER:-localhost:9092}"
TOPIC="${TOPIC:-place-order-topic}"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-kafka}"
RETRY_TOPIC="${RETRY_TOPIC:-place-order-retry-topic}"
DLQ_TOPIC="${DLQ_TOPIC:-place-order-dlq-topic}"

echo "======================================================================"
echo "Sending Retry and DLQ Scenario Messages to Kafka"
echo "======================================================================"
echo ""

echo "1. Sending a standard order that should succeed immediately..."
echo '{"orderType":"STANDARD","orderId":"ORD-SUCCESS-001","customerId":"CUST-001","items":[{"productId":"PROD-111","quantity":2,"price":29.99}],"totalAmount":59.98,"orderDate":"2026-01-19T10:00:00Z"}' | docker exec -i "$KAFKA_CONTAINER" kafka-console-producer --bootstrap-server "$KAFKA_BROKER" --topic "$TOPIC"
echo "✓ Success-path message sent"
echo ""

echo "2. Sending an order that should fail once and then succeed from the retry topic..."
echo '{"orderType":"STANDARD","orderId":"ORD-RETRY-90001","customerId":"CUST-RETRY-001","items":[{"productId":"PROD-RETRY-111","quantity":1,"price":99.99}],"totalAmount":99.99,"orderDate":"2026-01-19T10:00:00Z"}' | docker exec -i "$KAFKA_CONTAINER" kafka-console-producer --bootstrap-server "$KAFKA_BROKER" --topic "$TOPIC"
echo "✓ Retry-scenario message sent"
echo ""

echo "3. Sending an order that should exhaust retries and land in the DLQ topic..."
echo '{"orderType":"PRIORITY","orderId":"ORD-DLQ-90001","customerId":"CUST-DLQ-001","items":[{"productId":"PROD-DLQ-555","quantity":2,"price":199.99}],"totalAmount":399.98,"orderDate":"2026-01-19T11:00:00Z","priorityLevel":"HIGH","expectedDeliveryDate":"2026-01-20T18:00:00Z"}' | docker exec -i "$KAFKA_CONTAINER" kafka-console-producer --bootstrap-server "$KAFKA_BROKER" --topic "$TOPIC"
echo "✓ DLQ-scenario message sent"
echo ""

echo "Expected flow:"
echo "  - Success message -> SQS queue"
echo "  - Retry message -> $RETRY_TOPIC -> SQS queue"
echo "  - DLQ message -> $RETRY_TOPIC -> $DLQ_TOPIC"
