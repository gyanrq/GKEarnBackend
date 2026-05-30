package com.myworld.modules.identity.infrastructure;

import com.myworld.modules.identity.domain.DeviceFingerprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceFingerprintRepository extends JpaRepository<DeviceFingerprint, Long> {

    // How many OTHER users share this device hash?
    @Query("SELECT COUNT(DISTINCT d.user.id) FROM DeviceFingerprint d " +
           "WHERE d.deviceId = :deviceId AND d.user.id != :excludeUserId")
    long countOtherUsersWithDeviceId(
            @Param("deviceId")      String deviceId,
            @Param("excludeUserId") Long   excludeUserId);

    // For admin duplicate panel — all fingerprints for a given device hash
    @Query("SELECT d FROM DeviceFingerprint d WHERE d.deviceId = :deviceId")
    List<DeviceFingerprint> findAllByDeviceId(@Param("deviceId") String deviceId);

    // Save/update fingerprint for a user
    @Query("SELECT d FROM DeviceFingerprint d WHERE d.user.id = :userId AND d.deviceId = :deviceId")
    Optional<DeviceFingerprint> findByUserIdAndDeviceId(
            @Param("userId")   Long   userId,
            @Param("deviceId") String deviceId);

    // FIX: GROUP BY query — replaces findAll() in FraudAdminService.getSameDeviceGroups().
    // Returns only device IDs shared by >= minCount distinct users, avoiding full table scan.
    @Query(value = """
        SELECT device_id, COUNT(DISTINCT user_id) AS user_count
        FROM device_fingerprints
        WHERE device_id IS NOT NULL AND device_id <> ''
        GROUP BY device_id
        HAVING COUNT(DISTINCT user_id) >= :min
        ORDER BY user_count DESC
    """, nativeQuery = true)
    List<Object[]> findSharedDeviceGroups(@Param("min") int min);
}