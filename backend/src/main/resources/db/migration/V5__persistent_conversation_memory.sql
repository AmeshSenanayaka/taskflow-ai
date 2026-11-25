-- Phase 3.4: persistent, isolated conversation memory.
--
-- Two different things are stored per conversation:
--   * ai_chat_memory  : what the MODEL sees next turn (LangChain4j chat memory window), one row per agent namespace
--                       ('supervisor' planner memory, 'task-agent' memory). Written only when a turn is committed.
--   * ai_chat_messages: what the USER sees (the transcript shown when a conversation is resumed).
-- Both belong to a conversation, which belongs to a user; deleting either cascades.

ALTER TABLE ai_conversations ADD COLUMN mode       VARCHAR(20)  NOT NULL DEFAULT 'supervisor';
ALTER TABLE ai_conversations ADD COLUMN title      VARCHAR(120);
ALTER TABLE ai_conversations ADD COLUMN updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW();
UPDATE ai_conversations SET updated_at = created_at;
ALTER TABLE ai_conversations
    ADD CONSTRAINT chk_ai_conversations_mode CHECK (mode IN ('agent', 'supervisor'));
-- Listing a user's conversations (newest first) and the retention job both read by owner / last activity.
CREATE INDEX idx_ai_conversations_owner_updated ON ai_conversations (owner_id, updated_at DESC);
CREATE INDEX idx_ai_conversations_updated ON ai_conversations (updated_at);

CREATE TABLE ai_chat_memory (
    conversation_id UUID         NOT NULL,
    namespace       VARCHAR(40)  NOT NULL,
    -- LangChain4j ChatMessageSerializer JSON of the memory window.
    messages        TEXT         NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_ai_chat_memory PRIMARY KEY (conversation_id, namespace),
    CONSTRAINT fk_ai_chat_memory_conversation FOREIGN KEY (conversation_id)
        REFERENCES ai_conversations (id) ON DELETE CASCADE
);

CREATE TABLE ai_chat_messages (
    id              UUID         PRIMARY KEY,
    -- Insertion order: user message, then the answer, even when both share a timestamp.
    seq             BIGSERIAL    NOT NULL,
    conversation_id UUID         NOT NULL,
    role            VARCHAR(10)  NOT NULL,
    content         TEXT         NOT NULL,
    -- JSON of the validated DailyPlan shown with the answer, if any.
    plan            TEXT,
    -- Comma-separated guardrail codes that acted on the turn, if any.
    guardrails      VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL,

    CONSTRAINT chk_ai_chat_messages_role CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT fk_ai_chat_messages_conversation FOREIGN KEY (conversation_id)
        REFERENCES ai_conversations (id) ON DELETE CASCADE
);
CREATE INDEX idx_ai_chat_messages_conversation ON ai_chat_messages (conversation_id, seq);
