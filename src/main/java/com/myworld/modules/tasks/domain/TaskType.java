package com.myworld.modules.tasks.domain;

/**
 * All supported task types.
 * LINK_* tasks require the user to submit their email/phone before
 * the partner URL is unlocked.  The controller uses the REQUIRES_LEAD
 * flag to decide whether to send a LeadForm response instead of a
 * direct redirect URL.
 */
public enum TaskType {

    // ── Daily engagement ──────────────────────────────────────────────────────
    LOGIN,
    SPIN_WHEEL,
    SHARE_REFERRAL,
    LEAD_SUBMIT,

    // ── Crypto exchange referral links ────────────────────────────────────────
    LINK_BINANCE,
    LINK_BYBIT,
    LINK_BITGET,
    LINK_BITMART,
    LINK_COINSWITCH,
    LINK_WAZIRX,
    LINK_COINDCX,

    // ── Demat / Stock accounts ────────────────────────────────────────────────
    LINK_SBI_SECURITIES,
    LINK_HDFC_SKY,
    LINK_ZERODHA,
    LINK_GROWW,
    LINK_ANGELONE,
    LINK_UPSTOX,
    LINK_IIFL,

    // ── App Install / Random earning ──────────────────────────────────────────
    LINK_MEESHO,
    LINK_CASHKARO,
    LINK_TASKBUCKS,
    LINK_ROZDHAN,
    LINK_MILESTONEIT,

    // ── KYC / Survey ──────────────────────────────────────────────────────────
    KYC_VERIFY,
    SURVEY_COMPLETE,
    PROFILE_COMPLETE
}