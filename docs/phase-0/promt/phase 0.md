# Role

Act as a Senior Full-Stack Software Engineer and Software Architect specializing in Java, Spring Boot, Next.js, TypeScript, and PostgreSQL.

Your task is to design and implement **TaskFlow AI — Phase 0**, a modern, full-stack Personal Task Manager.

This project is part of a Medium article series titled:

**"Building Agentic AI Applications with Java: From Spring Boot to Multi-Agent Systems Using LangChain4j"**

The project will evolve through three phases:

- **Phase 0:** Traditional full-stack Personal Task Manager using Spring Boot, PostgreSQL, and Next.js.
- **Phase 1:** Introduce LangChain4j, Azure OpenAI through Azure AI Foundry, and a single TaskAgent.
- **Phase 2:** Introduce SupervisorAgent and specialized agents for dynamic multi-agent orchestration.

**IMPORTANT:** Implement Phase 0 only. Do not implement LangChain4j, LLM integration, AI agents, MCP, RAG, or multi-agent orchestration yet.

The application must be simple enough for Java developers to understand while following professional software engineering practices.

---

# 1. Project Overview

**Project Name:** TaskFlow AI

**Project Type:** Full-Stack Personal Task Manager

**Objective:**

Build a clean, modern, responsive personal task management application that allows users to organize daily tasks, manage priorities, track deadlines, and monitor progress.

The application must support task management through a Next.js UI and REST APIs documented using Swagger.

The architecture must allow future AI agents to reuse existing business services without changing the core task management logic.

For Phase 0, assume a single-user application. Authentication and authorization are out of scope.

---

# 2. Technology Stack

## Backend

- Java 21
- Spring Boot 3.x
- Spring Web
- Spring Data JPA
- Spring Validation
- PostgreSQL
- Maven
- Lombok
- Springdoc OpenAPI / Swagger UI
- Flyway for database migrations
- JUnit 5
- Mockito
- Testcontainers for PostgreSQL integration tests

Use compatible, stable dependency versions.

Do not introduce unnecessary dependencies.

## Frontend

- Next.js with App Router
- React
- TypeScript
- Tailwind CSS
- shadcn/ui
- Lucide React icons
- React Hook Form
- Zod for form validation

Use compatible stable versions.

## Infrastructure

- Docker Compose for PostgreSQL
- Environment-based configuration
- Local development support

Do not introduce Kubernetes, Kafka, Redis, or microservices.

---

# 3. Functional Requirements

## 3.1 Task Management

Implement complete CRUD functionality.

Users must be able to:

1. Create a task.
2. View all tasks.
3. View task details.
4. Update a task.
5. Delete a task.
6. Change task status.
7. Change task priority.
8. Set or modify a due date.
9. Mark tasks as completed.
10. Reopen completed tasks.

## 3.2 Task Properties

Each task must contain:

| Field | Type | Description |
|---|---|---|
| id | UUID | Unique task identifier |
| title | String | Required, maximum 150 characters |
| description | String | Optional, maximum 2000 characters |
| priority | Enum | LOW, MEDIUM, HIGH |
| status | Enum | TODO, IN_PROGRESS, COMPLETED |
| dueDate | LocalDate | Optional |
| createdAt | Instant | Creation timestamp |
| updatedAt | Instant | Last update timestamp |
| completedAt | Instant | Completion timestamp |

Business rules:

- New tasks default to TODO.
- New tasks default to MEDIUM priority when not specified.
- `completedAt` must be set when a task becomes COMPLETED.
- `completedAt` must be cleared when a completed task is reopened.
- Task titles cannot be blank.
- Invalid enum values must return HTTP 400.
- Unknown task IDs must return HTTP 404.
- Deletion must return HTTP 204.

Use UTC instants for timestamps.

## 3.3 Filtering and Sorting

Support filtering tasks by:

- Status
- Priority
- Due date
- Search text matching title or description

Support sorting by:

- Due date
- Priority
- Creation date
- Last updated date

Implement pagination for task listing.

Do not retrieve all tasks and filter them in application memory.

Use database-level filtering and pagination.

## 3.4 Dashboard Statistics

Provide:

- Total tasks
- Pending tasks
- In-progress tasks
- Completed tasks
- Overdue tasks
- Tasks due today

