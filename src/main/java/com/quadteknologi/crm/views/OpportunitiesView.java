package com.quadteknologi.crm.views;

import com.quadteknologi.crm.domain.entity.Company;
import com.quadteknologi.crm.domain.entity.Country;
import com.quadteknologi.crm.domain.entity.Lead;
import com.quadteknologi.crm.domain.entity.Opportunity;
import com.quadteknologi.crm.domain.entity.OpportunityItem;
import com.quadteknologi.crm.domain.entity.OptionValue;
import com.quadteknologi.crm.domain.entity.Person;
import com.quadteknologi.crm.domain.entity.Region;
import com.quadteknologi.crm.domain.entity.User;
import com.quadteknologi.crm.security.AppViewAccess;
import com.quadteknologi.crm.security.ViewAccessService;
import com.quadteknologi.crm.service.ContactService;
import com.quadteknologi.crm.service.OpportunityService;
import com.quadteknologi.crm.ui.component.ActivityTimeline;
import com.quadteknologi.crm.ui.component.CurrencyField;
import com.quadteknologi.crm.ui.component.KanbanBoard;
import com.quadteknologi.crm.ui.layout.MainLayout;
import com.vaadin.componentfactory.addons.inputmask.InputMask;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.masterdetaillayout.MasterDetailLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.renderer.LitRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.quadteknologi.crm.ui.util.CurrencyFormatter.formatRupiah;

@PermitAll
@PageTitle("Opportunities | Quad CRM")
@Route(value = "opportunities", layout = MainLayout.class)
public class OpportunitiesView extends VerticalLayout implements BeforeEnterObserver {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final OpportunityService opportunityService;
    private final ContactService contactService;
    private final ViewAccessService viewAccessService;
    private KanbanBoard<Opportunity> kanbanBoard;
    private Component activeContent;
    private MasterDetailLayout gridLayout;
    private List<OptionValue> pipelineStatuses = List.of();
    private Map<String, List<Opportunity>> pipelineOpportunitiesByStatus = Map.of();
    private boolean pipelineDataLoaded;
    private ViewMode viewMode = ViewMode.KANBAN;
    private String searchTerm = "";

    private enum ViewMode {
        KANBAN,
        GRID
    }

    public OpportunitiesView(
            OpportunityService opportunityService,
            ContactService contactService,
            ViewAccessService viewAccessService) {
        this.opportunityService = opportunityService;
        this.contactService = contactService;
        this.viewAccessService = viewAccessService;

        addClassName("page-view");
        setPadding(false);
        setSpacing(false);
        setSizeFull();

        activeContent = createPipelineContent();
        add(createHeader(), activeContent);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!viewAccessService.canAccess(AppViewAccess.OPPORTUNITIES)) {
            viewAccessService.checkBeforeEnter(event, AppViewAccess.OPPORTUNITIES);
            return;
        }

