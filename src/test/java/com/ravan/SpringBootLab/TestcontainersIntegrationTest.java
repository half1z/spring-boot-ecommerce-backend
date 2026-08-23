package com.ravan.SpringBootLab;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.TestInstance;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class TestcontainersIntegrationTest {

    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("spring_boot_lab_test")
                    .withUsername("ravan")
                    .withPassword("password");

    static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.0"));

    static {
        postgres.start();
        redis.start();
        kafka.start();

        createKafkaTopics();
    }

    private static void createKafkaTopics() {
        List<NewTopic> topics = List.of(
                new NewTopic("order-created", 1, (short) 1),
                new NewTopic("order-created-retry-1000", 1, (short) 1),
                new NewTopic("order-created-retry-2000", 1, (short) 1),
                new NewTopic("order-created-retry-4000", 1, (short) 1),
                new NewTopic("order-created-dlt", 1, (short) 1),

                new NewTopic("payment-paid", 1, (short) 1),
                new NewTopic("payment-paid-retry-1000", 1, (short) 1),
                new NewTopic("payment-paid-retry-2000", 1, (short) 1),
                new NewTopic("payment-paid-retry-4000", 1, (short) 1),
                new NewTopic("payment-paid-dlt", 1, (short) 1)
        );

        try (AdminClient adminClient = AdminClient.create(
                Map.of(
                        "bootstrap.servers",
                        kafka.getBootstrapServers()
                )
        )) {
            adminClient.createTopics(topics).all().get();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to create Kafka test topics",
                    exception
            );
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                postgres::getJdbcUrl
        );
        registry.add(
                "spring.datasource.username",
                postgres::getUsername
        );
        registry.add(
                "spring.datasource.password",
                postgres::getPassword
        );

        registry.add(
                "spring.data.redis.host",
                redis::getHost
        );
        registry.add(
                "spring.data.redis.port",
                () -> redis.getMappedPort(6379)
        );

        registry.add(
                "spring.kafka.bootstrap-servers",
                kafka::getBootstrapServers
        );
    }
}