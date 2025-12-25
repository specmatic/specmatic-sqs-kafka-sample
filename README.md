# SQS to Kafka Bridge

A Kotlin application that consumes order messages from AWS SQS and publishes transformed messages to Apache Kafka.

## Overview

This service bridges SQS and Kafka by:
- Polling messages from an SQS queue (long polling)
- Transforming order messages based on type (Standard → WIP, Priority → DELIVERED, Bulk → COMPLETED)
- Publishing to Kafka topic
- Deleting successfully processed messages from SQS

### Architecture

![SQS to Kafka Architecture](assets/SpecmaticSQS.png)

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
 
## Contract Testing with Specmatic Programmatically using TestContainers

### 1. Run the Contract Test

```bash
./gradlew clean test
```

This approach uses JUnit tests with TestContainers to programmatically start infrastructure, run the application, and execute Specmatic tests.

### 2. View Test Reports

After running tests, reports are saved in:
```
build/reports/specmatic/
├── html/index.html        # HTML report
└── ctrf/ctrf-report.json  # CTRF JSON report
```

## Contract Testing with Specmatic using Script

### Run the Tests

## Simply execute the provided script:

```bash
./run-contract-tests.sh
```

## Manual Cleanup (if needed)

If the script is interrupted and containers are still running:

```bash
docker-compose -f docker-compose-test.yml --profile test down -v
```

### CI/CD Integration

The script returns appropriate exit codes:
- `0` - All tests passed
- `1` - Tests failed or error occurred

Example CI/CD usage:

Add this in your `.github/workflows/test.yml`
```yaml
- name: Run Contract Tests
  run: ./run-contract-tests.sh
```

### View Test Reports

After running tests, reports are saved in:
```
build/reports/specmatic/
├── html/index.html        # HTML report
└── ctrf/ctrf-report.json  # CTRF JSON report
```

## Contract Testing with Specmatic Manually

### 1. Start Infrastructure

```bash
./start-infrastructure.sh
```

### 2. Run the application

```bash
./gradlew run
```

### 3. Run contract tests using Specmatic

```bash
docker run --rm --network host -v "$PWD/specmatic.yaml:/usr/src/app/specmatic.yaml" -v "$PWD/spec:/usr/src/app/spec" -v "$PWD/build/reports/specmatic:/usr/src/app/build/reports/specmatic" specmatic/specmatic-async-core test
```

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

## Testing the Application Manually

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

Standard Order (WIP)
```json
{"orderId": "ORD-90001", "itemsCount": 2, "status": "WIP", "processingStartedAt": "..."}
```
Priority Order (DELIVERED)
```json
{"orderId": "ORD-PRIORITY-90002", "itemsCount": 2, "status": "DELIVERED", "deliveredAt": "...", "deliveryLocation": "..."}
```
Bulk Order (COMPLETED)
```json
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