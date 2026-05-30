package com.myworld.modules.notification.infrastructure;

import com.myworld.modules.notification.domain.Notification;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // FIX: was countByUserIdAndIsReadFalse — field is now `read` not `isRead`
    long countByUserIdAndReadFalse(Long userId);

    // FIX: JPQL updated to use `n.read` not `n.isRead`
    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.user.id = :userId AND n.read = false")
    int markAllAsReadByUserId(@Param("userId") Long userId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);
}