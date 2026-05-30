package com.myworld.core.constant;

public final class AppConstants {
    private AppConstants() {}

    public static final String REFERRAL_PREFIX = "EX-";
    public static final int OTP_EXPIRY_MINUTES = 10;
    public static final int OTP_MAX_ATTEMPTS = 3;
    public static final int OTP_RATE_LIMIT = 3;
    public static final int OTP_RATE_WINDOW_MINUTES = 15;
    public static final int LOGIN_MAX_ATTEMPTS = 5;
    public static final int LOGIN_BLOCK_MINUTES = 15;
    public static final long ACCESS_TOKEN_EXPIRY_MS = 15 * 60 * 1000L;      // 15 min
    public static final long REFRESH_TOKEN_EXPIRY_DAYS = 7L;
}
