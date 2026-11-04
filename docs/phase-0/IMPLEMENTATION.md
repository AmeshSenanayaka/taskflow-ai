# TaskFlow AI — Phase 0 Implementation Notes

These notes explain the important decisions behind Phase 0 so they can be reused when writing the Medium article.
Everything described here is implemented and tested in this repository; nothing AI-related exists yet.

## 1. Why a layered architecture

The backend follows the classic three layers:

```text
controller  →  service  →  repository  →  PostgreSQL
```

- **Controllers** only translate HTTP into method calls: they bind and validate request bodies and query parameters,
  call one service method and return a DTO. They contain no business rules.
- **Services** own the business rules: default priority/status, `completedAt` handling, "today" in the user's timezone,
  not-found semantics. They are plain Spring beans with constructor injection and `@Transactional` boundaries.
- **Repositories** are Spring Data JPA interfaces. Filtering and sorting are expressed as JPA `Specification`s so the
  database does the work.

The layering is deliberately boring. For an educational project the value is in the *separation*, not in extra
abstractions: there are no interfaces-for-everything, no generic response wrappers and no custom frameworks.

## 2. Why business logic is kept out of controllers

Phase 1 will add a `TaskAgent` built with LangChain4j. An LLM agent calls **tools**, and a tool is just a Java method.
If the rules lived in controllers, the agent would have to call HTTP endpoints of its own application, or the rules would
be duplicated. Because every rule lives in `TaskService`, `DashboardService` and `PreferencesService`, Phase 1 only needs
thin tool classes that delegate to those services:

```text
REST controller ─┐
                 ├──▶ TaskService ──▶ TaskRepository ──▶ PostgreSQL
AI tool (later) ─┘
```

Two things make the services agent-friendly already:

- They accept and return **DTO records** (`CreateTaskRequest`, `TaskResponse`, …), never JPA entities, so they can be
  called from any context without an open persistence session.
- They depend on a `java.time.Clock` bean rather than calling `Instant.now()` directly, which keeps date logic
  deterministic in tests and lets an agent reason about "today" consistently.

## 3. How task management works

### Entity and rules

`Task` is a JPA entity with a UUID primary key generated in Java (`UUID.randomUUID()`), a title (≤150 chars),
optional description (≤2000 chars), priority, status, optional due date and three timestamps.

The only non-trivial rule is completion handling, implemented in one place on the entity:

```java
public void changeStatus(TaskStatus newStatus, Instant now) {
    boolean wasCompleted = this.status == TaskStatus.COMPLETED;
    boolean willBeCompleted = newStatus == TaskStatus.COMPLETED;
    this.status = newStatus;
    if (willBeCompleted && !wasCompleted) completedAt = now;   // just completed
    else if (!willBeCompleted)            completedAt = null;  // reopened
}
```

Both `PUT /api/tasks/{id}` and `PATCH /api/tasks/{id}/status` go through this method, so the "set `completedAt` on
completion, clear it on reopen" rule cannot drift between endpoints. A database `CHECK` constraint enforces the same
invariant as a safety net.

Other defaults: new tasks are `TODO`, and priority falls back to `MEDIUM` when omitted. Titles and descriptions are
trimmed; an empty description is stored as `NULL`.

### Validation and errors

Request DTOs carry Jakarta Bean Validation annotations (`@NotBlank`, `@Size`, `@NotNull`, a custom `@ValidTimezone`).
A single `@RestControllerAdvice` converts every failure into a Spring `ProblemDetail`:

| Case | Status | Extra |
|---|---|---|
| Bean validation failure | 400 | `errors: { field: message }` |
| Invalid enum in JSON body (e.g. `"priority": "URGENT"`) | 400 | `detail` lists allowed values |
| Invalid enum/number in a query parameter | 400 | `detail` lists allowed values |
| Unknown task id | 404 | |
| Anything else | 500 | logged server-side, generic message to client |

Successful responses are returned as plain DTOs. There is no `{ success, data }` envelope because HTTP status codes
already carry that information and envelopes make Swagger schemas and client code noisier.

### Listing: filtering, sorting and pagination in the database

