package com.taskflow.ai;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Supplies the scripted {@code ChatModel} for integration tests. The LangChain4j Spring starter refuses
 * two {@code ChatModel} beans (it does not honour {@code @Primary}), so tests that import this
 * configuration must also exclude the OpenAI auto-configuration; see {@link AbstractAiIntegrationTest}.
 */
@TestConfiguration
public class AiTestConfig {

    @Bean
    public ScriptedChatModel scriptedChatModel() {
        return new ScriptedChatModel();
    }
}
