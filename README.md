# TaskFlow AI — Phase 1

A modern, full-stack **personal task manager** built with **Spring Boot 3**, **PostgreSQL** and **Next.js**, now with an
**AI assistant** powered by **LangChain4j** and **OpenAI**.

This repository accompanies the Medium article series
**"Building Agentic AI Applications with Java: From Spring Boot to Multi-Agent Systems Using LangChain4j"**.

| Phase | Scope | Status |
|---|---|---|
| Phase 0 | Traditional full-stack task manager | ✅ Implemented |
| **Phase 1** | LangChain4j + OpenAI (`gpt-4o-mini`) + a single `TaskAgent` (this repository state) | ✅ Implemented |
| Phase 2 | `SupervisorAgent` orchestrating `TaskAgent`, `PriorityAgent`, `PlanningAgent`, `SummaryAgent` | Planned |
| Phase 3 | Cost-aware routing: Java rules vs Jev vs cheap model vs LLM supervisor, measured | Planned |

> Phase 1 adds exactly one agent and keeps it deliberately small: the `TaskAgent` creates, finds and reads tasks through
> three LangChain4j tools that call the Phase 0 `TaskService`, and remembers the conversation. It has no delete tool.
> No multi-agent code yet.

---

## 1. Project overview

TaskFlow AI lets a single user organise daily tasks, manage priorities, track deadlines and monitor progress through:

- a responsive **Next.js dashboard** (sidebar navigation, statistics cards, filterable/sortable/paginated task list, create/edit drawer, settings page),
- a documented **REST API** (Swagger UI) backed by PostgreSQL, and
- an **AI assistant** page where you manage tasks in plain language ("create a high-priority task for Friday").

