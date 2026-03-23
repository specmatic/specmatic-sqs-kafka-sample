.PHONY: help start stop build run test contract-test clean send-test send-retry consume logs status all

help: ## Show this help message
	@echo "Available commands:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-15s %s\n", $$1, $$2}'

start: ## Start LocalStack, Kafka, and other infrastructure services
	@./start-infrastructure.sh

stop: ## Stop all Docker services
	@echo "Stopping all services..."
	@docker compose down
	@echo "✅ All services stopped"

build: ## Build the application
	@echo "Building application..."
	@./gradlew build
	@echo "✅ Build complete"

run: ## Run the application
	@echo "Starting Kafka to SQS Bridge..."
	@./gradlew run

test: ## Run tests
	@./gradlew test

contract-test: ## Run the contract test suite
	@./run-contract-tests.sh

clean: ## Clean build artifacts
	@./gradlew clean
	@echo "✅ Clean complete"

send-test: ## Send test messages to Kafka
	@./send-test-message.sh

send-retry: ## Send retry and DLQ scenario messages
	@./test-retry-dlq.sh

consume: ## Read transformed messages from the SQS queue
	@./consume-sqs-messages.sh

logs: ## View Docker service logs
	@docker compose logs -f

status: ## Check status of all services
	@docker compose ps

all: start build ## Start infrastructure and build application
	@echo "✅ Everything is ready! Run 'make run' to start the application."
