# TaskFlow AI — Phase 3.2: Deterministic rules

Phase 3.2 moves business behaviour out of the model's good intentions and into plain Java. Every task change and
every day plan passes the same rules, whether it comes from the UI, the REST API or an AI agent. A broken rule
returns a precise reason and nothing is written.

## The rule engine

`com.taskflow.rules`

| Type | Role |
|---|---|
| `DomainRule<T>` | One rule: a stable `name()` and `RuleResult evaluate(T)`. Rules read their context and never change data |
| `RuleResult`, `RuleViolation` | Violations carry a rule code, the field it concerns and a message a user or a model can act on |
| `RuleEngine` | Runs a list of rules and reports **every** violation at once, so one retry can fix all of them |
| `RuleViolationException` | Thrown by `RuleEngine.enforce`; HTTP `422` (below), a "Not allowed … Nothing was changed." tool result for the model |

Rules are Spring beans. `TaskService` receives every `DomainRule<TaskChange>`, `PlanTools` every
`DomainRule<PlanCheck>`; adding a rule is adding a `@Component`.

## Task rules (every create, update, partial update and status change)

| Code | Rule | Notes |
|---|---|---|
| `TASK_COMPLETED_IS_FROZEN` | A completed task cannot change priority or due date | Reopening in the same request is allowed; title and description stay editable |
| `TASK_DUE_DATE_NOT_IN_PAST` | A due date cannot be set or moved before the owner's today (in their timezone) | Existing overdue dates stay valid, so overdue tasks can still be edited |
| `TASK_UNIQUE_OPEN_TITLE` | A user cannot have two open tasks with the same title (case-insensitive) | Completed tasks do not count; checked on create, rename and reopen only, so pre-existing duplicates do not block other edits |

`TaskService` builds a `TaskChange` (owner, stored state, proposed state, today) and enforces the rules **before**
any field is modified, inside the same transaction. Because the UI, the REST API and `TaskTools` all go through
`TaskService`, there is no path around them.

Over HTTP a violation is a Problem Detail:

```json
{
  "status": 422,
  "title": "Business rule violation",
  "detail": "The due date 2026-11-01 is in the past (today is 2026-11-09). Choose today or a later date.",
  "errors": { "dueDate": "The due date 2026-11-01 is in the past (today is 2026-11-09). Choose today or a later date." },
  "violations": [ { "rule": "TASK_DUE_DATE_NOT_IN_PAST", "field": "dueDate", "message": "..." } ]
}
```

`errors` has the same shape as validation errors, so the task form shows the message on the field (and a
toast), and stays open for a correction; `violations`
carries the stable codes for clients and evaluations.

## Estimated minutes

Tasks have an optional `estimatedMinutes` (1–1440): Flyway `V4__add_estimated_minutes.sql`, the REST DTOs, the task
form ("Estimate (minutes)"), and the `createTask` / `updateTask` tools. The PlanningAgent uses an estimate when a task
has one and states its own assumption otherwise.

## Structured, rule-checked day plans

Phase 2 plans were free text: nothing could check that a "three-hour plan" fits in three hours. Now the PlanningAgent
must hand its plan in through a tool before presenting it:

```
submitPlan(date, blocks: [{ taskId, start "HH:mm", minutes }])
```

`PlanTools` parses the blocks without trusting them, loads the referenced tasks **as the signed-in user** (another
user's id is simply not found) and runs the plan rules:

| Code | Rule |
|---|---|
| `PLAN_WELL_FORMED` | A date not in the past, at least one block, each with a task id, a start time and 5–1440 minutes, not running past midnight |
| `PLAN_WITHIN_AVAILABLE_MINUTES` | Total minutes ≤ the user's daily available minutes |
| `PLAN_ONLY_OWN_OPEN_TASKS` | Only the user's own tasks, none completed, each at most once |
| `PLAN_NO_OVERLAP` | No overlapping blocks |

- **Rejected:** the tool returns the problems; the agent fixes them and submits again.
- **Third rejection:** the tool refuses further attempts and tells the agent to explain to the user why no plan fits.
- **Accepted:** the plan is recorded for the request and returned as `plan` in the `/api/ai/supervisor/chat`
  response. The UI shows it as a "checked" plan card under the answer, so what the user sees as the plan is always
  what the rules accepted, whatever the model wrote around it.

`submitPlan` changes no task, so the PlanningAgent stays read-only (it is not `@MutatesTasks`).

## Evaluation

| Scenario | What it shows |
|---|---|
| R01 | A completed task's priority change is refused through the AI |
| R02 | A due date moved into the past is refused |
| R03 | A duplicate open task is not created |
| R04 | An over-long plan is rejected and resubmitted to fit 120 minutes |
| R05 | Another user's task id offered in the request never reaches the plan |
| T02, F01 | Now require a validated plan; F01 must fit 180 minutes and exclude completed tasks |

New expectation in the dataset: `"plan": { "required": true, "maxTotalMinutes": 180, "excludes": ["done"] }`. Fixture
tasks accept `estimatedMinutes`.

Compare the 3.1 live baseline with a new run (`OPENAI_API_KEY=... mvn test "-Dtest=LiveModelEvalTest" "-Deval.live=true"`):
F01's plan is now deterministically checked, and the R scenarios measure how the model reacts to refusals.

## Tests

- `TaskRulesTest`: each task rule and the engine reporting all violations.
- `TaskRulesApiIntegrationTest`: 422 Problem Details for each rule over HTTP, reopen-and-change allowed, estimate
  stored and validated.
- `PlanToolsTest`: accepted plan recorded for the response, over-budget, completed, foreign, duplicate, overlapping,
  malformed and past-date plans rejected, the three-attempt limit.
- `TaskServiceTest`: a broken rule stops create and status changes before anything is written.
- Frontend: `plan-card.test.tsx`; form tests include the estimate.
- Integration tests that create tasks with fixed dates now use dates far in the future, since past due dates are
  refused.

## Limits

- `TASK_UNIQUE_OPEN_TITLE` is checked in the application, not by a database constraint: two simultaneous creates
  could both pass. A partial unique index would need existing duplicates cleaned up first.
- The model may still describe a plan differently in its text than the accepted blocks; the plan card and the
  `plan` field are the authoritative version. Detecting such mismatches is an output guardrail (3.3).
- Plans cover one day.
