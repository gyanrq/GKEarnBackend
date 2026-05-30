package com.myworld.modules.admin.application;

import com.myworld.core.constant.Role;
import com.myworld.core.dto.PageResponse;
import com.myworld.core.exception.BadRequestException;
import com.myworld.core.exception.ResourceNotFoundException;
import com.myworld.modules.admin.api.*;
import com.myworld.modules.identity.api.UserResponseDTO;
import com.myworld.modules.identity.application.OtpService;
import com.myworld.modules.identity.application.RefreshTokenService;
import com.myworld.modules.identity.domain.User;
import com.myworld.modules.identity.infrastructure.UserRepository;
import com.myworld.modules.identity.mapper.UserMapper;
import com.myworld.modules.notification.application.NotificationService;
import com.myworld.modules.referral.application.EventProcessorService;
import com.myworld.modules.rewards.application.RewardService;
import com.myworld.modules.rewards.domain.RewardSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepo;
    private final UserMapper userMapper;
    private final RewardService rewardService;
    private final EventProcessorService eventProcessorService;
    private final NotificationService notificationService;
    private final OtpService otpService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.security.jwt.expiration-ms:86400000}")
    private long jwtExpirationMs;

    // ── Search ────────────────────────────────────────────────────────────────
    @Override
    public PageResponse<UserResponseDTO> searchUsers(String q, Role role, Boolean isBlocked,
                                                     int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        Page<User> results = userRepo.adminSearch(
                (q != null && q.isBlank()) ? null : q,
                role, isBlocked, pageable);
        List<UserResponseDTO> content = results.getContent()
                .stream().map(userMapper::toDTOForAdmin).toList();
        return buildPage(content, results);
    }

    @Override
    public UserResponseDTO getUserDetail(Long userId) {
        return userMapper.toDTOForAdmin(findOrThrow(userId));
    }

    // ── Block / Unblock ───────────────────────────────────────────────────────
    @Override
    @Transactional
    public void blockUser(Long userId, AdminUserActionDTO dto) {
        User user = findOrThrow(userId);
        if (user.getRole() == Role.ADMIN)
            throw new BadRequestException("Cannot block another admin");
        user.setIsBlocked(true);
        user.setBlockReason(dto.getReason());
        user.setBlockedAt(OffsetDateTime.now());
        userRepo.save(user);
        
        refreshTokenService.revokeAllForUser(userId);
        redisTemplate.opsForValue().set("user:blocked:" + userId, "1", Duration.ofMillis(jwtExpirationMs));
        
        log.info("Admin blocked userId={} reason={}", userId, dto.getReason());

        notificationService.sendNotification(
                userId, "INFO",
                "Account Blocked",
                "Your account has been blocked. Reason: " + dto.getReason() +
                ". Please contact support for assistance."
        );
    }

    @Override
    @Transactional
    public void unblockUser(Long userId) {
        User user = findOrThrow(userId);
        user.setIsBlocked(false);
        user.setBlockReason(null);
        user.setBlockedAt(null);
        userRepo.save(user);
        log.info("Admin unblocked userId={}", userId);

        notificationService.sendNotification(
                userId, "SUCCESS",
                "Account Restored",
                "Great news! Your account has been unblocked and you can now access all features."
        );
    }

    // ── Delete ────────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public void deleteUser(Long userId) {
        User user = findOrThrow(userId);
        if (user.getRole() == Role.ADMIN)
            throw new BadRequestException("Cannot delete an admin account");
        user.setIsDeleted(true);
        userRepo.save(user);
        log.info("Admin soft-deleted userId={}", userId);
    }

    // ── Role ──────────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public void changeRole(Long userId, AdminChangeRoleDTO dto) {
        User user = findOrThrow(userId);
        Role oldRole = user.getRole();
        user.setRole(dto.getRole());
        userRepo.save(user);
        log.info("Admin changed role: userId={} {} -> {}", userId, oldRole, dto.getRole());
    }

    // ── Credits Management ────────────────────────────────────────────────────
    @Override
    @Transactional
    public void creditUserCredits(Long userId, AdminCreditActionDTO dto) {
        findOrThrow(userId);
        rewardService.adminCredit(userId, dto.getCredits(), "[ADMIN] " + dto.getReason());
        log.info("Admin credited: userId={} credits={}", userId, dto.getCredits());

        notificationService.sendNotification(
                userId, "PAYOUT",
                "Credits Added 🎁",
                dto.getCredits() + " credits have been added to your account. Reason: " + dto.getReason()
        );
    }

    @Override
    @Transactional
    public void debitUserCredits(Long userId, AdminCreditActionDTO dto) {
        findOrThrow(userId);
        rewardService.adminDebit(userId, dto.getCredits(), "[ADMIN] " + dto.getReason());
        log.info("Admin debited: userId={} credits={}", userId, dto.getCredits());

        notificationService.sendNotification(
                userId, "INFO",
                "Credits Deducted",
                dto.getCredits() + " credits have been deducted from your account. Reason: " + dto.getReason()
        );
    }

    // ── Notes ─────────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public void addAdminNote(Long userId, AdminUserActionDTO dto) {
        User user = findOrThrow(userId);
        user.setAdminNotes(dto.getNotes());
        userRepo.save(user);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private User findOrThrow(Long id) {
        return userRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private <T> PageResponse<T> buildPage(List<T> content, Page<?> page) {
        return PageResponse.<T>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    // ── Admin Password Reset ──────────────────────────────────────────────────
    /**
     * Generates a secure random temporary password, sets it on the account,
     * revokes all active refresh tokens (forcing re-login), and emails the
     * new password to the user.  The user should be prompted to change it
     * on next login.
     */
    @Override
    @Transactional
    public void resetUserPassword(Long userId, AdminUserActionDTO dto) {
        User user = findOrThrow(userId);
        if (user.getRole() == Role.ADMIN)
            throw new BadRequestException("Cannot reset another admin's password");

        String tempPassword = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        user.setPassword(passwordEncoder.encode(tempPassword));
        userRepo.save(user);

        refreshTokenService.revokeAllForUser(userId);
        log.info("Admin force-reset password for userId={}", userId);

        notificationService.sendEmail(
            user.getEmail(),
            "Your EarnX3 Password Has Been Reset",
            """
            Hi %s,

            An administrator has reset your account password.

            Temporary password: %s

            Please log in and change your password immediately.
            Reason: %s

            If you did not request this, contact support right away.

            — EarnX3 Team
            """.formatted(user.getName(), tempPassword,
                dto.getReason() != null ? dto.getReason() : "Admin action")
        );

        notificationService.sendNotification(
            userId, "WARNING",
            "Password Reset by Admin",
            "Your password has been reset by an administrator. Check your email for the temporary password."
        );
    }

    // ── Mark As Fraud ─────────────────────────────────────────────────────────
    /**
     * Blocks the account, records the fraud reason in admin notes and the
     * block_reason column, revokes all sessions, and notifies the user.
     */
    @Override
    @Transactional
    public void markAsFraud(Long userId, AdminUserActionDTO dto) {
        User user = findOrThrow(userId);
        if (user.getRole() == Role.ADMIN)
            throw new BadRequestException("Cannot mark an admin account as fraud");

        String reason = dto.getReason() != null ? dto.getReason() : "Suspicious activity detected";
        String notes  = "[FRAUD] " + reason;

        user.setIsBlocked(true);
        user.setBlockReason(notes);
        user.setBlockedAt(OffsetDateTime.now());
        user.setAdminNotes(
            (user.getAdminNotes() != null ? user.getAdminNotes() + "\n" : "") + notes
        );
        userRepo.save(user);

        refreshTokenService.revokeAllForUser(userId);
        log.warn("Admin marked userId={} as FRAUD. Reason={}", userId, reason);

        notificationService.sendNotification(
            userId, "WARNING",
            "Account Suspended",
            "Your account has been suspended due to suspicious activity. Contact support if you believe this is a mistake."
        );
    }
}