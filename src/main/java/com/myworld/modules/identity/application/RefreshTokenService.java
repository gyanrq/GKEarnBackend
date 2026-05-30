package com.myworld.modules.identity.application;

import com.myworld.core.constant.AppConstants;
import com.myworld.core.exception.TokenRefreshException;
import com.myworld.modules.identity.domain.RefreshToken;
import com.myworld.modules.identity.domain.User;
import com.myworld.modules.identity.infrastructure.RefreshTokenRepository;
import com.myworld.modules.identity.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepo;
    private final UserRepository userRepo;

    @Transactional
    public RefreshToken createRefreshToken(Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Revoke all existing tokens for this user (single session)
        refreshTokenRepo.revokeAllByUserId(userId);

        RefreshToken token = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiresAt(OffsetDateTime.now().plusDays(AppConstants.REFRESH_TOKEN_EXPIRY_DAYS))
                .revoked(false)
                .build();
        return refreshTokenRepo.save(token);
    }

    public RefreshToken validateAndGet(String token) {
        RefreshToken rt = refreshTokenRepo.findByToken(token)
                .orElseThrow(() -> new TokenRefreshException("Invalid refresh token"));

        if (Boolean.TRUE.equals(rt.getRevoked())) {
            throw new TokenRefreshException("Refresh token has been revoked");
        }
        if (rt.isExpired()) {
            throw new TokenRefreshException("Refresh token has expired");
        }
        return rt;
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        refreshTokenRepo.revokeAllByUserId(userId);
    }
}
