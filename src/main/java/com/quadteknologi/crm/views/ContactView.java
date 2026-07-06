package com.quadteknologi.crm.views;

import com.quadteknologi.crm.domain.entity.Company;
import com.quadteknologi.crm.domain.entity.Country;
import com.quadteknologi.crm.domain.entity.Lead;
import com.quadteknologi.crm.domain.entity.Opportunity;
import com.quadteknologi.crm.domain.entity.Person;
import com.quadteknologi.crm.domain.entity.Region;
import com.quadteknologi.crm.security.AppViewAccess;
import com.quadteknologi.crm.security.ViewAccessService;
import com.quadteknologi.crm.service.CompanyContactSummaryDto;
import com.quadteknologi.crm.service.ContactService;
import com.quadteknologi.crm.service.PersonContactSummaryDto;
import com.quadteknologi.crm.ui.layout.MainLayout;
import com.vaadin.componentfactory.addons.inputmask.InputMask;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.masterdetaillayout.MasterDetailLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.renderer.LitRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.function.ValueProvider;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.quadteknologi.crm.ui.util.CurrencyFormatter.formatRupiah;

@PermitAll
@PageTitle("Contact | Quad CRM")
@Route(value = "contact", layout = MainLayout.class)
public class ContactView extends VerticalLayout implements BeforeEnterObserver {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private final ContactService contactService;
    private final ViewAccessService viewAccessService;

    private final MasterDetailLayout personLayout = new MasterDetailLayout();
    private final MasterDetailLayout organizationLayout = new MasterDetailLayout();
    private final Grid<Person> personGrid = new Grid<>(Person.class, false);
    private final Grid<Company> organizationGrid = new Grid<>(Company.class, false);

    private final Div content = new Div();
    private final Tab personTab = new Tab("Person");
    private final Tab organizationTab = new Tab("Organization");
    private final Tabs tabs = new Tabs(personTab, organizationTab);
    private Map<Long, PersonContactSummaryDto> personSummaries = new HashMap<>();
    private Map<Long, CompanyContactSummaryDto> companySummaries = new HashMap<>();
    private List<Person> persons = List.of();
    private List<Company> companies = List.of();
    private ComboBox<Company> organizationFilter;
    private boolean openingRelatedDetail;
    private boolean contentConfigured;
    private boolean loadScheduled;
    private Company selectedOrganizationFilter;
    private String searchTerm = "";

    public ContactView(ContactService contactService, ViewAccessService viewAccessService) {
        this.contactService = contactService;
        this.viewAccessService = viewAccessService;

        addClassNames("page-view", "contact-view");
        setPadding(false);
        setSpacing(false);
        setSizeFull();

        add(createHeader(), createTabs(), content);
        content.addClassName("contact-tab-content");

        showLoadingState();
        addAttachListener(event -> scheduleContactLoad());
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        viewAccessService.checkBeforeEnter(event, AppViewAccess.CONTACT);
    }

    @ClientCallable
    public void loadContactData() {
        loadScheduled = false;
        reloadContactData();
        if (!contentConfigured) {
            configurePersonLayout();
            configureOrganizationLayout();
            contentConfigured = true;
        }
        refreshPersons();
        refreshOrganizations();
        showCurrentTab();
    }

    private void scheduleContactLoad() {
        showLoadingState();
        if (loadScheduled) {
            return;
        }
        loadScheduled = true;
        getElement().executeJs("setTimeout(() => this.$server.loadContactData(), 0)");
    }

    private void reloadContactData() {
        personSummaries = contactService.findPersonSummaries().stream()
                .collect(java.util.stream.Collectors.toMap(
                        PersonContactSummaryDto::personId,
                        Function.identity(),
                        (current, replacement) -> current));
        companySummaries = contactService.findCompanySummaries().stream()
                .collect(java.util.stream.Collectors.toMap(
                        CompanyContactSummaryDto::companyId,
                        Function.identity(),
                        (current, replacement) -> current));
        persons = contactService.findAllPersons();
        companies = contactService.findAllCompanies();
        refreshOrganizationFilterOptions();
    }

    private void showLoadingState() {
        content.removeAll();
        Div loading = new Div();
        loading.addClassName("contact-loading-state");
        H3 title = new H3("Loading contacts");
        Paragraph text = new Paragraph("Preparing people and organization data...");
        loading.add(title, text);
        content.add(loading);
    }

    private void showCurrentTab() {
        showTab(tabs.getSelectedTab() == organizationTab ? organizationLayout : personLayout);
    }

    private Component createHeader() {
        Header header = new Header();
        header.addClassName("pipeline-header");

        Div titleGroup = new Div();
        titleGroup.addClassName("pipeline-title-group");

        H2 title = new H2("Contact");
        Paragraph subtitle = new Paragraph("Manage people and organizations in one place.");
        titleGroup.add(title, subtitle);

        Div actions = new Div();
        actions.addClassName("pipeline-header-actions");
        actions.add(createSearchField());

        header.add(titleGroup, actions);
        return header;
    }

