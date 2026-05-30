package com.myworld.modules.spin.infrastructure;

import com.myworld.modules.spin.domain.SpinHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public interface SpinHistoryRepository extends JpaRepository<SpinHistory, Long> {
    boolean existsByUserIdAndCreatedAtAfter(Long userId, OffsetDateTime since);
    
    Optional<SpinHistory> findTopByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(
    	    Long userId, OffsetDateTime since);
}