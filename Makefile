.PHONY: help start stop build run test clean send-test logs

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
	@echo "Starting SQS to Kafka Bridge..."
	@./gradlew run

test: ## Run tests
	@./gradlew test

clean: ## Clean build artifacts
	@./gradlew clean
	@echo "✅ Clean complete"

send-test: ## Send test messages to SQS
	@./send-test-message.sh

send-orders: ## Send test order messages matching AsyncAPI spec
	@./send-order-messages.sh

logs: ## View Docker service logs
	@docker compose logs -f

status: ## Check status of all services
	@docker compose ps

all: start build ## Start infrastructure and build application
	@echo "✅ Everything is ready! Run 'make run' to start the application."

