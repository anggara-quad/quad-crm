package com.quadteknologi.crm.service;

import com.quadteknologi.crm.domain.entity.InternalCompany;
import com.quadteknologi.crm.domain.entity.Lead;
import com.quadteknologi.crm.domain.entity.Opportunity;
import com.quadteknologi.crm.domain.entity.OptionValue;
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
import static org.mockito.Mockito.mock;
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
    @Mock
    private ActiveInternalCompanySession activeInternalCompanySession;

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
                contactService,
                activeInternalCompanySession);
        when(activeInternalCompanySession.requireCompanyId()).thenReturn(1L);
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
    void forecastedMarginDetailsExcludeWonAndLostOpportunities() {
        Opportunity open = opportunity("Open opportunity", "DISCOVERY");
        Opportunity won = opportunity("Won opportunity", "WON");
        Opportunity lost = opportunity("Lost opportunity", "LOST");
        when(opportunityRepository.findDashboardForecastOpportunities(
                "OPPORTUNITY_STATUS",
                filter.startDate(),
                filter.endDate(),
                null)).thenReturn(List.of(open, won, lost));

        MetricDetails details = service.getMetricDetails(filter, MetricType.FORECASTED_MARGIN);

        assertThat(details.rows()).extracting(DashboardService.MetricDetailRow::title)
                .containsExactly("Open opportunity");
    }

    @Test
    void forecastedMarginDetailsSortByExpectedCloseDate() {
        Opportunity later = opportunity("Later close", "DISCOVERY");
        later.setExpectedCloseDate(filter.endDate().plusDays(5));
        Opportunity sooner = opportunity("Sooner close", "DISCOVERY");
        sooner.setExpectedCloseDate(filter.startDate());
        when(opportunityRepository.findDashboardForecastOpportunities(
                "OPPORTUNITY_STATUS",
                filter.startDate(),
                filter.endDate(),
                null)).thenReturn(List.of(later, sooner));

        MetricDetails details = service.getMetricDetails(filter, MetricType.FORECASTED_MARGIN);

        assertThat(details.rows()).extracting(DashboardService.MetricDetailRow::title)
                .containsExactly("Sooner close", "Later close");
    }

    @Test
    void invoicedAndClosedOpportunitiesCountAsWonButNotAsForecast() {
        Opportunity invoiced = opportunityWithStatus("Invoiced opportunity", wonStatus("INVOICED"));
        Opportunity closed = opportunityWithStatus("Closed opportunity", wonStatus("CLOSED"));
        when(opportunityRepository.findDashboardOpportunities(
                "OPPORTUNITY_STATUS",
                filter.startDate().atStartOfDay(),
                filter.endDate().plusDays(1).atStartOfDay(),
                null)).thenReturn(List.of(invoiced, closed));
        when(opportunityRepository.findDashboardForecastOpportunities(
                "OPPORTUNITY_STATUS",
                filter.startDate(),
                filter.endDate(),
                null)).thenReturn(List.of(invoiced, closed));

        MetricDetails wonDetails = service.getMetricDetails(filter, MetricType.WON_MARGIN);
        MetricDetails forecastDetails = service.getMetricDetails(filter, MetricType.FORECASTED_MARGIN);

        assertThat(wonDetails.rows()).extracting(DashboardService.MetricDetailRow::title)
                .containsExactly("Invoiced opportunity", "Closed opportunity");
        assertThat(forecastDetails.rows()).isEmpty();
    }

    @Test
    void invoicedDetailsOnlyIncludeInvoicedOpportunities() {
        Opportunity invoiced = opportunity("Invoiced opportunity", "INVOICED");
        Opportunity closed = opportunity("Closed opportunity", "CLOSED");
        Opportunity won = opportunity("Won opportunity", "WON");
        when(opportunityRepository.findDashboardOpportunities(
                "OPPORTUNITY_STATUS",
                filter.startDate().atStartOfDay(),
                filter.endDate().plusDays(1).atStartOfDay(),
                null)).thenReturn(List.of(invoiced, closed, won));

        MetricDetails details = service.getMetricDetails(filter, MetricType.INVOICED);

        assertThat(details.rows()).extracting(DashboardService.MetricDetailRow::title)
                .containsExactly("Invoiced opportunity");
        assertThat(details.rows()).extracting(DashboardService.MetricDetailRow::badge)
                .containsExactly("Invoiced");
    }

    @Test
    void closedDetailsOnlyIncludeClosedOpportunities() {
        Opportunity invoiced = opportunity("Invoiced opportunity", "INVOICED");
        Opportunity closed = opportunity("Closed opportunity", "CLOSED");
        when(opportunityRepository.findDashboardOpportunities(
                "OPPORTUNITY_STATUS",
                filter.startDate().atStartOfDay(),
                filter.endDate().plusDays(1).atStartOfDay(),
                null)).thenReturn(List.of(invoiced, closed));

        MetricDetails details = service.getMetricDetails(filter, MetricType.CLOSED);

        assertThat(details.rows()).extracting(DashboardService.MetricDetailRow::title)
                .containsExactly("Closed opportunity");
        assertThat(details.rows()).extracting(DashboardService.MetricDetailRow::badge)
                .containsExactly("Closed");
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
        opportunity.setInternalCompany(activeCompany());
        return opportunity;
    }

    private Opportunity opportunityWithStatus(String title, OptionValue status) {
        String statusCode = status.getCode();
        InternalCompany company = activeCompany();
        Opportunity opportunity = mock(Opportunity.class);
        when(opportunity.getTitle()).thenReturn(title);
        when(opportunity.getStatus()).thenReturn(status);
        when(opportunity.getStatusCode()).thenReturn(statusCode);
        when(opportunity.getInternalCompany()).thenReturn(company);
        return opportunity;
    }

    private InternalCompany activeCompany() {
        InternalCompany company = mock(InternalCompany.class);
        when(company.getId()).thenReturn(1L);
        return company;
    }

    private OptionValue wonStatus(String code) {
        OptionValue status = mock(OptionValue.class);
        when(status.getCode()).thenReturn(code);
        when(status.getName()).thenReturn(code);
        when(status.getWon()).thenReturn(true);
        return status;
    }

    private Lead lead(String title, String statusCode) {
        Lead lead = new Lead();
        lead.setTitle(title);
        lead.setStatusCode(statusCode);
        lead.setInternalCompany(activeCompany());
        return lead;
    }
}
