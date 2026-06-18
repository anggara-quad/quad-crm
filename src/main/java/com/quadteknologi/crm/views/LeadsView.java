package com.quadteknologi.crm.views;

import com.quadteknologi.crm.domain.entity.Lead;
import com.quadteknologi.crm.domain.entity.Opportunity;
import com.quadteknologi.crm.domain.entity.OptionValue;
import com.quadteknologi.crm.domain.entity.Company;
import com.quadteknologi.crm.domain.entity.Person;
import com.quadteknologi.crm.domain.entity.User;
import com.quadteknologi.crm.service.ContactService;
import com.quadteknologi.crm.service.LeadService;
import com.quadteknologi.crm.ui.component.ActivityTimeline;
import com.quadteknologi.crm.ui.component.KanbanBoard;
import com.quadteknologi.crm.ui.layout.MainLayout;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
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
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@RolesAllowed({"Administrator", "Manager", "Sales"})
@PageTitle("Leads | Quad CRM")
@Route(value = "leads", layout = MainLayout.class)
public class LeadsView extends VerticalLayout {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final List<String> SOURCE_OPTIONS = List.of("Email", "Web", "Phone", "Direct");

    private final LeadService leadService;
    private final ContactService contactService;
    private KanbanBoard<Lead> kanbanBoard;
    private String searchTerm = "";

    public LeadsView(LeadService leadService, ContactService contactService) {
        this.leadService = leadService;
        this.contactService = contactService;

        addClassName("page-view");
        setPadding(false);
        setSpacing(false);
        setSizeFull();

        add(createHeader(), createKanbanBoard());
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
        actions.add(createSearchField("Search leads"), createLeadButton);

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
        List<OptionValue> statuses = leadService.findPipelineStatuses();
        Map<String, List<Lead>> leadsByStatus = filterLeadsBySearch(leadService.findPipelineLeadsByStatus());

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

        ComboBox<String> source = new ComboBox<>("Source");
        source.setItems(sourceOptions(request.getSource()));
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

        binder.forField(title).asRequired("Lead title is required").bind(
                LeadService.CreateLeadRequest::getTitle,
                LeadService.CreateLeadRequest::setTitle);
        binder.forField(status).asRequired("Status is required").bind(
                LeadService.CreateLeadRequest::getStatus,
                LeadService.CreateLeadRequest::setStatus);
        binder.bind(company, LeadService.CreateLeadRequest::getCompany, LeadService.CreateLeadRequest::setCompany);
        binder.bind(person, LeadService.CreateLeadRequest::getPerson, LeadService.CreateLeadRequest::setPerson);
        binder.bind(source, LeadService.CreateLeadRequest::getSource, LeadService.CreateLeadRequest::setSource);
        binder.bind(assignedTo, LeadService.CreateLeadRequest::getAssignedTo, LeadService.CreateLeadRequest::setAssignedTo);
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
                sectionDivider("Activity"),
                initialActivityNote);

        detail.add(formBody, createLeadFormActions(lead, request, binder));
        kanbanBoard.setDetail(detail);
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
        return request;
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
        EmailField email = new EmailField("Email");
        TextField phone = phoneField("Phone");
        TextField city = new TextField("City");

        binder.forField(name).asRequired("Name is required").bind(Company::getName, Company::setName);
        binder.bind(industry, Company::getIndustry, Company::setIndustry);
        binder.bind(email, Company::getEmail, Company::setEmail);
        binder.bind(phone, Company::getPhone, Company::setPhone);
        binder.bind(city, Company::getCity, Company::setCity);
        binder.readBean(company);

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
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(name, industry, email, phone, city, dialogActions(save, dialog));
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
        TextField phone = phoneField("Phone");
        TextField whatsapp = phoneField("WhatsApp");

        binder.forField(fullName).asRequired("Full name is required").bind(Person::getFullName, Person::setFullName);
        binder.bind(company, Person::getCompany, Person::setCompany);
        binder.bind(email, Person::getEmail, Person::setEmail);
        binder.bind(phone, Person::getPhone, Person::setPhone);
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
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(fullName, company, email, phone, whatsapp, dialogActions(save, dialog));
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

    private Component dialogActions(Button save, Dialog dialog) {
        Button cancel = new Button("Cancel", event -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout actions = new HorizontalLayout(save, cancel);
        actions.addClassName("pipeline-form-actions");
        actions.setPadding(false);
        actions.setSpacing(true);
        return actions;
    }

    private Div createLeadFormShell(String titleText, String subtitleText, boolean editing) {
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
                refreshKanbanBoard();
                showSuccess(editing ? "Lead updated" : "Lead created");
                kanbanBoard.setDetail(createLeadDetail(savedLead));
            } catch (ValidationException exception) {
                showError("Please complete required fields");
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

    private void refreshKanbanBoard() {
        remove(kanbanBoard);
        add(createKanbanBoard());
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
        if (kanbanBoard != null) {
            kanbanBoard.setDetailSize("360px");
        }

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

        Button close = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> kanbanBoard.setDetail(null));
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
            refreshKanbanBoard();
            showSuccess("Lead moved to " + event.getValue().getName());
            kanbanBoard.setDetail(createLeadDetail(updatedLead));
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
                ? new Button("Create Opportunity", VaadinIcon.TRENDING_UP.create(), event -> convertLeadToOpportunity(lead))
                : new Button("Open Opportunity", VaadinIcon.EXTERNAL_LINK.create(),
                        event -> navigateToOpportunity(opportunity));
        action.addClassName("lead-conversion-button");
        action.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        panel.add(label, text, action);
        return panel;
    }

    private void convertLeadToOpportunity(Lead lead) {
        try {
            Opportunity opportunity = leadService.createOpportunityFromLead(lead.getId());
            showSuccess("Opportunity created from lead");
            navigateToOpportunity(opportunity);
        } catch (IllegalStateException exception) {
            showError(exception.getMessage());
        }
    }

    private void navigateToOpportunity(Opportunity opportunity) {
        UI.getCurrent().navigate(OpportunitiesView.class,
                QueryParameters.of("opportunity", String.valueOf(opportunity.getId())));
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

    private List<String> sourceOptions(String currentSource) {
        List<String> options = new ArrayList<>(SOURCE_OPTIONS);
        if (currentSource != null && !currentSource.isBlank() && !options.contains(currentSource)) {
            options.add(currentSource);
        }
        return options;
    }

    private TextField phoneField(String label) {
        TextField field = new TextField(label);
        field.setClearButtonVisible(true);
        field.setPlaceholder("+62 812 3456 7890");
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

    private void showSuccess(String message) {
        Notification notification = Notification.show(message, 2500, Notification.Position.BOTTOM_END);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void showError(String message) {
        Notification notification = Notification.show(message, 3500, Notification.Position.BOTTOM_END);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
