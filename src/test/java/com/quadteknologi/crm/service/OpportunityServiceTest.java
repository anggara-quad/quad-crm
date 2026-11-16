package com.quadteknologi.crm.service;

import com.quadteknologi.crm.domain.entity.InternalCompany;
import com.quadteknologi.crm.domain.entity.Opportunity;
import com.quadteknologi.crm.domain.entity.OptionValue;
import com.quadteknologi.crm.domain.repository.ActivityRepository;
import com.quadteknologi.crm.domain.repository.CompanyRepository;
import com.quadteknologi.crm.domain.repository.LeadItemRepository;
import com.quadteknologi.crm.domain.repository.LeadRepository;
import com.quadteknologi.crm.domain.repository.OpportunityItemRepository;
import com.quadteknologi.crm.domain.repository.OpportunityRepository;
import com.quadteknologi.crm.domain.repository.OptionValueRepository;
import com.quadteknologi.crm.domain.repository.PersonRepository;
import com.quadteknologi.crm.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpportunityServiceTest {

    @Mock
    private OptionValueRepository optionValueRepository;
    @Mock
    private OpportunityRepository opportunityRepository;
    @Mock
    private OpportunityItemRepository opportunityItemRepository;
    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private LeadRepository leadRepository;
    @Mock
    private LeadItemRepository leadItemRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private PersonRepository personRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DataAccessService dataAccessService;
    @Mock
    private ActiveInternalCompanySession activeInternalCompanySession;

    private OpportunityService service;

    @BeforeEach
    void setUp() {
        service = new OpportunityService(
                optionValueRepository,
                opportunityRepository,
                opportunityItemRepository,
                activityRepository,
                leadRepository,
                leadItemRepository,
                companyRepository,
                personRepository,
                userRepository,
                dataAccessService,
                activeInternalCompanySession);
    }

    @Test
    void selectableStatusesFollowInvoiceAndCloseOrder() {
        List<OptionValue> statuses = List.of(
                status("PRODUCT_SOLUTIONING"),
                status("TENDER"),
                status("NEGOTIATION"),
                status("WON"),
                status("LOST"),
                status("INVOICED"),
                status("CLOSED"));
        when(optionValueRepository.findByGroupCodeAndActiveTrueOrderBySortOrderAsc("OPPORTUNITY_STATUS"))
                .thenReturn(statuses);

        assertThat(codes(service.findSelectableStatuses("NEGOTIATION")))
                .containsExactly("PRODUCT_SOLUTIONING", "TENDER", "NEGOTIATION", "WON", "LOST");
        assertThat(codes(service.findSelectableStatuses("WON")))
                .containsExactly("PRODUCT_SOLUTIONING", "TENDER", "NEGOTIATION", "WON", "LOST", "INVOICED");
        assertThat(codes(service.findSelectableStatuses("INVOICED")))
                .containsExactly("INVOICED", "CLOSED");
        assertThat(codes(service.findSelectableStatuses("CLOSED")))
                .containsExactly("CLOSED");
    }

    @Test
    void invoicedAndClosedTransitionsAreGuarded() {
        Opportunity negotiation = opportunity("NEGOTIATION");
        when(opportunityRepository.findById(1L)).thenReturn(Optional.of(negotiation));
        assertThatThrownBy(() -> service.updateOpportunityStatus(1L, status("INVOICED")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only Won opportunities can move to Invoiced");

        Opportunity won = opportunity("WON");
        when(opportunityRepository.findById(2L)).thenReturn(Optional.of(won));
        assertThatThrownBy(() -> service.updateOpportunityStatus(2L, status("CLOSED")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only Invoiced opportunities can move to Closed");

        Opportunity invoiced = opportunity("INVOICED");
        when(opportunityRepository.findById(3L)).thenReturn(Optional.of(invoiced));
        assertThatThrownBy(() -> service.updateOpportunityStatus(3L, status("NEGOTIATION")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invoiced opportunities can only move to Closed");
    }

    @Test
    void movingWonToInvoicedKeepsWonDateAndSetsInvoiceDate() {
        Opportunity won = opportunity("WON");
        LocalDateTime wonAt = LocalDateTime.of(2026, 1, 2, 3, 4);
        won.setWonAt(wonAt);
        when(opportunityRepository.findById(1L)).thenReturn(Optional.of(won));
        when(opportunityRepository.save(won)).thenReturn(won);
        OptionValue invoiced = status("INVOICED");
        when(invoiced.getWon()).thenReturn(true);

        service.updateOpportunityStatus(1L, invoiced);

        assertThat(won.getWonAt()).isEqualTo(wonAt);
        assertThat(won.getInvoiceDate()).isEqualTo(LocalDate.now());
        assertThat(won.getClosedDate()).isNull();
    }

    @Test
    void newOpportunitiesCannotStartInvoicedOrClosed() {
        OpportunityService.OpportunityRequest request = new OpportunityService.OpportunityRequest();
        OptionValue invoiced = status("INVOICED");
        when(invoiced.getName()).thenReturn("Invoiced");
        request.setStatus(invoiced);

        assertThatThrownBy(() -> service.createOpportunity(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("New opportunities cannot start at Invoiced");
    }

    @Test
    void createOpportunityAssignsActiveInternalCompany() {
        InternalCompany company = mock(InternalCompany.class);
        when(activeInternalCompanySession.requireCompany()).thenReturn(company);
        when(opportunityRepository.save(any(Opportunity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OpportunityService.OpportunityRequest request = new OpportunityService.OpportunityRequest();
        request.setTitle("New opportunity");
        request.setMargin(BigDecimal.ONE);
        request.setProbability(50);
        request.setExpectedCloseDate(LocalDate.now());

        Opportunity saved = service.createOpportunity(request);

        assertThat(saved.getInternalCompany()).isSameAs(company);
    }

    private List<String> codes(List<OptionValue> statuses) {
        return statuses.stream().map(OptionValue::getCode).toList();
    }

    private OptionValue status(String code) {
        OptionValue status = mock(OptionValue.class);
        when(status.getCode()).thenReturn(code);
        return status;
    }

    private Opportunity opportunity(String statusCode) {
        Opportunity opportunity = new Opportunity();
        opportunity.setTitle("Opportunity");
        opportunity.setStatusCode(statusCode);
        return opportunity;
    }
}
