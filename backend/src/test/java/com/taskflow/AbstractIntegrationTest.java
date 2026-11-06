package com.taskflow;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the full application against a real PostgreSQL container.
 * Flyway migrations run on startup exactly as they do in production.
 * The container is shared by all integration tests to keep the suite fast.
 */
@SpringBootTest
// application.yml reads ${OPENAI_API_KEY} with no default, so without this property the Spring context
// cannot even be built. A placeholder key lets the LangChain4j starter create its ChatModel bean;
// nothing in these tests ever calls OpenAI.
@TestPropertySource(properties = "langchain4j.open-ai.chat-model.api-key=test-key")
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
}
