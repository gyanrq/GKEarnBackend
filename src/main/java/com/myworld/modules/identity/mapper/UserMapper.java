package com.myworld.modules.identity.mapper;

import com.myworld.core.constant.Role;
import com.myworld.modules.identity.api.UserRequestDTO;
import com.myworld.modules.identity.api.UserResponseDTO;
import com.myworld.modules.identity.domain.User;
import com.myworld.modules.identity.infrastructure.MfaConfigRepository;
import com.myworld.modules.rewards.application.RewardService;
import com.myworld.modules.rewards.domain.UserReward;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserMapper {

    private final RewardService rewardService;
    private final MfaConfigRepository mfaConfigRepo; // ✅ FIX: inject to read mfaEnabled

    public UserResponseDTO toDTO(User user) {
        if (user == null) return null;

        UserReward reward = rewardService.getBalance(user.getId());
        long totalCredits    = reward.getTotalCredits()    != null ? reward.getTotalCredits()    : 0L;
        long redeemedCredits = reward.getRedeemedCredits() != null ? reward.getRedeemedCredits() : 0L;
        long pendingCredits  = reward.getPendingCredits()  != null ? reward.getPendingCredits()  : 0L;

        // ✅ FIX: read actual MFA status from DB so Settings screen badge is accurate
        Boolean mfaEnabled = mfaConfigRepo.findByUserId(user.getId())
                .map(cfg -> Boolean.TRUE.equals(cfg.getMfaEnabled()))
                .orElse(false);

        return UserResponseDTO.builder()
                .id(user.getId())
                .uuid(user.getUuid())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole() != null ? user.getRole().name() : "USER")
                .referralCode(user.getReferralCode())
                // profile
                .profilePictureUrl(user.getProfilePictureUrl())
                .dateOfBirth(user.getDateOfBirth())
                .address(user.getAddress())
                .city(user.getCity())
                .state(user.getState())
                .pincode(user.getPincode())
                // status
                .isBlocked(user.getIsBlocked())
                .isEmailVerified(user.getIsEmailVerified())
                .isPhoneVerified(user.getIsPhoneVerified())
                .mfaEnabled(mfaEnabled)   // ✅ FIX: populate from mfa_configs table
                // credits snapshot
                .totalCredits(totalCredits)
                .redeemedCredits(redeemedCredits)
                .pendingCredits(pendingCredits)
                // timestamps
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }

    /** Admin-facing DTO – same as toDTO (no sensitive data to unmask anymore) */
    public UserResponseDTO toDTOForAdmin(User user) {
        return toDTO(user);
    }

    public User toEntity(UserRequestDTO dto) {
        if (dto == null) return null;
        return User.builder()
                .name(dto.getName())
                .email(dto.getEmail().toLowerCase().trim())
                .phone(dto.getPhone().trim())
                .referredByCode(dto.getReferredByCode())
                .role(Role.USER)
                .build();
    }
}