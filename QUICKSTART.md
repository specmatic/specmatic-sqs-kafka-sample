# Quick Start Guide

This guide will help you get the SQS to Kafka bridge application up and running in just a few minutes.

## Prerequisites

- ✅ Docker Desktop installed and running
- ✅ Java 17 or higher
- ✅ AWS CLI (optional, for sending test messages)

## Step-by-Step Instructions

### Option 1: Using the automated script (Recommended)

```bash
# 1. Start all infrastructure services (LocalStack SQS + Kafka)
./start-infrastructure.sh

# 2. Build the application
./gradlew build

# 3. Run the application (in a new terminal)
./gradlew run

# 4. Send test messages (in another terminal)
./send-test-message.sh
```

### Option 2: Using Make commands (if you have make installed)

```bash
# Start infrastructure and build
make all

# Run the application (in a new terminal)
make run

# Send test messages (in another terminal)
make send-test

# View all available commands
make help
```

### Option 3: Manual step-by-step

#### 1. Start Infrastructure

```bash
docker compose up -d
```

Wait about 30 seconds for all services to start.

#### 2. Verify Services are Running

```bash
docker compose ps
```

You should see:
- localstack (port 4566)
- kafka (port 9092)
- zookeeper (port 2181)
- kafka-ui (port 8080)

#### 3. Build the Application

```bash
./gradlew build
```

#### 4. Run the Application

```bash
./gradlew run
```

You should see output like:
```
============================================================
SQS to Kafka Bridge Application
============================================================
Configuration:
  SQS Queue URL: http://localhost:4566/000000000000/place-order-queue
  SQS Endpoint: http://localhost:4566
  Kafka Topic: place-order-topic
  Kafka Bootstrap Servers: localhost:9092
============================================================
Starting SQS to Kafka bridge...
```

#### 5. Send Test Messages

In a new terminal window:

**Using the test script:**
```bash
./send-test-message.sh
```

**Or manually using AWS CLI:**
```bash
aws --endpoint-url=http://localhost:4566 --region us-east-1 sqs send-message \
  --queue-url http://localhost:4566/000000000000/place-order-queue \
  --message-body '{"orderId": "12345", "product": "Widget", "quantity": 10}'
```

#### 6. Verify Messages in Kafka

**Option A: Using Kafka UI (easiest)**

Open your browser and go to: http://localhost:8080

Navigate to Topics → place-order-topic → Messages

**Option B: Using Kafka CLI tools**

```bash
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9093 \
  --topic place-order-topic \
  --from-beginning
```

## Expected Behavior

When you send a message to SQS, you should see:

1. **In the application logs:**
   ```
   Received 1 message(s) from SQS
   Processing message: {"orderId": "12345", "product": "Widget", "quantity": 10}
   Message sent to Kafka - Topic: place-order-topic, Partition: 0, Offset: 0
   Successfully processed and forwarded message to Kafka
   ```

2. **In Kafka (via UI or console consumer):**
   ```json
   {
     "originalMessage": {"orderId": "12345", "product": "Widget", "quantity": 10},
     "processedAt": 1702345678901,
     "source": "sqs-to-kafka-bridge"
   }
   ```

## Stopping Everything

### Stop the Application
Press `Ctrl+C` in the terminal where the application is running.

### Stop Infrastructure
```bash
docker compose down
```

Or with make:
```bash
make stop
```

## Troubleshooting

### "Docker is not running"
Start Docker Desktop and wait for it to be fully running.

### "Cannot connect to SQS"
```bash
# Check LocalStack status
curl http://localhost:4566/_localstack/health

# View LocalStack logs
docker compose logs localstack
```

### "Cannot connect to Kafka"
```bash
# Check Kafka status
docker compose logs kafka

# Verify Kafka is listening
docker exec -it kafka kafka-broker-api-versions --bootstrap-server localhost:9093
```

### "AWS CLI not found"
Install AWS CLI:
- **macOS**: `brew install awscli`
- **Linux**: `sudo apt-get install awscli` or use pip
- **Windows**: Download from AWS website

**Note:** When using AWS CLI with LocalStack, always include `--region us-east-1` (or any AWS region) along with `--endpoint-url=http://localhost:4566`

### Services won't start
```bash
# Check what's using the ports
lsof -i :4566  # LocalStack
lsof -i :9092  # Kafka
lsof -i :2181  # Zookeeper

# Kill any conflicting processes or change ports in docker-compose.yml
```

## Next Steps

- Modify `SqsToKafkaBridge.kt` to add custom message processing logic
- Add error handling and retry logic
- Set up monitoring and metrics
- Deploy to production environment

## Useful Commands

```bash
# View all Docker service logs
docker compose logs -f

# View specific service logs
docker compose logs -f kafka
docker compose logs -f localstack

# Restart a service
docker compose restart kafka

# Check service status
docker compose ps

# Remove all containers and volumes
docker compose down -v
```

## Support

For issues or questions, refer to:
- [LocalStack Documentation](https://docs.localstack.cloud/)
- [Kafka Documentation](https://kafka.apache.org/documentation/)
- Project README.md

