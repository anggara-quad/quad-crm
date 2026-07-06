package com.quadteknologi.crm.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record SalesPerformanceDto(
        Long salesId,
        String salesName,
        long leads,
        long validLeads,
        long openOpportunities,
        BigDecimal openPipeline,
        BigDecimal openMargin,
        BigDecimal wonRevenue,
        BigDecimal wonMargin,
        BigDecimal forecastedMargin,
        int closedWinRate,
        long closingSoon,
        LocalDateTime lastActivity,
        String health,
        List<SalesLeadDetailDto> leadDetails,
        List<SalesOpportunityDetailDto> opportunityDetails) {
}
