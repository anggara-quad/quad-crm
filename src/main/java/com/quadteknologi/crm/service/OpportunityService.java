package com.quadteknologi.crm.service;

import com.quadteknologi.crm.domain.entity.Activity;
import com.quadteknologi.crm.domain.entity.Company;
import com.quadteknologi.crm.domain.entity.Lead;
import com.quadteknologi.crm.domain.entity.Opportunity;
import com.quadteknologi.crm.domain.entity.OpportunityItem;
import com.quadteknologi.crm.domain.entity.OptionValue;
import com.quadteknologi.crm.domain.entity.Person;
import com.quadteknologi.crm.domain.entity.User;
import com.quadteknologi.crm.domain.repository.ActivityRepository;
import com.quadteknologi.crm.domain.repository.CompanyRepository;
import com.quadteknologi.crm.domain.repository.LeadItemRepository;
import com.quadteknologi.crm.domain.repository.LeadRepository;
import com.quadteknologi.crm.domain.repository.OpportunityItemRepository;
import com.quadteknologi.crm.domain.repository.OpportunityRepository;
import com.quadteknologi.crm.domain.repository.OptionValueRepository;
import com.quadteknologi.crm.domain.repository.PersonRepository;
import com.quadteknologi.crm.domain.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.quadteknologi.crm.util.TextUtils.trimToNull;
import static com.quadteknologi.crm.util.TextUtils.truncate;

@Service
@Transactional(readOnly = true)
public class OpportunityService {

    private static final String OPPORTUNITY_STATUS_GROUP = "OPPORTUNITY_STATUS";
    private static final String LEAD_STATUS_GROUP = "LEAD_STATUS";
    private static final String PRODUCT_TYPE_GROUP = "PRODUCT_TYPE";
    private static final String ACTIVITY_TYPE_GROUP = "ACTIVITY_TYPE";
    private static final String WON_STATUS_CODE = "WON";
    private static final String CREATED_ACTIVITY_TYPE = "CREATED";
    private static final String STATUS_CHANGE_ACTIVITY_TYPE = "STATUS_CHANGE";

    private final OptionValueRepository optionValueRepository;
    private final OpportunityRepository opportunityRepository;
    private final OpportunityItemRepository opportunityItemRepository;
    private final ActivityRepository activityRepository;
    private final LeadRepository leadRepository;
    private final LeadItemRepository leadItemRepository;
    private final CompanyRepository companyRepository;
    private final PersonRepository personRepository;
    private final UserRepository userRepository;
    private final DataAccessService dataAccessService;

    public OpportunityService(
            OptionValueRepository optionValueRepository,
            OpportunityRepository opportunityRepository,
            OpportunityItemRepository opportunityItemRepository,
            ActivityRepository activityRepository,
            LeadRepository leadRepository,
            LeadItemRepository leadItemRepository,
            CompanyRepository companyRepository,
            PersonRepository personRepository,
            UserRepository userRepository,
            DataAccessService dataAccessService) {
        this.optionValueRepository = optionValueRepository;
        this.opportunityRepository = opportunityRepository;
        this.opportunityItemRepository = opportunityItemRepository;
        this.activityRepository = activityRepository;
        this.leadRepository = leadRepository;
        this.leadItemRepository = leadItemRepository;
        this.companyRepository = companyRepository;
        this.personRepository = personRepository;
        this.userRepository = userRepository;
        this.dataAccessService = dataAccessService;
    }

    public List<OptionValue> findPipelineStatuses() {
        return optionValueRepository
                .findByGroupCodeAndActiveTrueOrderBySortOrderAsc(OPPORTUNITY_STATUS_GROUP)
                .stream()
                .filter(this::isShownInPipeline)
                .toList();
    }

    public Map<String, List<Opportunity>> findPipelineOpportunitiesByStatus() {
        List<Opportunity> opportunities = dataAccessService.isSalesScope()
                ? opportunityRepository.findByStatusGroupCodeAndCreatedByIdOrderByCreatedAtDesc(
                        OPPORTUNITY_STATUS_GROUP, dataAccessService.requireCurrentUserId())
                : opportunityRepository.findByStatusGroupCodeOrderByCreatedAtDesc(OPPORTUNITY_STATUS_GROUP);
        return opportunities
                .stream()
                .collect(Collectors.groupingBy(Opportunity::getStatusCode));
    }

