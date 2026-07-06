package com.quadteknologi.crm.service;

import java.math.BigDecimal;

public record LeadSourcePerformanceDto(
        String source,
        long totalLeads,
        long validLeads,
        int validRate,
        BigDecimal estimatedLeadValue,
        long convertedOpportunities,
        int conversionRate) {
}
