# Phase 3.4 — Isolated Persistent Memory

Until Phase 3.3 every conversation lived in the backend process (`MessageWindowChatMemory` with LangChain4j's
default in-memory store): a restart forgot everything. Phase 3.4 stores conversation memory in PostgreSQL, keeps it
isolated, and makes sure that only good turns are remembered.

Scope decision: **conversation memory only.** Long-term facts about the user ("I work 9-5") are left for later;
they raise their own memory-poisoning questions.

## Two kinds of stored conversation data

| Table | What | Who reads it |
|---|---|---|
| `ai_chat_memory` | The LangChain4j memory window per agent namespace, as `ChatMessageSerializer` JSON | the **model**, next turn |
| `ai_chat_messages` | The transcript: user message, shown answer, plan, guardrail codes | the **user**, when a conversation is resumed |

They are kept apart on purpose. The memory window contains tool calls, tool results and the planner's internal
messages and is cut to the last N messages; the transcript is exactly what the user saw. Both belong to an
`ai_conversations` row (new columns: `mode`, `title`, `updated_at`) and are deleted with it (`ON DELETE CASCADE`).

## Isolation: user, conversation, agent

```
memory id  = ownerId:conversationId          (ConversationRef.memoryKey(), since 3.0)
row key    = (conversation_id, namespace)    namespace: supervisor | task-agent
```

- **Per user.** `PostgresChatMemoryStore` refuses a memory id whose owner is not the signed-in caller, and every
  SQL statement in `ChatMemoryRepository` also checks `ai_conversations.owner_id`. Either mismatch fails closed:
  nothing is read, nothing is written.
- **Per conversation.** Rows are keyed by conversation id; the API checks ownership before any agent runs (3.0).
- **Per agent.** LangChain4j gives the supervisor's planner and TaskAgent the same `@MemoryId`. Each gets its own
  store instance with its own namespace (`MemoryConfig`), so neither reads or overwrites the other's window.

## Staged memory commits

LangChain4j writes memory as it goes: the user message, tool calls, tool results, guardrail reprompts, the answers
they rejected, the final answer. A turn that ends badly would leave all of that behind. So writes are staged:

```
MultiAgentService / AiChatController
  input guardrail refuses         → nothing staged; transcript records the refusal
  memory.begin()                  → PostgresChatMemoryStore writes to a thread-local buffer (StagedMemory),
                                    and reads its own staged writes back, so agents behave exactly as before
  agents run …
  answer shown as written         → memory.commit(): one transaction, reprompts and rejected drafts removed
  answer rejected (fallback)      → memory.discard(): memory stays as it was before the turn
  model error / timeout           → memory.discard() in finally
```

The supervisor runs every sub-agent on the request thread (the same fact the 3.0 `CurrentUserProvider` and the
`AgentRunRecorder` rely on), so one thread-local buffer sees every agent of the turn.

`ConversationMemory.withoutRepairs` removes each output-guardrail reprompt (recognised by
`GroundedAnswerOutputGuardrail.REPROMPT_MARKER`) together with the rejected answer just before it. Only the repaired
answer, which passed the checks, is remembered.

Trade-off: a discarded turn is forgotten by the model, even if tasks were changed in it. The tools always read live
data, and the transcript still shows the fallback, so the next turn does not depend on it.

## Memory hygiene

- Stored JSON is treated as data: a window that cannot be deserialised is logged and dropped, never fatal.
- One window is capped (`max-stored-bytes`, default 256 KB of JSON). Above it the oldest messages are dropped, the
  system message is kept, and a tool result whose tool call was dropped goes too (a provider would reject it).
- Tool results that carried the 3.3 untrusted-data notice are stored with that notice, so poisoned task text stays
  marked as data in later turns.
- Input refusals never reach memory; they are in the transcript only.

## Retention and limits

| Setting | Default | Effect |
|---|---|---|
| `taskflow.ai.memory.retention` | `30d` | `ConversationRetentionJob` deletes conversations without activity for longer |
| `taskflow.ai.memory.max-conversations` | `100` | per user; starting a new one deletes the least recently used beyond it |
| `taskflow.ai.memory.cleanup-interval` | `PT1H` | how often the retention job runs |
| `taskflow.ai.memory.max-stored-bytes` | `262144` | cap on one stored window |
| `taskflow.ai.memory.max-messages` | `30` | TaskAgent's window (supervisor: `supervisor.memory-max-messages`, 20) |

## API

| Method | Path | |
|---|---|---|
| `POST` | `/api/ai/conversations?mode=supervisor\|agent` | start; the mode is stored so a resumed conversation reopens in it |
| `GET` | `/api/ai/conversations` | the caller's conversations, most recent first (max 50) |
| `GET` | `/api/ai/conversations/{id}/messages` | transcript; `404` if not the caller's |
| `DELETE` | `/api/ai/conversations/{id}` | deletes memory and transcript; `404` if not the caller's |

## UI

The assistant has a conversation list (always visible on wide screens, behind a History button on small ones).
Opening a conversation loads its transcript and mode; the model's memory is restored by the backend. Deleting asks
for confirmation and says that tasks are not affected.

## Evaluation

`04-memory.json` is now fully executable:

| ID | Scenario | How |
|---|---|---|
| M01 | A follow-up ("its") resolves against the previous turn | as before |
| M02 | Memory survives a backend restart | `restartBetweenTurns`: every agent is rebuilt between turns, so only the database carries the conversation; `contextContains` checks the model saw turn 1 |
| M03 | A rejected answer is not stored | the agent invents a task twice → fallback; `memoryNotContains: ["Tokyo"]` |
| M04 | A repaired answer is stored without the draft and the reprompt | `memoryNotContains: ["Tokyo", "automatic checks"]` |

`ScenarioRunner.withPersistence(...)` enables `restartBetweenTurns` and `memoryNotContains`.

## Tests

- `PersistentMemoryIntegrationTest` (PostgreSQL): transcript and list, another user gets `404` on list, read and
  delete, the store fails closed for another user's key, discard vs commit, delete cascades, retention.
- `ConversationMemoryTest`: memory-key parsing, reprompt removal, the size cap.
- `assistant.test.tsx`: reopening and deleting stored conversations, new conversations in the selected mode.
- `TaskRepositoryIntegrationTest`: migrations 1–5.

## Limits

- The Phase 1 endpoint has no per-conversation lock; two simultaneous messages to the same agent-mode conversation
  could interleave their memory commits (the supervisor path is serialised).
- `@InputGuardrails` on the Phase 1 agent: whether LangChain4j adds the refused message to memory before the
  guardrail runs does not matter, because the staged turn is discarded.
- No long-term user memory yet; no export of a conversation.
