package com.quadteknologi.crm.views;

import com.quadteknologi.crm.domain.entity.Company;
import com.quadteknologi.crm.domain.entity.Country;
import com.quadteknologi.crm.domain.entity.Person;
import com.quadteknologi.crm.domain.entity.Region;
import com.quadteknologi.crm.service.ContactService;
import com.quadteknologi.crm.ui.layout.MainLayout;
import com.vaadin.componentfactory.addons.inputmask.InputMask;
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
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.function.ValueProvider;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Comparator;
import java.util.Locale;
import java.util.List;
import java.util.Objects;

@RolesAllowed({"Administrator", "Manager", "Sales"})
@PageTitle("Contact | Quad CRM")
@Route(value = "contact", layout = MainLayout.class)
public class ContactView extends VerticalLayout {

    private final ContactService contactService;

    private final MasterDetailLayout personLayout = new MasterDetailLayout();
    private final MasterDetailLayout organizationLayout = new MasterDetailLayout();
    private final Grid<Person> personGrid = new Grid<>(Person.class, false);
    private final Grid<Company> organizationGrid = new Grid<>(Company.class, false);

    private final Div content = new Div();
    private String searchTerm = "";

    public ContactView(ContactService contactService) {
        this.contactService = contactService;

        addClassNames("page-view", "contact-view");
        setPadding(false);
        setSpacing(false);
        setSizeFull();

        add(createHeader(), createTabs(), content);
        content.addClassName("contact-tab-content");

        configurePersonLayout();
        configureOrganizationLayout();
        showTab(personLayout);
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
            refreshPersons();
            refreshOrganizations();
        });
        return search;
    }

    private Component createTabs() {
        Tab personTab = new Tab("Person");
        Tab organizationTab = new Tab("Organization");
        Tabs tabs = new Tabs(personTab, organizationTab);
        tabs.addClassName("contact-tabs");
        tabs.addSelectedChangeListener(event -> {
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
        refreshPersons();
    }

    private Component createPersonMaster() {
        VerticalLayout master = new VerticalLayout();
        master.addClassName("contact-master");
        master.setPadding(false);
        master.setSpacing(false);
        master.setSizeFull();

        master.add(createMasterToolbar("Person", "New Person", event -> openPersonForm(new Person())));

        personGrid.addClassName("contact-grid");
        personGrid.setSizeFull();
        addSortableTextColumn(personGrid, Person::getFullName, "Name").setFlexGrow(1);
        addSortableTextColumn(personGrid,
                person -> person.getCompany() == null ? null : person.getCompany().getName(),
                "Organization").setFlexGrow(1);
        addSortableTextColumn(personGrid, Person::getJobTitle, "Job Title");
        addSortableTextColumn(personGrid, Person::getEmail, "Email");
        addSortableTextColumn(personGrid, Person::getPhone, "Phone");
        personGrid.asSingleSelect().addValueChangeListener(event -> {
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
        company.setItems(contactService.findAllCompanies());
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
                refreshPersons();
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
            refreshPersons();
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
        refreshOrganizations();
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
        addSortableTextColumn(organizationGrid, Company::getName, "Name").setFlexGrow(1);
        addSortableTextColumn(organizationGrid, Company::getIndustry, "Industry");
        addSortableTextColumn(organizationGrid, Company::getEmail, "Email");
        addSortableTextColumn(organizationGrid, Company::getPhone, "Phone");
        addSortableTextColumn(organizationGrid,
                company -> company.getCountry() == null ? null : company.getCountry().getName(),
                "Country");
        addSortableTextColumn(organizationGrid,
                company -> company.getProvince() == null ? null : company.getProvince().getName(),
                "Province");
        addSortableTextColumn(organizationGrid,
                company -> company.getCity() == null ? null : company.getCity().getName(),
                "City / Regency");
        organizationGrid.asSingleSelect().addValueChangeListener(event -> {
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
            selectedCountry = countries.get(0);
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
                refreshOrganizations();
                refreshPersons();
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
                refreshOrganizations();
                refreshPersons();
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

    private Div createDetailShell(String title) {
        Div detail = new Div();
        detail.addClassName("contact-detail");

        H3 heading = new H3(title);
        heading.addClassName("contact-detail-title");
        detail.add(heading);

        return detail;
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
        personGrid.setItems(contactService.findAllPersons()
                .stream()
                .filter(person -> keyword.isBlank() || matchesSearch(person, keyword))
                .toList());
    }

    private void refreshOrganizations() {
        String keyword = normalizeSearch(searchTerm);
        organizationGrid.setItems(contactService.findAllCompanies()
                .stream()
                .filter(company -> keyword.isBlank() || matchesSearch(company, keyword))
                .toList());
    }

    private boolean matchesSearch(Person person, String keyword) {
        return containsSearch(person.getFullName(), keyword)
                || containsSearch(person.getCompany() == null ? null : person.getCompany().getName(), keyword)
                || containsSearch(person.getJobTitle(), keyword)
                || containsSearch(person.getEmail(), keyword)
                || containsSearch(person.getPhone(), keyword)
                || containsSearch(person.getWhatsapp(), keyword)
                || containsSearch(person.getNotes(), keyword);
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
                || containsSearch(company.getNotes(), keyword);
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
