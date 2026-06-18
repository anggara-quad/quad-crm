package com.quadteknologi.crm.service;

import com.quadteknologi.crm.domain.entity.Activity;
import com.quadteknologi.crm.domain.entity.Company;
import com.quadteknologi.crm.domain.entity.Lead;
import com.quadteknologi.crm.domain.entity.Opportunity;
import com.quadteknologi.crm.domain.entity.OptionValue;
import com.quadteknologi.crm.domain.entity.Person;
import com.quadteknologi.crm.domain.repository.ActivityRepository;
import com.quadteknologi.crm.domain.repository.CompanyRepository;
import com.quadteknologi.crm.domain.repository.LeadRepository;
import com.quadteknologi.crm.domain.repository.OpportunityRepository;
import com.quadteknologi.crm.domain.repository.OptionValueRepository;
import com.quadteknologi.crm.domain.repository.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final String LEAD_STATUS_GROUP = "LEAD_STATUS";
    private static final String OPPORTUNITY_STATUS_GROUP = "OPPORTUNITY_STATUS";

    private final LeadRepository leadRepository;
    private final OpportunityRepository opportunityRepository;
    private final CompanyRepository companyRepository;
    private final PersonRepository personRepository;
    private final ActivityRepository activityRepository;
    private final OptionValueRepository optionValueRepository;
    private final DataAccessService dataAccessService;

    public DashboardService(
            LeadRepository leadRepository,
            OpportunityRepository opportunityRepository,
            CompanyRepository companyRepository,
            PersonRepository personRepository,
            ActivityRepository activityRepository,
            OptionValueRepository optionValueRepository,
            DataAccessService dataAccessService) {
        this.leadRepository = leadRepository;
        this.opportunityRepository = opportunityRepository;
        this.companyRepository = companyRepository;
        this.personRepository = personRepository;
        this.activityRepository = activityRepository;
        this.optionValueRepository = optionValueRepository;
        this.dataAccessService = dataAccessService;
    }

    public DashboardData getDashboardData() {
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        List<OptionValue> leadStatuses = optionValueRepository.findByGroupCodeAndActiveTrueOrderBySortOrderAsc(
                LEAD_STATUS_GROUP);
        List<OptionValue> opportunityStatuses = optionValueRepository.findByGroupCodeAndActiveTrueOrderBySortOrderAsc(
                OPPORTUNITY_STATUS_GROUP);

        Long ownerId = dataAccessService.isSalesScope() ? dataAccessService.requireCurrentUserId() : null;

        long totalLeads = ownerId == null
                ? leadRepository.count()
                : leadRepository.findByStatusGroupCodeAndCreatedByIdOrderByCreatedAtDesc(
                        LEAD_STATUS_GROUP, ownerId).size();
        long totalOpportunities = ownerId == null
                ? opportunityRepository.count()
                : opportunityRepository.findByStatusGroupCodeAndCreatedByIdOrderByCreatedAtDesc(
                        OPPORTUNITY_STATUS_GROUP, ownerId).size();
        long totalCompanies = ownerId == null ? companyRepository.count() : companyRepository.countByCreatedById(ownerId);
        long totalPersons = ownerId == null ? personRepository.count() : personRepository.countByCreatedById(ownerId);

        long newLeadsThisMonth = ownerId == null
                ? leadRepository.countByCreatedAtGreaterThanEqual(monthStart)
                : leadRepository.countByCreatedAtGreaterThanEqualAndCreatedById(monthStart, ownerId);
        long newOpportunitiesThisMonth = ownerId == null
                ? opportunityRepository.countByCreatedAtGreaterThanEqual(monthStart)
                : opportunityRepository.countByCreatedAtGreaterThanEqualAndCreatedById(monthStart, ownerId);
        long validLeads = ownerId == null
                ? leadRepository.countByStatusGroupCodeAndStatusCode(LEAD_STATUS_GROUP, "VALID")
                : leadRepository.countByStatusGroupCodeAndStatusCodeAndCreatedById(LEAD_STATUS_GROUP, "VALID", ownerId);
        long wonOpportunities = opportunityStatuses.stream()
                .filter(status -> Boolean.TRUE.equals(status.getWon()))
                .mapToLong(status -> ownerId == null
                        ? opportunityRepository.countByStatusGroupCodeAndStatusCode(
                                OPPORTUNITY_STATUS_GROUP, status.getCode())
                        : opportunityRepository.countByStatusGroupCodeAndStatusCodeAndCreatedById(
                                OPPORTUNITY_STATUS_GROUP, status.getCode(), ownerId))
                .sum();

        BigDecimal openPipelineValue = ownerId == null
                ? opportunityRepository.sumOpenEstimatedAmount(OPPORTUNITY_STATUS_GROUP)
                : opportunityRepository.sumOpenEstimatedAmountByCreatedById(OPPORTUNITY_STATUS_GROUP, ownerId);

        List<Metric> metrics = List.of(
                new Metric("Leads", totalLeads, "+" + newLeadsThisMonth + " this month", "lead"),
                new Metric("Opportunities", totalOpportunities, "+" + newOpportunitiesThisMonth + " this month",
                        "opportunity"),
                new Metric("Open Pipeline", openPipelineValue, "Estimated active value", "pipeline"),
                new Metric("Contacts", totalCompanies + totalPersons,
                        totalCompanies + " organizations / " + totalPersons + " persons", "contact"));

        List<StatusSlice> leadStatus = mapLeadStatuses(leadStatuses, ownerId);
        List<StatusSlice> opportunityStatus = mapOpportunityStatuses(opportunityStatuses, ownerId);
        List<ActivityItem> recentActivities = (ownerId == null
                ? activityRepository.findTop8ByOrderByActivityDateDesc()
                : activityRepository.findTop8ByCreatedByIdOrderByActivityDateDesc(ownerId)).stream()
                .map(this::mapActivity)
                .toList();
        List<OpportunityItem> closingSoon = (ownerId == null
                ? opportunityRepository.findByStatusGroupCodeOrderByCreatedAtDesc(OPPORTUNITY_STATUS_GROUP)
                : opportunityRepository.findByStatusGroupCodeAndCreatedByIdOrderByCreatedAtDesc(
                        OPPORTUNITY_STATUS_GROUP, ownerId))
                .stream()
                .filter(this::isOpenOpportunity)
                .sorted(Comparator
                        .comparing(Opportunity::getExpectedCloseDate,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Opportunity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(6)
                .map(this::mapOpportunity)
                .toList();

        long conversionBase = totalLeads == 0 ? 1 : totalLeads;
        int validRate = Math.toIntExact(Math.round(validLeads * 100.0 / conversionBase));
        int opportunityRate = Math.toIntExact(Math.round(totalOpportunities * 100.0 / conversionBase));
        int wonRate = totalOpportunities == 0 ? 0
                : Math.toIntExact(Math.round(wonOpportunities * 100.0 / totalOpportunities));

        return new DashboardData(metrics, leadStatus, opportunityStatus, recentActivities, closingSoon,
                new Funnel(validLeads, totalOpportunities, wonOpportunities, validRate, opportunityRate, wonRate));
    }

    private List<StatusSlice> mapLeadStatuses(List<OptionValue> statuses, Long ownerId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        (ownerId == null
                ? leadRepository.countByStatusGroupCode(LEAD_STATUS_GROUP)
                : leadRepository.countByStatusGroupCodeAndCreatedById(LEAD_STATUS_GROUP, ownerId))
                .forEach(row -> counts.put(String.valueOf(row[0]), (Long) row[1]));

        return statuses.stream()
                .map(status -> new StatusSlice(status.getCode(), status.getName(),
                        counts.getOrDefault(status.getCode(), 0L), status.getColor()))
                .toList();
    }

    private List<StatusSlice> mapOpportunityStatuses(List<OptionValue> statuses, Long ownerId) {
        Map<String, OpportunitySummary> summaries = new LinkedHashMap<>();
        (ownerId == null
                ? opportunityRepository.summarizeByStatusGroupCode(OPPORTUNITY_STATUS_GROUP)
                : opportunityRepository.summarizeByStatusGroupCodeAndCreatedById(OPPORTUNITY_STATUS_GROUP, ownerId))
                .forEach(row -> summaries.put(String.valueOf(row[0]),
                        new OpportunitySummary((Long) row[1], (BigDecimal) row[2])));

        return statuses.stream()
                .map(status -> {
                    OpportunitySummary summary = summaries.getOrDefault(status.getCode(),
                            new OpportunitySummary(0, BigDecimal.ZERO));
                    return new StatusSlice(status.getCode(), status.getName(), summary.count(), status.getColor(),
                            summary.amount());
                })
                .toList();
    }

    private boolean isOpenOpportunity(Opportunity opportunity) {
        OptionValue status = opportunity.getStatus();
        return status == null
                || (!Boolean.TRUE.equals(status.getWon()) && !Boolean.TRUE.equals(status.getLost()));
    }

    private ActivityItem mapActivity(Activity activity) {
        String title = valueOrFallback(activity.getSubject(), "Activity");
        String type = activity.getType() == null ? activity.getTypeCode() : activity.getType().getName();
        String target = firstNonBlank(
                activity.getOpportunity() == null ? null : activity.getOpportunity().getTitle(),
                activity.getLead() == null ? null : activity.getLead().getTitle(),
                activity.getCompany() == null ? null : activity.getCompany().getName(),
                activity.getPerson() == null ? null : activity.getPerson().getFullName(),
                "General");

        return new ActivityItem(title, type, target, activity.getActivityDate());
    }

    private OpportunityItem mapOpportunity(Opportunity opportunity) {
        String account = firstNonBlank(
                opportunity.getCompany() == null ? null : opportunity.getCompany().getName(),
                opportunity.getPerson() == null ? null : opportunity.getPerson().getFullName(),
                "No account");
        String stage = opportunity.getStatus() == null ? opportunity.getStatusCode() : opportunity.getStatus().getName();
        String color = opportunity.getStatus() == null ? null : opportunity.getStatus().getColor();
        return new OpportunityItem(opportunity.getTitle(), account, stage, color,
                opportunity.getEstimatedAmount(), opportunity.getExpectedCloseDate());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String valueOrFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record OpportunitySummary(long count, BigDecimal amount) {
    }

    public record DashboardData(
            List<Metric> metrics,
            List<StatusSlice> leadStatus,
            List<StatusSlice> opportunityStatus,
            List<ActivityItem> recentActivities,
            List<OpportunityItem> closingSoon,
            Funnel funnel) {
    }

    public record Metric(String label, long value, BigDecimal amount, String caption, String tone) {

        public Metric(String label, long value, String caption, String tone) {
            this(label, value, null, caption, tone);
        }

        public Metric(String label, BigDecimal amount, String caption, String tone) {
            this(label, 0, amount, caption, tone);
        }
    }

    public record StatusSlice(String code, String label, long count, String color, BigDecimal amount) {

        public StatusSlice(String code, String label, long count, String color) {
            this(code, label, count, color, BigDecimal.ZERO);
        }
    }

    public record ActivityItem(String title, String type, String target, LocalDateTime activityDate) {
    }

    public record OpportunityItem(
            String title,
            String account,
            String stage,
            String stageColor,
            BigDecimal amount,
            LocalDate expectedCloseDate) {
    }

    public record Funnel(
            long validLeads,
            long opportunities,
            long wonOpportunities,
            int validRate,
            int opportunityRate,
            int wonRate) {
    }
}
