-- Daily task race protection: one completion per user/task/calendar day.
DROP INDEX IF EXISTS idx_task_user_date;

CREATE UNIQUE INDEX IF NOT EXISTS uq_task_user_day
    ON daily_task_completions (user_id, task_type, ((created_at AT TIME ZONE 'Asia/Kolkata')::date));