    private TextField createSearchField() {
        TextField search = new TextField();
        search.addClassName("pipeline-search-field");
        search.setPlaceholder("Search contacts");
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.EAGER);
        search.addValueChangeListener(event -> {
            searchTerm = event.getValue() == null ? "" : event.getValue();
            if (!contentConfigured) {
                return;
            }
            refreshPersons();
            refreshOrganizations();
        });
        return search;
    }

    private Component createTabs() {
        tabs.addClassName("contact-tabs");
        tabs.addSelectedChangeListener(event -> {
            if (!contentConfigured) {
                showLoadingState();
                return;
            }
            if (event.getSelectedTab() == personTab) {
                showTab(personLayout);
            } else {
                showTab(organizationLayout);
            }
        });
        return tabs;
    }

    private void showTab(Component component) {
        content.removeAll();
        content.add(component);
    }

    private void configurePersonLayout() {
        personLayout.addClassName("contact-master-detail");
        personLayout.setSizeFull();
        personLayout.setMasterMinSize("640px");
        personLayout.setDetailSize("420px");
        personLayout.setOverlayMode(MasterDetailLayout.OverlayMode.DRAWER);
        personLayout.setForceOverlay(true);
        personLayout.addBackdropClickListener(event -> closePersonDetail());
        personLayout.addDetailEscapePressListener(event -> closePersonDetail());
        personLayout.setMaster(createPersonMaster());
    }

    private Component createPersonMaster() {
        VerticalLayout master = new VerticalLayout();
        master.addClassName("contact-master");
        master.setPadding(false);
        master.setSpacing(false);
        master.setSizeFull();

        master.add(createPersonToolbar());

        personGrid.addClassName("contact-grid");
        personGrid.setSizeFull();
        personGrid.addColumn(personIdentityRenderer())
                .setHeader("Person")
                .setWidth("200px")
                .setFlexGrow(2)
                .setSortable(true)
                .setComparator(Comparator.comparing(Person::getFullName, String.CASE_INSENSITIVE_ORDER));
        personGrid.addColumn(personContactRenderer())
                .setHeader("Organization / Contact")
                .setWidth("200px")
                .setFlexGrow(2)
                .setSortable(true)
                .setComparator(Comparator.comparing(
                        this::personCompanyName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        personGrid.addColumn(personMetricActionRenderer(
                        person -> formatNumber(personSummary(person).leads()) + " / "
                                + formatNumber(personSummary(person).validLeads()),
                        person -> "Leads / valid",
                        person -> personSummary(person).leads(),
                        this::openPersonLeads))
                .setHeader("Leads")
                .setAutoWidth(true)
                .setSortable(true)
                .setComparator(Comparator.comparingLong(person -> personSummary(person).leads()));
        personGrid.addColumn(personMetricActionRenderer(
                        person -> formatNumber(personSummary(person).opportunities()) + " total",
                        person -> formatNumber(personSummary(person).openOpportunities()) + " open opportunities",
                        person -> personSummary(person).opportunities(),
                        this::openPersonOpportunities))
                .setHeader("Opportunities")
                .setAutoWidth(true)
                .setSortable(true)
                .setComparator(Comparator.comparingLong(person -> personSummary(person).opportunities()));
        personGrid.addColumn(personStackRenderer(
                        person -> formatCurrency(personSummary(person).wonRevenue()),
                        person -> "Won revenue"))
                .setHeader("Won")
                .setAutoWidth(true)
                .setSortable(true)
                .setComparator(Comparator.comparing(person -> personSummary(person).wonRevenue()));
        personGrid.addColumn(personStackRenderer(
                        person -> createdByName(person.getCreatedBy()),
                        person -> "Created by"))
                .setHeader("Created By")
                .setAutoWidth(true)
                .setSortable(true)
                .setComparator(Comparator.comparing(
                        person -> createdByName(person.getCreatedBy()),
                        String.CASE_INSENSITIVE_ORDER));
        personGrid.addColumn(personStackRenderer(
                        person -> formatDateTime(personSummary(person).lastActivityAt()),
                        person -> "Last activity"))
                .setHeader("Activity")
                .setAutoWidth(true)
                .setSortable(true)
                .setComparator(Comparator.comparing(
                        person -> personSummary(person).lastActivityAt(),
                        Comparator.nullsLast(Comparator.naturalOrder())));
        personGrid.asSingleSelect().addValueChangeListener(event -> {
            if (openingRelatedDetail) {
                openingRelatedDetail = false;
                return;
            }
            if (event.getValue() != null) {
                openPersonForm(event.getValue());
            }
        });

        master.add(personGrid);
        return master;
    }

    private void openPersonForm(Person person) {
        Binder<Person> binder = new Binder<>(Person.class);

        Div detail = createDetailShell(person.getId() == null ? "New Person" : person.getFullName());

        ComboBox<Company> company = new ComboBox<>("Organization");
        company.setItems(companies);
        company.setItemLabelGenerator(Company::getName);
        company.setClearButtonVisible(true);

        TextField fullName = new TextField("Full Name");
        TextField jobTitle = new TextField("Job Title");
        EmailField email = new EmailField("Email");
        TextField phone = phoneField("Phone");
        TextField whatsapp = phoneField("WhatsApp");
        TextArea notes = new TextArea("Notes");
        notes.setMinHeight("110px");

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

        binder.bind(notes, Person::getNotes, Person::setNotes);
        binder.readBean(person);

        detail.add(company, fullName, jobTitle, email, phone, whatsapp, notes,
                createPersonActions(person, binder));
        personLayout.setDetail(detail);
    }

    private Component createPersonActions(Person person, Binder<Person> binder) {
        Button save = new Button("Save", VaadinIcon.CHECK.create(), event -> {
            try {
                binder.writeBean(person);
                contactService.savePerson(person);
                reloadContactDataAndRefreshGrids();
                closePersonDetail();
                showSuccess("Person saved");
            } catch (ValidationException exception) {
                showError("Please complete required fields");
            } catch (IllegalArgumentException exception) {
                showError(exception.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button delete = new Button("Delete", VaadinIcon.TRASH.create(), event -> {
            if (person.getId() == null) {
                closePersonDetail();
                return;
            }
            contactService.deletePerson(person);
            reloadContactDataAndRefreshGrids();
            closePersonDetail();
            showSuccess("Person deleted");
        });
        delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);

        Button cancel = new Button("Cancel", event -> closePersonDetail());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        return actionBar(save, delete, cancel);
    }

    private void configureOrganizationLayout() {
        organizationLayout.addClassName("contact-master-detail");
        organizationLayout.setSizeFull();
        organizationLayout.setMasterMinSize("640px");
        organizationLayout.setDetailSize("420px");
        organizationLayout.setOverlayMode(MasterDetailLayout.OverlayMode.DRAWER);
        organizationLayout.setForceOverlay(true);
        organizationLayout.addBackdropClickListener(event -> closeOrganizationDetail());
        organizationLayout.addDetailEscapePressListener(event -> closeOrganizationDetail());
        organizationLayout.setMaster(createOrganizationMaster());
    }

    private Component createOrganizationMaster() {
        VerticalLayout master = new VerticalLayout();
        master.addClassName("contact-master");
        master.setPadding(false);
        master.setSpacing(false);
        master.setSizeFull();

        master.add(createMasterToolbar("Organization", "New Organization", event -> openOrganizationForm(new Company())));

        organizationGrid.addClassName("contact-grid");
        organizationGrid.setSizeFull();
        organizationGrid.addColumn(companyIdentityRenderer())
                .setHeader("Organization")
                .setWidth("200px")
                .setFlexGrow(2)
                .setSortable(true)
                .setComparator(Comparator.comparing(Company::getName, String.CASE_INSENSITIVE_ORDER));
        organizationGrid.addColumn(companyContactRenderer())
                .setHeader("Contact / Location")
                .setWidth("200px")
                .setFlexGrow(2)
                .setSortable(true)
                .setComparator(Comparator.comparing(this::companyLocation, String.CASE_INSENSITIVE_ORDER));
        organizationGrid.addColumn(companyMetricActionRenderer(
                        company -> formatNumber(companySummary(company).contacts()),
                        company -> "Contacts",
                        company -> companySummary(company).contacts(),
                        this::openCompanyContacts))
                .setHeader("Contacts")
                .setAutoWidth(true)
                .setSortable(true)
                .setComparator(Comparator.comparingLong(company -> companySummary(company).contacts()));
        organizationGrid.addColumn(companyMetricActionRenderer(
                        company -> formatNumber(companySummary(company).leads()) + " / "
                                + formatNumber(companySummary(company).validLeads()),
                        company -> "Leads / valid",
                        company -> companySummary(company).leads(),
                        this::openCompanyLeads))
                .setHeader("Leads")
                .setAutoWidth(true)
                .setSortable(true)
                .setComparator(Comparator.comparingLong(company -> companySummary(company).leads()));
        organizationGrid.addColumn(companyMetricActionRenderer(
                        company -> formatNumber(companySummary(company).opportunities()) + " total",
                        company -> formatNumber(companySummary(company).openOpportunities()) + " open opportunities",
                        company -> companySummary(company).opportunities(),
                        this::openCompanyOpportunities))
                .setHeader("Opportunities")
                .setAutoWidth(true)
                .setSortable(true)
                .setComparator(Comparator.comparingLong(company -> companySummary(company).opportunities()));
        organizationGrid.addColumn(companyStackRenderer(
                        company -> formatCurrency(companySummary(company).wonRevenue()),
                        company -> "Won revenue"))
                .setHeader("Won")
                .setAutoWidth(true)
                .setSortable(true)
                .setComparator(Comparator.comparing(company -> companySummary(company).wonRevenue()));
        organizationGrid.addColumn(companyStackRenderer(
                        company -> formatDateTime(companySummary(company).lastActivityAt()),
                        company -> "Last activity"))
                .setHeader("Activity")
                .setAutoWidth(true)
                .setSortable(true)
                .setComparator(Comparator.comparing(
                        company -> companySummary(company).lastActivityAt(),
                        Comparator.nullsLast(Comparator.naturalOrder())));
        organizationGrid.addColumn(companyStackRenderer(
                        company -> createdByName(company.getCreatedBy()),
                        company -> "Created by"))
                .setHeader("Created By")
                .setAutoWidth(true)
                .setSortable(true)
                .setComparator(Comparator.comparing(
                        company -> createdByName(company.getCreatedBy()),
                        String.CASE_INSENSITIVE_ORDER));
        organizationGrid.asSingleSelect().addValueChangeListener(event -> {
            if (openingRelatedDetail) {
                openingRelatedDetail = false;
                return;
            }
            if (event.getValue() != null) {
                openOrganizationForm(event.getValue());
            }
        });

        master.add(organizationGrid);
        return master;
    }

    private void openOrganizationForm(Company company) {
        Binder<Company> binder = new Binder<>(Company.class);

        Div detail = createDetailShell(company.getId() == null ? "New Organization" : company.getName());

        TextField name = new TextField("Name");
        TextField industry = new TextField("Industry");
        TextField website = new TextField("Website");
        EmailField email = new EmailField("Email");
        TextField phone = phoneField("Phone");
        ComboBox<Country> country = new ComboBox<>("Country");
        ComboBox<Region> province = new ComboBox<>("Province");
        ComboBox<Region> city = new ComboBox<>("City / Regency");
        TextArea address = new TextArea("Address");
        TextArea notes = new TextArea("Notes");
        address.setMinHeight("90px");
        notes.setMinHeight("110px");

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

        Country selectedCountry = findCountryById(countries, company.getCountry());
        if (selectedCountry == null && company.getId() == null && countries.size() == 1) {
            selectedCountry = countries.getFirst();
        }
        company.setCountry(selectedCountry);

        List<Region> provinces = selectedCountry == null ? List.of() : contactService.findActiveProvinces(selectedCountry);
        province.setItems(provinces);
        province.setEnabled(!provinces.isEmpty());

        Region selectedProvince = findRegionById(provinces, company.getProvince());
        company.setProvince(selectedProvince);

        List<Region> cities = selectedProvince == null ? List.of() : contactService.findActiveCities(selectedProvince);
        city.setItems(cities);
        city.setEnabled(!cities.isEmpty());
        company.setCity(findRegionById(cities, company.getCity()));

        country.addValueChangeListener(event -> {
            if (loadingForm[0]) {
                return;
            }
            Country selected = event.getValue();
            province.clear();
            city.clear();
            city.setItems(List.of());
            city.setEnabled(false);

            List<Region> nextProvinces = selected == null ? List.of() : contactService.findActiveProvinces(selected);
            province.setItems(nextProvinces);
            province.setEnabled(!nextProvinces.isEmpty());
        });

        province.addValueChangeListener(event -> {
            if (loadingForm[0]) {
                return;
            }
            Region selected = event.getValue();
            city.clear();

            List<Region> nextCities = selected == null ? List.of() : contactService.findActiveCities(selected);
            city.setItems(nextCities);
            city.setEnabled(!nextCities.isEmpty());
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

        binder.bind(notes, Company::getNotes, Company::setNotes);
        binder.readBean(company);
        loadingForm[0] = false;

        detail.add(name, industry, website, email, phone, country, province, city, address, notes,
                createOrganizationActions(company, binder));
        organizationLayout.setDetail(detail);
    }

    private Component createOrganizationActions(Company company, Binder<Company> binder) {
        Button save = new Button("Save", VaadinIcon.CHECK.create(), event -> {
            try {
                binder.writeBean(company);
                contactService.saveCompany(company);
                reloadContactDataAndRefreshGrids();
                closeOrganizationDetail();
                showSuccess("Organization saved");
            } catch (ValidationException exception) {
                showError("Please complete required fields");
            } catch (IllegalArgumentException exception) {
                showError(exception.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button delete = new Button("Delete", VaadinIcon.TRASH.create(), event -> {
            if (company.getId() == null) {
                closeOrganizationDetail();
                return;
            }
            try {
                contactService.deleteCompany(company);
                reloadContactDataAndRefreshGrids();
                closeOrganizationDetail();
                showSuccess("Organization deleted");
            } catch (DataIntegrityViolationException exception) {
                showError("Organization still has related contacts");
            }
        });
        delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);

        Button cancel = new Button("Cancel", event -> closeOrganizationDetail());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        return actionBar(save, delete, cancel);
    }

    private Component createPersonToolbar() {
        Div toolbar = new Div();
        toolbar.addClassName("contact-master-toolbar");

        Span titleSpan = new Span("Person");
        titleSpan.addClassName("contact-master-title");

        Div filters = new Div();
        filters.addClassName("contact-master-filters");

        organizationFilter = new ComboBox<>();
        organizationFilter.addClassName("contact-organization-filter");
        organizationFilter.setItemLabelGenerator(Company::getName);
        organizationFilter.setPlaceholder("All Organizations");
        organizationFilter.setClearButtonVisible(true);
        refreshOrganizationFilterOptions();
        organizationFilter.addValueChangeListener(event -> {
            selectedOrganizationFilter = event.getValue();
            closePersonDetail();
            refreshPersons();
        });
        filters.add(organizationFilter);

        Button action = new Button("New Person", VaadinIcon.PLUS.create(), event -> openPersonForm(new Person()));
        action.addClassName("pipeline-create-button");

        toolbar.add(titleSpan, filters, action);
        return toolbar;
    }

    private void refreshOrganizationFilterOptions() {
        if (organizationFilter == null) {
            return;
        }
        organizationFilter.setItems(companies);
        Company selected = findSelectedOrganization(companies);
        if (!Objects.equals(organizationFilter.getValue(), selected)) {
            organizationFilter.setValue(selected);
        }
    }

    private Component createMasterToolbar(String title, String actionLabel,
            com.vaadin.flow.component.ComponentEventListener<com.vaadin.flow.component.ClickEvent<Button>> listener) {
        Div toolbar = new Div();
        toolbar.addClassName("contact-master-toolbar");

        Span titleSpan = new Span(title);
        titleSpan.addClassName("contact-master-title");

        Button action = new Button(actionLabel, VaadinIcon.PLUS.create(), listener);
        action.addClassName("pipeline-create-button");

        toolbar.add(titleSpan, action);
        return toolbar;
    }

    private Company findSelectedOrganization(List<Company> companies) {
        if (selectedOrganizationFilter == null || selectedOrganizationFilter.getId() == null) {
            return null;
        }
        return companies.stream()
                .filter(company -> Objects.equals(company.getId(), selectedOrganizationFilter.getId()))
                .findFirst()
                .orElse(null);
    }

    private Div createDetailShell(String title) {
        Div detail = new Div();
        detail.addClassName("contact-detail");

        H3 heading = new H3(title);
        heading.addClassName("contact-detail-title");
        detail.add(heading);

        return detail;
    }

    private Div createRelatedDetailShell(String title, Runnable closeAction) {
        Div detail = new Div();
        detail.addClassName("contact-detail");

        Div header = new Div();
        header.addClassName("contact-related-header");

        H3 heading = new H3(title);
        heading.addClassName("contact-detail-title");

        Button close = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> closeAction.run());
        close.addClassName("contact-related-close");
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        close.getElement().setAttribute("aria-label", "Close detail");

        header.add(heading, close);
        detail.add(header);
        return detail;
    }

    private void openPersonLeads(Person person) {
        List<Lead> leads = contactService.findLeadsForPerson(person);
        personLayout.setDetail(createLeadRelatedDetail(
                "Leads - " + person.getFullName(),
                leads,
                this::closePersonDetail));
    }

    private void openPersonOpportunities(Person person) {
        List<Opportunity> opportunities = contactService.findOpportunitiesForPerson(person);
        personLayout.setDetail(createOpportunityRelatedDetail(
                "Opportunities - " + person.getFullName(),
                opportunities,
                this::closePersonDetail));
    }

    private void openCompanyContacts(Company company) {
        List<Person> persons = contactService.findPersonsForCompany(company);
        organizationLayout.setDetail(createContactRelatedDetail(
                "Contacts - " + company.getName(),
                persons));
    }

    private void openCompanyLeads(Company company) {
        List<Lead> leads = contactService.findLeadsForCompany(company);
        organizationLayout.setDetail(createLeadRelatedDetail(
                "Leads - " + company.getName(),
                leads,
                this::closeOrganizationDetail));
    }

    private void openCompanyOpportunities(Company company) {
        List<Opportunity> opportunities = contactService.findOpportunitiesForCompany(company);
        organizationLayout.setDetail(createOpportunityRelatedDetail(
                "Opportunities - " + company.getName(),
                opportunities,
                this::closeOrganizationDetail));
    }

    private Component createLeadRelatedDetail(String title, List<Lead> leads, Runnable closeAction) {
        Div detail = createRelatedDetailShell(title, closeAction);
        Div list = createRelatedList();
        if (leads.isEmpty()) {
            list.add(createRelatedEmpty("No related leads"));
        } else {
            leads.forEach(lead -> list.add(createLeadRelatedRow(lead)));
        }
        detail.add(list);
        return detail;
    }

    private Component createOpportunityRelatedDetail(String title, List<Opportunity> opportunities, Runnable closeAction) {
        Div detail = createRelatedDetailShell(title, closeAction);
        Div list = createRelatedList();
        if (opportunities.isEmpty()) {
            list.add(createRelatedEmpty("No related opportunities"));
        } else {
            opportunities.forEach(opportunity -> list.add(createOpportunityRelatedRow(opportunity)));
        }
        detail.add(list);
        return detail;
    }

    private Component createContactRelatedDetail(String title, List<Person> persons) {
        Div detail = createRelatedDetailShell(title, this::closeOrganizationDetail);
        Div list = createRelatedList();
        if (persons.isEmpty()) {
            list.add(createRelatedEmpty("No related contacts"));
        } else {
            persons.forEach(person -> list.add(createContactRelatedRow(person)));
        }
        detail.add(list);
        return detail;
    }

    private Div createRelatedList() {
        Div list = new Div();
        list.addClassName("contact-related-list");
        return list;
    }

    private Component createLeadRelatedRow(Lead lead) {
        Div row = createRelatedRow(() -> openLeadDetail(lead));
        row.add(
                createRelatedBody(
                        lead.getTitle(),
                        firstNonBlank(leadAccount(lead), "No account"),
                        leadStatusName(lead) + " | Source " + valueOrFallback(lead.getSource(), "-")),
                createRelatedAside(formatDateTime(lead.getCreatedAt()), "Open lead"));
        return row;
    }

    private Component createOpportunityRelatedRow(Opportunity opportunity) {
        Div row = createRelatedRow(() -> openOpportunityDetail(opportunity));
        row.add(
                createRelatedBody(
                        opportunity.getTitle(),
                        firstNonBlank(opportunityAccount(opportunity), "No account"),
                        opportunityStageName(opportunity) + " | Amount " + formatCurrency(opportunity.getEstimatedAmount())),
                createRelatedAside(formatDateTime(opportunity.getCreatedAt()), "Open opportunity"));
        return row;
    }

    private Component createContactRelatedRow(Person person) {
        Div row = createRelatedRow(() -> openPersonFromOrganizationDetail(person));
        row.add(
                createRelatedBody(
                        person.getFullName(),
                        firstNonBlank(person.getJobTitle(), "No job title"),
                        "Phone " + valueOrFallback(person.getPhone(), "-") + " | WhatsApp "
                                + valueOrFallback(person.getWhatsapp(), "-")),
                createRelatedAside(valueOrFallback(person.getEmail(), "-"), "Open contact"));
        return row;
    }

    private Div createRelatedRow(Runnable action) {
        Div row = new Div();
        row.addClassName("contact-related-row");
        row.getElement().setAttribute("role", "button");
        row.getElement().setAttribute("tabindex", "0");
        row.addClickListener(event -> action.run());
        row.getElement().addEventListener("keydown", event -> action.run())
                .setFilter("event.key === 'Enter' || event.key === ' '");
        return row;
    }

    private Component createRelatedBody(String title, String meta, String caption) {
        Div body = new Div();
        body.addClassName("contact-related-body");
        Span titleSpan = new Span(valueOrFallback(title, "-"));
        titleSpan.addClassName("contact-related-title");
        Span metaSpan = new Span(valueOrFallback(meta, "-"));
        metaSpan.addClassName("contact-related-meta");
        Span captionSpan = new Span(valueOrFallback(caption, "-"));
        captionSpan.addClassName("contact-related-meta");
        body.add(titleSpan, metaSpan, captionSpan);
        return body;
    }

    private Component createRelatedAside(String primary, String secondary) {
        Div aside = new Div();
        aside.addClassName("contact-related-aside");
        Span primarySpan = new Span(valueOrFallback(primary, "-"));
        primarySpan.addClassName("contact-related-aside-primary");
        Span secondarySpan = new Span(secondary);
        secondarySpan.addClassName("contact-related-meta");
        aside.add(primarySpan, secondarySpan);
        return aside;
    }

    private Component createRelatedEmpty(String text) {
        Span empty = new Span(text);
        empty.addClassName("contact-related-empty");
        return empty;
    }

    private void openLeadDetail(Lead lead) {
        UI.getCurrent().navigate(LeadsView.class,
                QueryParameters.of("lead", String.valueOf(lead.getPublicId())));
    }

    private void openOpportunityDetail(Opportunity opportunity) {
        UI.getCurrent().navigate(OpportunitiesView.class,
                QueryParameters.of("opportunity", String.valueOf(opportunity.getPublicId())));
    }

    private void openPersonFromOrganizationDetail(Person person) {
        organizationLayout.setDetail(null);
        tabs.setSelectedTab(personTab);
        showTab(personLayout);
        openPersonForm(person);
    }

    private TextField phoneField(String label) {
        TextField field = new TextField(label);
        field.setClearButtonVisible(true);
        field.setPlaceholder("+6281234567890");
        field.getElement().setAttribute("inputmode", "tel");
        field.getElement().setAttribute("autocomplete", "tel");
        new InputMask("+000000000000000").extend(field);
        return field;
    }

    private Component actionBar(Button save, Button delete, Button cancel) {
        HorizontalLayout actions = new HorizontalLayout(save, delete, cancel);
        actions.addClassName("contact-form-actions");
        actions.setPadding(false);
        actions.setSpacing(true);
        return actions;
    }

    private <T> Grid.Column<T> addSortableTextColumn(Grid<T> grid, ValueProvider<T, String> valueProvider,
            String header) {
        return grid.addColumn(item -> displayText(valueProvider.apply(item)))
                .setHeader(header)
                .setAutoWidth(true)
                .setSortable(true)
                .setComparator(Comparator.comparing(item -> sortText(valueProvider.apply(item)),
                        String.CASE_INSENSITIVE_ORDER));
    }

    private void refreshPersons() {
        String keyword = normalizeSearch(searchTerm);
        personGrid.setItems(persons.stream()
                .filter(this::matchesOrganizationFilter)
                .filter(person -> keyword.isBlank() || matchesSearch(person, keyword))
                .toList());
    }

    private void refreshOrganizations() {
        String keyword = normalizeSearch(searchTerm);
        organizationGrid.setItems(companies.stream()
                .filter(company -> keyword.isBlank() || matchesSearch(company, keyword))
                .toList());
    }

    private void reloadContactDataAndRefreshGrids() {
        reloadContactData();
        refreshPersons();
        refreshOrganizations();
    }

    private boolean matchesSearch(Person person, String keyword) {
        return containsSearch(person.getFullName(), keyword)
                || containsSearch(person.getCompany() == null ? null : person.getCompany().getName(), keyword)
                || containsSearch(person.getJobTitle(), keyword)
                || containsSearch(person.getEmail(), keyword)
                || containsSearch(person.getPhone(), keyword)
                || containsSearch(person.getWhatsapp(), keyword)
                || containsSearch(person.getNotes(), keyword)
                || containsSearch(formatNumber(personSummary(person).leads()), keyword)
                || containsSearch(formatNumber(personSummary(person).opportunities()), keyword);
    }

    private boolean matchesOrganizationFilter(Person person) {
        if (selectedOrganizationFilter == null || selectedOrganizationFilter.getId() == null) {
            return true;
        }
        return person.getCompany() != null
                && Objects.equals(person.getCompany().getId(), selectedOrganizationFilter.getId());
    }

    private boolean matchesSearch(Company company, String keyword) {
        return containsSearch(company.getName(), keyword)
                || containsSearch(company.getIndustry(), keyword)
                || containsSearch(company.getWebsite(), keyword)
                || containsSearch(company.getEmail(), keyword)
                || containsSearch(company.getPhone(), keyword)
                || containsSearch(company.getCity() == null ? null : company.getCity().getName(), keyword)
                || containsSearch(company.getProvince() == null ? null : company.getProvince().getName(), keyword)
                || containsSearch(company.getCountry() == null ? null : company.getCountry().getName(), keyword)
                || containsSearch(company.getAddress(), keyword)
                || containsSearch(company.getNotes(), keyword)
                || containsSearch(formatNumber(companySummary(company).contacts()), keyword)
                || containsSearch(formatNumber(companySummary(company).leads()), keyword)
                || containsSearch(formatNumber(companySummary(company).opportunities()), keyword);
    }

    private LitRenderer<Person> personIdentityRenderer() {
        return personStackRenderer(Person::getFullName, person -> firstNonBlank(person.getJobTitle(), "No job title"));
    }

    private LitRenderer<Person> personContactRenderer() {
        return LitRenderer.<Person>of("""
                <div class="pipeline-grid-stack-cell">
                  <span class="pipeline-grid-primary">${item.organization}</span>
                  <span class="pipeline-grid-secondary">Email: ${item.email}</span>
                  <span class="pipeline-grid-secondary">Phone: ${item.phone}</span>
                  <span class="pipeline-grid-secondary">WhatsApp: ${item.whatsapp}</span>
                </div>
                """)
                .withProperty("organization", person -> valueOrFallback(personCompanyName(person), "No organization"))
                .withProperty("email", person -> valueOrFallback(person.getEmail(), "-"))
                .withProperty("phone", person -> valueOrFallback(person.getPhone(), "-"))
                .withProperty("whatsapp", person -> valueOrFallback(person.getWhatsapp(), "-"));
    }

    private LitRenderer<Person> personStackRenderer(
            Function<Person, String> primaryProvider,
            Function<Person, String> secondaryProvider) {
        return LitRenderer.<Person>of(stackTemplate())
                .withProperty("primary", person -> valueOrFallback(primaryProvider.apply(person), "-"))
                .withProperty("secondary", person -> valueOrFallback(secondaryProvider.apply(person), "-"));
    }

    private LitRenderer<Person> personMetricActionRenderer(
            Function<Person, String> primaryProvider,
            Function<Person, String> secondaryProvider,
            Function<Person, Long> countProvider,
            java.util.function.Consumer<Person> action) {
        return LitRenderer.<Person>of(metricActionTemplate())
                .withProperty("primary", person -> valueOrFallback(primaryProvider.apply(person), "-"))
                .withProperty("secondary", person -> valueOrFallback(secondaryProvider.apply(person), "-"))
                .withProperty("enabled", person -> countProvider.apply(person) > 0)
                .withFunction("openMetric", person -> {
                    if (countProvider.apply(person) <= 0) {
                        return;
                    }
                    openingRelatedDetail = true;
                    action.accept(person);
                });
    }

    private LitRenderer<Company> companyIdentityRenderer() {
        return companyStackRenderer(Company::getName, company -> firstNonBlank(company.getIndustry(), "No industry"));
    }

    private LitRenderer<Company> companyContactRenderer() {
        return LitRenderer.<Company>of("""
                <div class="pipeline-grid-stack-cell">
                  <span class="pipeline-grid-primary">${item.location}</span>
                  <span class="pipeline-grid-secondary">Email: ${item.email}</span>
                  <span class="pipeline-grid-secondary">Phone: ${item.phone}</span>
                  <span class="pipeline-grid-secondary">Website: ${item.website}</span>
                </div>
                """)
                .withProperty("location", this::companyLocation)
                .withProperty("email", company -> valueOrFallback(company.getEmail(), "-"))
                .withProperty("phone", company -> valueOrFallback(company.getPhone(), "-"))
                .withProperty("website", company -> valueOrFallback(company.getWebsite(), "-"));
    }

    private LitRenderer<Company> companyStackRenderer(
            Function<Company, String> primaryProvider,
            Function<Company, String> secondaryProvider) {
        return LitRenderer.<Company>of(stackTemplate())
                .withProperty("primary", company -> valueOrFallback(primaryProvider.apply(company), "-"))
                .withProperty("secondary", company -> valueOrFallback(secondaryProvider.apply(company), "-"));
    }

    private LitRenderer<Company> companyMetricActionRenderer(
            Function<Company, String> primaryProvider,
            Function<Company, String> secondaryProvider,
            Function<Company, Long> countProvider,
            java.util.function.Consumer<Company> action) {
        return LitRenderer.<Company>of(metricActionTemplate())
                .withProperty("primary", company -> valueOrFallback(primaryProvider.apply(company), "-"))
                .withProperty("secondary", company -> valueOrFallback(secondaryProvider.apply(company), "-"))
                .withProperty("enabled", company -> countProvider.apply(company) > 0)
                .withFunction("openMetric", company -> {
                    if (countProvider.apply(company) <= 0) {
                        return;
                    }
                    openingRelatedDetail = true;
                    action.accept(company);
                });
    }

    private String stackTemplate() {
        return """
                <div class="pipeline-grid-stack-cell">
                  <span class="pipeline-grid-primary">${item.primary}</span>
                  <span class="pipeline-grid-secondary">${item.secondary}</span>
                </div>
                """;
    }

    private String metricActionTemplate() {
        return """
                <div class="pipeline-grid-stack-cell contact-summary-metric-cell">
                  <button class="contact-summary-action" ?disabled="${!item.enabled}" @click="${openMetric}">
                    ${item.primary}
                  </button>
                  <span class="pipeline-grid-secondary">${item.secondary}</span>
                </div>
                """;
    }

    private Component createPersonIdentityCell(Person person) {
        return createStackCell(person.getFullName(), firstNonBlank(person.getJobTitle(), "No job title"));
    }

    private Component createPersonContactCell(Person person) {
        return createStackCell(
                valueOrFallback(personCompanyName(person), "No organization"),
                "Email: " + valueOrFallback(person.getEmail(), "-"),
                "Phone: " + valueOrFallback(person.getPhone(), "-"),
                "WhatsApp: " + valueOrFallback(person.getWhatsapp(), "-"));
    }

    private Component createCompanyIdentityCell(Company company) {
        return createStackCell(company.getName(), firstNonBlank(company.getIndustry(), "No industry"));
    }

    private Component createCompanyContactCell(Company company) {
        return createStackCell(
                companyLocation(company),
                "Email: " + valueOrFallback(company.getEmail(), "-"),
                "Phone: " + valueOrFallback(company.getPhone(), "-"),
                "Website: " + valueOrFallback(company.getWebsite(), "-"));
    }

    private Div createStackCell(String primaryText, String... secondaryTexts) {
        Div cell = new Div();
        cell.addClassName("pipeline-grid-stack-cell");

        Span primary = new Span(valueOrFallback(primaryText, "-"));
        primary.addClassName("pipeline-grid-primary");
        cell.add(primary);

        for (String secondaryText : secondaryTexts) {
            Span secondary = new Span(valueOrFallback(secondaryText, "-"));
            secondary.addClassName("pipeline-grid-secondary");
            cell.add(secondary);
        }
        return cell;
    }

    private Div createMetricCell(String primaryText, String secondaryText) {
        Div cell = createStackCell(primaryText, secondaryText);
        cell.addClassName("contact-summary-metric-cell");
        return cell;
    }

    private Div createMetricActionCell(String primaryText, String secondaryText, long count, Runnable action) {
        Div cell = new Div();
        cell.addClassNames("pipeline-grid-stack-cell", "contact-summary-metric-cell");

        Button button = new Button(primaryText, event -> {
            openingRelatedDetail = true;
            action.run();
        });
        button.addClassName("contact-summary-action");
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        button.setEnabled(count > 0);

        Span secondary = new Span(secondaryText);
        secondary.addClassName("pipeline-grid-secondary");

        cell.add(button, secondary);
        return cell;
    }

    private PersonContactSummaryDto personSummary(Person person) {
        PersonContactSummaryDto summary = person.getId() == null ? null : personSummaries.get(person.getId());
        return summary == null
                ? new PersonContactSummaryDto(
                        person.getId(),
                        person.getPublicId(),
                        person.getFullName(),
                        person.getJobTitle(),
                        personCompanyName(person),
                        person.getEmail(),
                        person.getPhone(),
                        person.getWhatsapp(),
                        0,
                        0,
                        0,
                        0,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        person.getUpdatedAt())
                : summary;
    }

    private CompanyContactSummaryDto companySummary(Company company) {
        CompanyContactSummaryDto summary = company.getId() == null ? null : companySummaries.get(company.getId());
        return summary == null
                ? new CompanyContactSummaryDto(
                        company.getId(),
                        company.getPublicId(),
                        company.getName(),
                        company.getIndustry(),
                        company.getEmail(),
                        company.getPhone(),
                        company.getWebsite(),
                        company.getCity() == null ? company.getLegacyCity() : company.getCity().getName(),
                        company.getProvince() == null ? company.getLegacyProvince() : company.getProvince().getName(),
                        company.getCountry() == null ? company.getLegacyCountry() : company.getCountry().getName(),
                        0,
                        0,
                        0,
                        0,
                        0,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        company.getUpdatedAt())
                : summary;
    }

    private String personCompanyName(Person person) {
        return person.getCompany() == null ? null : person.getCompany().getName();
    }

    private String companyLocation(Company company) {
        return Stream.of(
                        company.getCity() == null ? company.getLegacyCity() : company.getCity().getName(),
                        company.getProvince() == null ? company.getLegacyProvince() : company.getProvince().getName(),
                        company.getCountry() == null ? company.getLegacyCountry() : company.getCountry().getName())
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("No location");
    }

    private String leadAccount(Lead lead) {
        return firstNonBlank(
                lead.getCompany() == null ? null : lead.getCompany().getName(),
                lead.getPerson() == null ? null : lead.getPerson().getFullName(),
                lead.getRawCompanyName(),
                lead.getRawPersonName());
    }

    private String opportunityAccount(Opportunity opportunity) {
        return firstNonBlank(
                opportunity.getCompany() == null ? null : opportunity.getCompany().getName(),
                opportunity.getPerson() == null ? null : opportunity.getPerson().getFullName(),
                opportunity.getLead() == null ? null : leadAccount(opportunity.getLead()));
    }

    private String leadStatusName(Lead lead) {
        return lead.getStatus() == null ? valueOrFallback(lead.getStatusCode(), "No status") : lead.getStatus().getName();
    }

    private String opportunityStageName(Opportunity opportunity) {
        return opportunity.getStatus() == null
                ? valueOrFallback(opportunity.getStatusCode(), "No stage")
                : opportunity.getStatus().getName();
    }

    private String createdByName(com.quadteknologi.crm.domain.entity.User user) {
        return user == null ? "-" : valueOrFallback(user.getFullName(), user.getEmail());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Country findCountryById(List<Country> countries, Country country) {
        if (country == null || country.getId() == null) {
            return null;
        }
        return countries.stream()
                .filter(candidate -> Objects.equals(candidate.getId(), country.getId()))
                .findFirst()
                .orElse(null);
    }

    private Region findRegionById(List<Region> regions, Region region) {
        if (region == null || region.getId() == null) {
            return null;
        }
        return regions.stream()
                .filter(candidate -> Objects.equals(candidate.getId(), region.getId()))
                .findFirst()
                .orElse(null);
    }

    private boolean containsSearch(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String displayText(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String valueOrFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String formatNumber(long value) {
        return NumberFormat.getIntegerInstance(new Locale("id", "ID")).format(value);
    }

    private String formatCurrency(BigDecimal amount) {
        return formatRupiah(amount == null ? BigDecimal.ZERO : amount);
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? "-" : dateTime.format(DATE_TIME_FORMAT);
    }

    private String sortText(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeSearch(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void closePersonDetail() {
        personGrid.deselectAll();
        personLayout.setDetail(null);
    }

    private void closeOrganizationDetail() {
        organizationGrid.deselectAll();
        organizationLayout.setDetail(null);
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
