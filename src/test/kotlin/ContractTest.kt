import io.specmatic.async.BridgeApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.testcontainers.containers.ComposeContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import org.testcontainers.images.PullPolicy
import org.testcontainers.containers.BindMode
import java.io.File
import java.time.Duration
import java.util.Properties

@Testcontainers
@EnabledIf(value = "isNonCIOrLinux", disabledReason = "Run only on Linux in CI; all platforms allowed locally")
class ContractTest {

    companion object {
        @JvmStatic
        fun isNonCIOrLinux(): Boolean = System.getenv("CI") != "true" || System.getProperty("os.name").lowercase().contains("linux")

        private val DOCKER_COMPOSE_FILE = File("docker-compose.yml")
        private const val LOCALSTACK_SERVICE = "localstack"
        private const val KAFKA_SERVICE = "kafka"
        private val EXPECTED_TOPICS = setOf(
            "place-order-topic",
            "place-order-retry-topic",
            "place-order-dlq-topic"
        )

        private lateinit var infrastructure: ComposeContainer
        private lateinit var application: BridgeApplication

        @JvmStatic
        @BeforeAll
        fun setup() {
            // Start infrastructure using docker-compose
            infrastructure = ComposeContainer(DOCKER_COMPOSE_FILE)
                .withExposedService(LOCALSTACK_SERVICE, 4566, Wait.forHealthcheck())
                .withExposedService(KAFKA_SERVICE, 9092, Wait.forHealthcheck())

            infrastructure.start()

            println("Infrastructure started via docker-compose")

            waitForKafkaTopics()

            // Create reports directory if it doesn't exist
            File("./build/reports/specmatic").mkdirs()

            Thread.sleep(5000)

            // Start the Kafka to SQS bridge application
            startApplication()
        }

        private fun startApplication() {
            println("Starting Kafka to SQS Bridge application...")
            application = BridgeApplication()
            application.logConfiguration()

            // Configure transformer to fail specific order IDs for testing retry/DLQ scenarios
            application.messageTransformer.addFailingOrderId("ORD-RETRY-90001")
            application.messageTransformer.addFailingOrderId("ORD-DLQ-90001")
            application.messageTransformer.addDirectDlqOrderId("ORD-RECEIVE-DLQ-90001")

            application.startAsync()

            // Wait a bit for all components to start
            Thread.sleep(5000)

            println("All application components started successfully")
            println("- Main Bridge: Processing Kafka topic, sending to SQS queue")
            println("- Retry Consumer: Managing retry logic within Kafka retry topic")
        }

        private fun waitForKafkaTopics(timeout: Duration = Duration.ofSeconds(30)) {
            val deadline = System.nanoTime() + timeout.toNanos()
            val props = Properties().apply {
                put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
            }

            while (System.nanoTime() < deadline) {
                try {
                    AdminClient.create(props).use { adminClient ->
                        val topicNames = adminClient.listTopics().names().get()
                        if (EXPECTED_TOPICS.all(topicNames::contains)) {
                            println("Kafka topics are ready: $EXPECTED_TOPICS")
                            return
                        }
                    }
                } catch (_: Exception) {
                    // Kafka may still be starting or the init container may still be creating topics.
                }

                Thread.sleep(1000)
            }

            error("Timed out waiting for Kafka topics: $EXPECTED_TOPICS")
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            if (::application.isInitialized) {
                println("Stopping application gracefully...")
                application.close()
            }

            // Stop infrastructure
            infrastructure.stop()
        }
    }

    @Test
    fun `run contract test`() {
        // Setup Specmatic container with host network mode
        val specmaticContainer = GenericContainer(DockerImageName.parse("specmatic/enterprise"))
            .withImagePullPolicy(PullPolicy.alwaysPull())
            .withCommand(
                "test"
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
            .waitingFor(Wait.forLogMessage(".*Failed:.*", 1))

        try {
            specmaticContainer.start()

            assertThat(specmaticContainer.logs)
                .contains("Failed: 0")
                .doesNotContain("Passed: 0")
        } finally {
            specmaticContainer.stop()
        }
    }
}
