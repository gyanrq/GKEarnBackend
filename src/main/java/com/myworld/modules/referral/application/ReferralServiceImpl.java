package com.myworld.modules.referral.application;

import com.myworld.core.dto.PageResponse;
import com.myworld.modules.referral.api.ReferralResponseDTO;
import com.myworld.modules.referral.domain.Referral;
import com.myworld.modules.referral.domain.ReferralStatus;
import com.myworld.modules.referral.infrastructure.ReferralRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReferralServiceImpl implements ReferralService {

    private final ReferralRepository referralRepo;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReferralResponseDTO> getMyReferrals(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Referral> pg = referralRepo.findByReferrerIdOrderByCreatedAtDesc(userId, pageable);

        List<ReferralResponseDTO> content = pg.getContent().stream()
                .map(r -> ReferralResponseDTO.builder()
                        .id(r.getId())
                        .referredUserName(r.getReferred() != null ? r.getReferred().getName() : "N/A")
                        .referredUserEmail(r.getReferred() != null ? r.getReferred().getEmail() : "N/A")
                        .status(r.getStatus().name())
                        .createdAt(r.getCreatedAt())
                        .build())
                .toList();

        return PageResponse.<ReferralResponseDTO>builder()
                .content(content)
                .page(pg.getNumber())
                .size(pg.getSize())
                .totalElements(pg.getTotalElements())
                .totalPages(pg.getTotalPages())
                .last(pg.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public long countSuccessfulReferrals(Long userId) {
        return referralRepo.countByReferrerIdAndStatus(userId, ReferralStatus.SUCCESS);
    }
}
