#!/bin/bash

echo "======================================"
echo "Starting Kafka to SQS Bridge Setup"
echo "======================================"
echo ""

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Error: Docker is not running. Please start Docker and try again."
    exit 1
fi

echo "✅ Docker is running"
echo ""

# Start Docker Compose services
echo "🚀 Starting LocalStack (SQS), Kafka, topic bootstrap, and Kafka UI..."
docker compose up -d localstack kafka kafka-init kafka-ui

echo ""
echo "⏳ Waiting for services to be ready (20 seconds)..."
sleep 20

# Check service health
echo ""
echo "🔍 Checking service health..."

# Check LocalStack
if curl -s http://localhost:4566/_localstack/health > /dev/null; then
    echo "✅ LocalStack (SQS) is ready"
else
    echo "⚠️  LocalStack might still be starting up"
fi

# Check Kafka
if docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list > /dev/null 2>&1; then
    echo "✅ Kafka is ready"
else
    echo "⚠️  Kafka might still be starting up"
fi

# Check Kafka UI
if curl -s http://localhost:8080 > /dev/null; then
    echo "✅ Kafka UI is ready"
else
    echo "⚠️  Kafka UI might still be starting up"
fi

echo ""
echo "======================================"
echo "✅ Infrastructure is ready!"
echo "======================================"
echo ""
echo "Services running:"
echo "  - LocalStack (SQS):     http://localhost:4566"
echo "  - Kafka:                localhost:9092"
echo "  - Kafka UI:             http://localhost:8080"
echo ""
echo "Next steps:"
echo "  1. Run the application:    ./gradlew run"
echo "  2. Send test messages:     ./send-test-message.sh"
echo "  3. Read queue output:      ./consume-sqs-messages.sh"
echo ""
echo "To stop all services:        docker compose down"
echo "======================================"
