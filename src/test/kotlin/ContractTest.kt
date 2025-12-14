import io.specmatic.async.SqsToKafkaBridge
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.testcontainers.containers.ComposeContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import org.testcontainers.containers.BindMode
import java.io.File
import java.time.Duration

@Testcontainers
@EnabledIf(value = "isNonCIOrLinux", disabledReason = "Run only on Linux in CI; all platforms allowed locally")
class ContractTest {

    companion object {
        @JvmStatic
        fun isNonCIOrLinux(): Boolean = System.getenv("CI") != "true" || System.getProperty("os.name").lowercase().contains("linux")

        private val DOCKER_COMPOSE_FILE = File("docker-compose.yml")
        private const val LOCALSTACK_SERVICE = "localstack"
        private const val KAFKA_SERVICE = "kafka"

        private lateinit var infrastructure: ComposeContainer
        private lateinit var appThread: Thread
        private lateinit var bridge: SqsToKafkaBridge

        @JvmStatic
        @BeforeAll
        fun setup() {
            // Start infrastructure using docker-compose
            infrastructure = ComposeContainer(DOCKER_COMPOSE_FILE)
                .withExposedService(LOCALSTACK_SERVICE, 4566, Wait.forListeningPort())
                .withExposedService(KAFKA_SERVICE, 9092, Wait.forListeningPort())
                .waitingFor(KAFKA_SERVICE, Wait.forLogMessage(".*started.*", 1)
                    .withStartupTimeout(Duration.ofSeconds(60)))

            infrastructure.start()

            println("Infrastructure started via docker-compose")

            // Create reports directory if it doesn't exist
            File("./build/reports/specmatic").mkdirs()

            Thread.sleep(2000)

            // Start the SQS to Kafka Bridge application
            startApplication()
        }

        private fun startApplication() {
            println("Starting SQS to Kafka Bridge application...")

            // Use localhost with standard ports from docker-compose
            val sqsEndpoint = "http://localhost:4566"
            val kafkaBootstrapServers = "localhost:9092"
            val sqsQueueUrl = "$sqsEndpoint/000000000000/place-order-queue"

            println("Using SQS endpoint: $sqsEndpoint")
            println("Using Kafka servers: $kafkaBootstrapServers")

            // Create the bridge instance
            bridge = SqsToKafkaBridge(
                sqsQueueUrl = sqsQueueUrl,
                kafkaTopic = "place-order-topic",
                sqsEndpoint = sqsEndpoint,
                kafkaBootstrapServers = kafkaBootstrapServers
            )

            // Start the application in a background thread
            appThread = Thread {
                runBlocking {
                    bridge.start()
                }
            }.apply {
                isDaemon = true
                start()
            }

            // Wait a bit for the application to start
            Thread.sleep(5000)

            println("Application started successfully")
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            // Gracefully stop the application
            if (::bridge.isInitialized) {
                println("Stopping application gracefully...")
                bridge.close()
            }

            // Wait for the thread to finish
            if (::appThread.isInitialized && appThread.isAlive) {
                appThread.join(5000)
            }

            // Stop infrastructure
            infrastructure.stop()
        }
    }

    @Test
    fun `run contract test`() {
        // Use localhost URLs with standard ports from docker-compose
        val kafkaBootstrapServers = "localhost:9092"
        val sqsEndpoint = "http://localhost:4566/000000000000"

        // Setup Specmatic container with host network mode
        val specmaticContainer = GenericContainer(DockerImageName.parse("specmatic/specmatic-sqs-kafka:latest"))
            .withCommand(
                "test",
                "--kafka-server=$kafkaBootstrapServers",
                "--sqs-server=$sqsEndpoint",
                "--aws-region=us-east-1",
                "--aws-access-key-id=test",
                "--aws-secret-access-key=test"
            )
            .withFileSystemBind(
                "./specmatic.yaml",
                "/usr/src/app/specmatic.yaml",
                BindMode.READ_ONLY
            )
            .withFileSystemBind(
                "./spec",
                "/usr/src/app/spec",
                BindMode.READ_ONLY
            )
            .withFileSystemBind(
                "./build/reports/specmatic",
                "/usr/src/app/build/reports/specmatic",
                BindMode.READ_WRITE
            )
            .withNetworkMode("host")
            .withStartupTimeout(Duration.ofMinutes(5))
            .withLogConsumer { print(it.utf8String) }
            .waitingFor(Wait.forLogMessage(".*The ctrf report is saved at.*", 1))

        try {
            // Start the Specmatic container
            specmaticContainer.start()

            // Check the logs for test results
            val logs = specmaticContainer.logs

            println("=".repeat(60))
            println("Specmatic Test Results")
            println("=".repeat(60))
            println(logs)
            println("=".repeat(60))

            assertThat(logs).contains("Failed Tests: 0")
        } finally {
            specmaticContainer.stop()
        }
    }
}