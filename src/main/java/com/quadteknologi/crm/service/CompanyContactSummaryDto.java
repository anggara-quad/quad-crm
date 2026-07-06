package com.quadteknologi.crm.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CompanyContactSummaryDto(
        Long companyId,
        UUID companyPublicId,
        String name,
        String industry,
        String email,
        String phone,
        String website,
        String city,
        String province,
        String country,
        long contacts,
        long leads,
        long validLeads,
        long opportunities,
        long openOpportunities,
        BigDecimal openPipeline,
        BigDecimal wonRevenue,
        LocalDateTime lastActivityAt) {
}
