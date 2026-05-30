package com.myworld.modules.referral.application;

import com.myworld.core.dto.PageResponse;
import com.myworld.modules.referral.api.ReferralResponseDTO;

public interface ReferralService {
    PageResponse<ReferralResponseDTO> getMyReferrals(Long userId, int page, int size);
    long countSuccessfulReferrals(Long userId);
}
