#!/bin/bash

# This script initializes LocalStack with required resources
# It will be automatically executed when LocalStack is ready

echo "Initializing LocalStack resources..."

# Create SQS queue
awslocal sqs create-queue --queue-name test-queue

# Get queue URL
QUEUE_URL=$(awslocal sqs get-queue-url --queue-name test-queue --query 'QueueUrl' --output text)

echo "SQS Queue created: $QUEUE_URL"
echo "LocalStack initialization complete!"

