package com.quadteknologi.crm.views;

import com.quadteknologi.crm.domain.entity.Company;
import com.quadteknologi.crm.domain.entity.Lead;
import com.quadteknologi.crm.domain.entity.Opportunity;
import com.quadteknologi.crm.domain.entity.OpportunityItem;
import com.quadteknologi.crm.domain.entity.OptionValue;
import com.quadteknologi.crm.domain.entity.Person;
import com.quadteknologi.crm.domain.entity.User;
import com.quadteknologi.crm.service.OpportunityService;
import com.quadteknologi.crm.ui.component.ActivityTimeline;
import com.quadteknologi.crm.ui.component.KanbanBoard;
import com.quadteknologi.crm.ui.layout.MainLayout;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@RolesAllowed({"Administrator", "Manager", "Sales"})
@PageTitle("Opportunities | Quad CRM")
@Route(value = "opportunities", layout = MainLayout.class)
public class OpportunitiesView extends VerticalLayout implements BeforeEnterObserver {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));

    private final OpportunityService opportunityService;
    private KanbanBoard<Opportunity> kanbanBoard;
    private String searchTerm = "";

    public OpportunitiesView(OpportunityService opportunityService) {
        this.opportunityService = opportunityService;

        addClassName("page-view");
        setPadding(false);
        setSpacing(false);
        setSizeFull();

        add(createHeader(), createKanbanBoard());
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        event.getLocation()
                .getQueryParameters()
                .getSingleParameter("opportunity")
                .flatMap(this::parseLong)
                .ifPresent(opportunityId -> {
                    try {
                        Opportunity opportunity = opportunityService.findOpportunity(opportunityId);
                        kanbanBoard.setDetail(createOpportunityDetail(opportunity));
                    } catch (RuntimeException exception) {
                        showError("Opportunity was not found");
                    }
                });
    }

    private Component createHeader() {
        Header header = new Header();
        header.addClassName("pipeline-header");

        Div titleGroup = new Div();
        titleGroup.addClassName("pipeline-title-group");

        H2 title = new H2("Opportunities");
        Paragraph subtitle = new Paragraph("Track opportunity progress by pipeline stage.");
        titleGroup.add(title, subtitle);

        Button createOpportunityButton = new Button("Create Opportunity", VaadinIcon.PLUS.create(),
                event -> openCreateOpportunityForm(opportunityService.getDefaultPipelineStatus()));
        createOpportunityButton.addClassName("pipeline-create-button");

        Div actions = new Div();
        actions.addClassName("pipeline-header-actions");
        actions.add(createSearchField("Search opportunities"), createOpportunityButton);

        header.add(titleGroup, actions);
        return header;
    }

    private TextField createSearchField(String placeholder) {
        TextField search = new TextField();
        search.addClassName("pipeline-search-field");
        search.setPlaceholder(placeholder);
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.EAGER);
        search.addValueChangeListener(event -> {
            searchTerm = event.getValue() == null ? "" : event.getValue();
            refreshKanbanBoard();
        });
        return search;
    }

    private Component createKanbanBoard() {
        List<OptionValue> statuses = opportunityService.findPipelineStatuses();
        Map<String, List<Opportunity>> opportunitiesByStatus =
                filterOpportunitiesBySearch(opportunityService.findPipelineOpportunitiesByStatus());

        kanbanBoard = new KanbanBoard<>(
                "opportunity",
                "Create Opportunity",
                "No opportunities yet",
                "Create an opportunity to fill this stage.",
                statuses,
                opportunitiesByStatus,
                this::opportunityCardData,
                this::createOpportunityDetail,
                this::openCreateOpportunityForm);
        return kanbanBoard;
    }

    private void openCreateOpportunityForm(OptionValue defaultStatus) {
        openOpportunityForm(null, defaultStatus);
    }

    private void openEditOpportunityForm(Opportunity opportunity) {
        openOpportunityForm(opportunityService.findOpportunity(opportunity.getId()), null);
    }

    private void openOpportunityForm(Opportunity opportunity, OptionValue defaultStatus) {
        boolean editing = opportunity != null && opportunity.getId() != null;
        OpportunityService.OpportunityRequest request = editing
                ? createRequestFromOpportunity(opportunity)
                : new OpportunityService.OpportunityRequest();

        Binder<OpportunityService.OpportunityRequest> binder =
                new Binder<>(OpportunityService.OpportunityRequest.class);
        Div detail = createOpportunityFormShell(
                editing ? "Edit Opportunity" : "Create Opportunity",
                editing ? "Update opportunity details and stage." : "Capture an opportunity and its first stage.",
                editing);

        List<OptionValue> statuses = opportunityService.findPipelineStatuses();
        List<OptionValue> productTypes = opportunityService.findProductTypes();
        List<Lead> leads = new ArrayList<>(opportunityService.findValidLeads());
        List<Company> companies = new ArrayList<>(opportunityService.findCompanies());
        List<Person> persons = new ArrayList<>(opportunityService.findPersons());
        if (editing && opportunity.getLead() != null
                && leads.stream().noneMatch(lead -> Objects.equals(lead.getId(), opportunity.getLead().getId()))) {
            leads.add(0, opportunity.getLead());
        }

        request.setStatus(editing
                ? findStatusByCode(statuses, opportunity.getStatusCode())
                : defaultStatus == null ? opportunityService.getDefaultPipelineStatus() : defaultStatus);

        TextField title = new TextField("Opportunity Title");
        title.setPlaceholder("e.g. ERP implementation proposal");

        ComboBox<OptionValue> status = new ComboBox<>("Stage");
        status.setItems(statuses);
        status.setItemLabelGenerator(OptionValue::getName);

        ComboBox<Lead> lead = new ComboBox<>("Lead");
        lead.setItems(leads);
        lead.setItemLabelGenerator(Lead::getTitle);
        lead.setClearButtonVisible(true);
        if (editing && opportunity.getLead() != null) {
            lead.setReadOnly(true);
        }

        ComboBox<Company> company = new ComboBox<>("Organization");
        company.setItems(companies);
        company.setItemLabelGenerator(Company::getName);
        company.setClearButtonVisible(true);

        ComboBox<Person> person = new ComboBox<>("Person");
        person.setItems(persons);
        person.setItemLabelGenerator(this::displayPersonOption);
        person.setClearButtonVisible(true);

        lead.addValueChangeListener(event -> {
            Lead selectedLead = event.getValue();
            if (selectedLead == null) {
                return;
            }
            if (title.getValue() == null || title.getValue().isBlank()) {
                title.setValue(selectedLead.getTitle());
            }
            if (selectedLead.getCompany() != null) {
                companies.stream()
                        .filter(candidate -> Objects.equals(candidate.getId(), selectedLead.getCompany().getId()))
                        .findFirst()
                        .ifPresent(company::setValue);
            }
            if (selectedLead.getPerson() != null) {
                persons.stream()
                        .filter(candidate -> Objects.equals(candidate.getId(), selectedLead.getPerson().getId()))
                        .findFirst()
                        .ifPresent(person::setValue);
            }
        });

        company.addValueChangeListener(event -> {
            Company selectedCompany = event.getValue();
            List<Person> filteredPersons = selectedCompany == null
                    ? persons
                    : persons.stream()
                            .filter(candidate -> candidate.getCompany() != null
                                    && Objects.equals(candidate.getCompany().getId(), selectedCompany.getId()))
                            .toList();
            person.setItems(filteredPersons);
            if (person.getValue() != null && selectedCompany != null
                    && (person.getValue().getCompany() == null
                    || !Objects.equals(person.getValue().getCompany().getId(), selectedCompany.getId()))) {
                person.clear();
            }
        });

        person.addValueChangeListener(event -> {
            Person selectedPerson = event.getValue();
            if (selectedPerson != null && selectedPerson.getCompany() != null && company.getValue() == null) {
                companies.stream()
                        .filter(candidate -> Objects.equals(candidate.getId(), selectedPerson.getCompany().getId()))
                        .findFirst()
                        .ifPresent(company::setValue);
            }
        });

        BigDecimalField estimatedAmount = new BigDecimalField("Estimated Amount");
        estimatedAmount.setPrefixComponent(new Span("Rp"));
        estimatedAmount.setClearButtonVisible(true);
        estimatedAmount.setPlaceholder("0");
        estimatedAmount.getElement().setAttribute("inputmode", "decimal");

        IntegerField probability = new IntegerField("Probability");
        probability.setMin(0);
        probability.setMax(100);
        probability.setSuffixComponent(new Span("%"));

        DatePicker expectedClose = new DatePicker("Expected Close");

        ComboBox<User> assignedTo = new ComboBox<>("Assigned To");
        assignedTo.setItems(opportunityService.findAssignableUsers());
        assignedTo.setItemLabelGenerator(User::getFullName);
        assignedTo.setClearButtonVisible(true);

        TextArea description = new TextArea("Description");
        description.setMinHeight("92px");
        TextArea notes = new TextArea("Internal Notes");
        notes.setMinHeight("92px");
        Component itemsEditor = createOpportunityItemsEditor(request.getItems(), productTypes);

        binder.forField(title)
                .asRequired("Opportunity title is required")
                .bind( OpportunityService.OpportunityRequest::getTitle, OpportunityService.OpportunityRequest::setTitle);

        binder.forField(status)
                .asRequired("Stage is required")
                .bind(OpportunityService.OpportunityRequest::getStatus, OpportunityService.OpportunityRequest::setStatus);

        binder.bind(lead, OpportunityService.OpportunityRequest::getLead, OpportunityService.OpportunityRequest::setLead);

        binder.forField(company)
                .asRequired("Company Name is required")
                .bind(OpportunityService.OpportunityRequest::getCompany, OpportunityService.OpportunityRequest::setCompany);

        binder.forField(person)
                .asRequired("Person Name is required")
                .bind(OpportunityService.OpportunityRequest::getPerson, OpportunityService.OpportunityRequest::setPerson);

        binder.forField(estimatedAmount)
                .asRequired("Estimated Amount is required")
                .bind(OpportunityService.OpportunityRequest::getEstimatedAmount, OpportunityService.OpportunityRequest::setEstimatedAmount);

        binder.forField(probability)
                .asRequired("Probability is required")
                .bind(OpportunityService.OpportunityRequest::getProbability, OpportunityService.OpportunityRequest::setProbability);

        binder.forField(expectedClose)
                .asRequired("Expected Close is required")
                .bind(OpportunityService.OpportunityRequest::getExpectedCloseDate, OpportunityService.OpportunityRequest::setExpectedCloseDate);

        binder.forField(assignedTo)
                .asRequired("Assigned To is required")
                .bind(OpportunityService.OpportunityRequest::getAssignedTo, OpportunityService.OpportunityRequest::setAssignedTo);

        binder.bind(description, OpportunityService.OpportunityRequest::getDescription,
                OpportunityService.OpportunityRequest::setDescription);

        binder.bind(notes, OpportunityService.OpportunityRequest::getNotes,
                OpportunityService.OpportunityRequest::setNotes);

        binder.readBean(request);

        Div formBody = new Div();
        formBody.addClassName("pipeline-form-body");
        formBody.add(
                sectionDivider("Opportunity"),
                title,
                status,
                lead,
                sectionDivider("Contact"),
                company,
                person,
                sectionDivider("Forecast"),
                estimatedAmount,
                probability,
                expectedClose,
                assignedTo,
                sectionDivider("Items"),
                itemsEditor,
                sectionDivider("Details"),
                description,
                notes);

        detail.add(formBody, createOpportunityFormActions(opportunity, request, binder));
        kanbanBoard.setDetail(detail);
    }

    private OpportunityService.OpportunityRequest createRequestFromOpportunity(Opportunity opportunity) {
        OpportunityService.OpportunityRequest request = new OpportunityService.OpportunityRequest();
        request.setTitle(opportunity.getTitle());
        request.setLead(opportunity.getLead());
        request.setCompany(opportunity.getCompany());
        request.setPerson(opportunity.getPerson());
        request.setEstimatedAmount(opportunity.getEstimatedAmount());
        request.setProbability(opportunity.getProbability());
        request.setExpectedCloseDate(opportunity.getExpectedCloseDate());
        request.setAssignedTo(opportunity.getAssignedTo());
        request.setDescription(opportunity.getDescription());
        request.setNotes(opportunity.getNotes());
        request.setItems(opportunityService.findOpportunityItems(opportunity.getId())
                .stream()
                .map(this::createItemRequestFromOpportunityItem)
                .toList());
        return request;
    }

    private OpportunityService.OpportunityItemRequest createItemRequestFromOpportunityItem(OpportunityItem item) {
        OpportunityService.OpportunityItemRequest request = new OpportunityService.OpportunityItemRequest();
        request.setProductType(item.getProductType());
        request.setItemName(item.getItemName());
        request.setDescription(item.getDescription());
        request.setQuantity(item.getQuantity());
        request.setUnitPrice(item.getUnitPrice());
        request.setNotes(item.getNotes());
        return request;
    }

    private Component createOpportunityItemsEditor(List<OpportunityService.OpportunityItemRequest> items,
            List<OptionValue> productTypes) {
        Div editor = new Div();
        editor.addClassName("sales-items-editor");

        Div rows = new Div();
        rows.addClassName("sales-items-editor-rows");

        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            rows.removeAll();
            for (OpportunityService.OpportunityItemRequest item : items) {
                rows.add(createOpportunityItemRow(item, items, productTypes, refresh[0]));
            }
        };

        Button add = new Button("Add Item", VaadinIcon.PLUS.create(), event -> {
            items.add(new OpportunityService.OpportunityItemRequest());
            refresh[0].run();
        });
        add.addClassName("sales-items-add");

        refresh[0].run();
        editor.add(rows, add);
        return editor;
    }

    private Component createOpportunityItemRow(OpportunityService.OpportunityItemRequest item,
            List<OpportunityService.OpportunityItemRequest> items, List<OptionValue> productTypes, Runnable refresh) {
        Div row = new Div();
        row.addClassName("sales-item-row");

        ComboBox<OptionValue> productType = new ComboBox<>("Product Type");
        productType.setItems(productTypes);
        productType.setItemLabelGenerator(OptionValue::getName);
        OptionValue selectedProductType = findOptionByCode(productTypes,
                item.getProductType() == null ? null : item.getProductType().getCode());
        item.setProductType(selectedProductType);
        productType.setValue(selectedProductType);
        productType.addValueChangeListener(event -> item.setProductType(event.getValue()));

        TextField itemName = new TextField("Item Name");
        itemName.setValue(valueOrEmpty(item.getItemName()));
        itemName.addValueChangeListener(event -> item.setItemName(event.getValue()));

        IntegerField quantity = new IntegerField("Qty");
        quantity.setMin(1);
        quantity.setValue(item.getQuantity());
        quantity.addValueChangeListener(event -> item.setQuantity(event.getValue()));

        BigDecimalField unitPrice = new BigDecimalField("Unit Price");
        unitPrice.setPrefixComponent(new Span("Rp"));
        unitPrice.setValue(item.getUnitPrice());
        unitPrice.getElement().setAttribute("inputmode", "decimal");
        unitPrice.addValueChangeListener(event -> item.setUnitPrice(event.getValue()));

        TextArea notes = new TextArea("Notes");
        notes.setValue(valueOrEmpty(item.getNotes()));
        notes.setMinHeight("76px");
        notes.addValueChangeListener(event -> item.setNotes(event.getValue()));

        Button remove = new Button(VaadinIcon.TRASH.create(), event -> {
            items.remove(item);
            refresh.run();
        });
        remove.addClassName("sales-item-remove");
        remove.getElement().setAttribute("aria-label", "Remove item");

        row.add(productType, itemName, quantity, unitPrice, notes, remove);
        return row;
    }

    private Div createOpportunityFormShell(String titleText, String subtitleText, boolean editing) {
        Div detail = new Div();
        detail.addClassName("pipeline-form-detail");
        kanbanBoard.setDetailSize("430px");

        Div header = new Div();
        header.addClassName("pipeline-detail-header");

        Div titleGroup = new Div();
        H3 title = new H3(titleText);
        Paragraph subtitle = new Paragraph(subtitleText);
        subtitle.addClassName("pipeline-form-subtitle");
        titleGroup.add(title, subtitle);

        Button close = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> kanbanBoard.setDetail(null));
        close.addClassName("pipeline-detail-close");
        close.getElement().setAttribute("aria-label",
                editing ? "Close edit opportunity form" : "Close create opportunity form");

        header.add(titleGroup, close);
        detail.add(header);
        return detail;
    }

    private Component createOpportunityFormActions(Opportunity opportunity,
            OpportunityService.OpportunityRequest request,
            Binder<OpportunityService.OpportunityRequest> binder) {
        boolean editing = opportunity != null && opportunity.getId() != null;
        Button save = new Button(editing ? "Save Changes" : "Save Opportunity", VaadinIcon.CHECK.create(), event -> {
            try {
                binder.writeBean(request);
                Opportunity savedOpportunity = editing
                        ? opportunityService.updateOpportunity(opportunity.getId(), request)
                        : opportunityService.createOpportunity(request);
                refreshKanbanBoard();
                showSuccess(editing ? "Opportunity updated" : "Opportunity created");
                kanbanBoard.setDetail(createOpportunityDetail(savedOpportunity));
            } catch (ValidationException exception) {
                showError("Please complete required fields");
            } catch (IllegalArgumentException exception) {
                showError(exception.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancel = new Button("Cancel", event -> kanbanBoard.setDetail(null));
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout actions = new HorizontalLayout(save, cancel);
        actions.addClassName("pipeline-form-actions");
        actions.setPadding(false);
        actions.setSpacing(true);
        return actions;
    }

    private Component sectionDivider(String label) {
        Div section = new Div();
        section.addClassName("pipeline-form-section");
        Span title = new Span(label);
        title.addClassName("pipeline-form-section-title");
        section.add(new Hr(), title);
        return section;
    }

    private void refreshKanbanBoard() {
        remove(kanbanBoard);
        add(createKanbanBoard());
    }

    private Map<String, List<Opportunity>> filterOpportunitiesBySearch(
            Map<String, List<Opportunity>> opportunitiesByStatus) {
        String keyword = normalizeSearch(searchTerm);
        if (keyword.isBlank()) {
            return opportunitiesByStatus;
        }

        Map<String, List<Opportunity>> filtered = new LinkedHashMap<>();
        opportunitiesByStatus.forEach((status, opportunities) -> filtered.put(status,
                opportunities.stream()
                        .filter(opportunity -> matchesSearch(opportunity, keyword))
                        .toList()));
        return filtered;
    }

    private boolean matchesSearch(Opportunity opportunity, String keyword) {
        return containsSearch(opportunity.getTitle(), keyword)
                || containsSearch(displayPrimaryName(opportunity), keyword)
                || containsSearch(displayCompanyName(opportunity), keyword)
                || containsSearch(opportunity.getLead() == null ? null : opportunity.getLead().getTitle(), keyword)
                || containsSearch(opportunity.getStatusCode(), keyword)
                || containsSearch(Optional.ofNullable(opportunity.getStatus()).map(OptionValue::getName).orElse(null), keyword)
                || containsSearch(formatAmount(opportunity.getEstimatedAmount()), keyword)
                || containsSearch(formatProbability(opportunity.getProbability()), keyword)
                || containsSearch(opportunity.getAssignedTo() == null ? null : opportunity.getAssignedTo().getFullName(), keyword);
    }

    private boolean containsSearch(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String normalizeSearch(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private KanbanBoard.CardData opportunityCardData(Opportunity opportunity) {
        return new KanbanBoard.CardData(
                initials(displayPrimaryName(opportunity)),
                displayPrimaryName(opportunity),
                displayCompanyName(opportunity),
                opportunity.getTitle(),
                List.of(
                        new KanbanBoard.TagData(opportunity.getAssignedTo() == null ? "Unassigned" : opportunity.getAssignedTo().getFullName(), "user"),
                        new KanbanBoard.TagData(formatAmount(opportunity.getEstimatedAmount()), "amount"),
                        new KanbanBoard.TagData(formatProbability(opportunity.getProbability()), "probability")));
    }

    private Component createOpportunityDetail(Opportunity opportunity) {
        Div detail = new Div();
        detail.addClassName("pipeline-detail");
        if (kanbanBoard != null) {
            kanbanBoard.setDetailSize("360px");
        }

        Div detailHeader = new Div();
        detailHeader.addClassName("pipeline-detail-header");

        Div titleGroup = new Div();
        H3 title = new H3(opportunity.getTitle());
        Span status = new Span(Optional.ofNullable(opportunity.getStatus()).map(OptionValue::getName).orElse(opportunity.getStatusCode()));
        status.addClassNames("pipeline-detail-status", "kanban-color-" + normalizeColor(
                Optional.ofNullable(opportunity.getStatus()).map(OptionValue::getColor).orElse(null)));
        titleGroup.add(title, status);

        Button edit = new Button("Edit", VaadinIcon.EDIT.create(), event -> openEditOpportunityForm(opportunity));
        edit.addClassName("pipeline-detail-edit");

        Button close = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> kanbanBoard.setDetail(null));
        close.addClassName("pipeline-detail-close");
        close.getElement().setAttribute("aria-label", "Close opportunity detail");

        HorizontalLayout headerActions = new HorizontalLayout(edit, close);
        headerActions.addClassName("pipeline-detail-actions");
        headerActions.setPadding(false);
        headerActions.setSpacing(true);

        detailHeader.add(titleGroup, headerActions);

        Div body = new Div();
        body.addClassName("pipeline-detail-body");
        body.add(
                createStatusControl(opportunity),
                detailRow("Company", displayCompanyName(opportunity)),
                detailRow("Contact", displayPrimaryName(opportunity)),
                detailRow("Lead", opportunity.getLead() == null ? "-" : opportunity.getLead().getTitle()),
                detailRow("Estimated amount", formatAmountOrDash(opportunity.getEstimatedAmount())),
                detailRow("Probability", formatProbabilityOrDash(opportunity.getProbability())),
                detailRow("Expected close", opportunity.getExpectedCloseDate() == null ? "-" : opportunity.getExpectedCloseDate().format(DATE_FORMAT)),
                detailRow("Assigned to", opportunity.getAssignedTo() == null ? "-" : opportunity.getAssignedTo().getFullName()),
                detailRow("Created", opportunity.getCreatedAt() == null ? "-" : opportunity.getCreatedAt().format(DATE_FORMAT)),
                detailItemsSection("Items", opportunityService.findOpportunityItems(opportunity.getId())),
                detailSection("Description", opportunity.getDescription()),
                detailSection("Notes", opportunity.getNotes()),
                new ActivityTimeline(opportunityService.findActivities(opportunity.getId())));

        detail.add(detailHeader, body);
        return detail;
    }

    private Component createStatusControl(Opportunity opportunity) {
        List<OptionValue> statuses = opportunityService.findPipelineStatuses();

        ComboBox<OptionValue> status = new ComboBox<>("Move Stage");
        status.addClassName("pipeline-status-control");
        status.setItems(statuses);
        status.setItemLabelGenerator(OptionValue::getName);
        status.setValue(findStatusByCode(statuses, opportunity.getStatusCode()));
        status.addValueChangeListener(event -> {
            if (!event.isFromClient() || event.getValue() == null
                    || Objects.equals(event.getValue().getCode(), opportunity.getStatusCode())) {
                return;
            }

            Opportunity updatedOpportunity = opportunityService.updateOpportunityStatus(
                    opportunity.getId(), event.getValue());
            refreshKanbanBoard();
            showSuccess("Opportunity moved to " + event.getValue().getName());
            kanbanBoard.setDetail(createOpportunityDetail(updatedOpportunity));
        });

        return status;
    }

    private Component detailRow(String label, String value) {
        Div row = new Div();
        row.addClassName("pipeline-detail-row");
        Span labelSpan = new Span(label);
        labelSpan.addClassName("pipeline-detail-label");
        Span valueSpan = new Span(valueOrDash(value));
        valueSpan.addClassName("pipeline-detail-value");
        row.add(labelSpan, valueSpan);
        return row;
    }

    private Component detailSection(String label, String value) {
        Div section = new Div();
        section.addClassName("pipeline-detail-section");
        Span labelSpan = new Span(label);
        labelSpan.addClassName("pipeline-detail-label");
        Paragraph paragraph = new Paragraph(valueOrDash(value));
        paragraph.addClassName("pipeline-detail-text");
        section.add(labelSpan, paragraph);
        return section;
    }

    private Component detailItemsSection(String label, List<OpportunityItem> items) {
        Div section = new Div();
        section.addClassNames("pipeline-detail-section", "sales-items-detail");

        Span labelSpan = new Span(label);
        labelSpan.addClassName("pipeline-detail-label");
        section.add(labelSpan);

        if (items.isEmpty()) {
            Paragraph empty = new Paragraph("-");
            empty.addClassName("pipeline-detail-text");
            section.add(empty);
            return section;
        }

        items.forEach(item -> {
            Div row = new Div();
            row.addClassName("sales-item-detail-row");

            Span name = new Span(item.getItemName());
            name.addClassName("sales-item-detail-name");

            Span meta = new Span(productTypeName(item.getProductType(), item.getProductTypeCode())
                    + " | " + item.getQuantity()
                    + " x " + formatAmountOrDash(item.getUnitPrice())
                    + " = " + formatAmountOrDash(item.getTotalAmount()));
            meta.addClassName("sales-item-detail-meta");

            row.add(name, meta);
            if (item.getNotes() != null && !item.getNotes().isBlank()) {
                Paragraph notes = new Paragraph(item.getNotes());
                notes.addClassName("sales-item-detail-notes");
                row.add(notes);
            }
            section.add(row);
        });
        return section;
    }

    private String displayPrimaryName(Opportunity opportunity) {
        if (opportunity.getPerson() != null) {
            return opportunity.getPerson().getFullName();
        }
        if (opportunity.getCompany() != null) {
            return opportunity.getCompany().getName();
        }
        return "Unknown contact";
    }

    private String displayCompanyName(Opportunity opportunity) {
        return opportunity.getCompany() == null ? "No company" : opportunity.getCompany().getName();
    }

    private String displayPersonOption(Person person) {
        if (person.getCompany() == null) {
            return person.getFullName();
        }
        return person.getFullName() + " - " + person.getCompany().getName();
    }

    private OptionValue findStatusByCode(List<OptionValue> statuses, String statusCode) {
        return statuses.stream()
                .filter(status -> Objects.equals(status.getCode(), statusCode))
                .findFirst()
                .orElse(null);
    }

    private OptionValue findOptionByCode(List<OptionValue> options, String code) {
        return options.stream()
                .filter(option -> Objects.equals(option.getCode(), code))
                .findFirst()
                .orElse(null);
    }

    private Optional<Long> parseLong(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private String initials(String value) {
        if (value == null || value.isBlank()) {
            return "?";
        }
        String[] parts = value.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }

    private String formatAmount(BigDecimal amount) {
        return amount == null ? null : CURRENCY_FORMAT.format(amount);
    }

    private String formatAmountOrDash(BigDecimal amount) {
        return amount == null ? "-" : CURRENCY_FORMAT.format(amount);
    }

    private String formatProbability(Integer probability) {
        return probability == null ? null : probability + "%";
    }

    private String formatProbabilityOrDash(Integer probability) {
        return probability == null ? "-" : probability + "%";
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String productTypeName(OptionValue productType, String fallbackCode) {
        return productType == null ? valueOrDash(fallbackCode) : productType.getName();
    }

    private String normalizeColor(String color) {
        return color == null || color.isBlank() ? "default" : color.toLowerCase().replace('_', '-');
    }

    private void showSuccess(String message) {
        Notification notification = Notification.show(message, 2500, Notification.Position.BOTTOM_END);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void showError(String message) {
        Notification notification = Notification.show(message, 3500, Notification.Position.BOTTOM_END);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
