#!/bin/bash

# This script initializes LocalStack with required resources
# It will be automatically executed when LocalStack is ready

echo "Initializing LocalStack resources..."

# Keep messages hidden long enough that earlier test examples do not
# reappear during the same Specmatic suite run after being received once.
awslocal sqs create-queue \
  --queue-name place-order-queue \
  --attributes VisibilityTimeout=300

# Get queue URL
QUEUE_URL=$(awslocal sqs get-queue-url --queue-name place-order-queue --query 'QueueUrl' --output text)

echo "Main SQS Queue created: $QUEUE_URL"
echo "LocalStack initialization complete!"
