#!/bin/bash

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}  Specmatic SQS-Kafka Contract Testing Suite${NC}"
echo -e "${BLUE}============================================================${NC}"

# Cleanup function
cleanup() {
    echo -e "\n${YELLOW}Stopping all containers...${NC}"
    docker compose -f docker-compose-test.yml --profile test down -v
    echo -e "${GREEN}Cleanup complete!${NC}"
}

# Set trap to cleanup on script exit
trap cleanup EXIT INT TERM

# Clean up any existing containers
echo -e "${YELLOW}Cleaning up any existing containers...${NC}"
docker compose -f docker-compose-test.yml --profile test down -v 2>/dev/null || true

# Build the application container
echo -e "\n${BLUE}Step 1: Building application container...${NC}"
docker compose -f docker-compose-test.yml build sqs-kafka-bridge

# Start infrastructure and application
echo -e "\n${BLUE}Step 2: Starting infrastructure and application...${NC}"
docker compose -f docker-compose-test.yml up -d localstack zookeeper kafka kafka-ui sqs-kafka-bridge

# Wait for services to be healthy
echo -e "\n${YELLOW}Waiting for services to be healthy...${NC}"
echo -e "${YELLOW}  - Waiting for LocalStack...${NC}"
timeout=60
counter=0
until docker exec sqs-kafka-localstack curl -sf http://localhost:4566/_localstack/health > /dev/null 2>&1; do
    sleep 2
    counter=$((counter + 2))
    if [ $counter -ge $timeout ]; then
        echo -e "${RED}LocalStack failed to start within ${timeout}s${NC}"
        exit 1
    fi
    echo -n "."
done
echo -e "\n${GREEN}  ✓ LocalStack is healthy${NC}"

echo -e "${YELLOW}  - Waiting for Kafka...${NC}"
counter=0
until docker exec sqs-kafka-kafka kafka-broker-api-versions --bootstrap-server localhost:9093 > /dev/null 2>&1; do
    sleep 2
    counter=$((counter + 2))
    if [ $counter -ge $timeout ]; then
        echo -e "${RED}Kafka failed to start within ${timeout}s${NC}"
        exit 1
    fi
    echo -n "."
done
echo -e "\n${GREEN}  ✓ Kafka is healthy${NC}"

# Give the application a few seconds to initialize
echo -e "${YELLOW}  - Waiting for application to initialize...${NC}"
sleep 5
echo -e "${GREEN}  ✓ Application ready${NC}"

# Create reports directory if it doesn't exist
mkdir -p build/reports/specmatic

# Run Specmatic tests
echo -e "\n${BLUE}Step 3: Running Specmatic contract tests...${NC}"
echo -e "${BLUE}============================================================${NC}\n"

# Run the test container and follow logs
docker compose -f docker-compose-test.yml --profile test up specmatic-tests

# Get the exit code of the test container
TEST_EXIT_CODE=$(docker inspect sqs-kafka-specmatic-tests --format='{{.State.ExitCode}}')

echo -e "\n${BLUE}============================================================${NC}"

# Check test results
if [ "$TEST_EXIT_CODE" -eq 0 ]; then
    echo -e "${GREEN}✓ All contract tests passed successfully!${NC}"
    echo -e "${BLUE}============================================================${NC}"

    # Show report location
    if [ -d "build/reports/specmatic" ] && [ "$(ls -A build/reports/specmatic)" ]; then
        echo -e "${GREEN}Test reports available at:${NC}"
        echo -e "  ${YELLOW}build/reports/specmatic/${NC}"
    fi

    exit 0
else
    echo -e "${RED}✗ Contract tests failed!${NC}"
    echo -e "${BLUE}============================================================${NC}"
    exit 1
fi

