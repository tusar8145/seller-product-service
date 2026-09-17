package com.enterprise.product;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("productdb")
                    .withUsername("productuser")
                    .withPassword("productpass");

    @Container
    static final RedisContainer REDIS =
            new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void wire(DynamicPropertyRegistry r) {
        // master + replica both point to the same test container
        r.add("spring.datasource.master.jdbc-url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.master.username", POSTGRES::getUsername);
        r.add("spring.datasource.master.password", POSTGRES::getPassword);
        r.add("spring.datasource.replica.jdbc-url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.replica.username", POSTGRES::getUsername);
        r.add("spring.datasource.replica.password", POSTGRES::getPassword);

        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        r.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        r.add("spring.kafka.consumer.group-id", () -> "test-group");

        // S3 not needed for ITs
        r.add("aws.s3.bucket-name", () -> "test-bucket");
    }
}