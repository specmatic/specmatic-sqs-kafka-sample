#!/bin/bash

# Test Retry and DLQ Flow
# This script demonstrates:
# 1. Normal message processing (success)
# 2. Retry scenario (fails initially, succeeds after retry)
# 3. DLQ scenario (fails all retries, goes to DLQ)

set -e

echo "======================================================================"
echo "Testing Retry and DLQ Flow"
echo "======================================================================"

# Configuration
SQS_QUEUE_URL="http://localhost:4566/000000000000/place-order-queue"
KAFKA_TOPIC="place-order-topic"
RETRY_TOPIC="place-order-retry-topic"
DLQ_TOPIC="place-order-dlq-topic"
AWS_ENDPOINT="http://localhost:4566"

echo ""
echo "Test 1: Normal Message Processing (Success)"
echo "----------------------------------------------------------------------"
echo "Sending a standard order that should process successfully..."

aws --endpoint-url=$AWS_ENDPOINT sqs send-message \
    --queue-url $SQS_QUEUE_URL \
    --message-body '{
        "orderType": "STANDARD",
        "orderId": "ORD-SUCCESS-001",
        "customerId": "CUST-001",
        "items": [
            {
                "productId": "PROD-111",
                "quantity": 2,
                "price": 29.99
            }
        ],
        "totalAmount": 59.98,
        "orderDate": "2026-01-19T10:00:00Z"
    }' \
    --message-attributes '{"MessageGroupId":{"StringValue":"test-group","DataType":"String"}}' \
    > /dev/null

echo "✓ Standard order sent to SQS"
echo "  Expected: Message should be processed and appear in '$KAFKA_TOPIC'"
echo ""

sleep 3

echo ""
echo "Test 2: Retry Scenario (Temporary Failure)"
echo "----------------------------------------------------------------------"
echo "Sending an order that will fail initially but succeed after retry..."

aws --endpoint-url=$AWS_ENDPOINT sqs send-message \
    --queue-url $SQS_QUEUE_URL \
    --message-body '{
        "orderType": "STANDARD",
        "orderId": "ORD-RETRY-90001",
        "customerId": "CUST-RETRY-001",
        "items": [
            {
                "productId": "PROD-RETRY-111",
                "quantity": 1,
                "price": 99.99
            }
        ],
        "totalAmount": 99.99,
        "orderDate": "2026-01-19T10:00:00Z"
    }' \
    --message-attributes '{"MessageGroupId":{"StringValue":"retry-test-group","DataType":"String"}}' \
    > /dev/null

echo "✓ Retry test order sent to SQS"
echo "  Expected flow:"
echo "    1. Initial processing fails → message sent to '$RETRY_TOPIC'"
echo "    2. Retry consumer picks it up after delay"
echo "    3. If simulated failure is removed, retry succeeds"
echo "    4. Transformed message appears in '$KAFKA_TOPIC'"
echo ""

sleep 3

echo ""
echo "Test 3: DLQ Scenario (Permanent Failure)"
echo "----------------------------------------------------------------------"
echo "Sending an order that will fail all retry attempts and go to DLQ..."

aws --endpoint-url=$AWS_ENDPOINT sqs send-message \
    --queue-url $SQS_QUEUE_URL \
    --message-body '{
        "orderType": "PRIORITY",
        "orderId": "ORD-DLQ-90001",
        "customerId": "CUST-DLQ-001",
        "items": [
            {
                "productId": "PROD-DLQ-555",
                "quantity": 2,
                "price": 199.99
            }
        ],
        "totalAmount": 399.98,
        "orderDate": "2026-01-19T11:00:00Z",
        "priorityLevel": "HIGH",
        "expectedDeliveryDate": "2026-01-20T18:00:00Z"
    }' \
    --message-attributes '{"MessageGroupId":{"StringValue":"dlq-test-group","DataType":"String"},"Priority":{"StringValue":"HIGH","DataType":"String"}}' \
    > /dev/null

echo "✓ DLQ test order sent to SQS"
echo "  Expected flow:"
echo "    1. Initial processing fails → message sent to '$RETRY_TOPIC'"
echo "    2. Retry attempt 1 fails → back to '$RETRY_TOPIC' (retry count: 1)"
echo "    3. Retry attempt 2 fails → back to '$RETRY_TOPIC' (retry count: 2)"
echo "    4. Retry attempt 3 fails → max retries reached"
echo "    5. Message sent to '$DLQ_TOPIC' with error details"
echo ""

echo ""
echo "======================================================================"
echo "Test Messages Sent Successfully"
echo "======================================================================"
echo ""
echo "To verify the flow:"
echo "  1. Check application logs for processing details"
echo "  2. Consume from Kafka topics to see messages:"
echo ""
echo "     # Main topic (successful messages)"
echo "     docker exec -it \$(docker ps -qf 'name=kafka') \\"
echo "       kafka-console-consumer --bootstrap-server localhost:9093 \\"
echo "       --topic $KAFKA_TOPIC --from-beginning"
echo ""
echo "     # Retry topic (messages being retried)"
echo "     docker exec -it \$(docker ps -qf 'name=kafka') \\"
echo "       kafka-console-consumer --bootstrap-server localhost:9093 \\"
echo "       --topic $RETRY_TOPIC --from-beginning"
echo ""
echo "     # DLQ topic (permanently failed messages)"
echo "     docker exec -it \$(docker ps -qf 'name=kafka') \\"
echo "       kafka-console-consumer --bootstrap-server localhost:9093 \\"
echo "       --topic $DLQ_TOPIC --from-beginning"
echo ""
echo "======================================================================"

