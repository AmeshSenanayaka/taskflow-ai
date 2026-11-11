# TaskFlow AI — Phase 2 implementation notes

Companion notes for the article *"Phase 2: Multi-Agent Orchestration with LangChain4j"*. Phase 1 put a single
`TaskAgent` in front of the Phase 0 services. Phase 2 adds three read-only specialists and a supervisor that routes
between all four, built with the `langchain4j-agentic` module. The Phase 1 endpoint (`POST /api/ai/chat`) is unchanged
and keeps working next to the new one, so the single agent and the multi-agent system can be compared on the same data.

## 1. Architecture

```text
Browser (Next.js /assistant, "Supervisor" mode)
   │  POST /api/ai/supervisor/chat { conversationId, message }
   ▼
AiChatController              validates input, nothing else
   │
   ▼
MultiAgentService             lock per conversation, request id + conversation id in the MDC,
   │                          AgentRunRecorder per request, provider failures -> AiServiceException
   ▼
TaskFlowSupervisor            LangChain4j supervisor agent (AgenticServices.supervisorBuilder)
   │   planner LLM: "which agent next?" -> {"agentName": "...", "arguments": {"request": "..."}} | "done"
   │   own MessageWindowChatMemory per conversation (CHAT_MEMORY strategy)
   │
   ├──▶ TaskAgent        @Agent + @MemoryId   TaskTools        createTask · getTaskById · listTasks ·
   │                                                           updateTask · updateTaskStatus · getToday
   ├──▶ PriorityAgent    @Agent               TaskQueryTools   findTasks · getTaskStatistics · getPlanningContext
   ├──▶ PlanningAgent    @Agent               TaskQueryTools   (same, read-only)
   └──▶ SummaryAgent     @Agent               TaskQueryTools   (same, read-only)
                                  │
                                  ▼
          TaskService / DashboardService / PreferencesService  (Phase 0, unchanged)  ──▶  PostgreSQL
```

One supervisor turn, for "Summarise my week and plan tomorrow":

```text
planner ──▶ SummaryAgent  [getPlanningContext, getTaskStatistics, findTasks] ──▶ "7 of 20 done, 2 overdue…"
planner ──▶ PlanningAgent [getPlanningContext, findTasks]                   ──▶ "Plan for 2026-11-09: …"
planner ──▶ done
response: message = PlanningAgent's answer (LAST), agents = [SummaryAgent, PlanningAgent], llmCalls = 8
```

Package layout under `com.taskflow.ai` (Phase 1 packages unchanged):

| Package | Contents | Role |
|---|---|---|
| `agent` | `TaskAgent` (modified), `PriorityAgent`, `PlanningAgent`, `SummaryAgent`, `TaskFlowSupervisor` | Agent interfaces: `@Agent` card (name, description, `outputKey`), `@SystemMessage(fromResource)`, `@UserMessage`, `@V("request")`, `@MemoryId` where memory is wanted |
| `service` | `MultiAgentService` | Running one supervisor request: lock per conversation, MDC ids, error translation, response assembly |
| `orchestration` | `TaskFlowAgentFactory`, `AgentRunRecorder`, `AgentContextProvider`, `TokenAccountingChatModel` | Building the agentic system (plain Java, testable without Spring) and observability |
| `tool` | `TaskTools` (modified), `TaskQueryTools` (new) | Read/write tools for `TaskAgent`; read-only tools for the specialists |
| `config` | `MultiAgentConfig`, `SupervisorProperties` | Spring wiring and `taskflow.ai.supervisor.*` limits |
| `dto` | `SupervisorChatResponse`, `AgentCallInfo`, `ToolCallInfo`, `AgentDescriptor` | API contract of the new endpoints |
| `resources/prompts` | `priority-agent-system.txt`, `planning-agent-system.txt`, `summary-agent-system.txt`, `supervisor-context.txt` | Prompts as files, reviewed like code |

## 2. Files created and modified

**Backend — created**

- `ai/agent/PriorityAgent.java`, `PlanningAgent.java`, `SummaryAgent.java`, `TaskFlowSupervisor.java`
- `ai/service/MultiAgentService.java`
- `ai/orchestration/TaskFlowAgentFactory.java`, `AgentRunRecorder.java`, `AgentContextProvider.java`,
  `TokenAccountingChatModel.java`