    public List<OptionValue> findProductTypes() {
        return optionValueRepository.findByGroupCodeAndActiveTrueOrderBySortOrderAsc(PRODUCT_TYPE_GROUP);
    }

    public OptionValue getDefaultPipelineStatus() {
        List<OptionValue> statuses = findPipelineStatuses();
        return statuses.stream()
                .filter(status -> "PRODUCT_SOLUTIONING".equals(status.getCode()))
                .findFirst()
                .orElseGet(() -> statuses.isEmpty() ? null : statuses.get(0));
    }

    public Opportunity findOpportunity(Long id) {
        Opportunity opportunity = opportunityRepository.findById(id).orElseThrow();
        dataAccessService.assertCanAccessCreatedBy("opportunity", opportunity.getCreatedBy());
        return opportunity;
    }

    public Opportunity findOpportunity(UUID publicId) {
        Opportunity opportunity = opportunityRepository.findByPublicId(publicId).orElseThrow();
        dataAccessService.assertCanAccessCreatedBy("opportunity", opportunity.getCreatedBy());
        return opportunity;
    }

    public List<Activity> findActivities(Long opportunityId) {
        Opportunity opportunity = findOpportunity(opportunityId);
        return activityRepository.findByOpportunityIdOrderByActivityDateDesc(opportunity.getId());
    }

    public List<OpportunityItem> findOpportunityItems(Long opportunityId) {
        Opportunity opportunity = findOpportunity(opportunityId);
        return opportunityItemRepository.findByOpportunityIdOrderBySortOrderAsc(opportunity.getId());
    }

    public List<Lead> findValidLeads() {
        if (dataAccessService.isSalesScope()) {
            return leadRepository.findUnconvertedByStatusGroupCodeAndStatusCodeAndCreatedByIdOrderByCreatedAtDesc(
                    LEAD_STATUS_GROUP, "VALID", dataAccessService.requireCurrentUserId());
        }
        return leadRepository.findUnconvertedByStatusGroupCodeAndStatusCodeOrderByCreatedAtDesc(LEAD_STATUS_GROUP, "VALID");
    }

    public List<Company> findCompanies() {
        if (dataAccessService.isSalesScope()) {
            return companyRepository.findByCreatedByIdOrderByNameAsc(dataAccessService.requireCurrentUserId());
        }
        return companyRepository.findAllByOrderByNameAsc();
    }

    public List<Person> findPersons() {
        if (dataAccessService.isSalesScope()) {
            return personRepository.findByCreatedByIdOrderByFullNameAsc(dataAccessService.requireCurrentUserId());
        }
        return personRepository.findAllByOrderByFullNameAsc();
    }

    public List<User> findAssignableUsers() {
        if (dataAccessService.isSalesScope()) {
            return dataAccessService.getCurrentUserReference().stream().toList();
        }
        return userRepository.findByActiveTrueOrderByFullNameAsc();
    }

    @Transactional
    public Opportunity createOpportunity(OpportunityRequest request) {
        Opportunity opportunity = new Opportunity();
        applyRequest(opportunity, request);
        opportunity.setCreatedBy(dataAccessService.requireCurrentUserReference());
        Opportunity savedOpportunity = opportunityRepository.save(opportunity);
        if (request.getItems().isEmpty() && savedOpportunity.getLead() != null) {
            copyLeadItemsToOpportunity(savedOpportunity.getLead(), savedOpportunity);
        } else {
            replaceOpportunityItems(savedOpportunity, request.getItems());
        }
        markLinkedLeadConverted(savedOpportunity);
        activityRepository.save(createCreatedActivity(savedOpportunity));
        return opportunityRepository.findById(savedOpportunity.getId()).orElse(savedOpportunity);
    }

