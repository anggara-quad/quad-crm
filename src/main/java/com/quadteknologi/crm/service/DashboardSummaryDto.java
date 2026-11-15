package com.quadteknologi.crm.service;

import java.math.BigDecimal;

public record DashboardSummaryDto(
        long leads,
        long validLeads,
        long openOpportunities,
        BigDecimal openPipeline,
        BigDecimal openMargin,
        BigDecimal wonRevenue,
        BigDecimal wonMargin,
        BigDecimal invoicedRevenue,
        BigDecimal invoicedMargin,
        long invoicedOpportunities,
        BigDecimal closedRevenue,
        BigDecimal closedMargin,
        long closedOpportunities,
        BigDecimal forecastedMargin,
        long contacts,
        long organizations,
        long closingSoon,
        int closedWinRate,
        int wonRatio,
        long activeSales) {
}
