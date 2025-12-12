# SQS to Kafka Bridge Application

This application bridges AWS SQS and Apache Kafka by polling messages from an SQS queue and forwarding them to a Kafka topic.

## Features

- Polls messages from SQS queue using long polling
- Processes and transforms messages
- Forwards messages to Kafka topic
- Automatic message deletion from SQS after successful processing
- Graceful shutdown handling
- Comprehensive logging

## Architecture

```
┌─────────────┐         ┌──────────────────┐         ┌─────────────┐
│   SQS Queue │ ───────>│  Bridge Service  │ ───────>│ Kafka Topic │
│ (LocalStack)│         │  (Kotlin App)    │         │ (test-topic)│
└─────────────┘         └──────────────────┘         └─────────────┘
                                 │
                                 │ Transform & Enrich
                                 ▼
                        {
                           "originalMessage": {...},
                           "processedAt": 1702345678901,
                           "source": "sqs-to-kafka-bridge"
                        }
```

## Prerequisites

- Docker and Docker Compose
- Java 17 or higher
- Gradle (wrapper included)

## Quick Start

### 1. Start Dependencies (LocalStack SQS + Kafka)

```bash
docker compose up -d
```

This will start:
- **LocalStack** (SQS) on port 4566
- **Kafka** on port 9092
- **Zookeeper** on port 2181
- **Kafka UI** on port 8080 (optional, for visualization)

Wait a few seconds for services to be ready.

### 2. Verify Services

Check if all services are running:
```bash
docker compose ps
```

### 3. Build the Application

```bash
./gradlew build
```

### 4. Run the Application

```bash
./gradlew run
```

Or with custom configuration:
```bash
export SQS_QUEUE_URL="http://localhost:4566/000000000000/test-queue"
export KAFKA_TOPIC="test-topic"
export SQS_ENDPOINT="http://localhost:4566"
export KAFKA_BOOTSTRAP_SERVERS="localhost:9092"
./gradlew run
```

## Testing the Application

### Send a Test Message to SQS

Using AWS CLI with LocalStack:

```bash
# Install AWS CLI if not already installed
# For macOS: brew install awscli

# Send a message
aws --endpoint-url=http://localhost:4566 --region us-east-1 sqs send-message \
  --queue-url http://localhost:4566/000000000000/test-queue \
  --message-body '{"orderId": "12345", "product": "Widget", "quantity": 10}'
```

Or using the provided test script:

```bash
./send-test-message.sh
```

### Verify Message in Kafka

You can use the Kafka UI at http://localhost:8080 or use command line:

```bash
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9093 \
  --topic test-topic \
  --from-beginning
```

## Project Structure

```
.
├── src/
│   └── main/
│       ├── kotlin/
│       │   ├── Main.kt                 # Application entry point
│       │   └── SqsToKafkaBridge.kt     # Core bridge logic
│       └── resources/
│           └── logback.xml             # Logging configuration
├── docker-compose.yml                   # Docker services definition
├── localstack-init/
│   └── 01-init-sqs.sh                  # LocalStack initialization script
├── build.gradle.kts                     # Gradle build configuration
└── README.md                            # This file
```

## Configuration

The application can be configured using environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `SQS_QUEUE_URL` | Full URL of the SQS queue | `http://localhost:4566/000000000000/test-queue` |
| `KAFKA_TOPIC` | Kafka topic name | `test-topic` |
| `SQS_ENDPOINT` | LocalStack endpoint | `http://localhost:4566` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker address | `localhost:9092` |

## Message Processing

The application performs the following on each message:

1. **Poll**: Long polls SQS queue (20 seconds wait time)
2. **Process**: Transforms the message by adding metadata (timestamp, source)
3. **Forward**: Sends processed message to Kafka topic
4. **Delete**: Removes message from SQS queue after successful processing

### Example Message Transformation

**Input (from SQS):**
```json
{"orderId": "12345", "product": "Widget"}
```

**Output (to Kafka):**
```json
{
  "originalMessage": {"orderId": "12345", "product": "Widget"},
  "processedAt": 1702345678901,
  "source": "sqs-to-kafka-bridge"
}
```

## Stopping the Application

1. Stop the Kotlin application: `Ctrl+C`
2. Stop Docker services:
   ```bash
   docker compose down
   ```

## Troubleshooting

### Services not starting
```bash
docker compose logs -f
```

### SQS connection issues
Ensure LocalStack is running and accessible:
```bash
curl http://localhost:4566/_localstack/health
```

### Kafka connection issues
Check Kafka broker status:
```bash
docker exec -it kafka kafka-broker-api-versions --bootstrap-server localhost:9093
```

## Development

### Running Tests
```bash
./gradlew test
```

### Building JAR
```bash
./gradlew jar
```

### Clean Build
```bash
./gradlew clean build
```

## Production Considerations

When deploying to production:

1. Use actual AWS SQS instead of LocalStack
2. Configure proper AWS credentials
3. Set up appropriate IAM roles
4. Use managed Kafka service (e.g., MSK, Confluent Cloud)
5. Implement proper error handling and dead-letter queues
6. Add metrics and monitoring
7. Configure appropriate resource limits
8. Implement message validation and schema registry

## License

This project is provided as-is for educational and development purposes.

