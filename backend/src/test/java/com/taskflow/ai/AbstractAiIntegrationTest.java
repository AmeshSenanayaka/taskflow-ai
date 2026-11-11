package com.taskflow.ai;

import com.taskflow.AbstractIntegrationTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * Full application context for AI tests: real PostgreSQL, MockMvc, and the scripted model in place of
 * OpenAI. Both the Phase 1 {@code @AiService} proxy and the Phase 2 supervisor are built on the scripted
 * model, so no network call can happen.
 */
@AutoConfigureMockMvc
@Import(AiTestConfig.class)
@TestPropertySource(properties = "spring.autoconfigure.exclude=dev.langchain4j.openai.spring.AutoConfig")
public abstract class AbstractAiIntegrationTest extends AbstractIntegrationTest {
}
