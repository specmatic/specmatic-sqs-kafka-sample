#!/bin/bash

QUEUE_URL="${1:-http://localhost:4566/000000000000/place-order-queue}"
ENDPOINT="${ENDPOINT:-http://localhost:4566}"
REGION="${REGION:-us-east-1}"

export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test

echo "======================================"
echo "SQS Message Consumer"
echo "======================================"
echo "Queue URL: $QUEUE_URL"
echo "Press Ctrl+C to stop"
echo "======================================"
echo ""

while true; do
  response=$(aws --endpoint-url="$ENDPOINT" --region "$REGION" sqs receive-message \
    --queue-url "$QUEUE_URL" \
    --wait-time-seconds 10 \
    --max-number-of-messages 10 \
    --output json 2>/dev/null)

  if [ -n "$response" ] && [ "$response" != "null" ]; then
    messages=$(echo "$response" | jq -r '.Messages // empty')
    if [ -n "$messages" ]; then
      echo "$messages" | jq -c '.[]' | while read -r msg; do
        echo "---"
        echo "Message ID: $(echo "$msg" | jq -r '.MessageId')"
        echo "Body: $(echo "$msg" | jq -r '.Body')"
        echo "---"

        receipt_handle=$(echo "$msg" | jq -r '.ReceiptHandle')
        aws --endpoint-url="$ENDPOINT" --region "$REGION" sqs delete-message \
          --queue-url "$QUEUE_URL" \
          --receipt-handle "$receipt_handle" 2>/dev/null
      done
    fi
  fi
done
