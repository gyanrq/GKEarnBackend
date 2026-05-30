package com.myworld.modules.identity.domain;

// ============================================================
// FILE: src/main/java/com/myworld/modules/identity/domain/User.java
// CHANGE: phone column ko nullable = true kiya (Google users ke liye)
// ============================================================

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.myworld.core.constant.Role;
import com.myworld.core.domain.BaseEntity;
import com.myworld.modules.referral.domain.Referral;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Entity
@Table(name = "users",
    indexes = {
        @Index(name = "idx_user_email",  columnList = "email",         unique = true),
        @Index(name = "idx_user_phone",  columnList = "phone",         unique = true),
        @Index(name = "idx_user_ref",    columnList = "referral_code", unique = true),
        @Index(name = "idx_user_role",   columnList = "role")
    }
)
@SQLRestriction("is_deleted = false")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder(toBuilder = true)
public class User extends BaseEntity {

    // ── Core Identity ─────────────────────────────────────────────────────────

    // ✅ FIX: nullable = true — Google users ka phone null hota hai signup pe
    // unique = true rakha hai taaki ek phone se 2 accounts na ban sakein
    @Column(nullable = true, unique = true)
    private String phone;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // ── Profile ───────────────────────────────────────────────────────────────
    private String    profilePictureUrl;
    private LocalDate dateOfBirth;
    private String    address;
    private String    city;
    private String    state;
    private String    pincode;

    // ── Status Flags ──────────────────────────────────────────────────────────
    @Builder.Default private Boolean isBlocked       = false;
    @Builder.Default private Boolean isDeleted       = false;
    @Builder.Default private Boolean isEmailVerified = false;
    @Builder.Default private Boolean isPhoneVerified = false;

    private OffsetDateTime lastLoginAt;
    private String         lastLoginIp;

    // ── Referral ──────────────────────────────────────────────────────────────
    @Column(unique = true)
    private String referralCode;

    private String referredByCode;

    // ── Admin Notes ───────────────────────────────────────────────────────────
    private String         adminNotes;
    private OffsetDateTime blockedAt;
    private String         blockReason;

    // ── Relations ─────────────────────────────────────────────────────────────
    @Builder.Default
    @OneToMany(mappedBy = "referrer", fetch = FetchType.LAZY)
    private List<Referral> referrals = new ArrayList<>();

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @PrePersist
    public void prePersist() {
        if (role == null)            role = Role.USER;
        if (isBlocked == null)       isBlocked = false;
        if (isDeleted == null)       isDeleted = false;
        if (isEmailVerified == null) isEmailVerified = false;
        if (isPhoneVerified == null) isPhoneVerified = false;
    }

    public Set<Role> getRoles() {
        return role != null ? Collections.singleton(role) : Collections.emptySet();
    }
}