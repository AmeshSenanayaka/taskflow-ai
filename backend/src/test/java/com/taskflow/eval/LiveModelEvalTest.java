package com.taskflow.eval;

import com.taskflow.AbstractIntegrationTest;
import com.taskflow.ai.conversation.ConversationService;
import com.taskflow.ai.service.MultiAgentService;
import com.taskflow.repository.TaskRepository;
import com.taskflow.service.PreferencesService;
import com.taskflow.service.UserAccountService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live tier of the Phase 3.1 evaluation: every executable scenario runs against the configured OpenAI model,
 * several times, each repetition from a clean database and a new conversation. This is the measurement; it costs
 * real API calls, so it never runs in an ordinary build.
 * <pre>
 * OPENAI_API_KEY=sk-... mvn test -Dtest=LiveModelEvalTest -Deval.live=true
 *   -Deval.repetitions=3          runs per scenario (default 3)
 *   -Deval.scenarios=G01,F01      only these scenario ids (default: all executable)
 *   -Deval.minPassRate=0.8        fail the build below this overall pass rate (default: report only)
 * </pre>
 * The report is written to {@code target/eval-reports/live/}.
 */
@Tag("live-eval")
@EnabledIfSystemProperty(named = "eval.live", matches = "true")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@Import(EvalTestConfig.class)
@TestPropertySource(properties = "langchain4j.open-ai.chat-model.api-key=${OPENAI_API_KEY}")
class LiveModelEvalTest extends AbstractIntegrationTest {

    @Autowired
    private MultiAgentService multiAgentService;
    @Autowired
    private ConversationService conversations;
    @Autowired
    private UserAccountService userAccounts;
    @Autowired
    private PreferencesService preferences;
    @Autowired
    private TaskRepository tasks;
    @Autowired
    private MutableClock clock;
    @Autowired
    private com.taskflow.ai.orchestration.TaskFlowAgentFactory agentFactory;
    @Autowired
    private com.taskflow.ai.guardrail.ConversationGuardrails conversationGuardrails;
    @Autowired
    private com.taskflow.ai.memory.ConversationMemory conversationMemory;
    @Autowired
    private com.taskflow.ai.memory.ChatMemoryRepository chatMemoryRepository;

    @Value("${langchain4j.open-ai.chat-model.model-name:unknown}")
    private String modelName;
    @Value("${langchain4j.open-ai.chat-model.temperature:unknown}")
    private String temperature;

    @Test
    void liveBaseline() {
        EvalDataset dataset = EvalDataset.load();
        int repetitions = Integer.getInteger("eval.repetitions", 3);
        Set<String> only = Arrays.stream(System.getProperty("eval.scenarios", "").split(","))
                .map(String::trim).filter(id -> !id.isEmpty()).collect(Collectors.toSet());
        ScenarioRunner runner = new ScenarioRunner(multiAgentService, conversations, userAccounts, preferences, tasks,
                clock, null)
                .withPersistence(() -> new MultiAgentService(agentFactory.supervisor(), conversationGuardrails,
                        conversationMemory, conversations), chatMemoryRepository);

        List<RunResult> results = new ArrayList<>();
        for (Scenario scenario : dataset.scenarios()) {
            if (!scenario.executable() || (!only.isEmpty() && !only.contains(scenario.id()))) {
                continue;
            }
            for (int run = 1; run <= repetitions; run++) {
                RunResult result = runner.run(scenario, run);
                results.add(result);
                System.out.printf("%s run %d: %s %s%n", scenario.id(), run, result.status(), result.failures());
                // A wrong key, model name or network problem makes every run error: stop instead of paying for nothing.
                if (results.size() == 3 && results.stream().allMatch(r -> r.status() == RunResult.Status.ERROR)) {
                    throw new IllegalStateException("The first 3 runs all failed with errors, so the model is not reachable. "
                            + "Last error: " + result.failures());
                }
            }
        }

        EvalReport.Metadata metadata = new EvalReport.Metadata("live", Instant.now(), EvalReport.gitCommit(),
                modelName, temperature, repetitions, dataset.sha256(), EvalDataset.promptHashes(),
                Scenario.Context.DEFAULT.now());
        EvalReport.Report report = EvalReport.build(metadata, dataset, results,
                only.isEmpty() ? "not selected" : "not in -Deval.scenarios");
        Path dir = EvalReport.write(report);
        System.out.println("Live evaluation report: " + dir.toAbsolutePath());

        assertThat(results).as("every run errored; see %s", dir.resolve("report.md"))
                .anyMatch(result -> result.status() != RunResult.Status.ERROR);
        double minPassRate = Double.parseDouble(System.getProperty("eval.minPassRate", "0"));
        assertThat(report.passRate() == null ? 0 : report.passRate())
                .as("overall pass rate (see %s)", dir.resolve("report.md"))
                .isGreaterThanOrEqualTo(minPassRate);
    }
}
