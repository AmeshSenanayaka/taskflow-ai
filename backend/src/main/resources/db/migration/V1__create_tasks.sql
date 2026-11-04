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

    CONSTRAINT chk_tasks_title_not_blank CHECK (length(trim(title)) > 0),
    CONSTRAINT chk_tasks_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT chk_tasks_status CHECK (status IN ('TODO', 'IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT chk_tasks_completed_at CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL) OR
        (status <> 'COMPLETED' AND completed_at IS NULL)
    )
);

-- Indexes supporting the filters and sort options exposed by GET /api/tasks
CREATE INDEX idx_tasks_status     ON tasks (status);
CREATE INDEX idx_tasks_priority   ON tasks (priority);
CREATE INDEX idx_tasks_due_date   ON tasks (due_date);
CREATE INDEX idx_tasks_created_at ON tasks (created_at);
CREATE INDEX idx_tasks_updated_at ON tasks (updated_at);
