-- =============================================================================
-- V2__seed_default_data.sql
-- Default configuration rows required for the app to function.
-- =============================================================================

-- Default reward config (only insert if table is empty)
INSERT INTO reward_config (uuid, created_at, updated_at, credits_per_rupee, min_redeem_credits,
                            max_daily_earn, redemption_wait_seconds, payout_processing_seconds, is_active)
SELECT gen_random_uuid()::text, NOW(), NOW(), 10, 1000, 4000, 0, 604800, true
WHERE NOT EXISTS (SELECT 1 FROM reward_config LIMIT 1);

-- Default spin prizes
INSERT INTO spin_prizes (uuid, created_at, updated_at, credits, weight, is_active, sort_order, label)
SELECT gen_random_uuid()::text, NOW(), NOW(), 10, 40, true, 1, '10 Credits' WHERE NOT EXISTS (SELECT 1 FROM spin_prizes LIMIT 1);
INSERT INTO spin_prizes (uuid, created_at, updated_at, credits, weight, is_active, sort_order, label)
SELECT gen_random_uuid()::text, NOW(), NOW(), 25, 30, true, 2, '25 Credits' WHERE NOT EXISTS (SELECT 1 FROM spin_prizes WHERE credits = 25);
INSERT INTO spin_prizes (uuid, created_at, updated_at, credits, weight, is_active, sort_order, label)
SELECT gen_random_uuid()::text, NOW(), NOW(), 50, 20, true, 3, '50 Credits' WHERE NOT EXISTS (SELECT 1 FROM spin_prizes WHERE credits = 50);
INSERT INTO spin_prizes (uuid, created_at, updated_at, credits, weight, is_active, sort_order, label)
SELECT gen_random_uuid()::text, NOW(), NOW(), 100, 8, true, 4, '100 Credits' WHERE NOT EXISTS (SELECT 1 FROM spin_prizes WHERE credits = 100);
INSERT INTO spin_prizes (uuid, created_at, updated_at, credits, weight, is_active, sort_order, label)
SELECT gen_random_uuid()::text, NOW(), NOW(), 250, 2, true, 5, '250 Credits' WHERE NOT EXISTS (SELECT 1 FROM spin_prizes WHERE credits = 250);