    @Transactional
    public Opportunity updateOpportunity(Long id, OpportunityRequest request) {
        Opportunity opportunity = findOpportunity(id);
        String previousStatusCode = opportunity.getStatusCode();
        applyRequest(opportunity, request);
        Opportunity savedOpportunity = opportunityRepository.save(opportunity);
        replaceOpportunityItems(savedOpportunity, request.getItems());
        markLinkedLeadConverted(savedOpportunity);
        if (!Objects.equals(previousStatusCode, savedOpportunity.getStatusCode())) {
            activityRepository.save(createStatusChangedActivity(savedOpportunity, previousStatusCode,
                    savedOpportunity.getStatusCode()));
        }
        return opportunityRepository.findById(savedOpportunity.getId()).orElse(savedOpportunity);
    }

    @Transactional
    public Opportunity updateOpportunityStatus(Long id, OptionValue status) {
        return updateOpportunityStatus(id, status, null, null);
    }

    @Transactional
    public Opportunity updateOpportunityStatus(Long id, OptionValue status, String soNumber, String contractPoNumber) {
        Opportunity opportunity = findOpportunity(id);
        String previousStatusCode = opportunity.getStatusCode();
        String nextStatusCode = status == null ? "PRODUCT_SOLUTIONING" : status.getCode();

        if (Objects.equals(previousStatusCode, nextStatusCode)) {
            return opportunity;
        }

        opportunity.setStatusGroupCode(OPPORTUNITY_STATUS_GROUP);
        opportunity.setStatusCode(nextStatusCode);
        applyWonDocumentNumbers(opportunity, soNumber, contractPoNumber);
        validateWonDocumentNumbers(opportunity);
        applyWinLossDates(opportunity, status);
        Opportunity savedOpportunity = opportunityRepository.save(opportunity);
        activityRepository.save(createStatusChangedActivity(savedOpportunity, previousStatusCode, nextStatusCode));
        return opportunityRepository.findById(savedOpportunity.getId()).orElse(savedOpportunity);
    }

    private boolean isShownInPipeline(OptionValue status) {
        Object value = status.getMetadata() == null ? null : status.getMetadata().get("showInPipeline");
        return value == null || Boolean.TRUE.equals(value) || Objects.equals("true", String.valueOf(value));
    }

    private void replaceOpportunityItems(Opportunity opportunity, List<OpportunityItemRequest> requests) {
        opportunityItemRepository.deleteByOpportunityId(opportunity.getId());
        List<OpportunityItem> items = buildOpportunityItems(opportunity, requests);
        if (!items.isEmpty()) {
            opportunityItemRepository.saveAll(items);
        }
    }

    private List<OpportunityItem> buildOpportunityItems(Opportunity opportunity, List<OpportunityItemRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        int sortOrder = 0;
        List<OpportunityItem> items = new java.util.ArrayList<>();
        for (OpportunityItemRequest request : requests) {
            if (isEmptyItem(request)) {
                continue;
            }
            validateOpportunityItem(request);

            BigDecimal unitPrice = nonNegativeAmount(request.getUnitPrice(), "Unit price");
            OpportunityItem item = new OpportunityItem();
            item.setOpportunity(opportunity);
            item.setProductTypeGroupCode(PRODUCT_TYPE_GROUP);
            item.setProductTypeCode(request.getProductType().getCode());
            item.setItemName(trimToNull(request.getItemName()));
            item.setDescription(trimToNull(request.getDescription()));
            item.setQuantity(request.getQuantity());
            item.setUnitPrice(unitPrice);
            item.setTotalAmount(unitPrice.multiply(BigDecimal.valueOf(request.getQuantity())));
            item.setNotes(trimToNull(request.getNotes()));
            item.setSortOrder(sortOrder++);
            items.add(item);
        }
        return items;
    }

