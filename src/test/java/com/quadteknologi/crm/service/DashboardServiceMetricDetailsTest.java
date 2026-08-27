package com.quadteknologi.crm.service;

import com.quadteknologi.crm.domain.entity.Lead;
import com.quadteknologi.crm.domain.entity.Opportunity;
import com.quadteknologi.crm.domain.repository.ActivityRepository;
import com.quadteknologi.crm.domain.repository.LeadRepository;
import com.quadteknologi.crm.domain.repository.OpportunityRepository;
import com.quadteknologi.crm.domain.repository.OptionValueRepository;
import com.quadteknologi.crm.domain.repository.UserRepository;
import com.quadteknologi.crm.security.CurrentUserService;
import com.quadteknologi.crm.service.DashboardService.MetricDetails;
import com.quadteknologi.crm.service.DashboardService.MetricType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceMetricDetailsTest {

    @Mock
    private LeadRepository leadRepository;
    @Mock
    private OpportunityRepository opportunityRepository;
    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private OptionValueRepository optionValueRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DataAccessService dataAccessService;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private ContactService contactService;

    private DashboardService service;
    private DashboardFilter filter;

    @BeforeEach
    void setUp() {
        service = new DashboardService(
                leadRepository,
                opportunityRepository,
                activityRepository,
                optionValueRepository,
                userRepository,
                dataAccessService,
                currentUserService,
                contactService);
        LocalDate date = LocalDate.of(2026, 8, 27);
        filter = new DashboardFilter(DashboardScope.TEAM_DATA, null, date, date);
    }

    @Test
    void openPipelineDetailsExcludeWonAndLostOpportunities() {
        Opportunity open = opportunity("Open opportunity", "DISCOVERY");
        Opportunity won = opportunity("Won opportunity", "WON");
        Opportunity lost = opportunity("Lost opportunity", "LOST");
        when(opportunityRepository.findDashboardOpportunities(
                "OPPORTUNITY_STATUS",
                filter.startDate().atStartOfDay(),
                filter.endDate().plusDays(1).atStartOfDay(),
                null)).thenReturn(List.of(open, won, lost));

        MetricDetails details = service.getMetricDetails(filter, MetricType.OPEN_PIPELINE);

        assertThat(details.rows()).extracting(DashboardService.MetricDetailRow::title)
                .containsExactly("Open opportunity");
    }

    @Test
    void forecastedMarginDetailsMatchTheNotWonSummaryDefinition() {
        Opportunity open = opportunity("Open opportunity", "DISCOVERY");
        Opportunity won = opportunity("Won opportunity", "WON");
        Opportunity lost = opportunity("Lost opportunity", "LOST");
        when(opportunityRepository.findDashboardOpportunities(
                "OPPORTUNITY_STATUS",
                filter.startDate().atStartOfDay(),
                filter.endDate().plusDays(1).atStartOfDay(),
                null)).thenReturn(List.of(open, won, lost));

        MetricDetails details = service.getMetricDetails(filter, MetricType.FORECASTED_MARGIN);

        assertThat(details.rows()).extracting(DashboardService.MetricDetailRow::title)
                .containsExactly("Open opportunity", "Lost opportunity");
    }

    @Test
    void validLeadDetailsOnlyIncludeQualifiedLeads() {
        Lead valid = lead("Valid lead", "VALID");
        Lead newLead = lead("New lead", "NEW");
        when(leadRepository.findDashboardLeads(
                "LEAD_STATUS",
                filter.startDate().atStartOfDay(),
                filter.endDate().plusDays(1).atStartOfDay(),
                null)).thenReturn(List.of(valid, newLead));

        MetricDetails details = service.getMetricDetails(filter, MetricType.VALID_LEADS);

        assertThat(details.rows()).extracting(DashboardService.MetricDetailRow::title)
                .containsExactly("Valid lead");
    }

    private Opportunity opportunity(String title, String statusCode) {
        Opportunity opportunity = new Opportunity();
        opportunity.setTitle(title);
        opportunity.setStatusCode(statusCode);
        opportunity.setEstimatedAmount(BigDecimal.TEN);
        opportunity.setMargin(BigDecimal.ONE);
        return opportunity;
    }

    private Lead lead(String title, String statusCode) {
        Lead lead = new Lead();
        lead.setTitle(title);
        lead.setStatusCode(statusCode);
        return lead;
    }
}
