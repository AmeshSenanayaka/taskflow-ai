-- Phase 3.0: identity and data ownership.
-- Every task, preferences row and AI conversation now belongs to a user. Users come from Keycloak and are
-- created in app_users on their first authenticated request (just-in-time provisioning).

CREATE TABLE app_users (
    id         UUID          PRIMARY KEY,
    subject    VARCHAR(255)  NOT NULL,   -- the identity provider's stable "sub" claim
    username   VARCHAR(255),
    created_at TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_app_users_subject UNIQUE (subject)
);

-- Data created before Phase 3.0 (single-user mode) is handed to the demo user "alice". Her id is fixed in
-- infrastructure/keycloak/taskflow-realm.json, so the Keycloak subject and this row match out of the box.
INSERT INTO app_users (id, subject, username)
VALUES ('5f2b8c1e-3d4a-4e6f-8a9b-1c2d3e4f5a61', '5f2b8c1e-3d4a-4e6f-8a9b-1c2d3e4f5a61', 'alice');

-- Tasks -------------------------------------------------------------------------------------------------
ALTER TABLE tasks ADD COLUMN owner_id UUID;
UPDATE tasks SET owner_id = '5f2b8c1e-3d4a-4e6f-8a9b-1c2d3e4f5a61';
ALTER TABLE tasks ALTER COLUMN owner_id SET NOT NULL;
ALTER TABLE tasks
    ADD CONSTRAINT fk_tasks_owner FOREIGN KEY (owner_id) REFERENCES app_users (id) ON DELETE CASCADE;

-- Every query is now scoped to one owner, so the owner leads each index.
DROP INDEX idx_tasks_status;
DROP INDEX idx_tasks_priority;
DROP INDEX idx_tasks_due_date;
DROP INDEX idx_tasks_created_at;
DROP INDEX idx_tasks_updated_at;
CREATE INDEX idx_tasks_owner_status     ON tasks (owner_id, status);
CREATE INDEX idx_tasks_owner_priority   ON tasks (owner_id, priority);
CREATE INDEX idx_tasks_owner_due_date   ON tasks (owner_id, due_date);
CREATE INDEX idx_tasks_owner_created_at ON tasks (owner_id, created_at);
CREATE INDEX idx_tasks_owner_updated_at ON tasks (owner_id, updated_at);

-- Preferences: one row per user instead of a single global row ---------------------------------------
ALTER TABLE user_preferences DROP CONSTRAINT chk_user_preferences_single_row;
ALTER TABLE user_preferences ADD COLUMN user_id UUID;
UPDATE user_preferences SET user_id = '5f2b8c1e-3d4a-4e6f-8a9b-1c2d3e4f5a61';
ALTER TABLE user_preferences ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE user_preferences DROP CONSTRAINT user_preferences_pkey;
ALTER TABLE user_preferences DROP COLUMN id;
ALTER TABLE user_preferences ADD CONSTRAINT user_preferences_pkey PRIMARY KEY (user_id);
ALTER TABLE user_preferences
    ADD CONSTRAINT fk_user_preferences_user FOREIGN KEY (user_id) REFERENCES app_users (id) ON DELETE CASCADE;

-- AI conversations: the server issues the id and records who owns it -----------------------------------
CREATE TABLE ai_conversations (
    id         UUID         PRIMARY KEY,
    owner_id   UUID         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_ai_conversations_owner FOREIGN KEY (owner_id) REFERENCES app_users (id) ON DELETE CASCADE
);
CREATE INDEX idx_ai_conversations_owner ON ai_conversations (owner_id);
