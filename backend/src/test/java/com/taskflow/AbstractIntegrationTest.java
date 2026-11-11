package com.taskflow;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Boots the full application against a real PostgreSQL container.
 * Flyway migrations run on startup exactly as they do in production.
 * <p>
 * The container follows the Testcontainers "singleton container" pattern: it is started once per JVM and
 * reused by every test class. Spring caches application contexts across classes, so a container managed
 * per class by {@code @Testcontainers} would be stopped while a cached context still points at its port
 * (the AI integration tests share a context and would fail with "Connection refused"). Testcontainers'
 * reaper removes the container when the JVM exits.
 */
@SpringBootTest
// A placeholder key lets the LangChain4j starter create its ChatModel bean; nothing in these tests ever
// calls OpenAI (the AI tests replace the model with a scripted double).
@TestPropertySource(properties = "langchain4j.open-ai.chat-model.api-key=test-key")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }
}
