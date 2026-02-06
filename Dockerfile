# Multi-stage build for efficient image size
FROM gradle:8.14-jdk17 AS build

WORKDIR /app

# Copy gradle files first for better caching
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle

# Download dependencies
RUN gradle dependencies --no-daemon

# Copy source code
COPY src ./src

# Build the application
RUN gradle build -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:17.0.18_8-jre

WORKDIR /app

# Copy the built jar from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Create a non-root user
RUN groupadd -r appgroup && useradd -r -g appgroup appuser
USER appuser

# Expose any ports if needed (not required for this app)
# EXPOSE 8080

# Set environment variables with defaults
ENV SQS_QUEUE_URL="http://localstack:4566/000000000000/place-order-queue" \
    SQS_ENDPOINT="http://localstack:4566" \
    KAFKA_TOPIC="place-order-topic" \
    KAFKA_BOOTSTRAP_SERVERS="kafka:9093"

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

