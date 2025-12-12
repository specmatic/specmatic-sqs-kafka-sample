#!/bin/bash

# Comprehensive test script to validate the entire setup

echo "======================================"
echo "SQS to Kafka Bridge - Full Test Suite"
echo "======================================"
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print status
print_status() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}✅ $2${NC}"
    else
        echo -e "${RED}❌ $2${NC}"
        return 1
    fi
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

# Test 1: Check Docker is running
echo "Test 1: Checking Docker..."
docker info > /dev/null 2>&1
print_status $? "Docker is running" || exit 1

# Test 2: Check if services are running
echo ""
echo "Test 2: Checking Docker services..."
RUNNING_SERVICES=$(docker compose ps --services --filter "status=running" 2>/dev/null | wc -l)
if [ $RUNNING_SERVICES -ge 3 ]; then
    print_status 0 "Docker services are running ($RUNNING_SERVICES services)"
else
    print_warning "Docker services not running. Run: ./start-infrastructure.sh"
fi

# Test 3: Check LocalStack health
echo ""
echo "Test 3: Checking LocalStack (SQS)..."
if curl -s http://localhost:4566/_localstack/health > /dev/null 2>&1; then
    print_status 0 "LocalStack is accessible"
else
    print_warning "LocalStack not accessible on port 4566"
fi

# Test 4: Check Kafka
echo ""
echo "Test 4: Checking Kafka..."
if nc -z localhost 9092 2>/dev/null; then
    print_status 0 "Kafka is accessible on port 9092"
else
    print_warning "Kafka not accessible on port 9092"
fi

# Test 5: Check Kafka UI
echo ""
echo "Test 5: Checking Kafka UI..."
if curl -s http://localhost:8080 > /dev/null 2>&1; then
    print_status 0 "Kafka UI is accessible at http://localhost:8080"
else
    print_warning "Kafka UI not accessible on port 8080"
fi

# Test 6: Check Gradle build
echo ""
echo "Test 6: Verifying Gradle build..."
if [ -d "build/classes/kotlin/main/io/specmatic/async" ]; then
    print_status 0 "Application is built successfully"
else
    print_warning "Application not built. Run: ./gradlew build"
fi

# Test 7: Check SQS queue exists
echo ""
echo "Test 7: Checking SQS queue..."
if command -v aws &> /dev/null; then
    QUEUE_CHECK=$(aws --endpoint-url=http://localhost:4566 sqs list-queues 2>/dev/null | grep -c "place-order-queue")
    if [ $QUEUE_CHECK -gt 0 ]; then
        print_status 0 "SQS queue 'place-order-queue' exists"
    else
        print_warning "SQS queue not found. LocalStack may still be initializing."
    fi
else
    print_warning "AWS CLI not installed - skipping queue check"
fi

# Test 8: Check all required files
echo ""
echo "Test 8: Checking project files..."
FILES=(
    "src/main/kotlin/Main.kt"
    "src/main/kotlin/SqsToKafkaBridge.kt"
    "docker-compose.yml"
    "build.gradle.kts"
    "start-infrastructure.sh"
    "send-test-message.sh"
)

ALL_FILES_EXIST=true
for file in "${FILES[@]}"; do
    if [ -f "$file" ]; then
        echo -e "  ${GREEN}✓${NC} $file"
    else
        echo -e "  ${RED}✗${NC} $file"
        ALL_FILES_EXIST=false
    fi
done

if $ALL_FILES_EXIST; then
    print_status 0 "All required files present"
else
    print_status 1 "Some files are missing"
fi

echo ""
echo "======================================"
echo "Test Summary"
echo "======================================"
echo ""
echo "Next Steps:"
echo ""
if [ $RUNNING_SERVICES -lt 3 ]; then
    echo "  1. Start infrastructure:"
    echo "     ${YELLOW}./start-infrastructure.sh${NC}"
    echo ""
fi
echo "  2. Build the application (if not built):"
echo "     ${YELLOW}./gradlew build${NC}"
echo ""
echo "  3. Run the application:"
echo "     ${YELLOW}./gradlew run${NC}"
echo ""
echo "  4. In another terminal, send test messages:"
echo "     ${YELLOW}./send-test-message.sh${NC}"
echo ""
echo "  5. View messages in Kafka:"
echo "     • Kafka UI: ${YELLOW}http://localhost:8080${NC}"
echo "     • Command line: ${YELLOW}./consume-kafka-messages.sh${NC}"
echo ""
echo "======================================"

