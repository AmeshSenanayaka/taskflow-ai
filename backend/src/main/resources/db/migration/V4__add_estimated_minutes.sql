-- Phase 3.2: an optional duration estimate per task, used by PlanningAgent and checked by the plan rules.
ALTER TABLE tasks ADD COLUMN estimated_minutes INTEGER;
ALTER TABLE tasks
    ADD CONSTRAINT chk_tasks_estimated_minutes CHECK (estimated_minutes IS NULL OR estimated_minutes BETWEEN 1 AND 1440);
