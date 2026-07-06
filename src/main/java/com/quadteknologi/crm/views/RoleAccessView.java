package com.quadteknologi.crm.views;

import com.quadteknologi.crm.security.AppViewAccess;
import com.quadteknologi.crm.security.ViewAccessService;
import com.quadteknologi.crm.service.UserSettingsService;
import com.quadteknologi.crm.ui.layout.MainLayout;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
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
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@PermitAll
@PageTitle("Role Access | Quad CRM")
@Route(value = "settings/role-access", layout = MainLayout.class)
public class RoleAccessView extends VerticalLayout implements BeforeEnterObserver {

    private final UserSettingsService userSettingsService;
    private final ViewAccessService viewAccessService;
    private final MasterDetailLayout layout = new MasterDetailLayout();
    private final Grid<UserSettingsService.RoleAccessSettings> grid = new Grid<>(
            UserSettingsService.RoleAccessSettings.class, false);

    private String searchTerm = "";

    public RoleAccessView(UserSettingsService userSettingsService, ViewAccessService viewAccessService) {
        this.userSettingsService = userSettingsService;
        this.viewAccessService = viewAccessService;

        addClassNames("page-view", "contact-view", "role-access-view");
        setPadding(false);
        setSpacing(false);
        setSizeFull();

        configureLayout();
        configureGrid();
        refreshGrid();

        add(createHeader(), layout);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        viewAccessService.checkBeforeEnter(event, AppViewAccess.ROLE_ACCESS);
    }

    private Component createHeader() {
        Header header = new Header();
        header.addClassName("pipeline-header");

        Div titleGroup = new Div();
        titleGroup.addClassName("pipeline-title-group");

        H2 title = new H2("Role Access");
        Paragraph subtitle = new Paragraph("Manage page access by role.");
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
        search.setPlaceholder("Search roles");
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.EAGER);
        search.addValueChangeListener(event -> {
            searchTerm = event.getValue() == null ? "" : event.getValue();
            refreshGrid();
        });
        return search;
    }

    private void configureLayout() {
        layout.addClassName("contact-master-detail");
        layout.setSizeFull();
        layout.setMasterMinSize("640px");
        layout.setDetailSize("460px");
        layout.setOverlayMode(MasterDetailLayout.OverlayMode.DRAWER);
        layout.setForceOverlay(true);
        layout.addBackdropClickListener(event -> closeDetail());
        layout.addDetailEscapePressListener(event -> closeDetail());
        layout.setMaster(createMaster());
    }

    private Component createMaster() {
        VerticalLayout master = new VerticalLayout();
        master.addClassName("contact-master");
        master.setPadding(false);
        master.setSpacing(false);
        master.setSizeFull();

        Div toolbar = new Div();
        toolbar.addClassName("contact-master-toolbar");

        Span title = new Span("Roles");
        title.addClassName("contact-master-title");

        Button refresh = new Button("Refresh", VaadinIcon.REFRESH.create(), event -> refreshGrid());
        refresh.addClassName("pipeline-create-button");

        toolbar.add(title, refresh);

        grid.addClassName("contact-grid");
        grid.setSizeFull();
        master.add(toolbar, grid);
        return master;
    }

    private void configureGrid() {
        grid.addColumn(settings -> settings.role().getName())
                .setHeader("Role")
                .setAutoWidth(true)
                .setFlexGrow(1);
        grid.addColumn(settings -> settings.role().getCode())
                .setHeader("Code")
                .setAutoWidth(true);
        grid.addColumn(settings -> settings.views().size() + " pages")
                .setHeader("Access")
                .setAutoWidth(true);
        grid.addColumn(settings -> accessSummary(settings.views()))
                .setHeader("Pages")
                .setFlexGrow(2);

        grid.asSingleSelect().addValueChangeListener(event -> {
            if (event.getValue() != null) {
                openDetail(event.getValue());
            }
        });
    }

    private void refreshGrid() {
        grid.setItems(findFilteredSettings());
    }

    private List<UserSettingsService.RoleAccessSettings> findFilteredSettings() {
        String keyword = normalizeSearch(searchTerm);
        List<UserSettingsService.RoleAccessSettings> settings = userSettingsService.findRoleAccessSettings();
        if (keyword.isBlank()) {
            return settings;
        }
        return settings.stream()
                .filter(item -> containsSearch(item.role().getName(), keyword)
                        || containsSearch(item.role().getCode(), keyword)
                        || containsSearch(accessSummary(item.views()), keyword))
                .toList();
    }

    private void openDetail(UserSettingsService.RoleAccessSettings settings) {
        layout.setDetail(createDetail(settings));
    }

    private Component createDetail(UserSettingsService.RoleAccessSettings settings) {
        Div detail = new Div();
        detail.addClassName("contact-detail");

        H3 title = new H3(settings.role().getName());
        title.addClassName("contact-detail-title");

        Paragraph subtitle = new Paragraph("Choose which pages this role can open and see in the navigation.");
        subtitle.addClassName("settings-form-hint");

        CheckboxGroup<AppViewAccess> views = new CheckboxGroup<>("Page Access");
        views.addClassName("role-access-field");
        views.setItems(userSettingsService.findConfigurableViews());
        views.setItemLabelGenerator(AppViewAccess::label);
        views.setValue(new LinkedHashSet<>(settings.views()));

        HorizontalLayout actions = new HorizontalLayout();
        actions.addClassName("contact-form-actions");
        actions.setPadding(false);
        actions.setSpacing(true);

        Button save = new Button("Save Access", VaadinIcon.CHECK.create(), event -> {
            userSettingsService.saveRoleAccess(settings.role().getId(), views.getValue());
            refreshGrid();
            closeDetail();
            showSuccess("Access updated for " + settings.role().getName());
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancel = new Button("Cancel", event -> closeDetail());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        actions.add(save, cancel);
        detail.add(title, subtitle, views, actions);
        return detail;
    }

    private void closeDetail() {
        grid.deselectAll();
        layout.setDetail(null);
    }

    private String accessSummary(java.util.Set<AppViewAccess> views) {
        if (views == null || views.isEmpty()) {
            return "-";
        }
        return views.stream()
                .map(AppViewAccess::label)
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse("-");
    }

    private boolean containsSearch(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String normalizeSearch(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void showSuccess(String message) {
        Notification notification = Notification.show(message, 2500, Notification.Position.BOTTOM_END);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void showError(String message) {
        Notification notification = Notification.show(Objects.toString(message, "Action failed"), 3500,
                Notification.Position.BOTTOM_END);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
