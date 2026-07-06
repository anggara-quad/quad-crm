package com.quadteknologi.crm.service;

import java.time.LocalDate;

public record DashboardFilter(
        DashboardScope scope,
        Long salesId,
        LocalDate startDate,
        LocalDate endDate,
        String leadStatusCode,
        String opportunityStatusCode,
        String leadSource) {

    public DashboardFilter(DashboardScope scope, Long salesId, LocalDate startDate, LocalDate endDate) {
        this(scope, salesId, startDate, endDate, null, null, null);
    }

    public static DashboardFilter currentMonth(DashboardScope scope, Long salesId) {
        LocalDate today = LocalDate.now();
        return new DashboardFilter(scope, salesId, today.withDayOfMonth(1), today);
    }

    public DashboardFilter withScope(DashboardScope scope, Long salesId) {
        return new DashboardFilter(scope, salesId, startDate, endDate, leadStatusCode, opportunityStatusCode, leadSource);
    }

    public DashboardFilter withDateRange(LocalDate startDate, LocalDate endDate) {
        return new DashboardFilter(scope, salesId, startDate, endDate, leadStatusCode, opportunityStatusCode, leadSource);
    }

    public DashboardFilter withLeadStatusCode(String leadStatusCode) {
        return new DashboardFilter(scope, salesId, startDate, endDate,
                normalize(leadStatusCode), opportunityStatusCode, leadSource);
    }

    public DashboardFilter withOpportunityStatusCode(String opportunityStatusCode) {
        return new DashboardFilter(scope, salesId, startDate, endDate,
                leadStatusCode, normalize(opportunityStatusCode), leadSource);
    }

    public DashboardFilter withLeadSource(String leadSource) {
        return new DashboardFilter(scope, salesId, startDate, endDate,
                leadStatusCode, opportunityStatusCode, normalize(leadSource));
    }

    public DashboardFilter clearInteractiveFilters() {
        return new DashboardFilter(scope, salesId, startDate, endDate, null, null, null);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
