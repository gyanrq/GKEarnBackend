package com.myworld.modules.fraud.application;

import com.myworld.modules.fraud.api.DuplicateGroupDTO;
import com.myworld.modules.identity.domain.DeviceFingerprint;
import com.myworld.modules.identity.domain.User;
import com.myworld.modules.identity.infrastructure.DeviceFingerprintRepository;
import com.myworld.modules.identity.infrastructure.UserRepository;
import com.myworld.modules.rewards.infrastructure.RedeemLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FraudAdminService {

    private final UserRepository              userRepo;
    private final DeviceFingerprintRepository fingerprintRepo;
    private final RedeemLogRepository         redeemLogRepo;

    // ── Shared IP groups ──────────────────────────────────────────────────────

    /**
     * FIX: Was calling userRepo.findAll() and filtering in Java — catastrophic on large datasets.
     * Now uses a native GROUP BY query that returns only the IPs with >= minCount users,
     * then fetches users per group individually. This is O(groups) queries not O(all users).
     */
    public List<DuplicateGroupDTO> getSameIpGroups(int minCount) {
        List<Object[]> rows = userRepo.findSharedIpGroups(minCount);

        return rows.stream().map(row -> {
            String ip        = (String) row[0];
            long   userCount = ((Number) row[1]).longValue();
            List<User> users = userRepo.findAllByLastLoginIp(ip);
            return DuplicateGroupDTO.builder()
                    .type("IP")
                    .value(ip)
                    .userCount((int) userCount)
                    .users(toUserDTOs(users))
                    .build();
        }).collect(Collectors.toList());
    }

    // ── Shared device groups ──────────────────────────────────────────────────

    /**
     * FIX: Was calling fingerprintRepo.findAll() — loads entire table into memory.
     * Now uses a GROUP BY query, then fetches records per device only for groups that qualify.
     */
    public List<DuplicateGroupDTO> getSameDeviceGroups(int minCount) {
        List<Object[]> rows = fingerprintRepo.findSharedDeviceGroups(minCount);

        return rows.stream().map(row -> {
            String deviceId  = (String) row[0];
            long   userCount = ((Number) row[1]).longValue();
            List<DeviceFingerprint> fps = fingerprintRepo.findAllByDeviceId(deviceId);
            List<User> users = fps.stream()
                    .map(DeviceFingerprint::getUser)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            return DuplicateGroupDTO.builder()
                    .type("DEVICE")
                    .value(deviceId.substring(0, Math.min(12, deviceId.length())) + "…")
                    .userCount(users.size())
                    .users(toUserDTOs(users))
                    .build();
        })
        .filter(g -> g.getUserCount() >= minCount)
        .sorted(Comparator.comparingInt(DuplicateGroupDTO::getUserCount).reversed())
        .collect(Collectors.toList());
    }

    // ── Shared UPI groups ─────────────────────────────────────────────────────
    public List<DuplicateGroupDTO> getSameUpiGroups() {
        return redeemLogRepo.findSharedUpiGroups().stream()
                .map(row -> {
                    String upi   = (String) row[0];
                    Long   count = (Long)   row[1];
                    List<User> users = redeemLogRepo.findUsersByPaymentDetails(upi).stream()
                            .map(uid -> userRepo.findById(uid).orElse(null))
                            .filter(Objects::nonNull)
                            .toList();
                    return DuplicateGroupDTO.builder()
                            .type("UPI")
                            .value(maskUpi(upi))
                            .userCount(count.intValue())
                            .users(toUserDTOs(users))
                            .build();
                })
                .collect(Collectors.toList());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<DuplicateGroupDTO.DupUserDTO> toUserDTOs(List<User> users) {
        return users.stream().map(u -> DuplicateGroupDTO.DupUserDTO.builder()
                .userId(u.getId())
                .name(u.getName())
                .email(u.getEmail())
                .phone(u.getPhone())
                .createdAt(u.getCreatedAt() != null ? u.getCreatedAt().toString() : null)
                .isBlocked(Boolean.TRUE.equals(u.getIsBlocked()))
                .build()
        ).collect(Collectors.toList());
    }

    private String maskUpi(String upi) {
        if (upi == null || upi.length() < 5) return "****";
        return upi.substring(0, 3) + "***" + upi.substring(upi.length() - 3);
    }
}