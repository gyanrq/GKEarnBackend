-- =============================================================================
-- V4__composite_indexes_and_perf.sql
-- PostgreSQL composite indexes for high-traffic query paths.
-- =============================================================================

CREATE INDEX IF NOT EXISTS idx_rtx_user_date
    ON reward_transactions (user_id, created_at);

CREATE INDEX IF NOT EXISTS idx_rtx_user_status
    ON reward_transactions (user_id, status);

CREATE INDEX IF NOT EXISTS idx_payout_user_status
    ON payout_requests (user_id, status);

CREATE INDEX IF NOT EXISTS idx_payout_status_date
    ON payout_requests (status, created_at);

CREATE INDEX IF NOT EXISTS idx_notif_user_read
    ON notifications (user_id, is_read);

CREATE INDEX IF NOT EXISTS idx_notif_user_date
    ON notifications (user_id, created_at);

CREATE INDEX IF NOT EXISTS idx_cl_user_status
    ON campaign_leads (user_id, status);

CREATE INDEX IF NOT EXISTS idx_cl_campaign_status
    ON campaign_leads (campaign_id, status);

CREATE INDEX IF NOT EXISTS idx_payout_user_day
    ON payout_requests (user_id, created_at, status);

CREATE INDEX IF NOT EXISTS idx_users_admin_search
    ON users (lower(name), lower(email));
