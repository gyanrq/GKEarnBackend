package com.myworld.modules.identity.infrastructure;

import com.myworld.modules.identity.domain.User;
import com.myworld.core.constant.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>,
        JpaSpecificationExecutor<User> {

    Optional<User> findByPhone(String phone);
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailOrPhone(String email, String phone);
    Optional<User> findByReferralCode(String code);
    Optional<User> findByReferralCodeIgnoreCase(String code);

    // FIX: added — required by UserServiceImpl to detect referral code collisions before saving
    boolean existsByReferralCode(String referralCode);

    // FIX: Changed return type to long and query to just COUNT(*). 
    // This avoids the Long to Boolean ClassCastException in native queries.
    @Query(value = "SELECT COUNT(*) FROM users WHERE email = :email",
           nativeQuery = true)
    long countByEmailIncludingDeleted(@Param("email") String email);

    @Query(value = "SELECT COUNT(*) FROM users WHERE phone = :phone",
           nativeQuery = true)
    long countByPhoneIncludingDeleted(@Param("phone") String phone);

    // Default boolean wrapper methods for the Service layer to use
    default boolean existsByEmailIncludingDeleted(String email) {
        return countByEmailIncludingDeleted(email) > 0;
    }

    default boolean existsByPhoneIncludingDeleted(String phone) {
        return countByPhoneIncludingDeleted(phone) > 0;
    }

    Page<User> findByRole(Role role, Pageable pageable);
    Page<User> findByIsBlocked(Boolean isBlocked, Pageable pageable);
    Page<User> findByIsDeleted(Boolean isDeleted, Pageable pageable);

    @Query("""
        SELECT u FROM User u
        WHERE u.isDeleted = false
        AND (:q IS NULL OR
             LOWER(u.name)  LIKE LOWER(CONCAT('%', :q, '%')) OR
             LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')) OR
             u.phone        LIKE CONCAT('%', :q, '%'))
        AND (:role      IS NULL OR u.role = :role)
        AND (:isBlocked IS NULL OR u.isBlocked = :isBlocked)
    """)
    Page<User> adminSearch(
        @Param("q") String q,
        @Param("role") Role role,
        @Param("isBlocked") Boolean isBlocked,
        Pageable pageable
    );

    long countByRole(Role role);
    long countByIsBlocked(Boolean isBlocked);
    long countByIsDeleted(Boolean isDeleted);

    // FIX: added — for correct active user count in AdminDashboardService
    long countByIsDeletedFalseAndIsBlockedFalse();

    @Modifying
    @Query("UPDATE User u SET u.isDeleted = true WHERE u.id = :id")
    void softDelete(@Param("id") Long id);

    // ── Fraud / Duplicate Detection ───────────────────────────────────────────

    @Query("SELECT COUNT(u) FROM User u " +
           "WHERE u.lastLoginIp = :ip " +
           "AND u.id != :excludeUserId " +
           "AND u.isBlocked = false AND u.isDeleted = false")
    long countActiveUsersWithSameIp(
            @Param("ip")            String ip,
            @Param("excludeUserId") Long   excludeUserId);

    @Query("SELECT u FROM User u " +
           "WHERE u.lastLoginIp = :ip AND u.isDeleted = false " +
           "ORDER BY u.createdAt DESC")
    List<User> findAllByLastLoginIp(@Param("ip") String ip);

    // ── Fraud admin panel — shared IP via GROUP BY (avoids full table scan) ──

    @Query(value = """
        SELECT last_login_ip AS ip, COUNT(*) AS user_count
        FROM users
        WHERE is_deleted = false
          AND last_login_ip IS NOT NULL
          AND last_login_ip <> ''
        GROUP BY last_login_ip
        HAVING COUNT(*) >= :min
        ORDER BY user_count DESC
    """, nativeQuery = true)
    List<Object[]> findSharedIpGroups(@Param("min") int min);
	boolean existsByPhone(String phone);
	boolean existsByEmail(String email);

    long countByCreatedAtBetween(OffsetDateTime start, OffsetDateTime end);
}