- `ai/tool/TaskQueryTools.java`
- `ai/config/MultiAgentConfig.java`, `SupervisorProperties.java`
- `ai/dto/SupervisorChatResponse.java`, `AgentCallInfo.java`, `ToolCallInfo.java`, `AgentDescriptor.java`
- `resources/prompts/priority-agent-system.txt`, `planning-agent-system.txt`, `summary-agent-system.txt`,
  `supervisor-context.txt`
- Tests: `ai/ScriptedChatModel`, `ai/AiTestConfig`, `ai/AbstractAiIntegrationTest`, `ai/SupervisorApiIntegrationTest`,
  `ai/orchestration/TaskFlowSupervisorTest`, `ai/tool/TaskToolsTest`, `ai/tool/TaskQueryToolsTest`

**Backend — modified**

- `pom.xml`: `langchain4j-agentic` 1.23.0-beta33
- `ai/agent/TaskAgent.java`: `@Agent` card, `@UserMessage("{{request}}")` + `@V("request")`, returns `String`
  (it is now both the Phase 1 `@AiService` and a Phase 2 sub-agent)
- `ai/tool/TaskTools.java`: `updateTask`, `updateTaskStatus`, `getToday` tools
- `controller/AiChatController.java`: `POST /api/ai/supervisor/chat`, `GET /api/ai/agents`; `/chat` adapted to the
  `String` return type
- `resources/prompts/task-agent-system.txt`: update/status guidance, relative-date guidance
- `application.yml`: `taskflow.ai.supervisor.*` block; `config/OpenApiConfig.java`: version `0.3.0 (Phase 2)`
- `test/.../AbstractIntegrationTest.java`: singleton Testcontainers container (see decision 10)
- `backend/.env.example`: OpenAI + Phase 2 variables (the file previously described an Azure setup that the code
  does not use)

**Frontend — created**

- `components/assistant/mode-switch.tsx`

**Frontend — modified**

- `types/ai.ts` (`AssistantMode`, `AgentCallInfo`, `AgentDescriptor`, `llmCalls`), `lib/api/ai.ts` (`supervise`, `agents`)
- `hooks/use-assistant.ts` (mode state, persisted; routes to the right endpoint)
- `components/assistant/assistant-chat.tsx` (mode switch, header), `example-prompts.tsx` (prompts per mode),
  `message-bubble.tsx` (agents and LLM calls in "Execution details"), `app/assistant/page.tsx`
- `__tests__/assistant.test.tsx` (two supervisor tests; `conversationId` expectation fixed, see decision 11)

**Docs**: `README.md`, `docs/phase-2/IMPLEMENTATION.md`, `docs/phase-2/screenshots/*.png`

## 3. Key implementation decisions

1. **LangChain4j's supervisor, not a hand-written router.** `AgenticServices.supervisorBuilder(TaskFlowSupervisor.class)`
   gives the planner loop, the agent cards, the "done" protocol, memory strategies and invocation limits. Phase 3 will
   compare this LLM router with cheaper alternatives, so the baseline should be the library's reference behaviour.