`GET /api/tasks` accepts `status` (one or many), `priority`, `dueDate`, `dueDateFrom`, `dueDateTo`, `search`, `sortBy`,
`sortDirection`, `page` and `size`. The controller packs them into two small records, `TaskFilter` and `TaskSort`, and
`TaskSpecifications` turns them into a JPA Criteria query:

- Filters are ANDed together; the free-text search is a case-insensitive `LIKE` over title and description.
- Priority is stored as text (`LOW` / `MEDIUM` / `HIGH`), so sorting by the column would be alphabetical. The
  specification sorts by a `CASE` expression that maps each value to a rank instead.
- Null due dates are always placed last so a task without a deadline never hides a real one.
- Every sort has a stable tie-breaker (`createdAt DESC`) so pagination never shuffles rows between pages.

Spring Data's `findAll(Specification, Pageable)` runs a `COUNT` query plus a `LIMIT/OFFSET` query. Nothing is loaded
into memory and filtered in Java.

### Dashboard statistics

`DashboardService` issues six `COUNT` queries. "Overdue" and "due today" need a date, and that date depends on the
user's timezone: `2026-11-02 22:30 UTC` is already the 3rd in Colombo but still the 2nd in New York. The service
resolves `LocalDate.now(clock.withZone(preferences zone))` and passes it to the repository, so the database compares
dates only and never needs to know about timezones.

Definitions used:

- **pending** = `TODO`, **inProgress** = `IN_PROGRESS`, **completed** = `COMPLETED`
- **overdue** = not completed and `due_date < today`
- **dueToday** = not completed and `due_date = today`

## 4. How Next.js communicates with Spring Boot

The frontend is a pure client of the REST API; there are no Next.js route handlers or server actions proxying calls.

- `lib/api/client.ts` is the single HTTP entry point. It prefixes `NEXT_PUBLIC_API_BASE_URL`, serialises JSON,
  treats `204` as "no content", and converts non-2xx responses into an `ApiError` that carries the backend's
  `ProblemDetail` (including field errors). Every other module calls `apiRequest`.
- `lib/api/tasks.ts`, `dashboard.ts` and `preferences.ts` are thin, typed wrappers around the endpoints.
- `types/*.ts` mirror the backend DTOs one-to-one (`Task` ↔ `TaskResponse`, `PageResponse<T>`, `DashboardStats`, …).
- Spring's CORS configuration allows `http://localhost:3000` (configurable), which is all the browser needs.

Data flow in the UI:

- `useApiQuery` is a ~40-line hook that runs a fetcher, aborts stale requests and keeps the previous page visible
  while the next one loads. It re-runs when a global **refresh version** changes.
- `useTaskMutations` wraps create/update/status/delete: on success it bumps the refresh version (so task lists *and*
  statistics re-fetch) and shows a toast; on failure it shows the backend's error message.
- Filters, sorting and pagination live in the **URL query string** (`useTaskQueryParams`), so a filtered view can be
  bookmarked or refreshed. Changing a filter resets the page to 0.
- The "Today" and "Upcoming" pages derive their date filters from the configured timezone, the same way the backend does.
- Forms use React Hook Form with Zod schemas that mirror the backend constraints, so most mistakes are caught before a
  request is sent. Backend validation remains the source of truth and its messages are surfaced in toasts.

Visual theme: the palette follows the series artwork. `app/globals.css` defines the shadcn tokens in OKLCH: a violet
`--primary`, a soft lavender `--background`, navy `--sidebar-*` tokens for the dark navigation rail, and a
`--brand-gradient` (indigo to violet) exposed as the `bg-brand-gradient` / `text-brand-gradient` utilities used by the
logo and the "Add Task" button. Stat cards and badges use fixed Tailwind colours (violet total, amber pending, sky in
progress, green completed; rose/amber/green priorities) so they stay recognisable in light and dark mode.

Phase 0 intentionally avoids a data-fetching library; the hooks above are small enough to read in one sitting, which
matters for an article aimed at Java developers.

## 5. How PostgreSQL stores tasks

Schema changes are versioned with Flyway and applied on startup; Hibernate only *validates* the mapping
(`ddl-auto=validate`), so the migration files are the single source of truth.

