#!/bin/bash

# Script to send test order messages matching the AsyncAPI spec

ENDPOINT="http://localhost:4566"
QUEUE_URL="http://localhost:4566/000000000000/test-queue"
REGION="us-east-1"

# Set fake credentials for LocalStack (to suppress warnings)
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test

echo "======================================"
echo "Sending Test Order Messages to SQS"
echo "======================================"
echo ""

# Test message 1 - STANDARD Order (should become WIP)
echo "1. Sending STANDARD order..."
aws --endpoint-url=$ENDPOINT --region $REGION sqs send-message \
  --queue-url $QUEUE_URL \
  --message-body '{
    "orderId": "ORD-90001",
    "customerId": "CUST-44556",
    "items": [
      {
        "productId": "PROD-111",
        "quantity": 1,
        "price": 899.99
      },
      {
        "productId": "PROD-222",
        "quantity": 2,
        "price": 129.50
      }
    ],
    "totalAmount": 1158.99,
    "orderDate": "2025-12-09T14:20:00Z"
  }'

echo "✅ STANDARD order sent (should transform to WIP status)"
echo ""

# Test message 2 - PRIORITY Order (should become DELIVERED)
echo "2. Sending PRIORITY order..."
aws --endpoint-url=$ENDPOINT --region $REGION sqs send-message \
  --queue-url $QUEUE_URL \
  --message-body '{
    "orderId": "ORD-PRIORITY-90002",
    "customerId": "CUST-77889",
    "items": [
      {
        "productId": "PROD-555",
        "quantity": 3,
        "price": 249.99
      },
      {
        "productId": "PROD-666",
        "quantity": 1,
        "price": 599.00
      }
    ],
    "totalAmount": 1348.97,
    "orderDate": "2025-12-09T08:00:00Z",
    "priorityLevel": "URGENT",
    "expectedDeliveryDate": "2025-12-09T20:00:00Z"
  }'

echo "✅ PRIORITY order sent (should transform to DELIVERED status)"
echo ""

# Test message 3 - BULK Order (should become COMPLETED)
echo "3. Sending BULK order..."
aws --endpoint-url=$ENDPOINT --region $REGION sqs send-message \
  --queue-url $QUEUE_URL \
  --message-body '{
    "batchId": "BATCH-90003",
    "customerId": "CUST-99001",
    "orders": [
      {
        "orderId": "ORD-BULK-001",
        "items": [
          {
            "productId": "PROD-333",
            "quantity": 10,
            "price": 45.50
          }
        ],
        "totalAmount": 455.00
      },
      {
        "orderId": "ORD-BULK-002",
        "items": [
          {
            "productId": "PROD-444",
            "quantity": 5,
            "price": 89.99
          },
          {
            "productId": "PROD-555",
            "quantity": 3,
            "price": 249.99
          }
        ],
        "totalAmount": 1199.92
      },
      {
        "orderId": "ORD-BULK-003",
        "items": [
          {
            "productId": "PROD-666",
            "quantity": 20,
            "price": 12.75
          }
        ],
        "totalAmount": 255.00
      }
    ],
    "totalOrderCount": 3,
    "batchTotalAmount": 1909.92,
    "orderDate": "2025-12-07T12:00:00Z"
  }'

echo "✅ BULK order sent (should transform to COMPLETED status)"
echo ""

# Test message 4 - INVALID message (should be rejected)
echo "4. Sending INVALID message (should be rejected)..."
aws --endpoint-url=$ENDPOINT --region $REGION sqs send-message \
  --queue-url $QUEUE_URL \
  --message-body '{
    "invalidField": "This does not match any schema",
    "random": "data"
  }'

echo "⚠️  INVALID message sent (should NOT be forwarded to Kafka)"
echo ""

echo "======================================"
echo "All test messages sent successfully!"
echo "======================================"
echo ""
echo "Expected transformations:"
echo "  1. STANDARD order → WIP status (2 items)"
echo "  2. PRIORITY order → DELIVERED status (2 items)"
echo "  3. BULK order → COMPLETED status (3 items total)"
echo "  4. INVALID message → NOT forwarded"
echo ""
echo "Check your application logs to see processing details."
echo "View Kafka messages at: http://localhost:8080"
echo ""

