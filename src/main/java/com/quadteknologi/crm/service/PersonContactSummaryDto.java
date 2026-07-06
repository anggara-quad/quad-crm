package com.quadteknologi.crm.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PersonContactSummaryDto(
        Long personId,
        UUID personPublicId,
        String fullName,
        String jobTitle,
        String companyName,
        String email,
        String phone,
        String whatsapp,
        long leads,
        long validLeads,
        long opportunities,
        long openOpportunities,
        BigDecimal openPipeline,
        BigDecimal wonRevenue,
        LocalDateTime lastActivityAt) {
}
