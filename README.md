# SQS to Kafka Bridge

A Kotlin application that consumes order messages from AWS SQS and publishes transformed messages to Apache Kafka.

## Overview

This service bridges SQS and Kafka by:
- Polling messages from an SQS queue (long polling)
- Transforming order messages based on type (Standard → WIP, Priority → DELIVERED, Bulk → COMPLETED)
- Publishing to Kafka topic
- Deleting successfully processed messages from SQS

### Architecture

```
┌──────────────┐         ┌────────────────────┐         ┌──────────────┐
│  SQS Queue   │ ──────> │  Kotlin Bridge App │ ──────> │ Kafka Topic  │
│ (LocalStack) │  Poll   │   - Type Detection │ Publish │ place-order  │
│              │         │   - Transform      │         │    -topic    │
└──────────────┘         └────────────────────┘         └──────────────┘
```

**Message Flow:**
1. Standard Order → WIP status (with itemsCount, processingStartedAt)
2. Priority Order → DELIVERED status (with itemsCount, deliveredAt, deliveryLocation)
3. Bulk Order → COMPLETED status (with total itemsCount, completedAt, customerConfirmation)
4. Invalid messages → Logged and NOT forwarded

## Prerequisites

- Docker and Docker Compose
- Java 17+
- Gradle (wrapper included)
- AWS CLI (optional, for manual testing)

## Running the Application

### 1. Start Infrastructure

```bash
./start-infrastructure.sh
```

This starts LocalStack (SQS), Kafka, Zookeeper, and Kafka UI.

### 2. Run the application

```bash
./gradlew run
```

You should see:
```
============================================================
SQS to Kafka Bridge Application
============================================================
Starting SQS to Kafka bridge...
```

## Testing the Application

### Send Test Messages

Use the provided script to send all order types:

```bash
./send-test-message.sh
```

This sends:
- Standard order → transforms to WIP
- Priority order → transforms to DELIVERED  
- Bulk order → transforms to COMPLETED
- Invalid message → rejected (not forwarded)

### View Results in Kafka UI

1. Open http://localhost:8080
2. Navigate to **Topics** → **place-order-topic** → **Messages**
3. You'll see the transformed messages

**Expected Kafka messages:**

```json
// Standard Order (WIP)
{"orderId": "ORD-90001", "itemsCount": 2, "status": "WIP", "processingStartedAt": "..."}

// Priority Order (DELIVERED)
{"orderId": "ORD-PRIORITY-90002", "itemsCount": 2, "status": "DELIVERED", "deliveredAt": "...", "deliveryLocation": "..."}

// Bulk Order (COMPLETED)
{"batchId": "BATCH-90003", "itemsCount": 3, "status": "COMPLETED", "completedAt": "...", "customerConfirmation": true}
```

**Application logs** will show:
```
Detected STANDARD order message
Processing STANDARD order: ORD-90001 with 2 items
Message sent to Kafka - Topic: place-order-topic, Partition: 0, Offset: 0
```

### Stop Everything

```bash
docker-compose down
```

## Contract Testing with Specmatic

### 1. Start Infrastructure

```bash
./start-infrastructure.sh
```

### 2. Run the application

```bash
./gradlew run
```

### 3. Run contract tests using Sepcmatic

```bash
specmatic-sqs-kafka test --kafka-server localhost:9092 --sqs-server http://localhost:4566/000000000000 --aws-region us-east-1 --aws-access-key-id test --aws-secret-access-key test 
```

