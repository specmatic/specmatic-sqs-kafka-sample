#!/bin/bash

echo "======================================"
echo "Starting SQS to Kafka Bridge Setup"
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
echo "🚀 Starting LocalStack (SQS), Kafka, and Zookeeper..."
docker compose up -d

echo ""
echo "⏳ Waiting for services to be ready (30 seconds)..."
sleep 30

# Check service health
echo ""
echo "🔍 Checking service health..."

# Check LocalStack
if curl -s http://localhost:4566/_localstack/health > /dev/null; then
    echo "✅ LocalStack (SQS) is ready"
else
    echo "⚠️  LocalStack might still be starting up"
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
echo ""
echo "To stop all services:        docker compose down"
echo "======================================"

