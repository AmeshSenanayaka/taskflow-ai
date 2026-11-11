# TaskFlow AI — Phase 1 implementation notes

Companion notes for the article *"Phase 1: The First AI Agent with LangChain4j and Azure OpenAI"*. Phase 0 built a
conventional Spring Boot + Next.js task manager; Phase 1 puts a single agent, the `TaskAgent`, in front of the same
business services. Nothing from Phase 0 was rewritten: the REST API, services, repositories, migrations and the
dashboard UI are unchanged, and the Phase 0 test suite still passes.

## 1. Architecture

```text
Browser (Next.js /assistant)
   │  POST /api/ai/chat { conversationId?, message }
   ▼
AiChatController            validates input, nothing else
   │
   ▼
AgentService                resolves user + conversation, per-conversation lock, MDC correlation ids,
   │                        translates provider failures, assembles ChatResponse
   ▼
TaskAgent (AI Service)      LangChain4j proxy: system prompt (resource file) + MessageWindowChatMemory
   │  ◀──────────────────▶  Azure OpenAI chat completions (AzureOpenAiChatModel)
   ▼
TaskTools (@Tool)           createTask · findTasks · listOverdueTasks · getTaskById · updateTask ·
   │                        updateTaskStatus · getTaskStatistics · requestTaskDeletion
   ▼
TaskService / DashboardService / PreferencesService   (Phase 0, unchanged)
   ▼
TaskRepository → PostgreSQL
```

Deletion is the only destructive path and it is split in two:

```text
chat turn:   requestTaskDeletion ──▶ ConfirmationService.request(user, conversation, DELETE_TASK, taskId)
                                     ──▶ ChatResponse.status = PENDING_CONFIRMATION (+ opaque confirmationId)
user click:  POST /api/ai/confirmations/{id}/approve ──▶ ConfirmationService.consume(...) ──▶ TaskService.delete
```

Responsibilities are kept in separate packages under `com.taskflow.ai` so that Phase 2 can lift the `TaskAgent`
unchanged into a supervisor:

| Package | Contents | Why separate |
|---|---|---|
| `config` | `AiProperties` (`taskflow.ai.*`), `AiConfig` | LLM configuration; the `ChatModel` bean exists only when credentials are set |
| `agent` | `TaskAgent`, `TaskAgentProvider`, `AgentExecutionContext` | The agent definition and how it is built; no HTTP knowledge |
| `tool` | `TaskTools`, `ToolResults`, `TaskToolErrorHandler` | The model-facing surface; delegates to services, never repositories |
| `memory` | `ConversationService`, `ConversationStore`, `InMemoryConversationStore`, `Conversation` | Conversation lifecycle, ownership, bounded memory |
| `confirmation` | `ConfirmationService`, `PendingConfirmation`, `ConfirmationOperation` | Authorisation for destructive operations |
| `service` | `AgentService` | Orchestration of one chat turn and of approvals |
| `controller` | `AiChatController` | HTTP only |
| `identity` | `CurrentUserProvider`, `DevCurrentUserProvider` | The seam where real authentication plugs in |
| `observability` | `ChatModelLoggingListener` | Structured logs for every LLM round trip |
| `dto`, `exception` | `ChatRequest`, `ChatResponse`, `PendingConfirmationResponse`, `AgentExecutionInfo`, …; `AiServiceException`, `AiNotConfiguredException`, `ConversationNotFoundException`, `ConfirmationException` | API contracts and user-safe errors |

## 2. Files created and modified

**Backend — created**

- `backend/src/main/java/com/taskflow/ai/**` (all classes listed above)
- `backend/src/main/resources/prompts/task-agent-system.txt` — the system prompt
- `backend/.env.example` — every environment variable, without secrets
- Tests: `ScriptedChatModel`, `AiTestConfig`, `AbstractAiIntegrationTest`, `AiTestFixtures`, `TaskToolsTest`,
  `TaskToolErrorHandlerTest`, `ConfirmationServiceTest`, `ConversationServiceTest`, `AgentServiceTest`,
  `TaskAgentIntegrationTest`, `AiChatApiIntegrationTest`, `AiNotConfiguredApiTest`, `AzureOpenAiLiveTest` (manual)

**Backend — modified**

- `pom.xml`: `langchain4j-bom` 1.23.0, `langchain4j`, `langchain4j-azure-open-ai`
- `application.yml`: `taskflow.ai.*` block bound to `AZURE_OPENAI_*` / `AI_*` variables, MDC-aware log pattern
- `GlobalExceptionHandler`: handlers for the four AI exceptions (404 / 403 / 410 / 429 / 503 / 504 as `ProblemDetail`)
- `OpenApiConfig`: `AI Assistant` tag, version `0.2.0 (Phase 1)`

**Frontend — created**

- `types/ai.ts`, `lib/api/ai.ts`, `hooks/use-assistant.ts`
- `components/assistant/{assistant-chat,message-bubble,confirmation-card,chat-composer,example-prompts}.tsx`
- `app/assistant/page.tsx`, `__tests__/assistant.test.tsx`

**Frontend — modified**

- `components/layout/sidebar-nav.tsx`: "AI Assistant" entry

