package com.quadteknologi.crm.service;

import com.quadteknologi.crm.domain.repository.ActivityRepository;
import com.quadteknologi.crm.domain.repository.LeadRepository;
import com.quadteknologi.crm.domain.repository.OpportunityRepository;
import com.quadteknologi.crm.domain.repository.OptionValueRepository;
import com.quadteknologi.crm.domain.repository.UserRepository;
import com.quadteknologi.crm.security.CurrentUserService;
import com.quadteknologi.crm.service.DashboardService.MetricDetails;
import com.quadteknologi.crm.service.DashboardService.MetricType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardFilterPersistenceTest {

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

    @Test
    void dashboardAndSalesSlotsKeepSeparateFilters() {
        DashboardFilterSession session = new DashboardFilterSession();
        DashboardFilter dashboard = DashboardFilter.currentMonth(DashboardScope.TEAM_DATA, null);
        DashboardFilter sales = DashboardFilter.currentMonth(DashboardScope.SELECTED_SALES, 7L);

        session.setDashboard(dashboard);
        session.setSales(sales);

        assertThat(session.getDashboard()).isEqualTo(dashboard);
        assertThat(session.getSales()).isEqualTo(sales);
    }

    @Test
    void staleRestoredSalesUserFallsBackToTeamScope() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 31);
        when(dataAccessService.isSalesScope()).thenReturn(false);
        when(userRepository.findActiveSalesUsersOrderByFullNameAsc()).thenReturn(List.of());
        when(leadRepository.findDashboardLeads(
                eq("LEAD_STATUS"), any(LocalDateTime.class), any(LocalDateTime.class), isNull()))
                .thenReturn(List.of());
        when(activeInternalCompanySession.requireCompanyId()).thenReturn(1L);

        DashboardService service = new DashboardService(
                leadRepository,
                opportunityRepository,
                activityRepository,
                optionValueRepository,
                userRepository,
                dataAccessService,
                currentUserService,
                contactService,
                activeInternalCompanySession);

        MetricDetails details = service.getMetricDetails(
                new DashboardFilter(DashboardScope.SELECTED_SALES, 99L, start, end),
                MetricType.LEADS);

        assertThat(details.filter().scope()).isEqualTo(DashboardScope.TEAM_DATA);
        assertThat(details.filter().salesId()).isNull();
        verify(leadRepository).findDashboardLeads(eq("LEAD_STATUS"), any(), any(), isNull());
    }
}