    private void copyLeadItemsToOpportunity(Lead lead, Opportunity opportunity) {
        List<OpportunityItem> opportunityItems = leadItemRepository.findByLeadIdOrderBySortOrderAsc(lead.getId())
                .stream()
                .map(leadItem -> {
                    OpportunityItem opportunityItem = new OpportunityItem();
                    opportunityItem.setOpportunity(opportunity);
                    opportunityItem.setProductTypeGroupCode(PRODUCT_TYPE_GROUP);
                    opportunityItem.setProductTypeCode(leadItem.getProductTypeCode());
                    opportunityItem.setItemName(leadItem.getItemName());
                    opportunityItem.setDescription(leadItem.getDescription());
                    opportunityItem.setQuantity(leadItem.getQuantity());
                    opportunityItem.setUnitPrice(leadItem.getEstimatedUnitPrice());
                    opportunityItem.setTotalAmount(leadItem.getEstimatedTotal());
                    opportunityItem.setNotes(leadItem.getNotes());
                    opportunityItem.setSortOrder(leadItem.getSortOrder());
                    return opportunityItem;
                })
                .toList();
        if (!opportunityItems.isEmpty()) {
            opportunityItemRepository.saveAll(opportunityItems);
        }
    }

    private boolean isEmptyItem(OpportunityItemRequest request) {
        return request == null
                || (request.getProductType() == null
                && trimToNull(request.getItemName()) == null
                && request.getQuantity() == null
                && request.getUnitPrice() == null
                && trimToNull(request.getNotes()) == null);
    }

    private void validateOpportunityItem(OpportunityItemRequest request) {
        validateProductType(request.getProductType());
        if (trimToNull(request.getItemName()) == null) {
            throw new IllegalArgumentException("Item name is required");
        }
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        nonNegativeAmount(request.getUnitPrice(), "Unit price");
    }

    private void validateProductType(OptionValue productType) {
        if (productType == null || !PRODUCT_TYPE_GROUP.equals(productType.getGroupCode())) {
            throw new IllegalArgumentException("Product type is required");
        }
    }

