package com.quadteknologi.crm.service;

import com.quadteknologi.crm.domain.entity.Lead;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadServiceTest {

    @Mock
    private OptionValueRepository optionValueRepository;
    @Mock
    private LeadRepository leadRepository;
    @Mock
    private LeadItemRepository leadItemRepository;
    @Mock
    private OpportunityRepository opportunityRepository;
    @Mock
    private OpportunityItemRepository opportunityItemRepository;
    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private PersonRepository personRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DataAccessService dataAccessService;

    private LeadService service;

    @BeforeEach
    void setUp() {
        service = new LeadService(
                optionValueRepository,
                leadRepository,
                leadItemRepository,
                opportunityRepository,
                opportunityItemRepository,
                activityRepository,
                companyRepository,
                personRepository,
                userRepository,
                dataAccessService);
    }

    @Test
    void pipelineLeadsExcludeConvertedLeads() {
        Lead open = lead("VALID", null);
        Lead converted = lead("VALID", LocalDateTime.now());
        Lead otherStatus = lead("CONTACTED", null);
        when(dataAccessService.isSalesScope()).thenReturn(false);
        when(leadRepository.findByStatusGroupCodeOrderByCreatedAtDesc("LEAD_STATUS"))
                .thenReturn(List.of(open, converted, otherStatus));

        Map<String, List<Lead>> byStatus = service.findPipelineLeadsByStatus();

        assertThat(byStatus.get("VALID")).containsExactly(open);
        assertThat(byStatus.get("CONTACTED")).containsExactly(otherStatus);
    }

    private Lead lead(String statusCode, LocalDateTime convertedAt) {
        Lead lead = new Lead();
        lead.setStatusCode(statusCode);
        lead.setConvertedAt(convertedAt);
        return lead;
    }
}
