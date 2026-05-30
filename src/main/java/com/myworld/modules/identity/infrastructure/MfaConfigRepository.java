package com.myworld.modules.identity.infrastructure;

import com.myworld.modules.identity.domain.MfaConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MfaConfigRepository extends JpaRepository<MfaConfig, Long> {
    Optional<MfaConfig> findByUserId(Long userId);
}