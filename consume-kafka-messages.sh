#!/bin/bash

# Script to consume and display messages from Kafka topic

TOPIC="${1:-test-topic}"

echo "======================================"
echo "Kafka Message Consumer"
echo "======================================"
echo "Topic: $TOPIC"
echo "Press Ctrl+C to stop"
echo "======================================"
echo ""

docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9093 \
  --topic $TOPIC \
  --from-beginning \
  --property print.timestamp=true \
  --property print.key=true \
  --property print.value=true

