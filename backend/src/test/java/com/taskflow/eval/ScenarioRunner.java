package com.taskflow.eval;

import com.taskflow.ai.ScriptedChatModel;
import com.taskflow.ai.conversation.ConversationRef;
import com.taskflow.ai.conversation.ConversationService;
import com.taskflow.ai.dto.DailyPlan;
import com.taskflow.ai.dto.AgentCallInfo;
import com.taskflow.ai.dto.SupervisorChatResponse;
import com.taskflow.ai.dto.ToolCallInfo;
import com.taskflow.ai.memory.ChatMemoryRepository;
import com.taskflow.ai.memory.MemoryKey;
import com.taskflow.ai.service.MultiAgentService;
import com.taskflow.dto.request.UpdatePreferencesRequest;
import com.taskflow.entity.Task;
import com.taskflow.enums.TaskPriority;
import com.taskflow.enums.TaskStatus;
import com.taskflow.repository.TaskRepository;
import com.taskflow.service.PreferencesService;
import com.taskflow.service.UserAccountService;
import com.taskflow.support.TestUsers;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs one scenario end to end through the real application: resets the database, moves the clock, creates the
 * fixture, signs in as the scenario's user, sends every turn through {@link MultiAgentService} (the same path as
 * {@code POST /api/ai/supervisor/chat}) and checks the expectations against the responses and the database.
 * <p>
 * With a {@link ScriptedChatModel} the scenario's script is replayed (regression tier); without one the configured
 * model answers (live tier). Each run starts from a clean database and a new conversation, so runs are independent.
 */