Overdue tasks are incomplete tasks with due dates before the current date in the configured user timezone.

## 3.5 User Preferences

Since Phase 1 will introduce natural-language requests such as "tomorrow" and Phase 2 will introduce daily planning, implement a minimal preferences model.

Fields:

- `timezone`: IANA timezone identifier, default `Asia/Colombo`
- `dailyAvailableMinutes`: Integer, default `480`

For Phase 0, use a single-user preferences record.

Do not introduce a users table or authentication system.

The timezone must be configurable from the UI.

Use the configured timezone for date-sensitive dashboard calculations.

---

# 4. Backend Architecture

Follow a clean, layered architecture:

```text
controller
    |
    v
service
    |
    v
repository
    |
    v
PostgreSQL
```

Suggested package structure:

```text
backend/
└── src/main/java/com/taskflow/
    ├── TaskFlowApplication.java
    ├── controller/
    │   ├── TaskController.java
    │   ├── DashboardController.java
    │   └── PreferencesController.java
    ├── service/
    │   ├── TaskService.java
    │   ├── DashboardService.java
    │   └── PreferencesService.java
    ├── repository/
    │   ├── TaskRepository.java
    │   └── PreferencesRepository.java
    ├── entity/
    │   ├── Task.java
    │   └── UserPreferences.java
    ├── enums/
    │   ├── TaskStatus.java
    │   └── TaskPriority.java
    ├── dto/
    │   ├── request/
    │   └── response/
    ├── mapper/
    ├── exception/
    ├── config/
    └── specification/
```

Requirements:

- Use constructor injection.
- Use DTOs for API contracts.
- Never expose JPA entities directly.
- Use `@Transactional` appropriately.
- Implement centralized exception handling using `@RestControllerAdvice`.
- Use Jakarta Bean Validation.
- Use Spring Data JPA Specifications or an equivalent database-level filtering mechanism.
- Keep business logic out of controllers.
- Avoid unnecessary abstractions and design patterns.

**Important for future phases:**

The task management service must remain independent of LangChain4j and Azure OpenAI.

Future agents will call these services through AI tools.

---

# 5. REST API Requirements

Implement the following endpoints.

## Tasks

```http
POST   /api/tasks
GET    /api/tasks
GET    /api/tasks/{id}
PUT    /api/tasks/{id}
PATCH  /api/tasks/{id}/status
DELETE /api/tasks/{id}
```

The task listing endpoint must support pagination, filtering, and sorting.

Example:

```http
GET /api/tasks?status=TODO&priority=HIGH&page=0&size=10
```

## Dashboard

```http
GET /api/dashboard/stats
```

Example response:

```json
{
  "total": 20,
  "pending": 8,
  "inProgress": 5,
  "completed": 7,
  "overdue": 2,
  "dueToday": 3
}
```

## Preferences

```http
GET /api/preferences
PUT /api/preferences
```

## API Response Conventions

- POST returns HTTP 201.
- GET returns HTTP 200.
- PUT/PATCH returns HTTP 200.
- DELETE returns HTTP 204.
- Validation errors return HTTP 400.
- Unknown resources return HTTP 404.
- Unexpected errors return HTTP 500.

Use a consistent error response based on Spring's `ProblemDetail`.

Do not wrap every successful response in an unnecessary generic response object.

---

# 6. PostgreSQL Database

Use Flyway for schema migrations.

Create:

```text
tasks
user_preferences
```

Use appropriate:

- Primary keys
- Constraints
- Indexes
- Column types
- Default values

Add indexes supporting commonly used filtering and sorting operations.

Use migration files:

```text
V1__create_tasks.sql
V2__create_user_preferences.sql
```

Use `ddl-auto=validate` rather than Hibernate-generated production schemas.

Provide a Docker Compose configuration for PostgreSQL.

Do not require Docker to run the frontend or backend applications.

---

# 7. Swagger / OpenAPI

Integrate Springdoc OpenAPI.

Swagger UI must be accessible at:

```text
http://localhost:8080/swagger-ui/index.html
```

Document:

- API descriptions
- Request and response schemas
- Validation constraints
- Common HTTP responses
- Pagination parameters
- Filtering parameters

Group endpoints logically.

Swagger should be useful for demonstrating Phase 0 in a Medium article.

---

