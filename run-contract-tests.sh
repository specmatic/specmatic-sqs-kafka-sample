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
    docker compose --profile test down -v
    echo -e "${GREEN}Cleanup complete!${NC}"
}

# Set trap to cleanup on script exit
trap cleanup EXIT INT TERM

# Clean up any existing containers
echo -e "${YELLOW}Cleaning up any existing containers...${NC}"
docker compose --profile test down -v 2>/dev/null || true

# Build the application container
echo -e "\n${BLUE}Step 1: Building application container...${NC}"
docker compose --profile test build provider

# Create reports directory if it doesn't exist
mkdir -p build/reports/specmatic

# Run Specmatic tests
echo -e "\n${BLUE}Step 2: Running Specmatic contract tests...${NC}"
echo -e "${BLUE}============================================================${NC}\n"

# Run the test container and stream logs
set +e
docker compose --profile test up --build --abort-on-container-exit --exit-code-from contract-test contract-test
TEST_EXIT_CODE=$?
set -e

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
