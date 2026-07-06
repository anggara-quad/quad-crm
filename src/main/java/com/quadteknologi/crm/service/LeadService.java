package com.quadteknologi.crm.service;

import com.quadteknologi.crm.domain.entity.Activity;
import com.quadteknologi.crm.domain.entity.Company;
import com.quadteknologi.crm.domain.entity.Lead;
import com.quadteknologi.crm.domain.entity.LeadItem;
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

@Service
@Transactional(readOnly = true)
public class LeadService {

    private static final String LEAD_STATUS_GROUP = "LEAD_STATUS";
    private static final String OPPORTUNITY_STATUS_GROUP = "OPPORTUNITY_STATUS";
    private static final String PRODUCT_TYPE_GROUP = "PRODUCT_TYPE";
    private static final String SOURCE_TYPE_GROUP = "SOURCE_TYPE";
    private static final String ACTIVITY_TYPE_GROUP = "ACTIVITY_TYPE";
    private static final String CREATED_ACTIVITY_TYPE = "CREATED";
    private static final String STATUS_CHANGE_ACTIVITY_TYPE = "STATUS_CHANGE";
    private static final String CONVERTED_ACTIVITY_TYPE = "CONVERTED";

    private final OptionValueRepository optionValueRepository;
    private final LeadRepository leadRepository;
    private final LeadItemRepository leadItemRepository;
    private final OpportunityRepository opportunityRepository;
    private final OpportunityItemRepository opportunityItemRepository;
    private final ActivityRepository activityRepository;
    private final CompanyRepository companyRepository;
    private final PersonRepository personRepository;
    private final UserRepository userRepository;
    private final DataAccessService dataAccessService;

    public LeadService(
            OptionValueRepository optionValueRepository,
            LeadRepository leadRepository,
            LeadItemRepository leadItemRepository,
            OpportunityRepository opportunityRepository,
            OpportunityItemRepository opportunityItemRepository,
            ActivityRepository activityRepository,
            CompanyRepository companyRepository,
            PersonRepository personRepository,
            UserRepository userRepository,
            DataAccessService dataAccessService) {
        this.optionValueRepository = optionValueRepository;
        this.leadRepository = leadRepository;
        this.leadItemRepository = leadItemRepository;
        this.opportunityRepository = opportunityRepository;
        this.opportunityItemRepository = opportunityItemRepository;
        this.activityRepository = activityRepository;
        this.companyRepository = companyRepository;
        this.personRepository = personRepository;
        this.userRepository = userRepository;
        this.dataAccessService = dataAccessService;
    }

    public List<OptionValue> findPipelineStatuses() {
        return optionValueRepository
                .findByGroupCodeAndActiveTrueOrderBySortOrderAsc(LEAD_STATUS_GROUP)
                .stream()
                .filter(this::isShownInPipeline)
                .toList();
    }

    public List<OptionValue> findOpportunityPipelineStatuses() {
        return optionValueRepository
                .findByGroupCodeAndActiveTrueOrderBySortOrderAsc(OPPORTUNITY_STATUS_GROUP)
                .stream()
                .filter(this::isShownInPipeline)
                .toList();
    }

    public List<OptionValue> findProductTypes() {
        return optionValueRepository.findByGroupCodeAndActiveTrueOrderBySortOrderAsc(PRODUCT_TYPE_GROUP);
    }

    public List<OptionValue> findSourceTypes() {
        return optionValueRepository.findByGroupCodeAndActiveTrueOrderBySortOrderAsc(SOURCE_TYPE_GROUP);
    }

    public Map<String, List<Lead>> findPipelineLeadsByStatus() {
        List<Lead> leads = dataAccessService.isSalesScope()
                ? leadRepository.findByStatusGroupCodeAndCreatedByIdOrderByCreatedAtDesc(
                        LEAD_STATUS_GROUP, dataAccessService.requireCurrentUserId())
                : leadRepository.findByStatusGroupCodeOrderByCreatedAtDesc(LEAD_STATUS_GROUP);
        return leads
                .stream()
                .collect(Collectors.groupingBy(Lead::getStatusCode));
    }

    public OptionValue getDefaultPipelineStatus() {
        List<OptionValue> statuses = findPipelineStatuses();
        return statuses.stream()
                .filter(status -> "NEW".equals(status.getCode()))
                .findFirst()
                .orElseGet(() -> statuses.isEmpty() ? null : statuses.get(0));
    }

