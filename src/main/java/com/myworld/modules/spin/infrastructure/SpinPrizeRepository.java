package com.myworld.modules.spin.infrastructure;

import com.myworld.modules.spin.domain.SpinPrize;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SpinPrizeRepository extends JpaRepository<SpinPrize, Long> {
    List<SpinPrize> findByIsActiveTrueOrderBySortOrderAsc();
    List<SpinPrize> findAllByOrderBySortOrderAsc();
}