package com.taskflow.eval;

import com.taskflow.ai.AbstractAiIntegrationTest;
import com.taskflow.ai.ScriptedChatModel;
import com.taskflow.ai.conversation.ConversationService;
import com.taskflow.ai.service.MultiAgentService;
import com.taskflow.repository.TaskRepository;
import com.taskflow.service.PreferencesService;
import com.taskflow.service.UserAccountService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Regression tier of the Phase 3.1 evaluation: every scripted scenario runs through the real Spring wiring,
 * supervisor loop, tools, security context and PostgreSQL, with {@link ScriptedChatModel} replaying the
 * scenario's script. It needs no API key and runs in every build.
 * <p>
 * It verifies the harness, the tool contracts and the database effects. Its pass rate says nothing about a model:
 * that is what {@link LiveModelEvalTest} measures.
 */
@Tag("eval-regression")
@Import(EvalTestConfig.class)
class ScriptedRegressionEvalTest extends AbstractAiIntegrationTest {

    private static final EvalDataset DATASET = EvalDataset.load();
    private static final List<RunResult> RESULTS = Collections.synchronizedList(new ArrayList<>());

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
    private ScriptedChatModel model;
    @Autowired
    private com.taskflow.ai.orchestration.TaskFlowAgentFactory agentFactory;
    @Autowired
    private com.taskflow.ai.guardrail.ConversationGuardrails conversationGuardrails;
    @Autowired
    private com.taskflow.ai.memory.ConversationMemory conversationMemory;
    @Autowired
    private com.taskflow.ai.memory.ChatMemoryRepository chatMemoryRepository;

    @TestFactory
    Stream<DynamicTest> scriptedScenarios() {
        ScenarioRunner runner = new ScenarioRunner(multiAgentService, conversations, userAccounts, preferences, tasks,
                clock, model)
                .withPersistence(() -> new MultiAgentService(agentFactory.supervisor(), conversationGuardrails,
                        conversationMemory, conversations), chatMemoryRepository);
        return DATASET.scenarios().stream().map(scenario -> DynamicTest.dynamicTest(
                scenario.id() + " " + scenario.title(), () -> {
                    assumeTrue(scenario.executable(), () -> "not applicable until " + scenario.notApplicable().until());
                    assumeTrue(scenario.scripted(), "live-only scenario (no script)");
                    RunResult result = runner.run(scenario, 1);
                    RESULTS.add(result);
                    assertThat(result.failures()).as("%s %s (%s)", scenario.id(), scenario.title(), result.status())
                            .isEmpty();
                }));
    }

    @AfterAll
    static void writeReport() {
        EvalReport.Metadata metadata = new EvalReport.Metadata("scripted-regression", Instant.now(),
                EvalReport.gitCommit(), "ScriptedChatModel (no LLM)", "n/a", 1, DATASET.sha256(),
                EvalDataset.promptHashes(), Scenario.Context.DEFAULT.now());
        Path dir = EvalReport.write(EvalReport.build(metadata, DATASET, List.copyOf(RESULTS),
                "live-only scenario: it has no script, run LiveModelEvalTest"));
        System.out.println("Scripted regression report: " + dir.toAbsolutePath());
    }
}