public class ScenarioRunner {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{(task|title):([^}]+)}");

    private final MultiAgentService multiAgentService;
    private final ConversationService conversations;
    private final UserAccountService userAccounts;
    private final PreferencesService preferences;
    private final TaskRepository tasks;
    private final MutableClock clock;
    private final ScriptedChatModel scriptedModel;

    /** @param scriptedModel the scripted model for the regression tier, or null to use the configured model */
    public ScenarioRunner(MultiAgentService multiAgentService, ConversationService conversations,
                          UserAccountService userAccounts, PreferencesService preferences, TaskRepository tasks,
                          MutableClock clock, ScriptedChatModel scriptedModel) {
        this.multiAgentService = multiAgentService;
        this.conversations = conversations;
        this.userAccounts = userAccounts;
        this.preferences = preferences;
        this.tasks = tasks;
        this.clock = clock;
        this.scriptedModel = scriptedModel;
    }

    /** Phase 3.4: builds a fresh MultiAgentService (new agents, nothing in process) to simulate a restart. */
    private Supplier<MultiAgentService> restarter;
    /** Phase 3.4: reads the stored chat memory for {@code memoryNotContains}. */
    private ChatMemoryRepository memoryRepository;

    /**
     * Enables the persistent-memory scenarios: {@code restartBetweenTurns} and {@code memoryNotContains}.
     *
     * @param restarter        builds a MultiAgentService over freshly built agents, as after a backend restart
     * @param memoryRepository the stored chat memory
     */
    public ScenarioRunner withPersistence(Supplier<MultiAgentService> restarter, ChatMemoryRepository memoryRepository) {
        this.restarter = restarter;
        this.memoryRepository = memoryRepository;
        return this;
    }

    public RunResult run(Scenario scenario, int repetition) {
        long started = System.nanoTime();
        List<String> agents = new ArrayList<>();
        List<String> tools = new ArrayList<>();
        List<String> responses = new ArrayList<>();
        int llmCalls = 0;
        Integer tokens = null;
        try {
            Map<String, UUID> users = Map.of(
                    "alice", userAccounts.resolve(TestUsers.ALICE_SUBJECT, "alice").id(),
                    "bob", userAccounts.resolve(TestUsers.BOB_SUBJECT, "bob").id());
            UUID userId = users.get(scenario.userOrDefault());
            Scenario.Context context = scenario.contextOrDefault();

            clock.set(context.now());
            tasks.deleteAll();
            for (UUID id : users.values()) {
                preferences.update(id, new UpdatePreferencesRequest(context.timezone(), context.dailyAvailableMinutes()));
            }
            Map<String, Snapshot> fixture = createFixture(scenario, users);
            Map<String, UUID> keys = new HashMap<>();
            fixture.forEach((key, snapshot) -> keys.put(key, snapshot.id()));

            if (scriptedModel != null) {
                scriptedModel.reset();
                for (Scenario.ScriptStep step : scenario.scriptOrEmpty()) {
                    enqueue(step, keys, userId);
                }
            }

            signIn(scenario.userOrDefault());
            ConversationRef conversation = conversations.create(userId);
            List<SupervisorChatResponse> answers = new ArrayList<>();
            MultiAgentService service = multiAgentService;
            int lastTurnFirstRequest = 0;
            for (int index = 0; index < scenario.turns().size(); index++) {
                String turn = scenario.turns().get(index);
                if (index > 0 && Boolean.TRUE.equals(scenario.restartBetweenTurns())) {
                    if (restarter == null) {
                        throw new IllegalStateException(scenario.id() + ": restartBetweenTurns needs withPersistence");
                    }
                    // Everything in process is new: agents, their chat memory objects and caches. Only the
                    // database carries the conversation over, exactly as after a backend restart.
                    service = restarter.get();
                }
                if (scriptedModel != null) {
                    lastTurnFirstRequest = scriptedModel.requests().size();
                }
                SupervisorChatResponse answer = service.chat(conversation, resolve(turn, keys, userId));
                answers.add(answer);
                responses.add(answer.message());
                if (answer.execution() != null) {
                    answer.execution().agents().stream().map(AgentCallInfo::name).forEach(agents::add);
                    answer.execution().toolCalls().forEach(call ->
                            tools.add(call.success() ? call.name() : call.name() + "(failed)"));
                    llmCalls += answer.execution().llmCalls();
                    if (answer.execution().tokenUsage() != null && answer.execution().tokenUsage().totalTokens() != null) {
                        tokens = (tokens == null ? 0 : tokens) + answer.execution().tokenUsage().totalTokens();
                    }
                }
            }

            List<String> failures = judge(scenario, answers, fixture, keys, users, llmCalls);
            checkMemory(scenario, conversation, lastTurnFirstRequest, failures);
            if (scriptedModel != null && scriptedModel.remainingScript() > 0) {
                failures.add("the script was not fully used (" + scriptedModel.remainingScript()
                        + " steps left): the system took a different route than scripted");
            }
            return new RunResult(scenario.id(), repetition,
                    failures.isEmpty() ? RunResult.Status.PASSED : RunResult.Status.FAILED,
                    List.copyOf(failures), agents, tools, llmCalls, tokens, elapsedMs(started), responses);
        } catch (RuntimeException ex) {
            return new RunResult(scenario.id(), repetition, RunResult.Status.ERROR,
                    List.of(ex.getClass().getSimpleName() + ": " + ex.getMessage()),
                    agents, tools, llmCalls, tokens, elapsedMs(started), responses);
        } finally {
            SecurityContextHolder.clearContext();
            restoreDefaultPreferences();
        }
    }

    /**
     * Scenarios change both demo users' timezone and daily minutes (F01 uses 180). Other test classes share the same
     * database and expect the defaults, so every run puts them back, whatever its outcome.
     */
    private void restoreDefaultPreferences() {
        Scenario.Context defaults = Scenario.Context.DEFAULT;
        for (String subject : List.of(TestUsers.ALICE_SUBJECT, TestUsers.BOB_SUBJECT)) {
            String username = TestUsers.ALICE_SUBJECT.equals(subject) ? "alice" : "bob";
            preferences.update(userAccounts.resolve(subject, username).id(),
                    new UpdatePreferencesRequest(defaults.timezone(), defaults.dailyAvailableMinutes()));
        }
    }

    // ---------------------------------------------------------------------------------------------- setup

    private Map<String, Snapshot> createFixture(Scenario scenario, Map<String, UUID> users) {
        Map<String, Snapshot> created = new HashMap<>();
        Instant now = clock.instant();
        for (Scenario.FixtureTask item : scenario.fixtureOrEmpty()) {
            String ownerName = item.owner() == null ? scenario.userOrDefault() : item.owner();
            UUID owner = Objects.requireNonNull(users.get(ownerName), "unknown fixture owner " + ownerName);
            Task task = new Task(owner, item.title(), item.description(),
                    item.priority() == null ? TaskPriority.MEDIUM : item.priority(), item.dueDate(), now);
            task.setEstimatedMinutes(item.estimatedMinutes());
            if (item.status() != null && item.status() != TaskStatus.TODO) {
                task.changeStatus(item.status(), now);
            }
            Task saved = tasks.saveAndFlush(task);
            if (item.key() != null) {
                created.put(item.key(), Snapshot.of(saved));
            }
        }
        return created;
    }

    private void enqueue(Scenario.ScriptStep step, Map<String, UUID> keys, UUID userId) {
        if (step.invokeAgent() != null) {
            scriptedModel.invokeAgent(step.invokeAgent(), resolve(nullToEmpty(step.request()), keys, userId));
        } else if (step.toolCall() != null) {
            String arguments = step.arguments() == null ? "{}" : step.arguments().toString();
            // Resolved when the model is asked, so ${title:...} can find a task created by an earlier step.
            scriptedModel.toolCall(step.toolCall(), () -> resolve(arguments, keys, userId));
        } else if (step.text() != null) {
            scriptedModel.text(resolve(step.text(), keys, userId));
        } else {
            scriptedModel.done(resolve(step.done(), keys, userId));
        }
    }

    /** The harness acts as the signed-in user exactly like an HTTP request would: a validated JWT in the context. */
    private static void signIn(String user) {
        String subject = "bob".equals(user) ? TestUsers.BOB_SUBJECT : TestUsers.ALICE_SUBJECT;
        Instant issued = Instant.now();
        Jwt jwt = Jwt.withTokenValue("eval-" + user)
                .header("alg", "none")
                .subject(subject)
                .claim("preferred_username", user)
                .issuedAt(issued)
                .expiresAt(issued.plusSeconds(3600))
                .build();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        SecurityContextHolder.setContext(context);
    }

    private String resolve(String text, Map<String, UUID> keys, UUID userId) {
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value;
            if ("task".equals(matcher.group(1))) {
                UUID id = keys.get(matcher.group(2));
                if (id == null) {
                    throw new IllegalStateException("Unknown fixture key in ${task:" + matcher.group(2) + "}");
                }
                value = id.toString();
            } else {
                String fragment = matcher.group(2).toLowerCase(Locale.ROOT);
                value = tasks.findAll().stream()
                        .filter(task -> task.getOwnerId().equals(userId))
                        .filter(task -> task.getTitle().toLowerCase(Locale.ROOT).contains(fragment))
                        .map(task -> task.getId().toString())
                        .findFirst()
                        .orElse("00000000-0000-0000-0000-000000000000");
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    // ---------------------------------------------------------------------------------------------- judge

    private List<String> judge(Scenario scenario, List<SupervisorChatResponse> answers, Map<String, Snapshot> fixture,
                               Map<String, UUID> keys, Map<String, UUID> users, int llmCalls) {
        Scenario.Expectations expect = scenario.expectOrEmpty();
        UUID userId = users.get(scenario.userOrDefault());
        List<String> failures = new ArrayList<>();

        Set<String> agentsRun = new LinkedHashSet<>();
        Set<String> successfulTools = new LinkedHashSet<>();
        for (SupervisorChatResponse answer : answers) {
            if (answer.execution() == null) {
                continue;
            }
            answer.execution().agents().forEach(agent -> agentsRun.add(agent.name()));
            answer.execution().toolCalls().stream().filter(ToolCallInfo::success)
                    .forEach(call -> successfulTools.add(call.name()));
        }
        checkNames("agent", expect.agents(), agentsRun, failures);
        checkNames("tool", expect.tools(), successfulTools, failures);

        if (expect.dataChanged() != null) {
            boolean changed = answers.stream().anyMatch(SupervisorChatResponse::dataChanged);
            if (changed != expect.dataChanged()) {
                failures.add("expected dataChanged=" + expect.dataChanged() + " but was " + changed);
            }
        }
        if (expect.maxLlmCalls() != null && llmCalls > expect.maxLlmCalls()) {
            failures.add("used " + llmCalls + " LLM calls, more than the allowed " + expect.maxLlmCalls());
        }

        String finalAnswer = answers.isEmpty() ? "" : lower(answers.get(answers.size() - 1).message());
        for (String fragment : nz(expect.responseContains())) {
            String wanted = lower(resolve(fragment, keys, userId));
            if (!finalAnswer.contains(wanted)) {
                failures.add("the final answer does not mention \"" + fragment + "\"");
            }
        }
        for (String fragment : nz(expect.responseNotContains())) {
            String unwanted = lower(resolve(fragment, keys, userId));
            for (SupervisorChatResponse answer : answers) {
                if (lower(answer.message()).contains(unwanted)) {
                    failures.add("an answer mentions \"" + fragment + "\", which it must not");
                    break;
                }
            }
        }

        checkPlan(expect.plan(), answers, keys, failures);

        Set<String> guardrailsActed = new LinkedHashSet<>();
        answers.forEach(answer -> nz(answer.guardrails()).forEach(guardrailsActed::add));
        for (String code : nz(expect.guardrails())) {
            if (!guardrailsActed.contains(code)) {
                failures.add("guardrail " + code + " did not act (acted: " + guardrailsActed + ")");
            }
        }

        List<Task> current = tasks.findAll();
        if (expect.maxChangedTasks() != null) {
            long changedTasks = current.stream()
                    .filter(task -> task.getOwnerId().equals(userId))
                    .filter(task -> fixture.values().stream().noneMatch(before -> before.equals(Snapshot.of(task))))
                    .count();
            if (changedTasks > expect.maxChangedTasks()) {
                failures.add(changedTasks + " tasks were created or changed, more than the allowed "
                        + expect.maxChangedTasks());
            }
        }
        for (Scenario.TaskExpectation expected : nz(expect.tasks())) {
            checkTask(scenario, expected, current, keys, users, failures);
        }
        if (expect.taskCount() != null) {
            expect.taskCount().forEach((owner, count) -> {
                UUID ownerId = Objects.requireNonNull(users.get(owner), "unknown owner " + owner);
                long actual = current.stream().filter(task -> task.getOwnerId().equals(ownerId)).count();
                if (actual != count) {
                    failures.add(owner + " has " + actual + " tasks, expected " + count);
                }
            });
        }
        for (String key : nz(expect.unchanged())) {
            Snapshot before = fixture.get(key);
            if (before == null) {
                throw new IllegalStateException(scenario.id() + ": unknown fixture key in unchanged: " + key);
            }
            Snapshot after = current.stream().filter(task -> task.getId().equals(before.id()))
                    .findFirst().map(Snapshot::of).orElse(null);
            if (after == null) {
                failures.add("task '" + key + "' was deleted");
            } else if (!after.equals(before)) {
                failures.add("task '" + key + "' was changed: " + before + " -> " + after);
            }
        }
        return failures;
    }

    /** Phase 3.4: what the model saw in the last turn, and what was stored for the conversation. */
    private void checkMemory(Scenario scenario, ConversationRef conversation, int lastTurnFirstRequest,
                             List<String> failures) {
        Scenario.Expectations expect = scenario.expectOrEmpty();
        if (scriptedModel != null && !nz(expect.contextContains()).isEmpty()) {
            String seen = lower(scriptedModel.requests().subList(lastTurnFirstRequest, scriptedModel.requests().size())
                    .stream().map(request -> String.valueOf(request.messages())).collect(Collectors.joining("\n")));
            for (String fragment : expect.contextContains()) {
                if (!seen.contains(lower(fragment))) {
                    failures.add("the model did not see \"" + fragment + "\" in the last turn (memory not loaded)");
                }
            }
        }
        if (!nz(expect.memoryNotContains()).isEmpty()) {
            if (memoryRepository == null) {
                throw new IllegalStateException(scenario.id() + ": memoryNotContains needs withPersistence");
            }
            String stored = lower(String.join("\n", memoryRepository.loadAll(
                    new MemoryKey(conversation.ownerId(), conversation.id()))));
            for (String fragment : expect.memoryNotContains()) {
                if (stored.contains(lower(fragment))) {
                    failures.add("the stored chat memory contains \"" + fragment + "\", which must not be kept");
                }
            }
        }
    }

    private static void checkPlan(Scenario.PlanExpectation expected, List<SupervisorChatResponse> answers,
                                  Map<String, UUID> keys, List<String> failures) {
        if (expected == null) {
            return;
        }
        DailyPlan plan = null;
        for (SupervisorChatResponse answer : answers) {
            if (answer.plan() != null) {
                plan = answer.plan();
            }
        }
        if (plan == null) {
            if (Boolean.TRUE.equals(expected.required())) {
                failures.add("no validated plan was returned");
            }
            return;
        }
        if (plan.totalMinutes() > plan.availableMinutes()) {
            failures.add("the plan has " + plan.totalMinutes() + " minutes but only " + plan.availableMinutes()
                    + " are available");
        }
        if (expected.maxTotalMinutes() != null && plan.totalMinutes() > expected.maxTotalMinutes()) {
            failures.add("the plan has " + plan.totalMinutes() + " minutes, more than " + expected.maxTotalMinutes());
        }
        for (String key : nz(expected.excludes())) {
            UUID excluded = keys.get(key);
            if (plan.entries().stream().anyMatch(entry -> entry.taskId().equals(excluded))) {
                failures.add("the plan contains task '" + key + "', which it must not");
            }
        }
    }

    private static void checkNames(String kind, Scenario.Names expected, Set<String> actual, List<String> failures) {
        if (expected == null) {
            return;
        }
        for (String name : nz(expected.required())) {
            if (!actual.contains(name)) {
                failures.add("expected " + kind + " " + name + " to run; ran " + actual);
            }
        }
        for (String name : nz(expected.forbidden())) {
            if (actual.contains(name)) {
                failures.add(kind + " " + name + " must not run, but it did");
            }
        }
        if (Boolean.TRUE.equals(expected.none()) && !actual.isEmpty()) {
            failures.add("expected no " + kind + " to run; ran " + actual);
        }
    }

    private void checkTask(Scenario scenario, Scenario.TaskExpectation expected, List<Task> current,
                           Map<String, UUID> keys, Map<String, UUID> users, List<String> failures) {
        String ownerName = expected.owner() == null ? scenario.userOrDefault() : expected.owner();
        UUID owner = users.get(ownerName);
        String label;
        List<Task> matches;
        if (expected.key() != null) {
            UUID id = keys.get(expected.key());
            if (id == null) {
                throw new IllegalStateException(scenario.id() + ": unknown fixture key " + expected.key());
            }
            label = "task '" + expected.key() + "'";
            matches = current.stream().filter(task -> task.getId().equals(id)).toList();
        } else if (expected.title() != null) {
            label = "task titled \"" + expected.title() + "\"";
            matches = current.stream().filter(task -> task.getOwnerId().equals(owner))
                    .filter(task -> task.getTitle().equalsIgnoreCase(expected.title())).toList();
        } else if (expected.titleContains() != null) {
            String fragment = lower(expected.titleContains());
            label = "task with \"" + expected.titleContains() + "\" in its title";
            matches = current.stream().filter(task -> task.getOwnerId().equals(owner))
                    .filter(task -> lower(task.getTitle()).contains(fragment)).toList();
        } else {
            throw new IllegalStateException(scenario.id() + ": a task expectation needs key, title or titleContains");
        }

        boolean shouldExist = !Boolean.FALSE.equals(expected.exists());
        if (!shouldExist) {
            if (!matches.isEmpty()) {
                failures.add(label + " exists but must not");
            }
            return;
        }
        if (matches.size() != 1) {
            failures.add("expected exactly one " + label + " for " + ownerName + ", found " + matches.size());
            return;
        }
        Task task = matches.get(0);
        if (expected.status() != null && task.getStatus() != expected.status()) {
            failures.add(label + " has status " + task.getStatus() + ", expected " + expected.status());
        }
        if (expected.priority() != null && task.getPriority() != expected.priority()) {
            failures.add(label + " has priority " + task.getPriority() + ", expected " + expected.priority());
        }
        if (expected.dueDate() != null && !expected.dueDate().equals(task.getDueDate())) {
            failures.add(label + " is due " + task.getDueDate() + ", expected " + expected.dueDate());
        }
    }

    // ---------------------------------------------------------------------------------------------- helpers

    /** The fields a user or an agent can change; timestamps are left out on purpose. */
    record Snapshot(UUID id, UUID owner, String title, String description, TaskPriority priority, TaskStatus status,
                    LocalDate dueDate) {
        static Snapshot of(Task task) {
            return new Snapshot(task.getId(), task.getOwnerId(), task.getTitle(), task.getDescription(),
                    task.getPriority(), task.getStatus(), task.getDueDate());
        }
    }

    private static <T> List<T> nz(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String lower(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    private static long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
