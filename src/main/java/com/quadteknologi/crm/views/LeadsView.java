package com.quadteknologi.crm.views;

import com.quadteknologi.crm.domain.entity.Lead;
import com.quadteknologi.crm.domain.entity.LeadItem;
import com.quadteknologi.crm.domain.entity.Opportunity;
import com.quadteknologi.crm.domain.entity.OptionValue;
import com.quadteknologi.crm.domain.entity.Company;
import com.quadteknologi.crm.domain.entity.Country;
import com.quadteknologi.crm.domain.entity.Person;
import com.quadteknologi.crm.domain.entity.Region;
import com.quadteknologi.crm.domain.entity.User;
import com.quadteknologi.crm.security.AppViewAccess;
import com.quadteknologi.crm.security.ViewAccessService;
import com.quadteknologi.crm.service.ContactService;
import com.quadteknologi.crm.service.LeadService;
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
import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.QueryParameters;
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
@PageTitle("Leads | Quad CRM")
@Route(value = "leads", layout = MainLayout.class)
public class LeadsView extends VerticalLayout implements BeforeEnterObserver {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final LeadService leadService;
    private final ContactService contactService;
    private final ViewAccessService viewAccessService;
    private KanbanBoard<Lead> kanbanBoard;
    private Component activeContent;
    private MasterDetailLayout gridLayout;
    private List<OptionValue> pipelineStatuses = List.of();
    private Map<String, List<Lead>> pipelineLeadsByStatus = Map.of();
    private boolean pipelineDataLoaded;
    private ViewMode viewMode = ViewMode.KANBAN;
    private String searchTerm = "";

    private enum ViewMode {
        KANBAN,
        GRID
    }

    public LeadsView(
            LeadService leadService,
            ContactService contactService,
            ViewAccessService viewAccessService) {
        this.leadService = leadService;
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
        viewAccessService.checkBeforeEnter(event, AppViewAccess.LEADS);
        event.getLocation()
                .getQueryParameters()
                .getSingleParameter("lead")
                .flatMap(this::parseUuid)
                .ifPresent(leadPublicId -> {
                    try {
                        Lead lead = leadService.findLead(leadPublicId);
                        setDetail(createLeadDetail(lead));
                    } catch (RuntimeException exception) {
                        showError("Lead was not found");
                    }
                });
    }