2. **Agent cards are the routing contract.** The planner only sees `@Agent(name, description)` and the argument list.
   The descriptions therefore say what the agent is *for* and what it does *not* do ("it only recommends; it does not
   change tasks"). `supervisor-context.txt` adds the routing policy: one agent per concern, call each at most once,
   answer greetings directly, never invent task data.
3. **Read-only by construction.** `PriorityAgent`, `PlanningAgent` and `SummaryAgent` receive `TaskQueryTools` only.
   No prompt injection through a task title, and no planner mistake, can turn an analysis into a mutation, because the
   mutating tools are not in their tool list. `TaskAgent` is the single write path, as in Phase 1.
4. **Date context injected per call, not per build.** `AgentBuilder.systemMessageTransformer` appends
   `AgentContextProvider.render()` (today in the user's timezone, weekday, daily available minutes) to every rendered
   system message. Prompts stay static files; "overdue" means the same for the agents and the dashboard, because both
   use the same `Clock` and `PreferencesService`. `TaskAgent` additionally has a `getToday` tool so the Phase 1
   endpoint, which has no transformer, can resolve relative dates too.
5. **`LAST` response strategy with a safe fallback.** LangChain4j's `LAST` returns `null` when the planner answers
   without calling any agent. The factory therefore builds the supervisor with `SUMMARY` (the planner always writes a
   recap) and an `output` function that prefers the last sub-agent's verbatim answer when there is one. A plan or a
   summary reaches the user unchanged; greetings get the planner's own answer. `SUMMARY` and `SCORED` remain available
   through `AI_SUPERVISOR_RESPONSE_STRATEGY`.
6. **Observability through an `AgentListener` and a `ChatModel` decorator.** `AgentRunRecorder` (listener, inherited by
   sub-agents) records which agents ran, how long, which tools they called and whether they failed, in a per-request
   `ThreadLocal`. `TokenAccountingChatModel` wraps the provider model so every LLM call made by any agent is counted
   and its token usage summed, independently of the provider. Both feed `SupervisorChatResponse.execution` and the
   logs (`agent.invoked`, `tool.executed`, `supervisor.completed`).
7. **`dataChanged` is derived from tool executions**, not from the model's words: it is `true` only when a mutating
   tool (`createTask`, `updateTask`, `updateTaskStatus`) executed successfully. The UI refreshes lists and statistics
   on that flag.
8. **Two `TaskAgent` instances, one interface.** The Spring starter still builds the Phase 1 `@AiService` bean; the
   factory builds a second, agentic instance for the supervisor. Sub-agents are not Spring beans (a second bean of type
   `TaskAgent` would make injection ambiguous). Both instances share the same tools, services and model.
9. **Limits are configuration.** `max-agent-invocations` (5), `max-tool-round-trips` (8) and the supervisor's memory
   window (20) bound the cost of a confused planner. `SupervisorProperties` is a validated `@ConfigurationProperties`
   record, so a bad value fails at startup.
10. **Singleton Testcontainers container.** Spring caches application contexts across test classes. With the previous
    per-class `@Container`, the first AI test class stopped its container while a cached context still pointed at its
    port, and the next class failed with "Connection refused". The container is now started once per JVM in a static
    initializer; the Testcontainers reaper removes it on exit.
11. **Tests replay the LLM, run everything else for real.** `ScriptedChatModel` answers in order (planner choice, tool
    call, text, "done") while the real supervisor loop, the real tools and, in the integration tests, the real database
    run. The LangChain4j Spring starter refuses two `ChatModel` beans (it ignores `@Primary`), so the AI integration
    tests exclude the OpenAI auto-configuration and provide the scripted model instead. The existing frontend assistant
    test expected `conversationId: null` although the hook has always generated an id before the first call; it now
    expects any string.

## 4. OpenAI configuration

Unchanged from Phase 1: set `OPENAI_API_KEY` (and optionally `OPENAI_MODEL_NAME`, default `gpt-4o-mini`). Both the
Phase 1 agent and the Phase 2 supervisor use the `ChatModel` bean created by `langchain4j-open-ai-spring-boot-starter`.
The Phase 2 limits are listed in the README (section 9) and in `backend/.env.example`.

## 5. Running

```bash
cd infrastructure && docker compose up -d                # PostgreSQL
cd ../backend && export OPENAI_API_KEY=sk-... && mvn spring-boot:run
cd ../frontend && npm install && npm run dev             # http://localhost:3000/assistant, "Supervisor" mode
```

## 6. Example chat requests

```bash
B=http://localhost:8080
curl -s $B/api/ai/supervisor/chat -H "Content-Type: application/json" \
  -d '{"conversationId":"demo-1","message":"Create a high-priority task to review the architecture document tomorrow."}'
curl -s $B/api/ai/supervisor/chat -H "Content-Type: application/json" \
  -d '{"conversationId":"demo-1","message":"What should I focus on first?"}'
curl -s $B/api/ai/supervisor/chat -H "Content-Type: application/json" \
  -d '{"conversationId":"demo-1","message":"Plan my day."}'
curl -s $B/api/ai/supervisor/chat -H "Content-Type: application/json" \
  -d '{"conversationId":"demo-1","message":"Summarise my progress this week."}'
curl -s $B/api/ai/supervisor/chat -H "Content-Type: application/json" \
  -d '{"conversationId":"demo-1","message":"Mark the architecture review as completed."}'
curl -s $B/api/ai/agents
```

## 7. Verification

| Check | Result |
|---|---|
| `mvn clean verify` (backend, Docker running) | 64 tests, 0 failures, 0 errors: TaskFlowSupervisorTest 9, SupervisorApiIntegrationTest 6, TaskToolsTest 6, TaskQueryToolsTest 5, Phase 0 suites 38 |
| `npm run typecheck`, `npm run lint` | clean |
| `npm test` | 24 passed (assistant 9 incl. 2 supervisor tests) |
| `npm run build` | success |
| Packaged jar on port 8081 + production frontend | `/api/ai/agents` lists the four agents; `/v3/api-docs` version `0.3.0 (Phase 2)` with the two new paths; Phase 0 endpoints unchanged |
| Playwright on the production build (AI endpoints mocked at the network layer) | supervisor chat, execution details, mode switch, mobile layout, 0 console errors |

**Not verified here:** behaviour against the real OpenAI API (no key available in the build environment). The
scripted tests prove the loop, the wiring and the tools; routing quality with `gpt-4o-mini` must be checked with a key.

## 8. Known limitations

See README section 14: extra LLM calls per request, probabilistic routing, assumed task durations, in-process memory,
no delete tool, no streaming.

## 9. Suggested commit and pull request

Commit message:

```text
feat(phase-2): Add TaskFlowSupervisor with PriorityAgent, PlanningAgent and SummaryAgent

Build the multi-agent system with langchain4j-agentic: a supervisor agent routes
each request to TaskAgent (read/write), PriorityAgent, PlanningAgent or SummaryAgent
(read-only by construction), with per-conversation memory, date/timezone context
injected into every prompt, configurable invocation limits and per-request
observability (agents, tools, LLM calls, tokens).

- POST /api/ai/supervisor/chat and GET /api/ai/agents; Phase 1 /api/ai/chat unchanged
- TaskTools gains updateTask, updateTaskStatus and getToday
- UI: Supervisor/TaskAgent mode switch, execution details per reply
- Tests with a scripted ChatModel (no OpenAI key needed); singleton Testcontainers
  container so cached Spring contexts keep a live database

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
```

Pull request title: **Phase 2: TaskFlowSupervisor orchestrating TaskAgent, PriorityAgent, PlanningAgent and SummaryAgent**

Pull request description:

```markdown
## Summary
- Adds the Phase 2 multi-agent system on LangChain4j `langchain4j-agentic`: `TaskFlowSupervisor` (LLM planner +
  memory) routing to `TaskAgent`, `PriorityAgent`, `PlanningAgent`, `SummaryAgent`
- Specialists are read-only by construction (`TaskQueryTools` only); `TaskAgent` is the single write path and gains
  `updateTask` / `updateTaskStatus` / `getToday`
- Today's date, timezone and daily available minutes are injected into every agent prompt at call time
- New endpoints `POST /api/ai/supervisor/chat` (answer + agents, tools, LLM calls, tokens, dataChanged) and
  `GET /api/ai/agents`; Phase 1 `POST /api/ai/chat` unchanged
- UI: Supervisor / TaskAgent switch, per-mode example prompts, execution details show the route taken
- Docs: README (Phase 2 sections), `docs/phase-2/IMPLEMENTATION.md`, screenshots, `.env.example`

## Test plan
- [x] `mvn clean verify` — 64 tests green (scripted model, real supervisor loop, real PostgreSQL)
- [x] `npm run typecheck && npm run lint && npm test && npm run build`
- [x] Jar + production frontend smoke test; Playwright run over the assistant page in both modes
- [ ] Manual run with a real `OPENAI_API_KEY`: "plan my day", "what should I focus on", "summarise my week",
      "create … tomorrow" then "mark it as completed"

🤖 Generated with [Claude Code](https://claude.com/claude-code)
```