# 8. Next.js Frontend

Create a modern, responsive, professional SaaS-style dashboard.

The design should be clean, minimal, and visually appealing.

Use:

- Light theme by default
- Consistent spacing
- Rounded cards
- Subtle shadows
- Accessible colors
- Responsive layout
- Clear typography
- Lucide icons

Avoid unnecessary animations and complex visual effects.

## 8.1 Main Dashboard

The dashboard should include:

**Sidebar:**

- Dashboard
- All Tasks
- Today
- Upcoming
- Completed
- Settings

**Top bar:**

- Application name
- Current date
- Add Task button

**Statistics cards:**

- Total Tasks
- Pending
- In Progress
- Completed

**Main content:**

- Task list
- Status filters
- Priority filters
- Search
- Sorting
- Pagination

## 8.2 Task Cards

Each task card should display:

- Task title
- Description preview
- Priority badge
- Status badge
- Due date
- Edit action
- Delete action
- Complete action

Highlight overdue tasks clearly.

## 8.3 Create/Edit Task

Use a modal or side drawer.

Fields:

- Title
- Description
- Priority
- Status (editing only)
- Due date

Implement client-side validation using React Hook Form and Zod.

Display useful validation messages.

## 8.4 Dashboard Behavior

- Show loading skeletons while fetching data.
- Show empty states when no tasks exist.
- Show error messages when API requests fail.
- Display success/error toast notifications.
- Ask for confirmation before deleting a task.
- Refresh affected task lists and statistics after mutations.
- Keep filters and pagination synchronized with URL query parameters where practical.

## 8.5 Settings

Provide a simple preferences page allowing users to configure:

- Timezone
- Daily available minutes

---

# 9. Frontend Architecture

Suggested structure:

```text
frontend/
├── app/
│   ├── page.tsx
│   ├── tasks/
│   ├── today/
│   ├── upcoming/
│   ├── completed/
│   └── settings/
├── components/
│   ├── layout/
│   ├── dashboard/
│   ├── tasks/
│   └── ui/
├── hooks/
├── lib/
│   ├── api/
│   ├── utils.ts
│   └── validations/
├── types/
└── package.json
```

Requirements:

- Use TypeScript types matching backend DTOs.
- Centralize HTTP API communication.
- Use environment variables for backend URLs.
- Avoid hardcoded mock data in the final application.
- Ensure frontend actions persist to PostgreSQL through Spring Boot.
- Handle backend errors gracefully.
- Keep components reusable and reasonably small.

---

# 10. Testing Strategy

## Backend Unit Tests

Test:

- Task creation
- Default priority/status
- Task completion timestamps
- Reopening completed tasks
- Task updates
- Invalid task data
- Unknown task IDs
- Dashboard statistics

Use JUnit 5 and Mockito.

## Backend Integration Tests

Use Testcontainers with PostgreSQL.

Verify:

- Database migrations
- Repository filtering
- Pagination
- REST endpoint behavior
- Validation errors
- Task persistence

## Frontend Testing

Include a lightweight testing setup.

At minimum, test:

- Task form validation
- Task list rendering
- Task creation interaction
- Status update interaction

Avoid introducing an unnecessarily complex testing framework.

---

# 11. Future AI Integration — Architecture Preparation Only

Phase 1 will introduce:

```text
Next.js Chat Interface
        |
        v
Spring Boot Assistant API
        |
        v
TaskAgent (LangChain4j)
        |
        v
Azure OpenAI
        |
        v
Task Tools
        |
        v
Existing TaskService
        |
        v
PostgreSQL
```

Phase 2 will introduce:

```text
SupervisorAgent
      |
      +-- TaskAgent
      |
      +-- PriorityAgent
      |
      +-- PlanningAgent
      |
      +-- SummaryAgent
```

Do not implement these components in Phase 0.

However, make sure existing services can be reused by future AI tools.

Do not add empty AI classes or unnecessary placeholder abstractions.

---

# 12. Project Structure

Create a monorepo:

```text
taskflow-ai/
├── backend/
├── frontend/
├── infrastructure/
│   └── docker-compose.yml
├── docs/
│   └── phase-0/
├── .gitignore
└── README.md
```

The repository should be ready to publish on GitHub.

---

# 13. Documentation

Create a comprehensive README containing:

