package com.myworld.modules.fraud.web;

import com.myworld.core.dto.ApiResponse;
import com.myworld.modules.fraud.api.DuplicateGroupDTO;
import com.myworld.modules.fraud.application.FraudAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/fraud")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class FraudAdminController {

    private final FraudAdminService fraudAdminService;

    /**
     * GET /api/admin/fraud/duplicates/ip?minCount=3
     * Returns groups of users sharing the same login IP.
     */
    @GetMapping("/duplicates/ip")
    public ApiResponse<List<DuplicateGroupDTO>> getSameIpGroups(
            @RequestParam(defaultValue = "3") int minCount) {
        return ApiResponse.success(
                fraudAdminService.getSameIpGroups(minCount),
                "Same-IP duplicate groups fetched");
    }

    /**
     * GET /api/admin/fraud/duplicates/device?minCount=2
     * Returns groups of users sharing the same device fingerprint.
     */
    @GetMapping("/duplicates/device")
    public ApiResponse<List<DuplicateGroupDTO>> getSameDeviceGroups(
            @RequestParam(defaultValue = "2") int minCount) {
        return ApiResponse.success(
                fraudAdminService.getSameDeviceGroups(minCount),
                "Same-device duplicate groups fetched");
    }

    /**
     * GET /api/admin/fraud/duplicates/upi
     * Returns groups of users sharing the same UPI ID.
     */
    @GetMapping("/duplicates/upi")
    public ApiResponse<List<DuplicateGroupDTO>> getSameUpiGroups() {
        return ApiResponse.success(
                fraudAdminService.getSameUpiGroups(),
                "Same-UPI duplicate groups fetched");
    }
}