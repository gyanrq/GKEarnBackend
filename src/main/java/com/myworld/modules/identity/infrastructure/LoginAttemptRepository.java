package com.myworld.modules.identity.infrastructure;

import com.myworld.modules.identity.domain.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {}
