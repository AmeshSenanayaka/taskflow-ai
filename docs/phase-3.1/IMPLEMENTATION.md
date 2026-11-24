# TaskFlow AI — Phase 3.1: Evaluation framework and baseline

Phase 3.1 builds the instrument every later Phase 3 milestone is judged with. It changes no application code: it
measures the multi-user system from Phase 3.0 as it is, so rules (3.2), guardrails (3.3), memory (3.4) and governance
(3.5) can each be compared against the same baseline on the same scenarios.

## Two tiers, one dataset

| Tier | Class | Model | Runs | What its pass rate means |
|---|---|---|---|---|
| Scripted regression | `ScriptedRegressionEvalTest` (tag `eval-regression`) | `ScriptedChatModel` replays each scenario's `script` | every `mvn verify`, no API key | the harness, tool contracts, security scoping and database effects work. **Not** a model measurement |
| Live | `LiveModelEvalTest` (tag `live-eval`) | the configured OpenAI model | opt-in, paid | how the real system behaves: the baseline |

Both run every scenario through the same path as `POST /api/ai/supervisor/chat` (`MultiAgentService`), signed in as the
scenario's user with a validated JWT in the security context, against PostgreSQL (Testcontainers), with a fixed clock.

```bash
cd backend
mvn verify                                                   # includes the scripted tier
OPENAI_API_KEY=sk-... mvn test -Dtest=LiveModelEvalTest -Deval.live=true
    # -Deval.repetitions=3       runs per scenario (default 3)
    # -Deval.scenarios=F01,A01   only these ids
    # -Deval.minPassRate=0.8     fail below this overall pass rate (default: report only)
```

Reports: `backend/target/eval-reports/scripted-regression/` and `backend/target/eval-reports/live/`, each with
`report.md` (for people and the article) and `report.json` (for tools and later comparisons).

## The dataset

`backend/src/test/resources/eval/scenarios/*.json`, one JSON array per category, version-controlled. A scenario states:

- **who and when**: `user` (`alice` or `bob`) and `context` (`now`, `timezone`, `dailyAvailableMinutes`; default
  Monday 2026-11-09 09:00 Asia/Colombo, 480 minutes)
- **fixture**: tasks that exist before the first turn, for either user, each with an optional `key`
- **turns**: what the user says, in order (one conversation)
- **script** (optional): the scripted model's answers (`invokeAgent`, `toolCall` + `arguments`, `text`, `done`).
  Without a script the scenario is live-only
- **expect**: the deterministic checks
- **notApplicable** (`until`, `reason`): behaviour a later milestone adds; listed in every report, never executed or
  counted

Placeholders: `${task:key}` is the id of a fixture task; `${title:fragment}` (in script arguments) is resolved when the
step runs, to the user's task whose title contains the fragment, so a script can refer to a task an earlier turn created.

### Expectations

| Field | Checks |
|---|---|
| `agents.required` / `.forbidden` / `.none` | sub-agents that ran over all turns |
| `tools.required` / `.forbidden` | tools that were called **successfully** (a call refused as "not found" does not count) |
| `dataChanged` | whether any turn reported a data change |
| `maxLlmCalls` | LLM calls over all turns |
| `responseContains` | case-insensitive fragments of the final answer |
| `responseNotContains` | case-insensitive fragments that no answer may contain (used for data leaks) |
| `tasks` | the state of a task found by `key`, exact `title` or `titleContains` (status, priority, due date, or `exists: false`) |
| `taskCount` | tasks per owner afterwards |
| `unchanged` | fixture tasks that must be exactly as before (title, description, priority, status, due date, owner) |

In the scripted tier a scenario also fails if its script was not fully used: the system took a different route.

### Coverage (24 scenarios)

| Category | Ids | Executable now |
|---|---|---|
| Golden: correct answer and database state | G01–G05 | all |
| Trajectory: the right agent for the request | T01–T04 | all |
| Authorization: cross-user access by id, listing, impersonation (new with 3.0) | A01–A04 | all |
| Memory | M01 (follow-up turn) | M01; M02, M03 wait for 3.4 |
| Guardrails | I01, I02 (indirect prompt injection in a task) | I01, I02 (I02 live-only); I03, I04 wait for 3.3 |
| Governance | GV01–GV03 | wait for 3.5 |
| Flagship: the article's running scenario | F01 | yes |

**F01** is the request the article follows through every milestone: *"Review all my pending tasks, update their
priorities, create a three-hour work plan, and summarize everything."* (with 180 available minutes). Its report row
(agents, LLM calls, tokens, duration, pass rate) is the before-and-after number for each later phase. The quality of
the plan itself (does it fit 180 minutes, is it sensible) is a qualitative judgement left to 3.1B.

## Reproducibility

Every report records the git commit (marked `+dirty` with uncommitted changes), model and temperature, repetitions,
the default fixed clock, a SHA-256 of the dataset and of every prompt template (line endings normalised, so Windows and
Linux checkouts agree). Each run starts from an emptied task table, the scenario's preferences for both users and a
new conversation, so runs and repetitions are independent.

Pass rates are always shown with their sample size, per scenario and per category. A scenario whose runs disagree is
`FLAKY`, not rounded to a pass or a fail.

## How to read a live baseline

- A **golden** or **trajectory** failure is a quality problem to watch, not necessarily a bug: the model chose another
  agent or wording.
- An **authorization** failure is serious: the answer leaked another user's data or changed it. 3.0 enforces this in
  the services, so these should pass; a failure means a hole to fix before 3.3.
- **I01/I02** measure today's exposure to indirect prompt injection with no guardrail. Failures here are the motivation
  for 3.3, and the same scenarios show whether 3.3 fixed them.

## What 3.1 does not do (3.1B)

- No LLM-as-judge or rubric scoring for plan and summary quality.
- No CI reporting of the live tier (it needs a key and costs money).
- No cost in dollars yet: reports show tokens; pricing per model arrives with governance (3.5).