    private BigDecimal nonNegativeAmount(BigDecimal amount, String label) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        if (value.signum() < 0) {
            throw new IllegalArgumentException(label + " must not be negative");
        }
        return value;
    }

    private BigDecimal requiredNonNegativeAmount(BigDecimal amount, String label) {
        if (amount == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        return nonNegativeAmount(amount, label);
    }

    private void applyRequest(Opportunity opportunity, OpportunityRequest request) {
        Lead lead = resolveLead(request, opportunity);
        Person person = resolvePerson(request);
        Company company = resolveCompany(request, lead, person);
        User assignedTo = resolveAssignedTo(request);

        opportunity.setTitle(trimToNull(request.getTitle()));
        opportunity.setLead(lead);
        opportunity.setCompany(company);
        opportunity.setPerson(person);
        opportunity.setStatusGroupCode(OPPORTUNITY_STATUS_GROUP);
        opportunity.setStatusCode(request.getStatus() == null ? "PRODUCT_SOLUTIONING" : request.getStatus().getCode());
        opportunity.setEstimatedAmount(request.getEstimatedAmount());
        opportunity.setMargin(requiredNonNegativeAmount(request.getMargin(), "Margin"));
        opportunity.setProbability(request.getProbability());
        opportunity.setExpectedCloseDate(request.getExpectedCloseDate());
        opportunity.setAssignedTo(assignedTo);
        opportunity.setDescription(trimToNull(request.getDescription()));
        opportunity.setNotes(trimToNull(request.getNotes()));
        opportunity.setSoNumber(trimToNull(request.getSoNumber()));
        opportunity.setContractPoNumber(trimToNull(request.getContractPoNumber()));
        opportunity.setUpdatedBy(assignedTo);
        validateWonDocumentNumbers(opportunity);
        applyWinLossDates(opportunity, request.getStatus());
    }

    private void applyWonDocumentNumbers(Opportunity opportunity, String soNumber, String contractPoNumber) {
        String trimmedSoNumber = trimToNull(soNumber);
        String trimmedContractPoNumber = trimToNull(contractPoNumber);
        if (trimmedSoNumber != null) {
            opportunity.setSoNumber(trimmedSoNumber);
        }
        if (trimmedContractPoNumber != null) {
            opportunity.setContractPoNumber(trimmedContractPoNumber);
        }
    }

    private void validateWonDocumentNumbers(Opportunity opportunity) {
        if (!WON_STATUS_CODE.equals(opportunity.getStatusCode())) {
            return;
        }
        if (trimToNull(opportunity.getSoNumber()) == null) {
            throw new IllegalArgumentException("SO Number is required when opportunity is Won");
        }
        if (trimToNull(opportunity.getContractPoNumber()) == null) {
            throw new IllegalArgumentException("Contract / PO Number is required when opportunity is Won");
        }
    }

    private Lead resolveLead(OpportunityRequest request, Opportunity currentOpportunity) {
        if (request.getLead() == null || request.getLead().getId() == null) {
            return null;
        }
        Lead lead = leadRepository.findById(request.getLead().getId()).orElseThrow();
        dataAccessService.assertCanAccessCreatedBy("lead", lead.getCreatedBy());
        if (!"VALID".equals(lead.getStatusCode())) {
            throw new IllegalStateException("Only valid leads can be linked to opportunities.");
        }
        Optional<Opportunity> existingOpportunity = opportunityRepository.findByLeadId(lead.getId());
        if (existingOpportunity.isPresent()
                && (currentOpportunity.getId() == null
                || !Objects.equals(existingOpportunity.get().getId(), currentOpportunity.getId()))) {
            throw new IllegalStateException("This lead has already been converted to an opportunity.");
        }
        return lead;
    }

    private Person resolvePerson(OpportunityRequest request) {
        if (request.getPerson() == null || request.getPerson().getId() == null) {
            return null;
        }
        Person person = personRepository.findById(request.getPerson().getId()).orElseThrow();
        dataAccessService.assertCanAccessCreatedBy("person", person.getCreatedBy());
        return person;
    }

    private Company resolveCompany(OpportunityRequest request, Lead lead, Person person) {
        if (request.getCompany() != null && request.getCompany().getId() != null) {
            Company company = companyRepository.findById(request.getCompany().getId()).orElseThrow();
            dataAccessService.assertCanAccessCreatedBy("organization", company.getCreatedBy());
            return company;
        }
        if (lead != null && lead.getCompany() != null) {
            return lead.getCompany();
        }
        if (person != null && person.getCompany() != null) {
            return person.getCompany();
        }
        return null;
    }

    private User resolveAssignedTo(OpportunityRequest request) {
        if (dataAccessService.isSalesScope()) {
            User currentUser = dataAccessService.requireCurrentUserReference();
            if (request.getAssignedTo() != null && request.getAssignedTo().getId() != null
                    && !Objects.equals(request.getAssignedTo().getId(), currentUser.getId())) {
                throw new AccessDeniedException("Sales users can only assign their own opportunities.");
            }
            return currentUser;
        }
        if (request.getAssignedTo() == null || request.getAssignedTo().getId() == null) {
            return null;
        }
        return userRepository.getReferenceById(request.getAssignedTo().getId());
    }

    private void markLinkedLeadConverted(Opportunity opportunity) {
        Lead lead = opportunity.getLead();
        if (lead == null) {
            return;
        }

        if (lead.getConvertedAt() == null) {
            lead.setConvertedAt(LocalDateTime.now());
        }
        if (lead.getConvertedBy() == null) {
            lead.setConvertedBy(dataAccessService.getCurrentUserReference().orElse(opportunity.getAssignedTo()));
        }
        leadRepository.save(lead);
    }

    private void applyWinLossDates(Opportunity opportunity, OptionValue status) {
        if (status == null) {
            opportunity.setWonAt(null);
            opportunity.setLostAt(null);
            return;
        }
        if (Boolean.TRUE.equals(status.getWon())) {
            opportunity.setWonAt(LocalDateTime.now());
            opportunity.setLostAt(null);
            return;
        }
        if (Boolean.TRUE.equals(status.getLost())) {
            opportunity.setLostAt(LocalDateTime.now());
            opportunity.setWonAt(null);
            return;
        }
        opportunity.setWonAt(null);
        opportunity.setLostAt(null);
    }

    private Activity createCreatedActivity(Opportunity opportunity) {
        Activity activity = new Activity();
        activity.setTypeGroupCode(ACTIVITY_TYPE_GROUP);
        activity.setTypeCode(CREATED_ACTIVITY_TYPE);
        activity.setSubject(truncate("Opportunity created: " + opportunity.getTitle(), 200));
        activity.setOpportunity(opportunity);
        activity.setLead(opportunity.getLead());
        activity.setCompany(opportunity.getCompany());
        activity.setPerson(opportunity.getPerson());
        activity.setActivityDate(LocalDateTime.now());
        activity.setCreatedBy(dataAccessService.getCurrentUserReference().orElse(opportunity.getAssignedTo()));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("statusCode", opportunity.getStatusCode());
        metadata.put("createdFrom", "OpportunitiesView");
        activity.setMetadata(metadata);
        return activity;
    }

    private Activity createStatusChangedActivity(Opportunity opportunity, String previousStatusCode,
            String nextStatusCode) {
        Activity activity = new Activity();
        activity.setTypeGroupCode(ACTIVITY_TYPE_GROUP);
        activity.setTypeCode(STATUS_CHANGE_ACTIVITY_TYPE);
        activity.setSubject(truncate("Opportunity status changed: " + opportunity.getTitle(), 200));
        activity.setOpportunity(opportunity);
        activity.setLead(opportunity.getLead());
        activity.setCompany(opportunity.getCompany());
        activity.setPerson(opportunity.getPerson());
        activity.setActivityDate(LocalDateTime.now());
        activity.setCreatedBy(dataAccessService.getCurrentUserReference().orElse(opportunity.getAssignedTo()));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("fromStatusCode", previousStatusCode);
        metadata.put("toStatusCode", nextStatusCode);
        metadata.put("createdFrom", "OpportunitiesView");
        activity.setMetadata(metadata);
        return activity;
    }

    public static class OpportunityRequest {

        private String title;
        private Lead lead;
        private Company company;
        private Person person;
        private OptionValue status;
        private BigDecimal estimatedAmount;
        private BigDecimal margin;
        private Integer probability;
        private LocalDate expectedCloseDate;
        private User assignedTo;
        private String description;
        private String notes;
        private String soNumber;
        private String contractPoNumber;
        private List<OpportunityItemRequest> items = new java.util.ArrayList<>();

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public Lead getLead() {
            return lead;
        }

        public void setLead(Lead lead) {
            this.lead = lead;
        }

        public Company getCompany() {
            return company;
        }

        public void setCompany(Company company) {
            this.company = company;
        }

        public Person getPerson() {
            return person;
        }

        public void setPerson(Person person) {
            this.person = person;
        }

        public OptionValue getStatus() {
            return status;
        }

        public void setStatus(OptionValue status) {
            this.status = status;
        }

        public BigDecimal getEstimatedAmount() {
            return estimatedAmount;
        }

        public void setEstimatedAmount(BigDecimal estimatedAmount) {
            this.estimatedAmount = estimatedAmount;
        }

        public BigDecimal getMargin() {
            return margin;
        }

        public void setMargin(BigDecimal margin) {
            this.margin = margin;
        }

        public Integer getProbability() {
            return probability;
        }

        public void setProbability(Integer probability) {
            this.probability = probability;
        }

        public LocalDate getExpectedCloseDate() {
            return expectedCloseDate;
        }

        public void setExpectedCloseDate(LocalDate expectedCloseDate) {
            this.expectedCloseDate = expectedCloseDate;
        }

        public User getAssignedTo() {
            return assignedTo;
        }

        public void setAssignedTo(User assignedTo) {
            this.assignedTo = assignedTo;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }

        public String getSoNumber() {
            return soNumber;
        }

        public void setSoNumber(String soNumber) {
            this.soNumber = soNumber;
        }

        public String getContractPoNumber() {
            return contractPoNumber;
        }

        public void setContractPoNumber(String contractPoNumber) {
            this.contractPoNumber = contractPoNumber;
        }

        public List<OpportunityItemRequest> getItems() {
            return items;
        }

        public void setItems(List<OpportunityItemRequest> items) {
            this.items = items == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(items);
        }
    }

    public static class OpportunityItemRequest {

        private OptionValue productType;
        private String itemName;
        private String description;
        private Integer quantity;
        private BigDecimal unitPrice;
        private String notes;

        public OptionValue getProductType() {
            return productType;
        }

        public void setProductType(OptionValue productType) {
            this.productType = productType;
        }

        public String getItemName() {
            return itemName;
        }

        public void setItemName(String itemName) {
            this.itemName = itemName;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }
    }
}