`V1__create_tasks.sql`

```sql
CREATE TABLE tasks (
    id           UUID          PRIMARY KEY,
    title        VARCHAR(150)  NOT NULL,
    description  VARCHAR(2000),
    priority     VARCHAR(20)   NOT NULL DEFAULT 'MEDIUM',
    status       VARCHAR(20)   NOT NULL DEFAULT 'TODO',
    due_date     DATE,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    -- CHECK constraints for non-blank title, enum values and the completed_at invariant
);
CREATE INDEX idx_tasks_status ON tasks (status);      -- plus priority, due_date, created_at, updated_at
```

Decisions:

- **UUID ids** avoid leaking row counts and make ids safe to generate on the client or by an agent later.
- **Enums as `VARCHAR` + `CHECK`** rather than PostgreSQL enum types: readable in any SQL client, trivial to extend with
  a new migration, and mapped with `@Enumerated(EnumType.STRING)`.
- **`TIMESTAMPTZ` for instants, `DATE` for due dates.** Timestamps are stored in UTC (`hibernate.jdbc.time_zone=UTC`);
  a due date is a calendar day with no time component, which is exactly `java.time.LocalDate`.
- **Indexes** on every column that can be filtered or sorted by the list endpoint.

`V2__create_user_preferences.sql` creates a single-row table (`CHECK (id = 1)`) and seeds it with
`Asia/Colombo` / `480` minutes, so the application never has to handle a "no preferences yet" state.

## 6. How this architecture prepares for LangChain4j

Nothing in Phase 0 references LangChain4j, Azure OpenAI or agents, but the shape of the code is chosen with Phase 1 in
mind:

1. **Services are the API.** `TaskService.create(CreateTaskRequest)`, `updateStatus(id, status)`,
   `list(TaskFilter, TaskSort, page, size)` and `DashboardService.getStats()` are exactly the operations an agent needs.
   Phase 1 will wrap them in `@Tool`-annotated methods; no service code changes.
2. **DTOs are simple records.** LangChain4j derives tool schemas from method signatures; records with primitives,
   enums and `LocalDate` translate cleanly, and the Swagger descriptions already document each field.
3. **Preferences exist.** Natural-language dates ("tomorrow", "next Friday") require a timezone, and daily planning
   requires available minutes. Both are stored and editable from the UI.
4. **Time is injectable.** The `Clock` bean lets agent tests fix "now" just like service tests do.
5. **Errors are structured.** `TaskNotFoundException` and validation errors can be turned into tool error messages
   the agent can reason about, instead of opaque stack traces.
6. **The frontend is API-driven.** A chat panel in Phase 1 will call a new assistant endpoint and then simply bump the
   same refresh version, so task lists and statistics update after an agent acts.

What is intentionally *not* here: no `AssistantController`, no empty `agent` package, no placeholder interfaces. They
would be dead code in Phase 0 and would make the article harder to follow.

## 7. Testing approach

- **Unit tests** (`TaskServiceTest`, `DashboardServiceTest`) use Mockito for the repositories and a fixed `Clock`.
  They cover defaults, completion/reopen timestamps, updates, unknown ids and timezone-dependent statistics.
- **Integration tests** (`TaskRepositoryIntegrationTest`, `TaskApiIntegrationTest`) boot the full application against a
  real PostgreSQL container via Testcontainers and `@ServiceConnection`. They verify that Flyway migrations apply,
  that filtering/sorting/pagination happen correctly in SQL, and that every endpoint returns the documented status
  codes and `ProblemDetail` bodies.
- **Frontend tests** (Vitest + Testing Library) cover form validation, task list rendering, the create flow through
  the drawer and the complete/reopen interaction, with the API module mocked.

## 8. Known limitations (Phase 0)

- Single user, no authentication: anyone who can reach the API can modify tasks.
- No optimistic concurrency control (`@Version`); the last write wins.
- Search uses `LIKE` on title/description, which is fine for a personal task list but not full-text search.
- Timezone input on the settings page is free text with browser autocomplete; the backend validates it against the
  JDK's IANA list.