**Docs**: `README.md` (sections 6, 9, 11–15), this file, `docs/phase-1/screenshots/`.

## 3. Key implementation decisions

1. **AI Services over a hand-written loop.** `TaskAgent` is an interface; `AiServices.builder(TaskAgent.class)` wires the
   chat model, memory provider, tools, error handlers and the tool-calling loop. The loop is LangChain4j's own
   (`maxSequentialToolsInvocations` bounds it), so the article can focus on *what* the agent may do rather than on
   re-implementing function calling.
2. **Tools delegate, never implement.** Every `@Tool` calls a Phase 0 service and re-validates its inputs with the same
   Jakarta `Validator` the controllers use, so an LLM cannot create a task the REST API would reject. Tools return small
   string-typed records (`ToolResults`) so the JSON the model sees is predictable; `updateTask` merges partial changes
   onto the current task because the service's `update` has PUT semantics.
3. **Date context in the prompt, not in the model.** `AgentService` computes "today" from the `Clock` bean and the user's
   timezone preference and injects it into the system prompt (`{{currentDate}}`, `{{dayOfWeek}}`, `{{timezone}}`), the
   same source the dashboard uses, so "tomorrow" means the same thing in chat and in the statistics.
4. **Backend-enforced confirmation.** There is no `deleteTask` tool. `requestTaskDeletion` creates a `PendingConfirmation`
   bound to user, conversation, operation and task, with an opaque single-use id and a TTL. Only the REST approve
   endpoint can consume it (`ConcurrentHashMap.remove` makes consumption atomic, which prevents replay), and a mismatch
   in ownership burns it. The tool result tells the model explicitly that nothing was deleted, and the UI renders the
   confirmation from the structured response rather than from the model's text.
5. **Per-request context without plumbing.** `AgentExecutionContext` (a `ThreadLocal`, opened by `AgentService`) carries
   the user and conversation ids to the tools and collects what they did (`dataChanged`, pending confirmation, tool
   timings). Tools run synchronously on the calling thread, so this is safe and keeps tool signatures model-friendly.
6. **Memory abstraction ready for persistence.** `ConversationStore` extends LangChain4j's `ChatMemoryStore` and adds
   ownership and retention; `MessageWindowChatMemory` (30 messages, system message always kept) reads through it. Swapping
   the in-memory implementation for a JPA one touches no agent code. One `ReentrantLock` per conversation serialises
   concurrent turns so tool calls and memory updates cannot interleave.
7. **Fail soft without credentials, fail fast with partial ones.** The `ChatModel` bean is conditional on the API key;
   without it `/api/ai/status` reports `available: false` and chat returns `503` with the variable names. With the key but
   without endpoint or deployment, startup throws a message listing what is missing.
8. **Errors are translated, not leaked.** `TaskToolErrorHandler` passes expected problems (unknown task, bad date,
   validation) to the model verbatim and hides everything else behind a generic message after logging it.
   `AgentService.translate` maps LangChain4j/Azure exceptions to `AiServiceException` (429 rate limit, 504 timeout, 503
   auth/deployment/unknown) with user-facing text and a `retryable` flag; the original exception is logged with the
   request id. Retries inside the Azure SDK are capped (`maxRetries=1`).
9. **Observability with what the library offers.** `ChatModelListener` logs duration, model, finish reason and token
   usage per LLM call; `afterToolExecution` logs each tool with its duration and outcome; MDC carries `requestId` and
   `conversationId`; the same data is returned to the client in `execution` for debugging in the UI.
10. **Development identity, stated plainly.** `DevCurrentUserProvider` returns a fixed id. Conversations and confirmations
    are keyed by it, so the authorisation checks are real, but the README and this document say clearly that there is no
    multi-user isolation yet.
11. **Frontend keeps the Phase 0 pattern.** `useAssistant` is a small hook, not a chat framework: it persists the
    transcript in `sessionStorage`, retries once with a fresh conversation when the backend has forgotten the old one
    (404), and calls the shared `invalidate()` whenever a response says `dataChanged`, so the dashboard and task lists
    refresh exactly as they do after a drawer edit.

## 4. Azure OpenAI configuration

See README §6. In short: deploy a tool-calling chat model in Azure AI Foundry, then set
`AZURE_OPENAI_ENDPOINT`, `AZURE_OPENAI_API_KEY` and `AZURE_OPENAI_DEPLOYMENT_NAME` (optionally
`AZURE_OPENAI_API_VERSION`; blank uses the latest version the Azure SDK knows). `backend/.env.example` lists every
variable with its default.

## 5. Running

```bash
# database
cd infrastructure && docker compose up -d
# backend (with the three AZURE_OPENAI_* variables exported)
cd backend && mvn spring-boot:run
# frontend
cd frontend && npm install && npm run dev      # http://localhost:3000/assistant
```

## 6. Example chat requests