    public OptionValue getDefaultOpportunityPipelineStatus() {
        List<OptionValue> statuses = findOpportunityPipelineStatuses();
        return statuses.stream()
                .filter(status -> "PRODUCT_SOLUTIONING".equals(status.getCode()))
                .findFirst()
                .orElseGet(() -> statuses.isEmpty() ? null : statuses.get(0));
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

    public Lead findLead(Long id) {
        Lead lead = leadRepository.findById(id).orElseThrow();
        dataAccessService.assertCanAccessCreatedBy("lead", lead.getCreatedBy());
        return lead;
    }

    public Lead findLead(UUID publicId) {
        Lead lead = leadRepository.findByPublicId(publicId).orElseThrow();
        dataAccessService.assertCanAccessCreatedBy("lead", lead.getCreatedBy());
        return lead;
    }

    public Opportunity findOpportunityByLead(Long leadId) {
        Lead lead = findLead(leadId);
        Opportunity opportunity = opportunityRepository.findByLeadId(lead.getId()).orElse(null);
        if (opportunity != null) {
            try {
                dataAccessService.assertCanAccessCreatedBy("opportunity", opportunity.getCreatedBy());
            } catch (AccessDeniedException exception) {
                return null;
            }
        }
        return opportunity;
    }

    public List<LeadItem> findLeadItems(Long leadId) {
        Lead lead = findLead(leadId);
        return leadItemRepository.findByLeadIdOrderBySortOrderAsc(lead.getId());
    }

    public List<Activity> findActivities(Long leadId) {
        Lead lead = findLead(leadId);
        return activityRepository.findByLeadIdOrderByActivityDateDesc(lead.getId());
    }

    @Transactional
    public Lead createLead(CreateLeadRequest request) {
        Person person = resolvePerson(request);
        Company company = resolveCompany(request, person);
        User assignedTo = resolveAssignedTo(request);
        User currentUser = dataAccessService.requireCurrentUserReference();

        Lead lead = new Lead();
        lead.setTitle(trimToNull(request.getTitle()));
        lead.setCompany(company);
        lead.setPerson(person);
        lead.setRawCompanyName(trimToNull(request.getRawCompanyName()));
        lead.setRawPersonName(trimToNull(request.getRawPersonName()));
        lead.setRawEmail(trimToNull(request.getRawEmail()));
        lead.setRawPhone(trimToNull(request.getRawPhone()));
        lead.setSource(trimToNull(request.getSource()));
        lead.setStatusGroupCode(LEAD_STATUS_GROUP);
        lead.setStatusCode(request.getStatus() == null ? "NEW" : request.getStatus().getCode());
        lead.setAssignedTo(assignedTo);
        lead.setDescription(trimToNull(request.getDescription()));
        lead.setNotes(trimToNull(request.getNotes()));
        lead.setCreatedBy(currentUser);
        lead.setUpdatedBy(currentUser);

        Lead savedLead = leadRepository.save(lead);
        replaceLeadItems(savedLead, request.getItems());
        activityRepository.save(createLeadCreatedActivity(savedLead, request));
        return leadRepository.findById(savedLead.getId()).orElse(savedLead);
    }

    @Transactional
    public Lead updateLead(Long id, CreateLeadRequest request) {
        Lead lead = findLead(id);
        String previousStatusCode = lead.getStatusCode();

        Person person = resolvePerson(request);
        Company company = resolveCompany(request, person);
        User assignedTo = resolveAssignedTo(request);
        User currentUser = dataAccessService.requireCurrentUserReference();

        lead.setTitle(trimToNull(request.getTitle()));
        lead.setCompany(company);
        lead.setPerson(person);
        lead.setRawCompanyName(trimToNull(request.getRawCompanyName()));
        lead.setRawPersonName(trimToNull(request.getRawPersonName()));
        lead.setRawEmail(trimToNull(request.getRawEmail()));
        lead.setRawPhone(trimToNull(request.getRawPhone()));
        lead.setSource(trimToNull(request.getSource()));
        lead.setStatusGroupCode(LEAD_STATUS_GROUP);
        lead.setStatusCode(request.getStatus() == null ? "NEW" : request.getStatus().getCode());
        lead.setAssignedTo(assignedTo);
        lead.setDescription(trimToNull(request.getDescription()));
        lead.setNotes(trimToNull(request.getNotes()));
        lead.setUpdatedBy(currentUser);

        Lead savedLead = leadRepository.save(lead);
        replaceLeadItems(savedLead, request.getItems());
        if (!Objects.equals(previousStatusCode, savedLead.getStatusCode())) {
            activityRepository.save(createStatusChangedActivity(savedLead, previousStatusCode,
                    savedLead.getStatusCode(), request.getInitialActivityNote()));
        }
        return leadRepository.findById(savedLead.getId()).orElse(savedLead);
    }

    @Transactional
    public Lead updateLeadStatus(Long id, OptionValue status) {
        Lead lead = findLead(id);
        String previousStatusCode = lead.getStatusCode();
        String nextStatusCode = status == null ? "NEW" : status.getCode();

        if (Objects.equals(previousStatusCode, nextStatusCode)) {
            return lead;
        }

        lead.setStatusGroupCode(LEAD_STATUS_GROUP);
        lead.setStatusCode(nextStatusCode);
        Lead savedLead = leadRepository.save(lead);
        activityRepository.save(createStatusChangedActivity(savedLead, previousStatusCode, nextStatusCode, null));
        return leadRepository.findById(savedLead.getId()).orElse(savedLead);
    }

    @Transactional
    public Opportunity createOpportunityFromLead(Long leadId) {
        return createOpportunityFromLead(leadId, new ConvertLeadRequest());
    }

    @Transactional
    public Opportunity createOpportunityFromLead(Long leadId, ConvertLeadRequest request) {
        Lead lead = findLead(leadId);
        if (!"VALID".equals(lead.getStatusCode())) {
            throw new IllegalStateException("Only valid leads can be converted to opportunities.");
        }

        Optional<Opportunity> existingOpportunity = opportunityRepository.findByLeadId(leadId);
        if (existingOpportunity.isPresent()) {
            markLeadConverted(lead, existingOpportunity.get());
            return existingOpportunity.get();
        }

        OptionValue defaultStatus = getDefaultOpportunityPipelineStatus();
        String statusCode = defaultStatus == null ? "PRODUCT_SOLUTIONING" : defaultStatus.getCode();
        validateOpportunityConversion(lead, request);
        Opportunity opportunity = buildOpportunityFromLead(lead, statusCode, request);
        Opportunity savedOpportunity = opportunityRepository.save(opportunity);
        copyLeadItemsToOpportunity(lead, savedOpportunity);

        markLeadConverted(lead, savedOpportunity);
        activityRepository.save(createLeadConvertedActivity(lead, savedOpportunity));
        return opportunityRepository.findById(savedOpportunity.getId()).orElse(savedOpportunity);
    }

    private boolean isShownInPipeline(OptionValue status) {
        Object value = status.getMetadata() == null ? null : status.getMetadata().get("showInPipeline");
        return value == null || Boolean.TRUE.equals(value) || Objects.equals("true", String.valueOf(value));
    }

    private Person resolvePerson(CreateLeadRequest request) {
        if (request.getPerson() == null || request.getPerson().getId() == null) {
            return null;
        }
        Person person = personRepository.findById(request.getPerson().getId()).orElseThrow();
        dataAccessService.assertCanAccessCreatedBy("person", person.getCreatedBy());
        return person;
    }

    private Company resolveCompany(CreateLeadRequest request, Person person) {
        if (request.getCompany() != null) {
            Company company = companyRepository.findById(request.getCompany().getId()).orElseThrow();
            dataAccessService.assertCanAccessCreatedBy("organization", company.getCreatedBy());
            return company;
        }
        if (request.getPerson() != null && request.getPerson().getCompany() != null) {
            Company company = companyRepository.findById(request.getPerson().getCompany().getId()).orElseThrow();
            dataAccessService.assertCanAccessCreatedBy("organization", company.getCreatedBy());
            return company;
        }
        if (person != null && person.getCompany() != null) {
            return person.getCompany();
        }
        return null;
    }

    private User resolveAssignedTo(CreateLeadRequest request) {
        if (dataAccessService.isSalesScope()) {
            User currentUser = dataAccessService.requireCurrentUserReference();
            if (request.getAssignedTo() != null && request.getAssignedTo().getId() != null
                    && !Objects.equals(request.getAssignedTo().getId(), currentUser.getId())) {
                throw new AccessDeniedException("Sales users can only assign their own leads.");
            }
            return currentUser;
        }
        if (request.getAssignedTo() == null || request.getAssignedTo().getId() == null) {
            return null;
        }
        return userRepository.getReferenceById(request.getAssignedTo().getId());
    }

    private Opportunity buildOpportunityFromLead(Lead lead, String statusCode, ConvertLeadRequest request) {
        Opportunity opportunity = new Opportunity();
        opportunity.setTitle(lead.getTitle());
        opportunity.setLead(lead);
        opportunity.setCompany(lead.getCompany());
        opportunity.setPerson(lead.getPerson());
        opportunity.setStatusGroupCode(OPPORTUNITY_STATUS_GROUP);
        opportunity.setStatusCode(statusCode);
        opportunity.setEstimatedAmount(request.getEstimatedAmount());
        opportunity.setProbability(request.getProbability());
        opportunity.setExpectedCloseDate(request.getExpectedCloseDate());
        opportunity.setAssignedTo(lead.getAssignedTo());
        opportunity.setDescription(lead.getDescription());
        opportunity.setNotes(lead.getNotes());
        User currentUser = dataAccessService.requireCurrentUserReference();
        opportunity.setCreatedBy(currentUser);
        opportunity.setUpdatedBy(currentUser);
        return opportunity;
    }

    private void validateOpportunityConversion(Lead lead, ConvertLeadRequest request) {
        if (trimToNull(lead.getTitle()) == null) {
            throw new IllegalStateException("Opportunity title is required");
        }
        if (lead.getCompany() == null) {
            throw new IllegalStateException("Company is required before creating an opportunity");
        }
        if (lead.getPerson() == null) {
            throw new IllegalStateException("Person is required before creating an opportunity");
        }
        if (lead.getAssignedTo() == null) {
            throw new IllegalStateException("Assigned To is required before creating an opportunity");
        }
        if (request == null || request.getEstimatedAmount() == null) {
            throw new IllegalStateException("Estimated Amount is required before creating an opportunity");
        }
        if (request.getEstimatedAmount().signum() < 0) {
            throw new IllegalStateException("Estimated Amount must not be negative");
        }
        if (request.getProbability() == null) {
            throw new IllegalStateException("Probability is required before creating an opportunity");
        }
        if (request.getProbability() < 0 || request.getProbability() > 100) {
            throw new IllegalStateException("Probability must be between 0 and 100");
        }
        if (request.getExpectedCloseDate() == null) {
            throw new IllegalStateException("Expected Close is required before creating an opportunity");
        }
    }

    private void replaceLeadItems(Lead lead, List<LeadItemRequest> requests) {
        leadItemRepository.deleteByLeadId(lead.getId());
        List<LeadItem> items = buildLeadItems(lead, requests);
        if (!items.isEmpty()) {
            leadItemRepository.saveAll(items);
        }
    }

    private List<LeadItem> buildLeadItems(Lead lead, List<LeadItemRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        int sortOrder = 0;
        List<LeadItem> items = new java.util.ArrayList<>();
        for (LeadItemRequest request : requests) {
            if (isEmptyItem(request)) {
                continue;
            }
            validateLeadItem(request);

            BigDecimal unitPrice = nonNegativeAmount(request.getEstimatedUnitPrice(), "Estimated unit price");
            LeadItem item = new LeadItem();
            item.setLead(lead);
            item.setProductTypeGroupCode(PRODUCT_TYPE_GROUP);
            item.setProductTypeCode(request.getProductType().getCode());
            item.setItemName(trimToNull(request.getItemName()));
            item.setDescription(trimToNull(request.getDescription()));
            item.setQuantity(request.getQuantity());
            item.setEstimatedUnitPrice(unitPrice);
            item.setEstimatedTotal(unitPrice.multiply(BigDecimal.valueOf(request.getQuantity())));
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

    private boolean isEmptyItem(LeadItemRequest request) {
        return request == null
                || (request.getProductType() == null
                && trimToNull(request.getItemName()) == null
                && request.getQuantity() == null
                && request.getEstimatedUnitPrice() == null
                && trimToNull(request.getNotes()) == null);
    }

    private void validateLeadItem(LeadItemRequest request) {
        validateProductType(request.getProductType());
        if (trimToNull(request.getItemName()) == null) {
            throw new IllegalArgumentException("Item name is required");
        }
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        nonNegativeAmount(request.getEstimatedUnitPrice(), "Estimated unit price");
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

    private void markLeadConverted(Lead lead, Opportunity opportunity) {
        if (lead.getConvertedAt() == null) {
            lead.setConvertedAt(LocalDateTime.now());
        }
        if (lead.getConvertedBy() == null) {
            lead.setConvertedBy(dataAccessService.getCurrentUserReference().orElse(opportunity.getAssignedTo()));
        }
        leadRepository.save(lead);
    }

    private Activity createLeadCreatedActivity(Lead lead, CreateLeadRequest request) {
        Activity activity = new Activity();
        activity.setTypeGroupCode(ACTIVITY_TYPE_GROUP);
        activity.setTypeCode(CREATED_ACTIVITY_TYPE);
        activity.setSubject(truncate("Lead created: " + lead.getTitle(), 200));
        activity.setDescription(trimToNull(request.getInitialActivityNote()));
        activity.setLead(lead);
        activity.setCompany(lead.getCompany());
        activity.setPerson(lead.getPerson());
        activity.setActivityDate(LocalDateTime.now());
        activity.setCreatedBy(dataAccessService.getCurrentUserReference().orElse(lead.getAssignedTo()));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("statusCode", lead.getStatusCode());
        metadata.put("source", lead.getSource());
        metadata.put("createdFrom", "LeadsView");
        activity.setMetadata(metadata);
        return activity;
    }

    private Activity createStatusChangedActivity(Lead lead, String previousStatusCode, String nextStatusCode,
            String note) {
        Activity activity = new Activity();
        activity.setTypeGroupCode(ACTIVITY_TYPE_GROUP);
        activity.setTypeCode(STATUS_CHANGE_ACTIVITY_TYPE);
        activity.setSubject(truncate("Lead status changed: " + lead.getTitle(), 200));
        activity.setDescription(trimToNull(note));
        activity.setLead(lead);
        activity.setCompany(lead.getCompany());
        activity.setPerson(lead.getPerson());
        activity.setActivityDate(LocalDateTime.now());
        activity.setCreatedBy(dataAccessService.getCurrentUserReference().orElse(lead.getAssignedTo()));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("fromStatusCode", previousStatusCode);
        metadata.put("toStatusCode", nextStatusCode);
        metadata.put("createdFrom", "LeadsView");
        activity.setMetadata(metadata);
        return activity;
    }

    private Activity createLeadConvertedActivity(Lead lead, Opportunity opportunity) {
        Activity activity = new Activity();
        activity.setTypeGroupCode(ACTIVITY_TYPE_GROUP);
        activity.setTypeCode(CONVERTED_ACTIVITY_TYPE);
        activity.setSubject(truncate("Lead converted: " + lead.getTitle(), 200));
        activity.setLead(lead);
        activity.setOpportunity(opportunity);
        activity.setCompany(lead.getCompany());
        activity.setPerson(lead.getPerson());
        activity.setActivityDate(LocalDateTime.now());
        activity.setCreatedBy(dataAccessService.getCurrentUserReference().orElse(lead.getAssignedTo()));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("opportunityStatusCode", opportunity.getStatusCode());
        metadata.put("createdFrom", "LeadsView");
        activity.setMetadata(metadata);
        return activity;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public static class CreateLeadRequest {

        private String title;
        private Company company;
        private Person person;
        private String rawCompanyName;
        private String rawPersonName;
        private String rawEmail;
        private String rawPhone;
        private String source;
        private OptionValue status;
        private User assignedTo;
        private String description;
        private String notes;
        private String initialActivityNote;
        private List<LeadItemRequest> items = new java.util.ArrayList<>();

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
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

        public String getRawCompanyName() {
            return rawCompanyName;
        }

        public void setRawCompanyName(String rawCompanyName) {
            this.rawCompanyName = rawCompanyName;
        }

        public String getRawPersonName() {
            return rawPersonName;
        }

        public void setRawPersonName(String rawPersonName) {
            this.rawPersonName = rawPersonName;
        }

        public String getRawEmail() {
            return rawEmail;
        }

        public void setRawEmail(String rawEmail) {
            this.rawEmail = rawEmail;
        }

        public String getRawPhone() {
            return rawPhone;
        }

        public void setRawPhone(String rawPhone) {
            this.rawPhone = rawPhone;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public OptionValue getStatus() {
            return status;
        }

        public void setStatus(OptionValue status) {
            this.status = status;
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

        public String getInitialActivityNote() {
            return initialActivityNote;
        }

        public void setInitialActivityNote(String initialActivityNote) {
            this.initialActivityNote = initialActivityNote;
        }

        public List<LeadItemRequest> getItems() {
            return items;
        }

        public void setItems(List<LeadItemRequest> items) {
            this.items = items == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(items);
        }
    }

    public static class ConvertLeadRequest {

        private BigDecimal estimatedAmount;
        private Integer probability;
        private LocalDate expectedCloseDate;

        public BigDecimal getEstimatedAmount() {
            return estimatedAmount;
        }

        public void setEstimatedAmount(BigDecimal estimatedAmount) {
            this.estimatedAmount = estimatedAmount;
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
    }

    public static class LeadItemRequest {

        private OptionValue productType;
        private String itemName;
        private String description;
        private Integer quantity;
        private BigDecimal estimatedUnitPrice;
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

        public BigDecimal getEstimatedUnitPrice() {
            return estimatedUnitPrice;
        }

        public void setEstimatedUnitPrice(BigDecimal estimatedUnitPrice) {
            this.estimatedUnitPrice = estimatedUnitPrice;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }
    }
}
