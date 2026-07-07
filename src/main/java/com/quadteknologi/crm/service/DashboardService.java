package com.quadteknologi.crm.service;

import com.quadteknologi.crm.domain.entity.Activity;
import com.quadteknologi.crm.domain.entity.Lead;
import com.quadteknologi.crm.domain.entity.Opportunity;
import com.quadteknologi.crm.domain.entity.OptionValue;
import com.quadteknologi.crm.domain.entity.User;
import com.quadteknologi.crm.domain.repository.ActivityRepository;
import com.quadteknologi.crm.domain.repository.LeadRepository;
import com.quadteknologi.crm.domain.repository.OpportunityRepository;
import com.quadteknologi.crm.domain.repository.OptionValueRepository;
import com.quadteknologi.crm.domain.repository.UserRepository;
import com.quadteknologi.crm.security.CurrentUserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.quadteknologi.crm.util.TextUtils.firstNonBlank;
import static com.quadteknologi.crm.util.TextUtils.valueOrFallback;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final String LEAD_STATUS_GROUP = "LEAD_STATUS";
    private static final String OPPORTUNITY_STATUS_GROUP = "OPPORTUNITY_STATUS";
    private static final String VALID_LEAD_STATUS = "VALID";
    private static final int CLOSING_SOON_DAYS = 30;

    private final LeadRepository leadRepository;
    private final OpportunityRepository opportunityRepository;
    private final ActivityRepository activityRepository;
    private final OptionValueRepository optionValueRepository;
    private final UserRepository userRepository;
    private final DataAccessService dataAccessService;
    private final CurrentUserService currentUserService;
    private final ContactService contactService;

    public DashboardService(
            LeadRepository leadRepository,
            OpportunityRepository opportunityRepository,
            ActivityRepository activityRepository,
            OptionValueRepository optionValueRepository,
            UserRepository userRepository,
            DataAccessService dataAccessService,
            CurrentUserService currentUserService,
            ContactService contactService) {
        this.leadRepository = leadRepository;
        this.opportunityRepository = opportunityRepository;
        this.activityRepository = activityRepository;
        this.optionValueRepository = optionValueRepository;
        this.userRepository = userRepository;
        this.dataAccessService = dataAccessService;
        this.currentUserService = currentUserService;
        this.contactService = contactService;
    }

    public DashboardData getDashboardData(DashboardFilter requestedFilter) {
        return getDashboardData(requestedFilter, null, false, false);
    }

    public DashboardData getDashboardData(
            DashboardFilter requestedFilter,
            Long detailSalesId,
            boolean includeLeadDetails,
            boolean includeOpportunityDetails) {
        DashboardFilter filter = resolveFilter(requestedFilter);
        boolean teamDashboard = canUseTeamDashboard();
        LocalDateTime from = filter.startDate().atStartOfDay();
        LocalDateTime to = filter.endDate().plusDays(1).atStartOfDay();

        List<OptionValue> leadStatuses = optionValueRepository.findByGroupCodeAndActiveTrueOrderBySortOrderAsc(
                LEAD_STATUS_GROUP);
        List<OptionValue> opportunityStatuses = optionValueRepository.findByGroupCodeAndActiveTrueOrderBySortOrderAsc(
                OPPORTUNITY_STATUS_GROUP);

        Long salesId = filter.salesId();
        List<Lead> baseLeads = leadRepository.findDashboardLeads(LEAD_STATUS_GROUP, from, to, salesId);
        List<Opportunity> baseOpportunities = opportunityRepository.findDashboardOpportunities(
                OPPORTUNITY_STATUS_GROUP, from, to, salesId);
        List<Lead> leads = applyLeadFilters(baseLeads, baseOpportunities, filter);
        List<Opportunity> opportunities = applyOpportunityFilters(baseOpportunities, filter);
        List<Opportunity> closingSoon = opportunityRepository.findDashboardClosingSoon(
                OPPORTUNITY_STATUS_GROUP, LocalDate.now(), LocalDate.now().plusDays(CLOSING_SOON_DAYS), salesId);
        closingSoon = applyOpportunityFilters(closingSoon, filter);
        List<Activity> activities = activityRepository.findDashboardActivities(from, to, salesId).stream()
                .filter(activity -> matchesActivityFilters(activity, filter))
                .toList();
        List<User> salesUsers = teamDashboard ? userRepository.findActiveSalesUsersOrderByFullNameAsc() : List.of();

        List<SalesPerformanceDto> salesPerformance = teamDashboard
                ? buildSalesPerformance(
                        salesUsers,
                        leads,
                        opportunities,
                        closingSoon,
                        activities,
                        filter,
                        detailSalesId,
                        includeLeadDetails,
                        includeOpportunityDetails)
                : List.of();

        long contacts = contactService.countPersons(from, to, salesId);
        long organizations = contactService.countCompanies(from, to, salesId);
        DashboardSummaryDto summary = buildSummary(
                leads, opportunities, closingSoon, salesPerformance, filter, contacts, organizations);
        List<StatusSlice> leadQualification = mapLeadStatuses(leadStatuses, leads);
        List<StatusSlice> opportunityPipeline = mapOpportunityStatuses(opportunityStatuses, opportunities);
        List<LeadSourcePerformanceDto> leadSources = mapLeadSources(
                leadRepository.summarizeLeadSources(
                        LEAD_STATUS_GROUP,
                        VALID_LEAD_STATUS,
                        from,
                        to,
                        salesId,
                        filter.leadStatusCode(),
                        filter.opportunityStatusCode(),
                        filter.leadSource()));
        List<ClosingSoonDto> closingSoonItems = closingSoon.stream()
                .limit(teamDashboard ? 8 : 6)
                .map(this::mapClosingSoon)
                .toList();
        List<RecentActivityDto> recentActivities = activities.stream()
                .limit(8)
                .map(this::mapActivity)
                .toList();
        return new DashboardData(
                filter,
                canUseTeamDashboard(),
                salesUsers,
                summary,
                leadQualification,
                opportunityPipeline,
                leadSources,
                salesPerformance,
                closingSoonItems,
                recentActivities);
    }

    public DashboardFilter defaultFilter() {
        if (dataAccessService.isSalesScope()) {
            return DashboardFilter.currentMonth(DashboardScope.MY_DATA, dataAccessService.requireCurrentUserId());
        }
        return DashboardFilter.currentMonth(DashboardScope.TEAM_DATA, null);
    }

    public boolean canUseTeamDashboard() {
        return currentUserService.hasRole("Manager") || currentUserService.hasRole("Administrator");
    }

    private DashboardFilter resolveFilter(DashboardFilter requestedFilter) {
        DashboardFilter fallback = defaultFilter();
        DashboardFilter requested = requestedFilter == null ? fallback : requestedFilter;
        LocalDate startDate = Optional.ofNullable(requested.startDate()).orElse(fallback.startDate());
        LocalDate endDate = Optional.ofNullable(requested.endDate()).orElse(fallback.endDate());
        if (endDate.isBefore(startDate)) {
            endDate = startDate;
        }

        if (dataAccessService.isSalesScope()) {
            return new DashboardFilter(
                    DashboardScope.MY_DATA,
                    dataAccessService.requireCurrentUserId(),
                    startDate,
                    endDate,
                    requested.leadStatusCode(),
                    requested.opportunityStatusCode(),
                    requested.leadSource());
        }

        DashboardScope scope = requested.scope();
        Long salesId = requested.salesId();
        if (scope == DashboardScope.SELECTED_SALES && salesId == null) {
            scope = DashboardScope.TEAM_DATA;
        }
        if (scope == null) {
            scope = salesId == null ? DashboardScope.TEAM_DATA : DashboardScope.SELECTED_SALES;
        }
        if (scope == DashboardScope.MY_DATA) {
            salesId = dataAccessService.getCurrentUserId()
                    .orElseThrow(() -> new AccessDeniedException("Signed in user was not found."));
        }
        if (scope == DashboardScope.TEAM_DATA) {
            salesId = null;
        }
        if (salesId != null) {
            Long selectedSalesId = salesId;
            if (userRepository.findActiveSalesUsersOrderByFullNameAsc().stream()
                    .noneMatch(user -> Objects.equals(user.getId(), selectedSalesId))) {
                throw new AccessDeniedException("Selected sales user was not found.");
            }
        }

        return new DashboardFilter(
                scope,
                salesId,
                startDate,
                endDate,
                requested.leadStatusCode(),
                requested.opportunityStatusCode(),
                requested.leadSource());
    }

    private List<Lead> applyLeadFilters(
            List<Lead> leads,
            List<Opportunity> opportunities,
            DashboardFilter filter) {
        Set<Long> matchingOpportunityLeadIds = filter.opportunityStatusCode() == null
                ? Set.of()
                : opportunities.stream()
                        .filter(opportunity -> opportunity.getLead() != null)
                        .filter(opportunity -> matchesOpportunityStatus(opportunity, filter.opportunityStatusCode()))
                        .map(opportunity -> opportunity.getLead().getId())
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        return leads.stream()
                .filter(lead -> matchesLeadStatus(lead, filter.leadStatusCode()))
                .filter(lead -> matchesLeadSource(lead, filter.leadSource()))
                .filter(lead -> filter.opportunityStatusCode() == null
                        || matchingOpportunityLeadIds.contains(lead.getId()))
                .toList();
    }

    private List<Opportunity> applyOpportunityFilters(List<Opportunity> opportunities, DashboardFilter filter) {
        return opportunities.stream()
                .filter(opportunity -> matchesOpportunityStatus(opportunity, filter.opportunityStatusCode()))
                .filter(opportunity -> matchesRelatedLeadStatus(opportunity, filter.leadStatusCode()))
                .filter(opportunity -> matchesRelatedLeadSource(opportunity, filter.leadSource()))
                .toList();
    }

    private boolean matchesActivityFilters(Activity activity, DashboardFilter filter) {
        if (filter.opportunityStatusCode() != null) {
            if (activity.getOpportunity() == null
                    || !matchesOpportunityStatus(activity.getOpportunity(), filter.opportunityStatusCode())) {
                return false;
            }
        }
        if (filter.leadStatusCode() != null) {
            Lead relatedLead = relatedLead(activity);
            if (relatedLead == null || !matchesLeadStatus(relatedLead, filter.leadStatusCode())) {
                return false;
            }
        }
        if (filter.leadSource() != null) {
            Lead relatedLead = relatedLead(activity);
            return relatedLead != null && matchesLeadSource(relatedLead, filter.leadSource());
        }
        return true;
    }

    private Lead relatedLead(Activity activity) {
        if (activity.getOpportunity() != null && activity.getOpportunity().getLead() != null) {
            return activity.getOpportunity().getLead();
        }
        return activity.getLead();
    }

    private boolean matchesRelatedLeadStatus(Opportunity opportunity, String leadStatusCode) {
        return leadStatusCode == null
                || (opportunity.getLead() != null && matchesLeadStatus(opportunity.getLead(), leadStatusCode));
    }

    private boolean matchesRelatedLeadSource(Opportunity opportunity, String leadSource) {
        return leadSource == null
                || (opportunity.getLead() != null && matchesLeadSource(opportunity.getLead(), leadSource));
    }

    private boolean matchesLeadStatus(Lead lead, String leadStatusCode) {
        return leadStatusCode == null || Objects.equals(lead.getStatusCode(), leadStatusCode);
    }

    private boolean matchesOpportunityStatus(Opportunity opportunity, String opportunityStatusCode) {
        return opportunityStatusCode == null || Objects.equals(opportunity.getStatusCode(), opportunityStatusCode);
    }

    private boolean matchesLeadSource(Lead lead, String leadSource) {
        return leadSource == null || Objects.equals(normalizedSource(lead.getSource()), leadSource);
    }

    private DashboardSummaryDto buildSummary(
            List<Lead> leads,
            List<Opportunity> opportunities,
            List<Opportunity> closingSoon,
            List<SalesPerformanceDto> salesPerformance,
            DashboardFilter filter,
            long contacts,
            long organizations) {
        long validLeads = leads.stream()
                .filter(lead -> VALID_LEAD_STATUS.equals(lead.getStatusCode()))
                .count();
        long openOpportunities = opportunities.stream().filter(this::isOpenOpportunity).count();
        BigDecimal openPipeline = opportunities.stream()
                .filter(this::isOpenOpportunity)
                .map(Opportunity::getEstimatedAmount)
                .map(this::safeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal openMargin = opportunities.stream()
                .filter(this::isOpenOpportunity)
                .map(Opportunity::getMargin)
                .map(this::safeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal wonRevenue = opportunities.stream()
                .filter(this::isWonOpportunity)
                .map(Opportunity::getEstimatedAmount)
                .map(this::safeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal wonMargin = opportunities.stream()
                .filter(this::isWonOpportunity)
                .map(Opportunity::getMargin)
                .map(this::safeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal forecastedMargin = opportunities.stream()
                .filter(opportunity -> !isWonOpportunity(opportunity))
                .map(Opportunity::getMargin)
                .map(this::safeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long won = opportunities.stream().filter(this::isWonOpportunity).count();
        long lost = opportunities.stream().filter(this::isLostOpportunity).count();
        long activeSales = filter.scope() == DashboardScope.SELECTED_SALES
                ? (filter.salesId() == null ? 0 : 1)
                : salesPerformance.size();

        return new DashboardSummaryDto(
                leads.size(),
                validLeads,
                openOpportunities,
                openPipeline,
                openMargin,
                wonRevenue,
                wonMargin,
                forecastedMargin,
                contacts,
                organizations,
                closingSoon.size(),
                closedRate(won, lost),
                opportunities.isEmpty() ? 0 : Math.toIntExact(Math.round(won * 100.0 / opportunities.size())),
                activeSales);
    }

    private List<StatusSlice> mapLeadStatuses(List<OptionValue> statuses, List<Lead> leads) {
        Map<String, Long> counts = leads.stream()
                .collect(Collectors.groupingBy(Lead::getStatusCode, LinkedHashMap::new, Collectors.counting()));
        return statuses.stream()
                .map(status -> new StatusSlice(
                        status.getCode(),
                        status.getName(),
                        counts.getOrDefault(status.getCode(), 0L),
                        status.getColor()))
                .toList();
    }

    private List<StatusSlice> mapOpportunityStatuses(List<OptionValue> statuses, List<Opportunity> opportunities) {
        Map<String, OpportunityStatusSummary> summaries = new LinkedHashMap<>();
        opportunities.forEach(opportunity -> {
            OpportunityStatusSummary current = summaries.getOrDefault(
                    opportunity.getStatusCode(), new OpportunityStatusSummary(0, BigDecimal.ZERO));
            summaries.put(opportunity.getStatusCode(), new OpportunityStatusSummary(
                    current.count() + 1,
                    current.amount().add(safeAmount(opportunity.getEstimatedAmount()))));
        });

        return statuses.stream()
                .map(status -> {
                    OpportunityStatusSummary summary = summaries.getOrDefault(
                            status.getCode(), new OpportunityStatusSummary(0, BigDecimal.ZERO));
                    return new StatusSlice(status.getCode(), status.getName(), summary.count(),
                            status.getColor(), summary.amount());
                })
                .toList();
    }

    private List<SalesPerformanceDto> buildSalesPerformance(
            List<User> salesUsers,
            List<Lead> leads,
            List<Opportunity> opportunities,
            List<Opportunity> closingSoon,
            List<Activity> activities,
            DashboardFilter filter,
            Long detailSalesId,
            boolean includeLeadDetails,
            boolean includeOpportunityDetails) {
        Stream<User> users = salesUsers.stream();
        if (filter.scope() == DashboardScope.SELECTED_SALES && filter.salesId() != null) {
            users = users.filter(user -> Objects.equals(user.getId(), filter.salesId()));
        }

        Map<Long, List<Lead>> leadsBySales = leads.stream()
                .filter(lead -> lead.getAssignedTo() != null)
                .collect(Collectors.groupingBy(lead -> lead.getAssignedTo().getId()));
        Map<Long, List<Opportunity>> opportunitiesBySales = opportunities.stream()
                .filter(opportunity -> opportunity.getAssignedTo() != null)
                .collect(Collectors.groupingBy(opportunity -> opportunity.getAssignedTo().getId()));
        Map<Long, Long> closingSoonBySales = closingSoon.stream()
                .filter(opportunity -> opportunity.getAssignedTo() != null)
                .collect(Collectors.groupingBy(opportunity -> opportunity.getAssignedTo().getId(), Collectors.counting()));
        Map<Long, LocalDateTime> lastActivityBySales = activities.stream()
                .map(this::activityOwnerAndDate)
                .filter(item -> item.salesId() != null && item.activityDate() != null)
                .collect(Collectors.toMap(
                        ActivityOwnerDate::salesId,
                        ActivityOwnerDate::activityDate,
                        (left, right) -> left.isAfter(right) ? left : right,
                        LinkedHashMap::new));

        return users.map(user -> mapSalesPerformance(
                        user,
                        leadsBySales.getOrDefault(user.getId(), List.of()),
                        opportunitiesBySales.getOrDefault(user.getId(), List.of()),
                        closingSoonBySales.getOrDefault(user.getId(), 0L),
                        lastActivityBySales.get(user.getId()),
                        Objects.equals(user.getId(), detailSalesId) && includeLeadDetails,
                        Objects.equals(user.getId(), detailSalesId) && includeOpportunityDetails))
                .sorted(Comparator
                        .comparing(SalesPerformanceDto::wonRevenue, Comparator.reverseOrder())
                        .thenComparing(SalesPerformanceDto::openPipeline, Comparator.reverseOrder())
                        .thenComparing(SalesPerformanceDto::salesName))
                .toList();
    }

    private SalesPerformanceDto mapSalesPerformance(
            User user,
            List<Lead> leads,
            List<Opportunity> opportunities,
            long closingSoon,
            LocalDateTime lastActivity,
            boolean includeLeadDetails,
            boolean includeOpportunityDetails) {
        long validLeads = leads.stream()
                .filter(lead -> VALID_LEAD_STATUS.equals(lead.getStatusCode()))
                .count();
        long openOpportunities = opportunities.stream().filter(this::isOpenOpportunity).count();
        BigDecimal openPipeline = opportunities.stream()
                .filter(this::isOpenOpportunity)
                .map(Opportunity::getEstimatedAmount)
                .map(this::safeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal openMargin = opportunities.stream()
                .filter(this::isOpenOpportunity)
                .map(Opportunity::getMargin)
                .map(this::safeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal wonRevenue = opportunities.stream()
                .filter(this::isWonOpportunity)
                .map(Opportunity::getEstimatedAmount)
                .map(this::safeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal wonMargin = opportunities.stream()
                .filter(this::isWonOpportunity)
                .map(Opportunity::getMargin)
                .map(this::safeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal forecastedMargin = opportunities.stream()
                .filter(opportunity -> !isWonOpportunity(opportunity))
                .map(Opportunity::getMargin)
                .map(this::safeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long won = opportunities.stream().filter(this::isWonOpportunity).count();
        long lost = opportunities.stream().filter(this::isLostOpportunity).count();

        return new SalesPerformanceDto(
                user.getId(),
                user.getFullName(),
                leads.size(),
                validLeads,
                openOpportunities,
                openPipeline,
                openMargin,
                wonRevenue,
                wonMargin,
                forecastedMargin,
                closedRate(won, lost),
                closingSoon,
                lastActivity,
                health(openOpportunities, closingSoon, lastActivity),
                includeLeadDetails ? mapSalesLeadDetails(leads) : List.of(),
                includeOpportunityDetails ? mapSalesOpportunityDetails(opportunities) : List.of());
    }

    private List<SalesLeadDetailDto> mapSalesLeadDetails(List<Lead> leads) {
        return leads.stream()
                .sorted(Comparator
                        .comparing(Lead::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Lead::getTitle, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(lead -> new SalesLeadDetailDto(
                        lead.getPublicId(),
                        valueOrFallback(lead.getTitle(), "Untitled lead"),
                        leadAccount(lead),
                        statusName(lead.getStatus(), lead.getStatusCode()),
                        normalizedSource(lead.getSource()),
                        lead.getCreatedAt(),
                        VALID_LEAD_STATUS.equals(lead.getStatusCode())))
                .toList();
    }

    private List<SalesOpportunityDetailDto> mapSalesOpportunityDetails(List<Opportunity> opportunities) {
        return opportunities.stream()
                .filter(this::isOpenOpportunity)
                .sorted(Comparator
                        .comparing(Opportunity::getExpectedCloseDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Opportunity::getTitle, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(opportunity -> new SalesOpportunityDetailDto(
                        opportunity.getPublicId(),
                        valueOrFallback(opportunity.getTitle(), "Untitled opportunity"),
                        opportunityAccount(opportunity),
                        statusName(opportunity.getStatus(), opportunity.getStatusCode()),
                        opportunity.getLead() == null ? null : opportunity.getLead().getTitle(),
                        safeAmount(opportunity.getEstimatedAmount()),
                        safeAmount(opportunity.getMargin()),
                        opportunity.getProbability(),
                        opportunity.getExpectedCloseDate()))
                .toList();
    }

    private List<LeadSourcePerformanceDto> mapLeadSources(List<Object[]> rows) {
        return rows.stream()
                .map(row -> {
                    long totalLeads = asLong(row[1]);
                    long validLeads = asLong(row[2]);
                    long convertedOpportunities = asLong(row[4]);
                    return new LeadSourcePerformanceDto(
                            valueOrFallback((String) row[0], "Unknown"),
                            totalLeads,
                            validLeads,
                            rate(validLeads, totalLeads),
                            asBigDecimal(row[3]),
                            convertedOpportunities,
                            rate(convertedOpportunities, totalLeads));
                })
                .toList();
    }

    private ActivityOwnerDate activityOwnerAndDate(Activity activity) {
        Long ownerId = Optional.ofNullable(activity.getOpportunity())
                .map(Opportunity::getAssignedTo)
                .map(User::getId)
                .or(() -> Optional.ofNullable(activity.getLead())
                        .map(Lead::getAssignedTo)
                        .map(User::getId))
                .or(() -> Optional.ofNullable(activity.getCreatedBy()).map(User::getId))
                .orElse(null);
        return new ActivityOwnerDate(ownerId, activity.getActivityDate());
    }

    private RecentActivityDto mapActivity(Activity activity) {
        String title = valueOrFallback(activity.getSubject(), "Activity");
        String typeCode = valueOrFallback(activity.getTypeCode(), "NOTE");
        String typeName = activity.getType() == null ? displayCode(typeCode) : activity.getType().getName();
        String target = firstNonBlank(
                activity.getOpportunity() == null ? null : activity.getOpportunity().getTitle(),
                activity.getLead() == null ? null : activity.getLead().getTitle(),
                activity.getCompany() == null ? null : activity.getCompany().getName(),
                activity.getPerson() == null ? null : activity.getPerson().getFullName(),
                "General");
        String salesName = Optional.ofNullable(activity.getOpportunity())
                .map(Opportunity::getAssignedTo)
                .map(User::getFullName)
                .or(() -> Optional.ofNullable(activity.getLead())
                        .map(Lead::getAssignedTo)
                        .map(User::getFullName))
                .or(() -> Optional.ofNullable(activity.getCreatedBy()).map(User::getFullName))
                .orElse("Unassigned");

        String targetType = activity.getOpportunity() != null
                ? "OPPORTUNITY"
                : activity.getLead() == null ? null : "LEAD";
        java.util.UUID targetPublicId = activity.getOpportunity() != null
                ? activity.getOpportunity().getPublicId()
                : activity.getLead() == null ? null : activity.getLead().getPublicId();

        return new RecentActivityDto(title, typeCode, typeName, target, targetType, targetPublicId,
                salesName, activity.getActivityDate());
    }

    private ClosingSoonDto mapClosingSoon(Opportunity opportunity) {
        String account = firstNonBlank(
                opportunity.getCompany() == null ? null : opportunity.getCompany().getName(),
                opportunity.getPerson() == null ? null : opportunity.getPerson().getFullName(),
                "No account");
        String stage = opportunity.getStatus() == null
                ? displayCode(opportunity.getStatusCode())
                : opportunity.getStatus().getName();
        String color = opportunity.getStatus() == null ? null : opportunity.getStatus().getColor();
        String salesName = opportunity.getAssignedTo() == null ? "Unassigned" : opportunity.getAssignedTo().getFullName();
        return new ClosingSoonDto(opportunity.getPublicId(), opportunity.getTitle(), account, salesName, stage, color,
                safeAmount(opportunity.getEstimatedAmount()), opportunity.getExpectedCloseDate());
    }

    private boolean isOpenOpportunity(Opportunity opportunity) {
        return !isWonOpportunity(opportunity) && !isLostOpportunity(opportunity);
    }

    private boolean isWonOpportunity(Opportunity opportunity) {
        OptionValue status = opportunity.getStatus();
        return Boolean.TRUE.equals(status == null ? null : status.getWon()) || "WON".equals(opportunity.getStatusCode());
    }

    private boolean isLostOpportunity(Opportunity opportunity) {
        OptionValue status = opportunity.getStatus();
        return Boolean.TRUE.equals(status == null ? null : status.getLost()) || "LOST".equals(opportunity.getStatusCode());
    }

    private String health(long openOpportunities, long closingSoon, LocalDateTime lastActivity) {
        if (closingSoon > 0) {
            return "Closing Soon";
        }
        if (openOpportunities > 0
                && (lastActivity == null || lastActivity.isBefore(LocalDateTime.now().minusDays(7)))) {
            return "Need Follow Up";
        }
        if (lastActivity == null) {
            return "No Recent Activity";
        }
        return "Healthy";
    }

    private int closedRate(long won, long lost) {
        long closed = won + lost;
        return closed == 0 ? 0 : Math.toIntExact(Math.round(won * 100.0 / closed));
    }

    private int rate(long numerator, long denominator) {
        return denominator == 0 ? 0 : Math.toIntExact(Math.round(numerator * 100.0 / denominator));
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private long asLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private BigDecimal asBigDecimal(Object value) {
        if (value instanceof BigDecimal amount) {
            return amount;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return BigDecimal.ZERO;
    }

    private String leadAccount(Lead lead) {
        return firstNonBlank(
                lead.getCompany() == null ? null : lead.getCompany().getName(),
                lead.getPerson() == null ? null : lead.getPerson().getFullName(),
                lead.getRawCompanyName(),
                lead.getRawPersonName(),
                "No account");
    }

    private String opportunityAccount(Opportunity opportunity) {
        return firstNonBlank(
                opportunity.getCompany() == null ? null : opportunity.getCompany().getName(),
                opportunity.getPerson() == null ? null : opportunity.getPerson().getFullName(),
                "No account");
    }

    private String statusName(OptionValue status, String statusCode) {
        return status == null ? displayCode(statusCode) : status.getName();
    }

    private String normalizedSource(String source) {
        return valueOrFallback(source == null ? null : source.trim(), "Unknown");
    }

    private String displayCode(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Stream.of(value.toLowerCase(Locale.ROOT).split("_"))
                .filter(part -> !part.isBlank())
                .map(part -> part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1))
                .collect(Collectors.joining(" "));
    }

    private record OpportunityStatusSummary(long count, BigDecimal amount) {
    }

    private record ActivityOwnerDate(Long salesId, LocalDateTime activityDate) {
    }

    public record DashboardData(
            DashboardFilter filter,
            boolean managerMode,
            List<User> salesUsers,
            DashboardSummaryDto summary,
            List<StatusSlice> leadQualification,
            List<StatusSlice> opportunityPipeline,
            List<LeadSourcePerformanceDto> leadSources,
            List<SalesPerformanceDto> salesPerformance,
            List<ClosingSoonDto> closingSoon,
            List<RecentActivityDto> recentActivities) {
    }

    public record Metric(String label, long value, BigDecimal amount, String caption, String tone, boolean percent) {

        public Metric(String label, long value, String caption, String tone) {
            this(label, value, null, caption, tone, false);
        }

        public Metric(String label, BigDecimal amount, String caption, String tone) {
            this(label, 0, amount, caption, tone, false);
        }

        public Metric(String label, long value, String caption, String tone, boolean percent) {
            this(label, value, null, caption, tone, percent);
        }
    }

    public record StatusSlice(String code, String label, long count, String color, BigDecimal amount) {

        public StatusSlice(String code, String label, long count, String color) {
            this(code, label, count, color, BigDecimal.ZERO);
        }
    }

}
