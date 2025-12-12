# Running Contract Tests 

1. Start the dependencies
    ```shell
    ./start-infrastructure.sh
    ```
2. Run the application
   ```shell
    ./gradlew run
    ```

3. Run the contract tests
   ```shell
   specmatic-sqs-kafka test --kafka-server localhost:9092 --sqs-server http://localhost:4566/000000000000 --aws-region us-east-1 --aws-access-key-id test --aws-secret-access-key test 
   ```