        event.getLocation()
                .getQueryParameters()
                .getSingleParameter("opportunity")
                .flatMap(this::parseUuid)
                .ifPresent(opportunityPublicId -> {
                    try {
                        Opportunity opportunity = opportunityService.findOpportunity(opportunityPublicId);
                        setDetail(createOpportunityDetail(opportunity));
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
        actions.add(createViewToggle(), createSearchField("Search opportunities"), createOpportunityButton);

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

    private Component createViewToggle() {
        Div toggle = new Div();
        toggle.addClassName("pipeline-view-toggle");

        Button kanbanButton = new Button("Kanban");
        Button gridButton = new Button("Grid");
        kanbanButton.addClassName("pipeline-view-toggle-button");
        gridButton.addClassName("pipeline-view-toggle-button");
        kanbanButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        gridButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        kanbanButton.addClickListener(event -> switchViewMode(ViewMode.KANBAN, kanbanButton, gridButton));
        gridButton.addClickListener(event -> switchViewMode(ViewMode.GRID, kanbanButton, gridButton));
        updateViewToggleButtons(kanbanButton, gridButton);

        toggle.add(kanbanButton, gridButton);
        return toggle;
    }

    private void switchViewMode(ViewMode nextMode, Button kanbanButton, Button gridButton) {
        if (viewMode == nextMode) {
            return;
        }
        viewMode = nextMode;
        updateViewToggleButtons(kanbanButton, gridButton);
        refreshKanbanBoard();
    }

    private void updateViewToggleButtons(Button kanbanButton, Button gridButton) {
        updateViewToggleButton(kanbanButton, viewMode == ViewMode.KANBAN);
        updateViewToggleButton(gridButton, viewMode == ViewMode.GRID);
    }

    private void updateViewToggleButton(Button button, boolean active) {
        button.getElement().setAttribute("aria-pressed", String.valueOf(active));
        if (active) {
            button.addClassName("is-active");
        } else {
            button.removeClassName("is-active");
        }
    }

    private Component createPipelineContent() {
        return viewMode == ViewMode.KANBAN ? createKanbanBoard() : createGridView();
    }

    private Component createKanbanBoard() {
        ensurePipelineDataLoaded();
        List<OptionValue> statuses = pipelineStatuses;
        Map<String, List<Opportunity>> opportunitiesByStatus =
                filterOpportunitiesBySearch(pipelineOpportunitiesByStatus);

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

    private Component createGridView() {
        kanbanBoard = null;
        gridLayout = new MasterDetailLayout();
        gridLayout.addClassName("kanban-master-detail");
        gridLayout.setSizeFull();
        gridLayout.setMasterMinSize("720px");
        gridLayout.setDetailSize("360px");
        gridLayout.setOverlayMode(MasterDetailLayout.OverlayMode.DRAWER);
        gridLayout.setForceOverlay(true);
        gridLayout.addBackdropClickListener(event -> clearDetail());
        gridLayout.addDetailEscapePressListener(event -> clearDetail());

        Grid<Opportunity> grid = new Grid<>(Opportunity.class, false);
        grid.addClassName("pipeline-grid");
        grid.setSizeFull();
        grid.addColumn(opportunityIdentityRenderer())
                .setHeader("Opportunity")
                .setAutoWidth(true)
                .setFlexGrow(2)
                .setSortable(true)
                .setComparator(Comparator.comparing(
                        opportunity -> sortText(opportunity.getTitle()),
                        String.CASE_INSENSITIVE_ORDER));
        grid.addColumn(opportunityStageRenderer())
                .setHeader("Stage")
                .setAutoWidth(true)
                .setFlexGrow(0)
                .setSortable(true)
                .setComparator(Comparator.comparing(
                        this::opportunityStageName,
                        String.CASE_INSENSITIVE_ORDER));
        grid.addColumn(opportunityValueRenderer())
                .setHeader("Value")
                .setAutoWidth(true)
                .setFlexGrow(1)
                .setSortable(true)
                .setComparator(Comparator.comparing(
                        opportunity -> amountOrZero(opportunity.getEstimatedAmount())));
        grid.addColumn(opportunityForecastRenderer())
                .setHeader("Forecast")
                .setAutoWidth(true)
                .setFlexGrow(1)
                .setSortable(true)
                .setComparator(Comparator
                        .comparing((Opportunity opportunity) -> opportunity.getExpectedCloseDate(),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(opportunity -> probabilityOrZero(opportunity.getProbability())));
        grid.addColumn(opportunityOwnerRenderer())
                .setHeader("Owner")
                .setAutoWidth(true)
                .setFlexGrow(1)
                .setSortable(true)
                .setComparator(Comparator.comparing(
                        opportunity -> sortText(opportunity.getAssignedTo() == null
                                ? null
                                : opportunity.getAssignedTo().getFullName()),
                        String.CASE_INSENSITIVE_ORDER));
        grid.addColumn(opportunityOpenRenderer()).setHeader("").setAutoWidth(true).setFlexGrow(0);
        grid.setItems(currentOpportunities());
        grid.asSingleSelect().addValueChangeListener(event -> {
            if (event.getValue() != null) {
                setDetail(createOpportunityDetail(opportunityService.findOpportunity(event.getValue().getId())));
            }
        });

        gridLayout.setMaster(grid);
        return gridLayout;
    }

    private LitRenderer<Opportunity> opportunityIdentityRenderer() {
        return LitRenderer.<Opportunity>of("""
                <div class="pipeline-grid-stack-cell">
                  <span class="pipeline-grid-primary">${item.title}</span>
                  <span class="pipeline-grid-secondary">${item.meta}</span>
                </div>
                """)
                .withProperty("title", opportunity -> valueOrDash(opportunity.getTitle()))
                .withProperty("meta", opportunity -> displayCompanyName(opportunity) + " | "
                        + displayPrimaryName(opportunity));
    }

    private LitRenderer<Opportunity> opportunityStageRenderer() {
        return LitRenderer.<Opportunity>of("""
                <span class="pipeline-grid-status-badge ${item.colorClass}">${item.stage}</span>
                """)
                .withProperty("stage", this::opportunityStageName)
                .withProperty("colorClass", opportunity -> "kanban-color-" + normalizeColor(
                        Optional.ofNullable(opportunity.getStatus()).map(OptionValue::getColor).orElse(null)));
    }

    private LitRenderer<Opportunity> opportunityValueRenderer() {
        return LitRenderer.<Opportunity>of("""
                <div class="pipeline-grid-stack-cell">
                  <span class="pipeline-grid-primary">${item.amount}</span>
                  <span class="pipeline-grid-secondary">Margin ${item.margin}</span>
                </div>
                """)
                .withProperty("amount", opportunity -> formatAmountOrDash(opportunity.getEstimatedAmount()))
                .withProperty("margin", opportunity -> formatAmountOrDash(opportunity.getMargin()));
    }

    private LitRenderer<Opportunity> opportunityForecastRenderer() {
        return LitRenderer.<Opportunity>of("""
                <div class="pipeline-grid-stack-cell">
                  <span class="pipeline-grid-primary">${item.probability}</span>
                  <span class="pipeline-grid-secondary">${item.closeDate}</span>
                </div>
                """)
                .withProperty("probability", opportunity -> formatProbabilityOrDash(opportunity.getProbability()))
                .withProperty("closeDate", opportunity -> opportunity.getExpectedCloseDate() == null
                        ? "No close date"
                        : "Close " + opportunity.getExpectedCloseDate().format(DATE_FORMAT));
    }

    private LitRenderer<Opportunity> opportunityOwnerRenderer() {
        return LitRenderer.<Opportunity>of("""
                <div class="pipeline-grid-stack-cell">
                  <span class="pipeline-grid-primary">${item.owner}</span>
                  <span class="pipeline-grid-secondary">${item.lead}</span>
                </div>
                """)
                .withProperty("owner", opportunity -> opportunity.getAssignedTo() == null
                        ? "Unassigned"
                        : opportunity.getAssignedTo().getFullName())
                .withProperty("lead", opportunity -> opportunity.getLead() == null
                        ? "Direct opportunity"
                        : "Lead: " + opportunity.getLead().getTitle());
    }

    private LitRenderer<Opportunity> opportunityOpenRenderer() {
        return LitRenderer.<Opportunity>of("""
                <button class="pipeline-grid-open-action" @click="${openOpportunity}">Open</button>
                """)
                .withFunction("openOpportunity", opportunity -> setDetail(
                        createOpportunityDetail(opportunityService.findOpportunity(opportunity.getId()))));
    }

    private Component createOpportunityGridIdentity(Opportunity opportunity) {
        Div cell = new Div();
        cell.addClassName("pipeline-grid-stack-cell");

        Span title = new Span(opportunity.getTitle());
        title.addClassName("pipeline-grid-primary");
        Span meta = new Span(displayCompanyName(opportunity) + " | " + displayPrimaryName(opportunity));
        meta.addClassName("pipeline-grid-secondary");

        cell.add(title, meta);
        return cell;
    }

    private Component createOpportunityStageBadge(Opportunity opportunity) {
        Span badge = new Span(Optional.ofNullable(opportunity.getStatus())
                .map(OptionValue::getName)
                .orElse(opportunity.getStatusCode()));
        badge.addClassNames("pipeline-grid-status-badge", "kanban-color-" + normalizeColor(
                Optional.ofNullable(opportunity.getStatus()).map(OptionValue::getColor).orElse(null)));
        return badge;
    }

    private Component createOpportunityGridValue(Opportunity opportunity) {
        Div cell = new Div();
        cell.addClassName("pipeline-grid-stack-cell");

        Span amount = new Span(formatAmountOrDash(opportunity.getEstimatedAmount()));
        amount.addClassName("pipeline-grid-primary");
        Span margin = new Span("Margin " + formatAmountOrDash(opportunity.getMargin()));
        margin.addClassName("pipeline-grid-secondary");

        cell.add(amount, margin);
        return cell;
    }

    private Component createOpportunityGridForecast(Opportunity opportunity) {
        Div cell = new Div();
        cell.addClassName("pipeline-grid-stack-cell");

        Span probability = new Span(formatProbabilityOrDash(opportunity.getProbability()));
        probability.addClassName("pipeline-grid-primary");
        Span closeDate = new Span(opportunity.getExpectedCloseDate() == null
                ? "No close date"
                : "Close " + opportunity.getExpectedCloseDate().format(DATE_FORMAT));
        closeDate.addClassName("pipeline-grid-secondary");

        cell.add(probability, closeDate);
        return cell;
    }

    private Component createOpportunityGridOwner(Opportunity opportunity) {
        Div cell = new Div();
        cell.addClassName("pipeline-grid-stack-cell");

        Span owner = new Span(opportunity.getAssignedTo() == null
                ? "Unassigned"
                : opportunity.getAssignedTo().getFullName());
        owner.addClassName("pipeline-grid-primary");
        Span lead = new Span(opportunity.getLead() == null ? "Direct opportunity" : "Lead: " + opportunity.getLead().getTitle());
        lead.addClassName("pipeline-grid-secondary");

        cell.add(owner, lead);
        return cell;
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

        Button createCompanyButton = new Button(VaadinIcon.PLUS.create(),
                event -> openQuickCreateCompany(company, companies));
        createCompanyButton.addClassName("pipeline-inline-create-button");
        createCompanyButton.getElement().setAttribute("aria-label", "Create organization");

        Button createPersonButton = new Button(VaadinIcon.PLUS.create(),
                event -> openQuickCreatePerson(person, company, persons, companies));
        createPersonButton.addClassName("pipeline-inline-create-button");
        createPersonButton.getElement().setAttribute("aria-label", "Create person");

        HorizontalLayout companyRow = fieldActionRow(company, createCompanyButton);
        HorizontalLayout personRow = fieldActionRow(person, createPersonButton);

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

        CurrencyField estimatedAmount = new CurrencyField("Estimated Amount");
        CurrencyField margin = new CurrencyField("Margin");

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
        TextField soNumber = new TextField("SO Number");
        TextField contractPoNumber = new TextField("Contract / PO Number");
        Component wonDocumentsDivider = sectionDivider("Won Documents");
        Component itemsEditor = createOpportunityItemsEditor(request.getItems(), productTypes);

        Runnable updateWonDocumentVisibility = () -> {
            boolean visible = isWonStatus(status.getValue())
                    || !valueOrEmpty(soNumber.getValue()).isBlank()
                    || !valueOrEmpty(contractPoNumber.getValue()).isBlank();
            wonDocumentsDivider.setVisible(visible);
            soNumber.setVisible(visible);
            contractPoNumber.setVisible(visible);
        };
        status.addValueChangeListener(event -> updateWonDocumentVisibility.run());

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

        binder.forField(margin)
                .asRequired("Margin is required")
                .bind(OpportunityService.OpportunityRequest::getMargin, OpportunityService.OpportunityRequest::setMargin);

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

        binder.forField(soNumber)
                .withValidator(
                        value -> !isWonStatus(status.getValue()) || !valueOrEmpty(value).isBlank(),
                        "SO Number is required when opportunity is Won")
                .bind(OpportunityService.OpportunityRequest::getSoNumber,
                        OpportunityService.OpportunityRequest::setSoNumber);

        binder.forField(contractPoNumber)
                .withValidator(
                        value -> !isWonStatus(status.getValue()) || !valueOrEmpty(value).isBlank(),
                        "Contract / PO Number is required when opportunity is Won")
                .bind(OpportunityService.OpportunityRequest::getContractPoNumber,
                        OpportunityService.OpportunityRequest::setContractPoNumber);

        binder.readBean(request);
        updateWonDocumentVisibility.run();

        Div formBody = new Div();
        formBody.addClassName("pipeline-form-body");
        formBody.add(
                sectionDivider("Opportunity"),
                title,
                status,
                lead,
                sectionDivider("Contact"),
                companyRow,
                personRow,
                sectionDivider("Forecast"),
                estimatedAmount,
                margin,
                probability,
                expectedClose,
                assignedTo,
                wonDocumentsDivider,
                soNumber,
                contractPoNumber,
                sectionDivider("Items"),
                itemsEditor,
                sectionDivider("Details"),
                description,
                notes);

        detail.add(formBody, createOpportunityFormActions(opportunity, request, binder));
        setDetail(detail);
    }

    private OpportunityService.OpportunityRequest createRequestFromOpportunity(Opportunity opportunity) {
        OpportunityService.OpportunityRequest request = new OpportunityService.OpportunityRequest();
        request.setTitle(opportunity.getTitle());
        request.setLead(opportunity.getLead());
        request.setCompany(opportunity.getCompany());
        request.setPerson(opportunity.getPerson());
        request.setEstimatedAmount(opportunity.getEstimatedAmount());
        request.setMargin(opportunity.getMargin());
        request.setProbability(opportunity.getProbability());
        request.setExpectedCloseDate(opportunity.getExpectedCloseDate());
        request.setAssignedTo(opportunity.getAssignedTo());
        request.setDescription(opportunity.getDescription());
        request.setNotes(opportunity.getNotes());
        request.setSoNumber(opportunity.getSoNumber());
        request.setContractPoNumber(opportunity.getContractPoNumber());
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

        CurrencyField unitPrice = new CurrencyField("Unit Price");
        unitPrice.setValue(item.getUnitPrice());
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

    private HorizontalLayout fieldActionRow(Component field, Button action) {
        HorizontalLayout row = new HorizontalLayout(field, action);
        row.addClassName("pipeline-combo-action-row");
        row.setPadding(false);
        row.setSpacing(true);
        row.setWidthFull();
        row.setFlexGrow(1, field);
        return row;
    }

    private void openQuickCreateCompany(ComboBox<Company> companyField, List<Company> companies) {
        Company company = new Company();
        Binder<Company> binder = new Binder<>(Company.class);
        Dialog dialog = quickCreateDialog("Create Organization");

        TextField name = new TextField("Name");
        TextField industry = new TextField("Industry");
        TextField website = new TextField("Website");
        EmailField email = new EmailField("Email");
        TextField phone = phoneField("Phone");

        new InputMask("+000000000000000").extend(phone);

        ComboBox<Country> country = new ComboBox<>("Country");
        ComboBox<Region> province = new ComboBox<>("Province");
        ComboBox<Region> city = new ComboBox<>("City / Regency");
        TextArea address = new TextArea("Address");
        address.setMinHeight("90px");

        boolean[] loadingForm = {true};
        List<Country> countries = contactService.findActiveCountries();
        country.setItems(countries);
        country.setItemLabelGenerator(Country::getName);
        country.setClearButtonVisible(true);

        province.setItemLabelGenerator(Region::getName);
        province.setClearButtonVisible(true);
        province.setEnabled(false);

        city.setItemLabelGenerator(Region::getName);
        city.setClearButtonVisible(true);
        city.setEnabled(false);

        if (countries.size() == 1) {
            company.setCountry(countries.get(0));
            List<Region> provinces = contactService.findActiveProvinces(countries.get(0));
            province.setItems(provinces);
            province.setEnabled(!provinces.isEmpty());
        }

        country.addValueChangeListener(event -> {
            if (loadingForm[0]) {
                return;
            }
            Country selected = event.getValue();
            province.clear();
            city.clear();
            city.setItems(List.of());
            city.setEnabled(false);

            List<Region> provinces = selected == null ? List.of() : contactService.findActiveProvinces(selected);
            province.setItems(provinces);
            province.setEnabled(!provinces.isEmpty());
        });

        province.addValueChangeListener(event -> {
            if (loadingForm[0]) {
                return;
            }
            Region selected = event.getValue();
            city.clear();

            List<Region> cities = selected == null ? List.of() : contactService.findActiveCities(selected);
            city.setItems(cities);
            city.setEnabled(!cities.isEmpty());
        });

        binder.forField(name)
                .asRequired("Name is required")
                .bind(Company::getName, Company::setName);

        binder.forField(industry)
                .asRequired("Industry is required")
                .bind(Company::getIndustry, Company::setIndustry);

        binder.forField(website)
                .bind(Company::getWebsite, Company::setWebsite);

        binder.forField(email)
                .asRequired("Email is required")
                .bind(Company::getEmail, Company::setEmail);

        binder.forField(phone)
                .asRequired("Phone is required")
                .bind(Company::getPhone, Company::setPhone);

        binder.forField(country)
                .asRequired("Country is required")
                .bind(Company::getCountry, Company::setCountry);

        binder.forField(province)
                .asRequired("Province is required")
                .bind(Company::getProvince, Company::setProvince);

        binder.forField(city)
                .asRequired("City / Regency is required")
                .bind(Company::getCity, Company::setCity);

        binder.forField(address)
                .asRequired("Address is required")
                .bind(Company::getAddress, Company::setAddress);

        binder.readBean(company);
        loadingForm[0] = false;

        Button save = new Button("Save", VaadinIcon.CHECK.create(), event -> {
            try {
                binder.writeBean(company);
                Company savedCompany = contactService.saveCompany(company);
                companies.removeIf(item -> Objects.equals(item.getId(), savedCompany.getId()));
                companies.add(savedCompany);
                companies.sort((left, right) -> left.getName().compareToIgnoreCase(right.getName()));
                companyField.setItems(companies);
                companyField.setValue(savedCompany);
                dialog.close();
                showSuccess("Organization created");
            } catch (ValidationException exception) {
                showError("Please complete required fields");
            } catch (IllegalArgumentException exception) {
                showError(exception.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(
                quickCreateBody(name, industry, website, email, phone, country, province, city, address),
                dialogActions(save, dialog));
        dialog.open();
    }

    private void openQuickCreatePerson(ComboBox<Person> personField, ComboBox<Company> companyField,
            List<Person> persons, List<Company> companies) {
        Person person = new Person();
        person.setCompany(companyField.getValue());

        Binder<Person> binder = new Binder<>(Person.class);
        Dialog dialog = quickCreateDialog("Create Person");

        TextField fullName = new TextField("Full Name");
        ComboBox<Company> company = new ComboBox<>("Organization");
        company.setItems(companies);
        company.setItemLabelGenerator(Company::getName);
        company.setClearButtonVisible(true);
        TextField jobTitle = new TextField("Job Title");
        EmailField email = new EmailField("Email");
        TextField phone = phoneField("Phone");

        TextField whatsapp = phoneField("WhatsApp");
        binder.forField(fullName)
                .asRequired("Full name is required")
                .bind(Person::getFullName, Person::setFullName);

        binder.forField(company)
                .asRequired("Company name is required")
                .bind(Person::getCompany, Person::setCompany);

        binder.forField(jobTitle)
                .asRequired("Job title is required")
                .bind(Person::getJobTitle, Person::setJobTitle);

        binder.forField(email)
                .asRequired("Email is required")
                .bind(Person::getEmail, Person::setEmail);

        binder.forField(phone)
                .asRequired("Phone is required")
                .bind(Person::getPhone, Person::setPhone);

        binder.bind(whatsapp, Person::getWhatsapp, Person::setWhatsapp);

        binder.readBean(person);

        Button save = new Button("Save", VaadinIcon.CHECK.create(), event -> {
            try {
                binder.writeBean(person);
                Person savedPerson = contactService.savePerson(person);
                persons.removeIf(item -> Objects.equals(item.getId(), savedPerson.getId()));
                persons.add(savedPerson);
                persons.sort((left, right) -> left.getFullName().compareToIgnoreCase(right.getFullName()));
                personField.setItems(persons);
                if (savedPerson.getCompany() != null) {
                    companyField.setValue(savedPerson.getCompany());
                }
                personField.setValue(savedPerson);
                dialog.close();
                showSuccess("Person created");
            } catch (ValidationException exception) {
                showError("Please complete required fields");
            } catch (IllegalArgumentException exception) {
                showError(exception.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(
                quickCreateBody(fullName, company, jobTitle, email, phone, whatsapp),
                dialogActions(save, dialog));
        dialog.open();
    }

    private Dialog quickCreateDialog(String titleText) {
        Dialog dialog = new Dialog();
        dialog.addClassName("quick-create-dialog");
        dialog.setCloseOnEsc(true);
        dialog.setCloseOnOutsideClick(false);

        Div header = new Div();
        header.addClassName("quick-create-header");
        H3 title = new H3(titleText);
        Button close = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> dialog.close());
        close.addClassName("pipeline-detail-close");
        close.getElement().setAttribute("aria-label", "Close " + titleText);
        header.add(title, close);

        dialog.add(header);
        return dialog;
    }

    private Component quickCreateBody(Component... fields) {
        Div body = new Div();
        body.addClassName("quick-create-body");
        body.add(fields);
        return body;
    }

    private Component dialogActions(Button save, Dialog dialog) {
        Button cancel = new Button("Cancel", event -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout actions = new HorizontalLayout(save, cancel);
        actions.addClassNames("pipeline-form-actions", "quick-create-footer");
        actions.setPadding(false);
        actions.setSpacing(true);
        return actions;
    }

    private TextField phoneField(String label) {
        TextField field = new TextField(label);
        field.setClearButtonVisible(true);
        field.setPlaceholder("+6281234567890");
        new InputMask("+000000000000000").extend(field);
        field.getElement().setAttribute("inputmode", "tel");
        field.getElement().setAttribute("autocomplete", "tel");
        return field;
    }

    private Div createOpportunityFormShell(String titleText, String subtitleText, boolean editing) {
        Div detail = new Div();
        detail.addClassName("pipeline-form-detail");
        setDetailSize("430px");

        Div header = new Div();
        header.addClassName("pipeline-detail-header");

        Div titleGroup = new Div();
        H3 title = new H3(titleText);
        Paragraph subtitle = new Paragraph(subtitleText);
        subtitle.addClassName("pipeline-form-subtitle");
        titleGroup.add(title, subtitle);

        Button close = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> clearDetail());
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
                refreshPipelineDataAndBoard();
                showSuccess(editing ? "Opportunity updated" : "Opportunity created");
                setDetail(createOpportunityDetail(savedOpportunity));
            } catch (ValidationException exception) {
                showError("Please complete required fields");
            } catch (IllegalArgumentException exception) {
                showError(exception.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancel = new Button("Cancel", event -> clearDetail());
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
        if (activeContent != null) {
            remove(activeContent);
        }
        activeContent = createPipelineContent();
        add(activeContent);
    }

    private void refreshPipelineDataAndBoard() {
        pipelineDataLoaded = false;
        refreshKanbanBoard();
    }

    private void ensurePipelineDataLoaded() {
        if (pipelineDataLoaded) {
            return;
        }
        pipelineStatuses = opportunityService.findPipelineStatuses();
        pipelineOpportunitiesByStatus = opportunityService.findPipelineOpportunitiesByStatus();
        pipelineDataLoaded = true;
    }

    private List<Opportunity> currentOpportunities() {
        ensurePipelineDataLoaded();
        Map<String, List<Opportunity>> opportunitiesByStatus =
                filterOpportunitiesBySearch(pipelineOpportunitiesByStatus);
        return pipelineStatuses
                .stream()
                .flatMap(status -> opportunitiesByStatus.getOrDefault(status.getCode(), List.of()).stream())
                .toList();
    }

    private void setDetail(Component detail) {
        if (viewMode == ViewMode.KANBAN && kanbanBoard != null) {
            kanbanBoard.setDetail(detail);
            return;
        }
        if (gridLayout == null) {
            return;
        }
        gridLayout.setDetail(detail);
    }

    private void clearDetail() {
        setDetail(null);
    }

    private void setDetailSize(String size) {
        if (viewMode == ViewMode.KANBAN && kanbanBoard != null) {
            kanbanBoard.setDetailSize(size);
        }
        if (gridLayout != null) {
            gridLayout.setDetailSize(size);
        }
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
                || containsSearch(formatAmount(opportunity.getMargin()), keyword)
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
        setDetailSize("360px");

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

        Button close = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> clearDetail());
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
                detailRow("Margin", formatAmountOrDash(opportunity.getMargin())),
                detailRow("Probability", formatProbabilityOrDash(opportunity.getProbability())),
                detailRow("Expected close", opportunity.getExpectedCloseDate() == null ? "-" : opportunity.getExpectedCloseDate().format(DATE_FORMAT)),
                detailRow("SO Number", opportunity.getSoNumber()),
                detailRow("Contract / PO Number", opportunity.getContractPoNumber()),
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

            if (requiresWonDocumentInput(opportunity, event.getValue())) {
                openWonDocumentsDialog(opportunity, event.getValue(), status);
                return;
            }

            moveOpportunityStatus(opportunity, event.getValue(), null, null);
        });

        return status;
    }

    private void openWonDocumentsDialog(
            Opportunity opportunity,
            OptionValue nextStatus,
            ComboBox<OptionValue> statusControl) {
        Dialog dialog = quickCreateDialog("Complete Won Details");
        boolean[] saved = {false};

        TextField soNumber = new TextField("SO Number");
        soNumber.setValue(valueOrEmpty(opportunity.getSoNumber()));
        soNumber.setClearButtonVisible(true);

        TextField contractPoNumber = new TextField("Contract / PO Number");
        contractPoNumber.setValue(valueOrEmpty(opportunity.getContractPoNumber()));
        contractPoNumber.setClearButtonVisible(true);

        Button save = new Button("Save", VaadinIcon.CHECK.create(), event -> {
            if (soNumber.getValue() == null || soNumber.getValue().isBlank()) {
                showError("SO Number is required");
                return;
            }
            if (contractPoNumber.getValue() == null || contractPoNumber.getValue().isBlank()) {
                showError("Contract / PO Number is required");
                return;
            }

            saved[0] = true;
            dialog.close();
            moveOpportunityStatus(opportunity, nextStatus, soNumber.getValue(), contractPoNumber.getValue());
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.addOpenedChangeListener(event -> {
            if (!event.isOpened() && !saved[0]) {
                statusControl.setValue(findStatusByCode(opportunityService.findPipelineStatuses(), opportunity.getStatusCode()));
            }
        });

        dialog.add(
                quickCreateBody(soNumber, contractPoNumber),
                dialogActions(save, dialog));
        dialog.open();
    }

    private void moveOpportunityStatus(
            Opportunity opportunity,
            OptionValue nextStatus,
            String soNumber,
            String contractPoNumber) {
        try {
            Opportunity updatedOpportunity = opportunityService.updateOpportunityStatus(
                    opportunity.getId(), nextStatus, soNumber, contractPoNumber);
            refreshPipelineDataAndBoard();
            showSuccess("Opportunity moved to " + nextStatus.getName());
            setDetail(createOpportunityDetail(updatedOpportunity));
        } catch (IllegalArgumentException exception) {
            showError(exception.getMessage());
            setDetail(createOpportunityDetail(opportunityService.findOpportunity(opportunity.getId())));
        }
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

    private String opportunityStageName(Opportunity opportunity) {
        return opportunity.getStatus() == null ? valueOrDash(opportunity.getStatusCode()) : opportunity.getStatus().getName();
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

    private boolean requiresWonDocumentInput(Opportunity opportunity, OptionValue nextStatus) {
        return Objects.equals("NEGOTIATION", opportunity.getStatusCode()) && isWonStatus(nextStatus);
    }

    private boolean isWonStatus(OptionValue status) {
        return status != null && Objects.equals("WON", status.getCode());
    }

    private Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
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
        return amount == null ? null : formatRupiah(amount);
    }

    private String formatAmountOrDash(BigDecimal amount) {
        return formatRupiah(amount);
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

    private String sortText(String value) {
        return value == null ? "" : value.trim();
    }

    private BigDecimal amountOrZero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private int probabilityOrZero(Integer probability) {
        return probability == null ? 0 : probability;
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
