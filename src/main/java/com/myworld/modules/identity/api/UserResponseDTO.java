package com.myworld.modules.identity.api;

import lombok.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserResponseDTO {
    private Long id;
    private String uuid;
    private String name;
    private String email;
    private String phone;
    private String role;
    private String referralCode;
    // masked fields
    private String panNumber;
    private String aadhaarNumber;
    private String bankAccountNumber;
    private String ifscCode;
    private String bankName;
    // profile extras
    private String profilePictureUrl;
    private LocalDate dateOfBirth;
    private String address;
    private String city;
    private String state;
    private String pincode;
    // status flags
    private Boolean isBlocked;
    private Boolean isEmailVerified;
    private Boolean isPhoneVerified;
    private Boolean mfaEnabled;   // ✅ FIX: MFA status for Settings screen badge
    // credits snapshot (replaces walletBalance)
    private Long totalCredits;
    private Long redeemedCredits;
    private Long pendingCredits;
    // timestamps
    private OffsetDateTime createdAt;
    private OffsetDateTime lastLoginAt;
}