```bash
curl -s localhost:8080/api/ai/chat -H 'Content-Type: application/json' \
  -d '{"message":"Create a high-priority task to review the architecture document tomorrow."}'
# -> {"conversationId":"…","message":"Created …","status":"COMPLETED","dataChanged":true,"execution":{…}}

curl -s localhost:8080/api/ai/chat -H 'Content-Type: application/json' \
  -d '{"conversationId":"<id>","message":"Change its priority to LOW."}'

curl -s localhost:8080/api/ai/chat -H 'Content-Type: application/json' \
  -d '{"conversationId":"<id>","message":"Delete the architecture review task."}'
# -> "status":"PENDING_CONFIRMATION","pendingConfirmation":{"confirmationId":"…","taskTitle":"…"}

curl -s localhost:8080/api/ai/confirmations/<confirmationId>/approve -H 'Content-Type: application/json' \
  -d '{"conversationId":"<id>"}'
# -> {"message":"Deleted \"…\".","dataChanged":true,…}   (a second call returns 404)
```

## 7. Verification

All commands were run on 2026-11-04 against the code in this repository.

| Check | Command | Result |
|---|---|---|
| Backend unit + integration tests (Testcontainers PostgreSQL, scripted chat model) | `mvn clean verify` | 97 tests, 0 failures, 1 skipped (`AzureOpenAiLiveTest`, needs credentials), BUILD SUCCESS |
| Phase 0 backend tests still pass | part of the above | `TaskServiceTest` 10, `DashboardServiceTest` 2, `TaskRepositoryIntegrationTest` 9, `TaskApiIntegrationTest` 17 |
| Phase 1 backend tests | part of the above | `TaskToolsTest` 12, `TaskToolErrorHandlerTest` 3, `ConfirmationServiceTest` 6, `ConversationServiceTest` 8, `AgentServiceTest` 4, `TaskAgentIntegrationTest` 9, `AiChatApiIntegrationTest` 13, `AiNotConfiguredApiTest` 3 |
| Frontend types / lint | `npm run typecheck`, `npm run lint` | clean |
| Frontend tests (Vitest + Testing Library) | `npm test` | 22 tests passed (15 Phase 0 + 7 assistant) |
| Frontend production build | `npm run build` | success, `/assistant` prerendered |
| Packaged backend without Azure credentials | `java -jar …` then `GET /api/ai/status`, `POST /api/ai/chat` | `available: false`; chat answers `503` "AI assistant not configured" with the variable names; Swagger lists the AI Assistant tag |
| Assistant UI (production build, Playwright, `/api/ai/*` mocked at the network layer) | `assistant-shots.mjs` | example prompts, typing indicator, two exchanges, pending-confirmation card with Approve/Keep, mobile layout, 0 console errors |

Not verified here: behaviour against a real Azure OpenAI deployment. Run `AzureOpenAiLiveTest` with your credentials
(README §13) to confirm the model selects the tools as expected; the standard suite proves the loop with scripted
tool calls.

One Phase 0 test-infrastructure fix was needed: `AbstractIntegrationTest` now starts the PostgreSQL container once
per JVM (singleton pattern) instead of per test class, because Spring caches application contexts across classes and
a cached context would otherwise point at a stopped container.

## 8. Known limitations

- No authentication; a fixed development identity owns every conversation (documented in README §14).
- Conversation memory and pending confirmations live in process memory: bounded and expiring, but lost on restart and
  not shared between instances.
- No streaming responses.
- Live Azure behaviour was validated only through the optional `AzureOpenAiLiveTest`, which requires credentials; the
  standard suite proves the tool loop with a scripted model.
- `LIKE`-based task search means the agent asks for clarification more often than a semantic search would.

## 9. Suggested commit and pull request

Commit message:

```text
feat(phase-1): add TaskAgent with LangChain4j, Azure OpenAI and chat UI

- LangChain4j 1.23 AI Service (TaskAgent) with tools over TaskService
- Azure OpenAI chat model configured from AZURE_OPENAI_* env vars
- bounded per-conversation memory with ownership and retention
- backend-enforced, single-use, expiring confirmation for deletions
- /api/ai/** chat endpoints with ProblemDetail errors and OpenAPI docs
- Next.js /assistant chat page with confirmation cards and data refresh
- unit + Testcontainers tests with a scripted chat model (no credentials)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
```

Pull request title: **Phase 1: TaskAgent with LangChain4j and Azure OpenAI**

Pull request description:

```markdown
## Summary
- Adds the first AI agent (`TaskAgent`) using LangChain4j AI Services and Azure OpenAI (Azure AI Foundry).
- Exposes the Phase 0 `TaskService` operations as `@Tool`s; the agent creates, finds, updates and completes tasks
  from natural language and keeps conversation context.
- Deletion requires a backend-enforced confirmation (single-use, expiring, bound to user/conversation/task);
  the LLM has no delete tool.
- New `/api/ai/**` endpoints (OpenAPI documented), structured logging with correlation ids and token usage.
- New `/assistant` page in the Next.js app with confirmation cards; task views refresh after agent mutations.
- Phase 0 APIs and UI are unchanged; the app still boots and works without Azure credentials.

## Test plan
- [x] `mvn clean verify` (unit + Testcontainers, scripted chat model)
- [x] `npm run typecheck && npm run lint && npm test && npm run build`
- [ ] `AzureOpenAiLiveTest` against a real deployment (needs credentials)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
```