    private Component createHeader() {
        Header header = new Header();
        header.addClassName("pipeline-header");

        Div titleGroup = new Div();
        titleGroup.addClassName("pipeline-title-group");

        H2 title = new H2("Leads");
        Paragraph subtitle = new Paragraph("Track lead progress by status.");
        titleGroup.add(title, subtitle);

        Button createLeadButton = new Button("Create Lead", VaadinIcon.PLUS.create(),
                event -> openCreateLeadForm(leadService.getDefaultPipelineStatus()));
        createLeadButton.addClassName("pipeline-create-button");

        Div actions = new Div();
        actions.addClassName("pipeline-header-actions");
        actions.add(createViewToggle(), createSearchField("Search leads"), createLeadButton);

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
        Map<String, List<Lead>> leadsByStatus = filterLeadsBySearch(pipelineLeadsByStatus);

        kanbanBoard = new KanbanBoard<>(
                "lead",
                "Create Lead",
                "No leads yet",
                "Create a lead to fill this stage.",
                statuses,
                leadsByStatus,
                this::leadCardData,
                this::createLeadDetail,
                this::openCreateLeadForm);
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

        Grid<Lead> grid = new Grid<>(Lead.class, false);
        grid.addClassName("pipeline-grid");
        grid.setSizeFull();
        grid.addColumn(leadIdentityRenderer())
                .setHeader("Lead")
                .setAutoWidth(true)
                .setFlexGrow(2)
                .setSortable(true)
                .setComparator(Comparator.comparing(
                        lead -> sortText(lead.getTitle()),
                        String.CASE_INSENSITIVE_ORDER));
        grid.addColumn(leadStatusRenderer())
                .setHeader("Status")
                .setAutoWidth(true)
                .setFlexGrow(0)
                .setSortable(true)
                .setComparator(Comparator.comparing(
                        this::leadStatusName,
                        String.CASE_INSENSITIVE_ORDER));
        grid.addColumn(leadSourceRenderer())
                .setHeader("Source / Assigned")
                .setAutoWidth(true)
                .setFlexGrow(1)
                .setSortable(true)
                .setComparator(Comparator
                        .comparing((Lead lead) -> sortText(lead.getSource()), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(lead -> sortText(lead.getAssignedTo() == null
                                ? null
                                : lead.getAssignedTo().getFullName()), String.CASE_INSENSITIVE_ORDER));
        grid.addColumn(leadActivityRenderer())
                .setHeader("Activity")
                .setAutoWidth(true)
                .setFlexGrow(1)
                .setSortable(true)
                .setComparator(Comparator.comparing(
                        Lead::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        grid.addColumn(leadOpenRenderer()).setHeader("").setAutoWidth(true).setFlexGrow(0);
        grid.setItems(currentLeads());
        grid.asSingleSelect().addValueChangeListener(event -> {
            if (event.getValue() != null) {
                setDetail(createLeadDetail(leadService.findLead(event.getValue().getId())));
            }
        });

        gridLayout.setMaster(grid);
        return gridLayout;
    }

    private LitRenderer<Lead> leadIdentityRenderer() {
        return LitRenderer.<Lead>of("""
                <div class="pipeline-grid-stack-cell">
                  <span class="pipeline-grid-primary">${item.title}</span>
                  <span class="pipeline-grid-secondary">${item.meta}</span>
                </div>
                """)
                .withProperty("title", lead -> valueOrDash(lead.getTitle()))
                .withProperty("meta", lead -> displayPersonName(lead) + " | " + displayCompanyName(lead));
    }

    private LitRenderer<Lead> leadStatusRenderer() {
        return LitRenderer.<Lead>of("""
                <span class="pipeline-grid-status-badge ${item.colorClass}">${item.status}</span>
                """)
                .withProperty("status", this::leadStatusName)
                .withProperty("colorClass", lead -> "kanban-color-" + normalizeColor(
                        Optional.ofNullable(lead.getStatus()).map(OptionValue::getColor).orElse(null)));
    }

    private LitRenderer<Lead> leadSourceRenderer() {
        return LitRenderer.<Lead>of("""
                <div class="pipeline-grid-stack-cell">
                  <span class="pipeline-grid-primary">${item.source}</span>
                  <span class="pipeline-grid-secondary">${item.assigned}</span>
                </div>
                """)
                .withProperty("source", lead -> valueOrDash(lead.getSource()))
                .withProperty("assigned", lead -> lead.getAssignedTo() == null
                        ? "Unassigned"
                        : lead.getAssignedTo().getFullName());
    }

    private LitRenderer<Lead> leadActivityRenderer() {
        return LitRenderer.<Lead>of("""
                <div class="pipeline-grid-stack-cell">
                  <span class="pipeline-grid-primary">${item.conversion}</span>
                  <span class="pipeline-grid-secondary">${item.created}</span>
                </div>
                """)
                .withProperty("conversion", this::conversionLabel)
                .withProperty("created", lead -> lead.getCreatedAt() == null
                        ? "-"
                        : "Created " + lead.getCreatedAt().format(DATE_FORMAT));
    }

    private LitRenderer<Lead> leadOpenRenderer() {
        return LitRenderer.<Lead>of("""
                <button class="pipeline-grid-open-action" @click="${openLead}">Open</button>
                """)
                .withFunction("openLead", lead -> setDetail(createLeadDetail(leadService.findLead(lead.getId()))));
    }

    private Component createLeadGridIdentity(Lead lead) {
        Div cell = new Div();
        cell.addClassName("pipeline-grid-stack-cell");

        Span title = new Span(lead.getTitle());
        title.addClassName("pipeline-grid-primary");
        Span meta = new Span(displayPersonName(lead) + " | " + displayCompanyName(lead));
        meta.addClassName("pipeline-grid-secondary");

        cell.add(title, meta);
        return cell;
    }

    private Component createLeadStatusBadge(Lead lead) {
        Span badge = new Span(Optional.ofNullable(lead.getStatus()).map(OptionValue::getName).orElse(lead.getStatusCode()));
        badge.addClassNames("pipeline-grid-status-badge", "kanban-color-" + normalizeColor(
                Optional.ofNullable(lead.getStatus()).map(OptionValue::getColor).orElse(null)));
        return badge;
    }

    private Component createLeadGridSource(Lead lead) {
        Div cell = new Div();
        cell.addClassName("pipeline-grid-stack-cell");

        Span source = new Span(valueOrDash(lead.getSource()));
        source.addClassName("pipeline-grid-primary");
        Span assigned = new Span(lead.getAssignedTo() == null ? "Unassigned" : lead.getAssignedTo().getFullName());
        assigned.addClassName("pipeline-grid-secondary");

        cell.add(source, assigned);
        return cell;
    }

    private Component createLeadGridActivity(Lead lead) {
        Div cell = new Div();
        cell.addClassName("pipeline-grid-stack-cell");

        Span converted = new Span(conversionLabel(lead));
        converted.addClassName("pipeline-grid-primary");
        Span created = new Span(lead.getCreatedAt() == null ? "-" : "Created " + lead.getCreatedAt().format(DATE_FORMAT));
        created.addClassName("pipeline-grid-secondary");

        cell.add(converted, created);
        return cell;
    }

    private void openCreateLeadForm(OptionValue defaultStatus) {
        openLeadForm(null, defaultStatus);
    }

    private void openEditLeadForm(Lead lead) {
        openLeadForm(leadService.findLead(lead.getId()), null);
    }

    private void openLeadForm(Lead lead, OptionValue defaultStatus) {
        boolean editing = lead != null && lead.getId() != null;
        LeadService.CreateLeadRequest request = editing
                ? createRequestFromLead(lead)
                : new LeadService.CreateLeadRequest();

        Binder<LeadService.CreateLeadRequest> binder = new Binder<>(LeadService.CreateLeadRequest.class);
        Div detail = createLeadFormShell(
                editing ? "Edit Lead" : "Create Lead",
                editing ? "Update lead details and stage." : "Capture the lead and log its first activity.",
                editing);

        List<OptionValue> statuses = leadService.findPipelineStatuses();
        List<OptionValue> productTypes = leadService.findProductTypes();
        List<OptionValue> sourceTypes = leadService.findSourceTypes();
        List<Company> companies = new ArrayList<>(leadService.findCompanies());
        List<Person> persons = new ArrayList<>(leadService.findPersons());
        if (editing) {
            request.setStatus(findStatusByCode(statuses, lead.getStatusCode()));
        } else {
            request.setStatus(defaultStatus == null ? leadService.getDefaultPipelineStatus() : defaultStatus);
        }

        TextField title = new TextField("Lead Title");
        title.setPlaceholder("e.g. Website inquiry - ERP implementation");

        ComboBox<OptionValue> status = new ComboBox<>("Status");
        status.setItems(statuses);
        status.setItemLabelGenerator(OptionValue::getName);

        ComboBox<Company> company = new ComboBox<>("Organization");
        company.setItems(companies);
        company.setItemLabelGenerator(Company::getName);
        company.setClearButtonVisible(true);
        company.setWidthFull();

        ComboBox<Person> person = new ComboBox<>("Person");
        person.setItems(persons);
        person.setItemLabelGenerator(this::displayPersonOption);
        person.setClearButtonVisible(true);
        person.setWidthFull();

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

        ComboBox<OptionValue> source = new ComboBox<>("Source");
        source.setItems(sourceTypes);
        source.setItemLabelGenerator(OptionValue::getName);
        source.setClearButtonVisible(true);
        source.setPlaceholder("Select source");

        ComboBox<User> assignedTo = new ComboBox<>("Assigned To");
        assignedTo.setItems(leadService.findAssignableUsers());
        assignedTo.setItemLabelGenerator(User::getFullName);
        assignedTo.setClearButtonVisible(true);

        TextArea description = new TextArea("Description");
        description.setMinHeight("92px");
        TextArea notes = new TextArea("Internal Notes");
        notes.setMinHeight("92px");
        TextArea initialActivityNote = new TextArea(editing ? "Change Note" : "Initial Activity Note");
        initialActivityNote.setMinHeight("92px");
        Component itemsEditor = createLeadItemsEditor(request.getItems(), productTypes);

        binder.forField(title)
                .asRequired("Lead title is required")
                .bind(LeadService.CreateLeadRequest::getTitle, LeadService.CreateLeadRequest::setTitle);

        binder.forField(status)
                .asRequired("Status is required")
                .bind(LeadService.CreateLeadRequest::getStatus, LeadService.CreateLeadRequest::setStatus);

        binder.forField(company)
                .asRequired("Company is required")
                .bind(LeadService.CreateLeadRequest::getCompany, LeadService.CreateLeadRequest::setCompany);

        binder.forField(person)
                .asRequired("Person is required")
                .bind(LeadService.CreateLeadRequest::getPerson, LeadService.CreateLeadRequest::setPerson);

        binder.forField(source)
                .asRequired("Source is required")
                .bind(
                        value -> findSourceTypeByValue(sourceTypes, value.getSource()),
                        (value, selectedSource) -> value.setSource(
                                selectedSource == null ? null : selectedSource.getName()));

        binder.forField(assignedTo)
                .asRequired("Assigned To is required")
                .bind(LeadService.CreateLeadRequest::getAssignedTo, LeadService.CreateLeadRequest::setAssignedTo);

        binder.bind(description, LeadService.CreateLeadRequest::getDescription, LeadService.CreateLeadRequest::setDescription);
        binder.bind(notes, LeadService.CreateLeadRequest::getNotes, LeadService.CreateLeadRequest::setNotes);
        binder.bind(initialActivityNote, LeadService.CreateLeadRequest::getInitialActivityNote, LeadService.CreateLeadRequest::setInitialActivityNote);
        binder.readBean(request);

        Div formBody = new Div();
        formBody.addClassName("pipeline-form-body");
        formBody.add(
                sectionDivider("Lead"),
                title,
                status,
                source,
                assignedTo,
                sectionDivider("Contact"),
                companyRow,
                personRow,
                sectionDivider("Details"),
                description,
                notes,
                sectionDivider("Items"),
                itemsEditor,
                sectionDivider("Activity"),
                initialActivityNote);

        detail.add(formBody, createLeadFormActions(lead, request, binder));
        setDetail(detail);
    }

    private LeadService.CreateLeadRequest createRequestFromLead(Lead lead) {
        LeadService.CreateLeadRequest request = new LeadService.CreateLeadRequest();
        request.setTitle(lead.getTitle());
        request.setCompany(lead.getCompany());
        request.setPerson(lead.getPerson());
        request.setRawCompanyName(lead.getRawCompanyName());
        request.setRawPersonName(lead.getRawPersonName());
        request.setRawEmail(lead.getRawEmail());
        request.setRawPhone(lead.getRawPhone());
        request.setSource(lead.getSource());
        request.setAssignedTo(lead.getAssignedTo());
        request.setDescription(lead.getDescription());
        request.setNotes(lead.getNotes());
        request.setItems(leadService.findLeadItems(lead.getId())
                .stream()
                .map(this::createItemRequestFromLeadItem)
                .toList());
        return request;
    }

    private LeadService.LeadItemRequest createItemRequestFromLeadItem(LeadItem item) {
        LeadService.LeadItemRequest request = new LeadService.LeadItemRequest();
        request.setProductType(item.getProductType());
        request.setItemName(item.getItemName());
        request.setDescription(item.getDescription());
        request.setQuantity(item.getQuantity());
        request.setEstimatedUnitPrice(item.getEstimatedUnitPrice());
        request.setNotes(item.getNotes());
        return request;
    }

    private Component createLeadItemsEditor(List<LeadService.LeadItemRequest> items, List<OptionValue> productTypes) {
        Div editor = new Div();
        editor.addClassName("sales-items-editor");

        Div rows = new Div();
        rows.addClassName("sales-items-editor-rows");

        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            rows.removeAll();
            for (LeadService.LeadItemRequest item : items) {
                rows.add(createLeadItemRow(item, items, productTypes, refresh[0]));
            }
        };

        Button add = new Button("Add Item", VaadinIcon.PLUS.create(), event -> {
            items.add(new LeadService.LeadItemRequest());
            refresh[0].run();
        });
        add.addClassName("sales-items-add");

        refresh[0].run();
        editor.add(rows, add);
        return editor;
    }

    private Component createLeadItemRow(LeadService.LeadItemRequest item, List<LeadService.LeadItemRequest> items,
            List<OptionValue> productTypes, Runnable refresh) {
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

        CurrencyField unitPrice = new CurrencyField("Estimated Unit Price");
        unitPrice.setValue(item.getEstimatedUnitPrice());
        unitPrice.addValueChangeListener(event -> item.setEstimatedUnitPrice(event.getValue()));

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
        EmailField email = new EmailField("Email");
        TextField jobTitle = new TextField("Job Title");
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

    private Div createLeadFormShell(String titleText, String subtitleText, boolean editing) {
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
        close.getElement().setAttribute("aria-label", editing ? "Close edit lead form" : "Close create lead form");

        header.add(titleGroup, close);
        detail.add(header);
        return detail;
    }

    private Component sectionDivider(String label) {
        Div section = new Div();
        section.addClassName("pipeline-form-section");
        Span title = new Span(label);
        title.addClassName("pipeline-form-section-title");
        section.add(new Hr(), title);
        return section;
    }

    private Component createLeadFormActions(Lead lead, LeadService.CreateLeadRequest request,
            Binder<LeadService.CreateLeadRequest> binder) {
        boolean editing = lead != null && lead.getId() != null;
        Button save = new Button(editing ? "Save Changes" : "Save Lead", VaadinIcon.CHECK.create(), event -> {
            try {
                binder.writeBean(request);
                Lead savedLead = editing
                        ? leadService.updateLead(lead.getId(), request)
                        : leadService.createLead(request);
                refreshPipelineDataAndBoard();
                showSuccess(editing ? "Lead updated" : "Lead created");
                setDetail(createLeadDetail(savedLead));
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
        pipelineStatuses = leadService.findPipelineStatuses();
        pipelineLeadsByStatus = leadService.findPipelineLeadsByStatus();
        pipelineDataLoaded = true;
    }

    private List<Lead> currentLeads() {
        ensurePipelineDataLoaded();
        Map<String, List<Lead>> leadsByStatus = filterLeadsBySearch(pipelineLeadsByStatus);
        return pipelineStatuses
                .stream()
                .flatMap(status -> leadsByStatus.getOrDefault(status.getCode(), List.of()).stream())
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

    private Map<String, List<Lead>> filterLeadsBySearch(Map<String, List<Lead>> leadsByStatus) {
        String keyword = normalizeSearch(searchTerm);
        if (keyword.isBlank()) {
            return leadsByStatus;
        }

        Map<String, List<Lead>> filtered = new LinkedHashMap<>();
        leadsByStatus.forEach((status, leads) -> filtered.put(status,
                leads.stream()
                        .filter(lead -> matchesSearch(lead, keyword))
                        .toList()));
        return filtered;
    }

    private boolean matchesSearch(Lead lead, String keyword) {
        return containsSearch(lead.getTitle(), keyword)
                || containsSearch(displayPersonName(lead), keyword)
                || containsSearch(displayCompanyName(lead), keyword)
                || containsSearch(displayLeadEmail(lead), keyword)
                || containsSearch(displayLeadPhone(lead), keyword)
                || containsSearch(lead.getSource(), keyword)
                || containsSearch(lead.getStatusCode(), keyword)
                || containsSearch(Optional.ofNullable(lead.getStatus()).map(OptionValue::getName).orElse(null), keyword)
                || containsSearch(lead.getAssignedTo() == null ? null : lead.getAssignedTo().getFullName(), keyword)
                || containsSearch(conversionLabel(lead), keyword);
    }

    private boolean containsSearch(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String normalizeSearch(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private KanbanBoard.CardData leadCardData(Lead lead) {
        return new KanbanBoard.CardData(
                initials(displayPersonName(lead)),
                displayPersonName(lead),
                displayCompanyName(lead),
                lead.getTitle(),
                List.of(
                        new KanbanBoard.TagData(lead.getAssignedTo() == null ? "Unassigned" : lead.getAssignedTo().getFullName(), "user"),
                        new KanbanBoard.TagData(lead.getSource(), "source"),
                        new KanbanBoard.TagData(conversionLabel(lead), "converted"),
                        new KanbanBoard.TagData(displayLeadEmail(lead), "email")));
    }

    private Component createLeadDetail(Lead lead) {
        Opportunity opportunity = leadService.findOpportunityByLead(lead.getId());

        Div detail = new Div();
        detail.addClassName("pipeline-detail");
        setDetailSize("360px");

        Div detailHeader = new Div();
        detailHeader.addClassName("pipeline-detail-header");

        Div titleGroup = new Div();
        H3 title = new H3(lead.getTitle());
        Span status = new Span(Optional.ofNullable(lead.getStatus()).map(OptionValue::getName).orElse(lead.getStatusCode()));
        status.addClassNames("pipeline-detail-status", "kanban-color-" + normalizeColor(
                Optional.ofNullable(lead.getStatus()).map(OptionValue::getColor).orElse(null)));
        titleGroup.add(title, status);

        Button edit = new Button("Edit", VaadinIcon.EDIT.create(), event -> openEditLeadForm(lead));
        edit.addClassName("pipeline-detail-edit");

        Button close = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> clearDetail());
        close.addClassName("pipeline-detail-close");
        close.getElement().setAttribute("aria-label", "Close lead detail");

        HorizontalLayout headerActions = new HorizontalLayout(edit, close);
        headerActions.addClassName("pipeline-detail-actions");
        headerActions.setPadding(false);
        headerActions.setSpacing(true);

        detailHeader.add(titleGroup, headerActions);

        Div body = new Div();
        body.addClassName("pipeline-detail-body");
        body.add(
                createLeadActionControl(lead, opportunity),
                detailRow("Converted", conversionLabel(lead, opportunity)),
                opportunity == null ? detailRow("Opportunity", "-") : detailRow("Opportunity", opportunity.getTitle()),
                detailRow("Contact", displayPersonName(lead)),
                detailRow("Company", displayCompanyName(lead)),
                detailRow("Email", displayLeadEmail(lead)),
                detailRow("Phone", displayLeadPhone(lead)),
                detailRow("Source", valueOrDash(lead.getSource())),
                detailRow("Assigned to", lead.getAssignedTo() == null ? "-" : lead.getAssignedTo().getFullName()),
                detailRow("Created", lead.getCreatedAt() == null ? "-" : lead.getCreatedAt().format(DATE_FORMAT)),
                detailItemsSection("Items", leadService.findLeadItems(lead.getId())),
                detailSection("Description", lead.getDescription()),
                detailSection("Notes", lead.getNotes()),
                new ActivityTimeline(leadService.findActivities(lead.getId())));

        detail.add(detailHeader, body);
        return detail;
    }

    private Component createLeadActionControl(Lead lead, Opportunity opportunity) {
        if ("VALID".equals(lead.getStatusCode())) {
            return createConversionControl(lead, opportunity);
        }

        List<OptionValue> statuses = leadService.findPipelineStatuses();

        ComboBox<OptionValue> status = new ComboBox<>("Move Stage");
        status.addClassName("pipeline-status-control");
        status.setItems(statuses);
        status.setItemLabelGenerator(OptionValue::getName);
        status.setValue(findStatusByCode(statuses, lead.getStatusCode()));
        status.addValueChangeListener(event -> {
            if (!event.isFromClient() || event.getValue() == null
                    || Objects.equals(event.getValue().getCode(), lead.getStatusCode())) {
                return;
            }

            Lead updatedLead = leadService.updateLeadStatus(lead.getId(), event.getValue());
            refreshPipelineDataAndBoard();
            showSuccess("Lead moved to " + event.getValue().getName());
            setDetail(createLeadDetail(updatedLead));
        });

        return status;
    }

    private Component createConversionControl(Lead lead, Opportunity opportunity) {
        Div panel = new Div();
        panel.addClassName("lead-conversion-panel");

        Span label = new Span(opportunity == null ? "Qualified lead" : "Converted lead");
        label.addClassName("lead-conversion-label");

        Paragraph text = new Paragraph(opportunity == null
                ? "This lead is valid. Create an opportunity when Sales is ready to work the pipeline."
                : "This lead already has an opportunity. Continue the sales flow from the opportunity pipeline.");
        text.addClassName("lead-conversion-text");

        Button action = opportunity == null
                ? new Button("Create Opportunity", VaadinIcon.TRENDING_UP.create(), event -> openConvertLeadDialog(lead))
                : new Button("Open Opportunity", VaadinIcon.EXTERNAL_LINK.create(),
                        event -> navigateToOpportunity(opportunity));
        action.addClassName("lead-conversion-button");
        action.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        panel.add(label, text, action);
        return panel;
    }

    private void openConvertLeadDialog(Lead lead) {
        LeadService.ConvertLeadRequest request = new LeadService.ConvertLeadRequest();
        Binder<LeadService.ConvertLeadRequest> binder = new Binder<>(LeadService.ConvertLeadRequest.class);
        Dialog dialog = quickCreateDialog("Create Opportunity");

        CurrencyField estimatedAmount = new CurrencyField("Estimated Amount");

        IntegerField probability = new IntegerField("Probability");
        probability.setMin(0);
        probability.setMax(100);
        probability.setSuffixComponent(new Span("%"));

        DatePicker expectedClose = new DatePicker("Expected Close");

        binder.forField(estimatedAmount)
                .asRequired("Estimated Amount is required")
                .bind(LeadService.ConvertLeadRequest::getEstimatedAmount,
                        LeadService.ConvertLeadRequest::setEstimatedAmount);

        binder.forField(probability)
                .asRequired("Probability is required")
                .bind(LeadService.ConvertLeadRequest::getProbability,
                        LeadService.ConvertLeadRequest::setProbability);

        binder.forField(expectedClose)
                .asRequired("Expected Close is required")
                .bind(LeadService.ConvertLeadRequest::getExpectedCloseDate,
                        LeadService.ConvertLeadRequest::setExpectedCloseDate);

        binder.readBean(request);

        Button save = new Button("Create Opportunity", VaadinIcon.TRENDING_UP.create(), event -> {
            try {
                binder.writeBean(request);
                Opportunity opportunity = leadService.createOpportunityFromLead(lead.getId(), request);
                showSuccess("Opportunity created from lead");
                dialog.close();
                navigateToOpportunity(opportunity);
            } catch (ValidationException exception) {
                showError("Please complete required fields");
            } catch (IllegalStateException exception) {
                showError(exception.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(
                quickCreateBody(estimatedAmount, probability, expectedClose),
                dialogActions(save, dialog));
        dialog.open();
    }

    private void navigateToOpportunity(Opportunity opportunity) {
        UI.getCurrent().navigate(OpportunitiesView.class,
                QueryParameters.of("opportunity", String.valueOf(opportunity.getPublicId())));
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

    private Component detailItemsSection(String label, List<LeadItem> items) {
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
                    + " x " + formatAmountOrDash(item.getEstimatedUnitPrice())
                    + " = " + formatAmountOrDash(item.getEstimatedTotal()));
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

    private String displayPersonName(Lead lead) {
        if (lead.getPerson() != null) {
            return lead.getPerson().getFullName();
        }
        return valueOrFallback(lead.getRawPersonName(), "Unknown contact");
    }

    private String displayCompanyName(Lead lead) {
        if (lead.getCompany() != null) {
            return lead.getCompany().getName();
        }
        return valueOrFallback(lead.getRawCompanyName(), "No company");
    }

    private String leadStatusName(Lead lead) {
        return lead.getStatus() == null ? valueOrDash(lead.getStatusCode()) : lead.getStatus().getName();
    }

    private String displayLeadEmail(Lead lead) {
        if (lead.getPerson() != null && lead.getPerson().getEmail() != null
                && !lead.getPerson().getEmail().isBlank()) {
            return lead.getPerson().getEmail();
        }
        return lead.getRawEmail();
    }

    private String displayLeadPhone(Lead lead) {
        if (lead.getPerson() != null && lead.getPerson().getPhone() != null
                && !lead.getPerson().getPhone().isBlank()) {
            return lead.getPerson().getPhone();
        }
        return lead.getRawPhone();
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

    private OptionValue findSourceTypeByValue(List<OptionValue> sourceTypes, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return sourceTypes.stream()
                .filter(sourceType -> Objects.equals(sourceType.getCode(), value)
                        || Objects.equals(sourceType.getName(), value))
                .findFirst()
                .orElse(null);
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

    private String valueOrFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
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

    private String formatAmountOrDash(BigDecimal amount) {
        return formatRupiah(amount);
    }

    private String productTypeName(OptionValue productType, String fallbackCode) {
        return productType == null ? valueOrDash(fallbackCode) : productType.getName();
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

    private String conversionLabel(Lead lead) {
        return lead.getConvertedAt() == null ? null : "Converted";
    }

    private String conversionLabel(Lead lead, Opportunity opportunity) {
        if (opportunity != null || lead.getConvertedAt() != null) {
            return "Converted";
        }
        return "VALID".equals(lead.getStatusCode()) ? "Ready to convert" : "Not qualified yet";
    }

    private String normalizeColor(String color) {
        return color == null || color.isBlank() ? "default" : color.toLowerCase().replace('_', '-');
    }

    private Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
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
