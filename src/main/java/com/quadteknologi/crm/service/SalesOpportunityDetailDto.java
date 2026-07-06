package com.quadteknologi.crm.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SalesOpportunityDetailDto(
        UUID opportunityPublicId,
        String title,
        String account,
        String stage,
        String leadTitle,
        BigDecimal amount,
        BigDecimal margin,
        Integer probability,
        LocalDate expectedCloseDate) {
}
