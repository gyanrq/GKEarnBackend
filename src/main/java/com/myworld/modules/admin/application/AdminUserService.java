package com.myworld.modules.admin.application;

import com.myworld.core.constant.Role;
import com.myworld.core.dto.PageResponse;
import com.myworld.modules.admin.api.*;
import com.myworld.modules.identity.api.UserResponseDTO;

public interface AdminUserService {

    // ── List / Search ─────────────────────────────────────────────────────────
    PageResponse<UserResponseDTO> searchUsers(String q, Role role, Boolean isBlocked,
                                              int page, int size);

    UserResponseDTO getUserDetail(Long userId);

    // ── Block / Unblock ───────────────────────────────────────────────────────
    void blockUser(Long userId, AdminUserActionDTO dto);
    void unblockUser(Long userId);

    // ── Soft Delete ───────────────────────────────────────────────────────────
    void deleteUser(Long userId);

    // ── Role Management ───────────────────────────────────────────────────────
    void changeRole(Long userId, AdminChangeRoleDTO dto);

    // ── Credits Management ────────────────────────────────────────────────────
    void creditUserCredits(Long userId, AdminCreditActionDTO dto);
    void debitUserCredits(Long userId, AdminCreditActionDTO dto);

    // ── Admin Notes ───────────────────────────────────────────────────────────
    void addAdminNote(Long userId, AdminUserActionDTO dto);

	void resetUserPassword(Long userId, AdminUserActionDTO dto);

	void markAsFraud(Long userId, AdminUserActionDTO dto);
}