The application is intentionally single-user: there is no authentication, and preferences are a single record
(see [Known limitations](#14-known-limitations)).

### Screenshots

Captured from the running application (see [`docs/phase-0/screenshots`](docs/phase-0/screenshots)).

| Dashboard | Edit drawer | Mobile |
|---|---|---|
| ![Dashboard](docs/phase-0/screenshots/01-dashboard.png) | ![Edit task drawer](docs/phase-0/screenshots/05-edit-drawer.png) | ![Mobile dashboard](docs/phase-0/screenshots/07-mobile-dashboard.png) |

Phase 1 (see [`docs/phase-1/screenshots`](docs/phase-1/screenshots)):

| AI assistant | Mobile |
|---|---|
| ![AI assistant chat](docs/phase-1/screenshots/01-assistant-chat.png) | ![AI assistant on mobile](docs/phase-1/screenshots/03-assistant-mobile.png) |

### Features

- Full task CRUD with status (`TODO`, `IN_PROGRESS`, `COMPLETED`) and priority (`LOW`, `MEDIUM`, `HIGH`)
- Complete / reopen tasks with automatic `completedAt` handling
- Database-level filtering (status, priority, due date, date range, free-text search), sorting and pagination
- Dashboard statistics: total, pending, in progress, completed, overdue, due today (timezone-aware)
- User preferences: IANA timezone and daily available minutes
- Validation on both sides (Jakarta Bean Validation + Zod), consistent `ProblemDetail` error responses
- Loading skeletons, empty states, toasts, delete confirmation, URL-synchronised filters

**Phase 1 — AI assistant**

- `TaskAgent`, a LangChain4j AI Service (`@AiService`) backed by OpenAI, with tool calling over the existing `TaskService`
- Three tools: `createTask`, `getTaskById`, `listTasks` (filters, sorting, pagination) — the model never touches a repository
- Conversation memory: a sliding window of messages per `conversationId` (in process)
- Tool errors are translated into instructions the model can act on (invalid date, unknown task, validation failure);
  provider failures (rate limit, timeout, bad key) become clear `ProblemDetail` responses for the UI
- Chat UI with message bubbles, loading/error states with retry, a "new conversation" action and automatic refresh of task views

---

## 2. Technology stack

**Backend**

- Java 21, Spring Boot 3.5, Spring Web, Spring Data JPA, Spring Validation
- PostgreSQL 16, Flyway migrations
- Springdoc OpenAPI 2 (Swagger UI), Lombok, Maven
- JUnit 5, Mockito, Testcontainers
- **LangChain4j 1.23.0** (`langchain4j-spring-boot-starter`, `langchain4j-open-ai-spring-boot-starter`) and the **OpenAI API** (`gpt-4o-mini` by default)

**Frontend**

- Next.js 16 (App Router), React 19, TypeScript
- Tailwind CSS 4, shadcn/ui (Radix), Lucide icons
- React Hook Form + Zod
- Vitest + Testing Library

**Infrastructure**

- Docker Compose for PostgreSQL (only the database runs in Docker)

---

## 3. Architecture

```text
┌────────────────────────┐        HTTP / JSON        ┌───────────────────────────────┐
│  Next.js (App Router)  │ ────────────────────────▶ │  Spring Boot (REST API)        │
│  components / hooks    │ ◀──────────────────────── │                                │
│  lib/api (fetch client)│                           │  controller  → HTTP only       │
└────────────────────────┘                           │      │                         │
                                                     │  service     → business rules  │
                                                     │      │                         │
                                                     │  repository  → Spring Data JPA │
                                                     │      │         + Specifications│
                                                     └──────┼─────────────────────────┘
                                                            ▼
                                                     ┌───────────────┐
                                                     │  PostgreSQL   │  (Flyway-managed schema)
                                                     │  tasks        │
                                                     │  user_prefs   │
                                                     └───────────────┘
```

Phase 1 adds the agent path next to the REST path. Both end in the same services:

```text
Next.js  /assistant  ──▶  POST /api/ai/chat  ──▶  AiChatController   (validates, translates provider errors)
                                                     │
                                                     ▼
                                               TaskAgent  @AiService   (LangChain4j: system prompt + chat memory)
                                                     │  ◀──────────▶  OpenAI API (chat completions, tool calls)
                                                     ▼
                                               TaskTools  @Tool        (createTask, getTaskById, listTasks)
                                                     │
                                                     ▼
                                               TaskService  ──▶  TaskRepository  ──▶  PostgreSQL
```

The loop: LangChain4j sends the prompt, the conversation memory and the tool schemas to the model. The model answers
either with a tool call or with text. A tool call runs a Java method on `TaskTools`, which validates the arguments and
calls the same `TaskService` the REST API uses; the result goes back to the model, which decides again. Text ends the
loop and becomes the reply. This is **tool calling, not MCP**: the tools live in the same JVM as the agent.

Repository layout:

```text
taskflow-ai/
├── backend/          Spring Boot application (Maven); backend/.env.example lists every variable
├── frontend/         Next.js application
├── infrastructure/   docker-compose.yml for PostgreSQL
├── docs/phase-0/     Implementation notes for the Phase 0 article
├── docs/phase-1/     Implementation notes for the Phase 1 article
└── README.md
```

Backend packages (`backend/src/main/java/com/taskflow`):

```text
controller/     TaskController, DashboardController, PreferencesController, AiChatController (/api/ai/**)
service/        TaskService, DashboardService, PreferencesService
repository/     TaskRepository, PreferencesRepository
specification/  TaskFilter, TaskSort, TaskSpecifications  (DB-level filtering & sorting)
entity/         Task, UserPreferences
enums/          TaskStatus, TaskPriority
dto/            request/ and response/ records (API contracts)
mapper/         entity -> DTO mapping
exception/      GlobalExceptionHandler (ProblemDetail), TaskNotFoundException
validation/     @ValidTimezone
config/         ClockConfig, CorsConfig, OpenApiConfig
ai/             Phase 1 (everything AI lives here)
  agent/          TaskAgent            the AI Service interface (@AiService, @SystemMessage, @MemoryId)
  config/         AiConfig             ChatMemoryProvider (sliding window per conversation)
  tool/           TaskTools            the @Tool methods, delegating to TaskService
  exception/      ToolExecutionException (messages written for the model),
                  AiServiceException     (provider failures translated for the user)
resources/prompts/task-agent-system.txt   the TaskAgent system prompt
```

Frontend layout (`frontend/`):

```text
app/            Routes: / (dashboard), /assistant (AI chat), /tasks, /today, /upcoming, /completed, /settings
components/     layout/, dashboard/, tasks/, assistant/ (chat, bubbles, composer, example prompts), settings/, providers/, ui/
hooks/          useTasks, useDashboardStats, useTaskMutations, useTaskQueryParams, useApiQuery, useAssistant
lib/api/        Centralised fetch client + per-resource API modules (tasks, dashboard, preferences, ai)
lib/validations Zod schemas (task, preferences)
types/          TypeScript types mirroring the backend DTOs
__tests__/      Vitest + Testing Library tests
```

---

## 4. Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java (JDK) | 21 | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Node.js | 20.19+ or 22+ (tested with 24) | `node -v` |
| npm | 10+ | `npm -v` |
| Docker Desktop (or Docker Engine + Compose v2) | any recent | Only required for PostgreSQL and the backend integration tests |

---

## 5. PostgreSQL setup

Start PostgreSQL with Docker Compose:

```bash
cd infrastructure
docker compose up -d
```

This starts `postgres:16-alpine` on `localhost:5432` with database/user/password `taskflow` / `taskflow` / `taskflow`
(override via `infrastructure/.env`, see `.env.example`). Data is persisted in the `taskflow_pgdata` volume.

Useful commands:

```bash
docker compose ps                 # status
docker compose logs -f postgres   # logs
docker compose down               # stop (keeps data)
docker compose down -v            # stop and delete data
```

> **Port 5432 already in use?** If a PostgreSQL service is installed on your machine it usually owns `5432`, and the
> backend would connect to it instead of the container. Map the container to another port and point the backend at it:
>
> ```bash
> # infrastructure/.env
> POSTGRES_PORT=5433
> ```
>
> ```bash
> # when starting the backend
> DB_URL=jdbc:postgresql://localhost:5433/taskflow mvn spring-boot:run
> ```

If you prefer a locally installed PostgreSQL, create the database and user manually and point the backend at it with the
environment variables in section 8. Flyway creates the tables on first start.

---

## 6. OpenAI configuration

The assistant needs an OpenAI API key. The chat model is created by `langchain4j-open-ai-spring-boot-starter` from the
`langchain4j.open-ai.chat-model.*` properties in `application.yml`, so there is no model code to write:

1. Create an API key at [platform.openai.com](https://platform.openai.com/api-keys).
2. Export it (or copy `backend/.env.example` to `backend/.env`):

```bash
export OPENAI_API_KEY="sk-..."
# optional, defaults shown
export OPENAI_MODEL_NAME="gpt-4o-mini"
export OPENAI_TEMPERATURE="0.2"
```

3. Start the backend. `GET /api/ai/status` returns `{"available": true, "model": "gpt-4o-mini"}` and the
   **AI Assistant** page in the UI shows a green dot.

Any model that supports tool calling works; `gpt-4o-mini` is the default because it is cheap and fast enough for the
demo. Set `OPENAI_LOG_REQUESTS=true` / `OPENAI_LOG_RESPONSES=true` to see the full prompts and tool calls in the log
(development only: they include task text). Keys are never logged and never reach the browser; all LLM traffic stays
in the backend. Never commit a real key: `application.yml` must only reference `${OPENAI_API_KEY}`.

---

## 7. Backend startup

```bash
cd backend
mvn spring-boot:run
```

The API starts on <http://localhost:8080>. On startup Flyway applies `V1__create_tasks.sql` and `V2__create_user_preferences.sql`,
then Hibernate validates that the entities match the schema (`ddl-auto=validate`).

To build a runnable jar:

```bash
mvn clean package -DskipTests
java -jar target/taskflow-backend-0.1.0-SNAPSHOT.jar
```

---

## 8. Frontend startup

```bash
cd frontend
npm install
npm run dev
```

Open <http://localhost:3000>. The frontend talks to the backend at `http://localhost:8080` by default
(configurable, see below).

Production build:

```bash
npm run build
npm start
```

---

## 9. Environment variables

**Backend** (`backend/src/main/resources/application.yml` reads these; all but the API key have defaults):

| Variable | Default | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/taskflow` | JDBC URL |
| `DB_USERNAME` | `taskflow` | Database user |
| `DB_PASSWORD` | `taskflow` | Database password |
| `SERVER_PORT` | `8080` | HTTP port |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated list of allowed origins |
| `OPENAI_API_KEY` | *(unset)* | OpenAI API key; required for the assistant |
| `OPENAI_MODEL_NAME` | `gpt-4o-mini` | Any OpenAI chat model that supports tool calling |
| `OPENAI_TEMPERATURE` | `0.2` | Sampling temperature |
| `OPENAI_LOG_REQUESTS` / `OPENAI_LOG_RESPONSES` | `false` | Log full LLM request/response bodies (development only) |
| `AI_MEMORY_MAX_MESSAGES` | `30` | Messages kept per conversation (sliding window) |

Other `taskflow.ai.*` keys in `application.yml` (`max-tool-round-trips`, `memory.retention`, `confirmation.ttl`, the
`azure` block) are reserved for later phases and are not read by the Phase 1 code.

**Frontend** (copy `frontend/.env.example` to `frontend/.env.local` to override):

| Variable | Default | Description |
|---|---|---|
| `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080` | Base URL of the Spring Boot API |

**Infrastructure** (copy `infrastructure/.env.example` to `infrastructure/.env` to override):

| Variable | Default |
|---|---|
| `POSTGRES_DB` | `taskflow` |
| `POSTGRES_USER` | `taskflow` |
| `POSTGRES_PASSWORD` | `taskflow` |
| `POSTGRES_PORT` | `5432` |

---

## 10. Swagger / OpenAPI

With the backend running:

- Swagger UI: <http://localhost:8080/swagger-ui/index.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>

Endpoints are grouped under the **Tasks**, **Dashboard**, **Preferences** and **AI Assistant** tags, with request/response
schemas, validation constraints and the documented error responses.

---

## 11. API endpoint summary

| Method | Path | Description | Success |
|---|---|---|---|
| `POST` | `/api/tasks` | Create a task (status `TODO`, priority defaults to `MEDIUM`) | `201` |
| `GET` | `/api/tasks` | List tasks with filtering, sorting and pagination | `200` |
| `GET` | `/api/tasks/{id}` | Get one task | `200` |
| `PUT` | `/api/tasks/{id}` | Replace title, description, priority, status, due date | `200` |
| `PATCH` | `/api/tasks/{id}/status` | Change status (complete / reopen) | `200` |
| `DELETE` | `/api/tasks/{id}` | Delete a task | `204` |
| `GET` | `/api/dashboard/stats` | Counts: total, pending, inProgress, completed, overdue, dueToday | `200` |
| `GET` | `/api/preferences` | Read preferences | `200` |
| `PUT` | `/api/preferences` | Update timezone and daily available minutes | `200` |
| `GET` | `/api/ai/status` | Whether the assistant is configured (`available`, `model`) | `200` |
| `POST` | `/api/ai/chat` | Send a message to the `TaskAgent` | `200` |

List query parameters for `GET /api/tasks`:

| Parameter | Example | Notes |
|---|---|---|
| `status` | `TODO` or `TODO,IN_PROGRESS` | One or more statuses |
| `priority` | `HIGH` | |
| `dueDate` | `2026-11-02` | Exact date |
| `dueDateFrom` / `dueDateTo` | `2026-11-03` | Inclusive range |
| `search` | `article` | Case-insensitive match on title or description |
| `sortBy` | `DUE_DATE`, `PRIORITY`, `CREATED_AT` (default), `UPDATED_AT` | Priority sorts by rank, not alphabetically |
| `sortDirection` | `ASC`, `DESC` (default) | |
| `page` / `size` | `0` / `10` | Zero-based page; size 1–100 |

Example:

```http
GET /api/tasks?status=TODO&priority=HIGH&sortBy=DUE_DATE&sortDirection=ASC&page=0&size=10
```

Errors use RFC 9457 Problem Details (`application/problem+json`):

```json
{
  "type": "about:blank",
  "title": "Validation error",
  "status": 400,
  "detail": "Validation failed",
  "instance": "/api/tasks",
  "errors": { "title": "Title must not be blank" }
}
```

| Situation | Status |
|---|---|
| Validation error / invalid enum value / malformed body | `400` |
| Unknown task id | `404` |
| OpenAI rate limited (`retryable: true`) | `429` |
| OpenAI rejected the request (e.g. invalid request) | `502` |
| OpenAI unavailable, bad API key or unknown model | `503` |
| OpenAI timed out | `504` |
| Unexpected error | `500` |

---

## 12. AI chat API

### Send a message

The browser generates a `conversationId` (a UUID) and reuses it for every message of one conversation; the backend keeps
a separate chat memory per id. Both fields are required.

```http
POST /api/ai/chat
Content-Type: application/json

{ "conversationId": "6f1c2d3e-4b5a-4c6d-8e7f-9a0b1c2d3e4f",
  "message": "Create a high-priority task to review the architecture document on 2026-11-14." }
```

```json
{
  "conversationId": "6f1c2d3e-4b5a-4c6d-8e7f-9a0b1c2d3e4f",
  "message": "Created \"Review the architecture document\" with HIGH priority, due 2026-11-14."
}
```

Send the same `conversationId` on the next message to keep context ("Change **its** priority to LOW"); the UI's
**New conversation** button starts a fresh id. A message is limited to 4 000 characters. After every reply the UI
refreshes the task lists and statistics.

With curl:

```bash
curl -s http://localhost:8080/api/ai/chat -H "Content-Type: application/json" \
  -d '{"conversationId":"conv-1","message":"Show my pending tasks."}'
```

### Supported natural-language commands

| You say | The agent does |
|---|---|
| "Create a high-priority task to review the architecture document on Friday 14 November." | `createTask` with `priority=HIGH` and the date in `yyyy-MM-dd` |
| "Show me all my pending high-priority tasks." | `listTasks(statuses=[TODO, IN_PROGRESS], priority=HIGH)` and lists real results only |
| "Search for tasks about the article." | `listTasks(search=article)` |
| "What is the priority of the sprint planning task?" | `listTasks(search=…)` then `getTaskById` |

Everyday words map naturally: *pending/open* = not completed, *done* = completed, *urgent* = HIGH.
Updates, status changes and deletion are not available through the agent yet (see Known limitations); use the task views.

### How tool errors are handled

Every `@Tool` runs through a guard that turns failures into a message the model can act on, because LangChain4j returns
the exception message to the model as the tool result:

| Problem | What the model receives |
|---|---|
| Date not in `yyyy-MM-dd` | "Invalid date 'tomorrow'. Dates must be absolute and in yyyy-MM-dd format…" |
| Unknown task id | "Task not found: … Use listTasks to find the correct task id." |
| Bean Validation failure | "Validation failed: Title must not be blank. Fix the arguments and try again." |
| Unexpected exception | logged with the stack trace; the model gets a generic message and tells the user |

Failures of the model call itself (rate limit, timeout, bad key, network) are translated by `AiServiceException` into
the `ProblemDetail` responses listed in section 11; the UI shows the message with a **Retry** button.

---

## 13. Testing

**Backend** (unit tests with JUnit 5 + Mockito, integration tests with Testcontainers — Docker must be running):

```bash
cd backend
mvn clean verify
```

Covers: task creation defaults, completion/reopen timestamps, updates, unknown ids, dashboard statistics (unit);
Flyway migrations, repository filtering/sorting/pagination, REST endpoints, validation errors, persistence (integration).
These are the Phase 0 tests; the Phase 1 agent has no automated tests yet (a scripted `ChatModel` test double is on the
roadmap). To try the agent, start the backend with `OPENAI_API_KEY` set and use the curl command in section 12.

**Frontend** (Vitest + Testing Library):

```bash
cd frontend
npm test          # run once
npm run test:watch
npm run lint
npm run typecheck
```

Covers: task form validation, task list rendering (badges, overdue highlight, empty/loading/error states),
task creation through the drawer, complete/reopen status updates, date helpers, and the assistant page.

---

## 14. Known limitations

- **No authentication.** Everything runs as a single user; conversation ids are generated by the browser and are not
  bound to an identity.
- **The agent does not know today's date.** Relative dates ("tomorrow", "next Friday") are a guess; give absolute dates,
  or add the date and timezone to the system prompt (planned: inject `currentDate` / `timezone` from the `Clock` bean
  and the user's preference).
- **Read and create only.** There are no `updateTask`, `updateTaskStatus` or delete tools yet. Deletion, when it comes,
  will go through a backend-enforced confirmation rather than a tool the model can call.
- **Conversation memory is in-process**: a sliding window of 30 messages per conversation, lost on restart.
- **No cap on tool round trips beyond LangChain4j's default**, and no token-usage reporting to the UI yet.
- **No streaming**: replies arrive when the agent finishes. Typical turns take 1–4 s with tool calls.
- **Task search is `LIKE`-based**, so the agent may ask you to disambiguate titles more often than a semantic search would.
- **Tool results are the REST DTOs** (`TaskResponse`, `PageResponse`); flat, string-typed result records for the model
  are planned.

---

## 15. Roadmap

- **Phase 1 — Single agent (done).** LangChain4j + OpenAI; the `TaskAgent` exposes `TaskService` operations as tools and
  keeps conversation context. See [`docs/phase-1/IMPLEMENTATION.md`](docs/phase-1/IMPLEMENTATION.md).
  Next steps within Phase 1: date context in the prompt, update/status tools, a confirmation workflow for deletion,
  token usage and tool-call visibility in the response, guardrails, persistent memory, tests with a scripted model.
- **Phase 2 — Multi-agent orchestration.** A `SupervisorAgent` routes requests to specialised agents (`TaskAgent`,
  `PriorityAgent`, `PlanningAgent`, `SummaryAgent`), using the timezone and daily available minutes preferences for planning.
- **Phase 3 — Cost-aware routing.** Compare a Java rules router, Jev (TypeSafe AI's typed decision model), a cheaper
  model and the LLM supervisor on a fixed benchmark: routing accuracy, task success, tokens, cost and latency.

See [`docs/phase-0/IMPLEMENTATION.md`](docs/phase-0/IMPLEMENTATION.md) and
[`docs/phase-1/IMPLEMENTATION.md`](docs/phase-1/IMPLEMENTATION.md) for the design decisions behind each phase.
