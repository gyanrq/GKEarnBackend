package com.myworld.modules.referral.infrastructure;

import com.myworld.modules.referral.domain.Milestone;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MilestoneRepository extends JpaRepository<Milestone, Long> {
    List<Milestone> findByIsActiveTrue();
}