1. Project overview
2. Technology stack
3. Architecture diagram
4. Prerequisites
5. PostgreSQL setup
6. Backend startup instructions
7. Frontend startup instructions
8. Environment variables
9. Swagger URL
10. API endpoint summary
11. Testing instructions
12. Future phase roadmap

Create a separate document:

```text
docs/phase-0/IMPLEMENTATION.md
```

Explain important implementation decisions so they can be reused when writing the Phase 0 Medium article.

Include:

- Why we chose a layered architecture
- Why business logic is separated from controllers
- How task management works
- How Next.js communicates with Spring Boot
- How PostgreSQL stores tasks
- How this architecture prepares for LangChain4j

Do not claim that AI features already exist.

---

# 14. Development Instructions

Follow these steps:

### Step 1 — Inspect the Workspace

Check the current directory and existing files.

If an existing project is present, inspect it before modifying anything.

Do not overwrite unrelated files.

### Step 2 — Present the Implementation Plan

Before coding, briefly describe:

- Backend structure
- Frontend structure
- Database design
- API design
- Implementation order

Then proceed with implementation.

### Step 3 — Implement the Backend

Build the Spring Boot application, persistence layer, services, controllers, validation, exception handling, and Swagger documentation.

### Step 4 — Implement the Database

Create Flyway migrations and Docker Compose configuration.

### Step 5 — Implement the Frontend

Build the Next.js dashboard and connect it to real backend APIs.

### Step 6 — Add Tests

Implement backend and frontend tests.

### Step 7 — Verify the Application

Run available build and test commands.

Backend:

```bash
mvn clean verify
```

Frontend:

```bash
npm run build
```

Run additional tests where configured.

If Docker is available, verify PostgreSQL integration and application startup.

Do not claim successful execution unless the commands actually succeed.

If a command cannot be executed, clearly explain why.

### Step 8 — Final Review

Verify:

- No AI functionality has been introduced.
- CRUD operations work.
- Frontend communicates with backend.
- Database migrations are valid.
- Swagger documentation is available.
- Validation and exception handling work.
- README instructions are accurate.
- The application is ready for Phase 1.

---

# 15. Acceptance Criteria

Phase 0 is complete when:

- [ ] Spring Boot backend starts successfully.
- [ ] PostgreSQL runs using Docker Compose.
- [ ] Flyway migrations execute successfully.
- [ ] All task CRUD endpoints are implemented.
- [ ] Task filtering, sorting, and pagination work.
- [ ] Dashboard statistics are accurate.
- [ ] User preferences can be updated.
- [ ] Swagger UI documents the APIs.
- [ ] Next.js frontend starts successfully.
- [ ] Users can manage tasks through the UI.
- [ ] Task changes persist in PostgreSQL.
- [ ] Frontend is responsive.
- [ ] Backend tests pass.
- [ ] Frontend build succeeds.
- [ ] README contains complete setup instructions.
- [ ] No LangChain4j or LLM integration exists yet.

---

# 16. Important Engineering Principles

Follow these principles throughout implementation:

**Simplicity first.** This is an educational project, not an enterprise task management platform.

**Clean separation of concerns.** Controllers handle HTTP, services handle business logic, and repositories handle persistence.

**Production-minded, not overengineered.** Use validation, error handling, migrations, and tests without adding unnecessary infrastructure.

**AI-ready architecture.** Existing business services must be reusable by future LangChain4j tools.

**Real functionality.** Do not create a frontend that only displays mock data.

**Readable code.** Prefer clear implementations over clever abstractions.

**Incremental implementation.** Complete and verify each major component before moving to the next.

**Accurate documentation.** Document what has actually been implemented and tested.

---

# Expected Final Deliverables

Provide:

1. Complete Spring Boot backend.
2. Complete Next.js frontend.
3. PostgreSQL database migrations.
4. Docker Compose configuration.
5. Swagger/OpenAPI documentation.
6. Backend and frontend tests.
7. README with setup instructions.
8. Phase 0 implementation documentation.
9. Summary of implemented functionality.
10. Verification results and any remaining limitations.

The final application should be visually attractive, easy to run locally, and suitable for demonstrating in a Medium article.

**Begin by inspecting the workspace, presenting a concise implementation plan, and then implementing TaskFlow AI Phase 0.**