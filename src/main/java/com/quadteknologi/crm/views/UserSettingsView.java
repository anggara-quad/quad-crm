package com.quadteknologi.crm.views;

import com.quadteknologi.crm.domain.entity.Role;
import com.quadteknologi.crm.domain.entity.User;
import com.quadteknologi.crm.security.AppViewAccess;
import com.quadteknologi.crm.security.ViewAccessService;
import com.quadteknologi.crm.service.UserSettingsService;
import com.quadteknologi.crm.ui.component.MasterDetailCrud;
import com.quadteknologi.crm.ui.layout.MainLayout;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.quadteknologi.crm.ui.util.UiNotifications.showError;
import static com.quadteknologi.crm.ui.util.UiNotifications.showSuccess;
import static com.quadteknologi.crm.util.TextUtils.containsSearch;
import static com.quadteknologi.crm.util.TextUtils.normalizeSearch;

@PermitAll
@PageTitle("User Settings | Quad CRM")
@Route(value = "settings/users", layout = MainLayout.class)
public class UserSettingsView extends VerticalLayout implements BeforeEnterObserver {

    private final UserSettingsService userSettingsService;
    private final ViewAccessService viewAccessService;
    private final MasterDetailCrud<UserSettingsService.UserAccount> userCrud;
    private String searchTerm = "";

    public UserSettingsView(UserSettingsService userSettingsService, ViewAccessService viewAccessService) {
        this.userSettingsService = userSettingsService;
        this.viewAccessService = viewAccessService;

        addClassNames("page-view", "contact-view");
        setPadding(false);
        setSpacing(false);
        setSizeFull();

        userCrud = new MasterDetailCrud<>(
                "Users",
                "New User",
                UserSettingsService.UserAccount.class,
                () -> new UserSettingsService.UserAccount(new User(), List.of()),
                this::findFilteredUserAccounts,
                this::createUserForm);
        userCrud.getGrid().addClassName("settings-user-grid");
        configureGrid(userCrud.getGrid());
        userCrud.refresh();

        add(createHeader(), userCrud);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        viewAccessService.checkBeforeEnter(event, AppViewAccess.USER_SETTINGS);
    }

    private Component createHeader() {
        Header header = new Header();
        header.addClassName("pipeline-header");

        Div titleGroup = new Div();
        titleGroup.addClassName("pipeline-title-group");

        H2 title = new H2("User Settings");
        Paragraph subtitle = new Paragraph("Manage users and role assignment.");
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
        search.setPlaceholder("Search users");
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.EAGER);
        search.addValueChangeListener(event -> {
            searchTerm = event.getValue() == null ? "" : event.getValue();
            userCrud.refresh();
        });
        return search;
    }

    private List<UserSettingsService.UserAccount> findFilteredUserAccounts() {
        String keyword = normalizeSearch(searchTerm);
        List<UserSettingsService.UserAccount> users = userSettingsService.findUserAccounts();
        if (keyword.isBlank()) {
            return users;
        }
        return users.stream()
                .filter(account -> matchesSearch(account, keyword))
                .toList();
    }

    private boolean matchesSearch(UserSettingsService.UserAccount account, String keyword) {
        return containsSearch(account.user().getFullName(), keyword)
                || containsSearch(account.user().getEmail(), keyword)
                || containsSearch(account.roleNames(), keyword)
                || containsSearch(Boolean.TRUE.equals(account.user().getActive()) ? "active" : "inactive", keyword);
    }

    private void configureGrid(Grid<UserSettingsService.UserAccount> grid) {
        grid.addColumn(account -> account.user().getFullName())
                .setHeader("Name")
                .setAutoWidth(true)
                .setFlexGrow(1);
        grid.addColumn(account -> account.user().getEmail())
                .setHeader("Email")
                .setAutoWidth(true)
                .setFlexGrow(1);
        grid.addColumn(UserSettingsService.UserAccount::roleNames)
                .setHeader("Roles")
                .setAutoWidth(true)
                .setFlexGrow(1);
        grid.addColumn(account -> Boolean.TRUE.equals(account.user().getActive()) ? "Active" : "Inactive")
                .setHeader("Status")
                .setAutoWidth(true);
    }

    private Component createUserForm(UserSettingsService.UserAccount account) {
        User user = account.user();
        boolean editing = user.getId() != null;
        Binder<UserSettingsService.UserForm> binder = new Binder<>(UserSettingsService.UserForm.class);

        Div detail = createDetailShell(editing ? user.getFullName() : "New User");

        TextField fullName = new TextField("Full Name");
        EmailField email = new EmailField("Email");
        PasswordField password = new PasswordField(editing ? "New Password" : "Password");
        password.setRevealButtonVisible(true);
        Checkbox active = new Checkbox("Active");

        List<Role> activeRoles = userSettingsService.findActiveRoles();
        UserSettingsService.UserForm form = createFormModel(account, selectedRoles(activeRoles, account.roles()));
        CheckboxGroup<Role> roles = new CheckboxGroup<>("Roles");
        roles.addClassName("settings-role-group");
        roles.setItems(activeRoles);
        roles.setItemLabelGenerator(Role::getName);

        binder.forField(fullName)
                .asRequired("Full name is required")
                .bind(UserSettingsService.UserForm::getFullName, UserSettingsService.UserForm::setFullName);
        binder.forField(email)
                .asRequired("Email is required")
                .bind(UserSettingsService.UserForm::getEmail, UserSettingsService.UserForm::setEmail);
        if (editing) {
            binder.bind(password, UserSettingsService.UserForm::getPassword, UserSettingsService.UserForm::setPassword);
        } else {
            binder.forField(password)
                    .asRequired("Password is required")
                    .bind(UserSettingsService.UserForm::getPassword, UserSettingsService.UserForm::setPassword);
        }
        binder.bind(active, UserSettingsService.UserForm::getActive, UserSettingsService.UserForm::setActive);
        binder.bind(roles, UserSettingsService.UserForm::getRoles, UserSettingsService.UserForm::setRoles);
        binder.readBean(form);

        Paragraph passwordHint = new Paragraph(editing
                ? "Leave password empty to keep the current password."
                : "Password will be stored as a BCrypt hash.");
        passwordHint.addClassName("settings-form-hint");

        detail.add(fullName, email, password, passwordHint, active, roles,
                createUserActions(account, form, binder));
        return detail;
    }

    private UserSettingsService.UserForm createFormModel(
            UserSettingsService.UserAccount account,
            Set<Role> selectedRoles) {
        User user = account.user();
        UserSettingsService.UserForm form = new UserSettingsService.UserForm();
        form.setUserId(user.getId());
        form.setFullName(user.getFullName());
        form.setEmail(user.getEmail());
        form.setActive(user.getId() == null || Boolean.TRUE.equals(user.getActive()));
        form.setRoles(selectedRoles);
        return form;
    }

    private Set<Role> selectedRoles(List<Role> activeRoles, List<Role> selectedRoles) {
        Set<Long> selectedIds = selectedRoles.stream()
                .map(Role::getId)
                .collect(Collectors.toSet());
        return activeRoles.stream()
                .filter(role -> selectedIds.contains(role.getId()))
                .collect(Collectors.toSet());
    }

    private Div createDetailShell(String title) {
        Div detail = new Div();
        detail.addClassName("contact-detail");

        H3 heading = new H3(title);
        heading.addClassName("contact-detail-title");
        detail.add(heading);

        return detail;
    }

    private Component createUserActions(UserSettingsService.UserAccount account,
            UserSettingsService.UserForm form,
            Binder<UserSettingsService.UserForm> binder) {
        Button save = new Button("Save", VaadinIcon.CHECK.create(), event -> {
            try {
                binder.writeBean(form);
                userSettingsService.saveUser(form);
                userCrud.refresh();
                userCrud.closeDetail();
                showSuccess("User saved");
            } catch (ValidationException exception) {
                showError("Please complete required fields");
            } catch (DataIntegrityViolationException exception) {
                showError("Email already exists or user is still referenced");
            } catch (IllegalArgumentException exception) {
                showError(exception.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button delete = new Button("Delete", VaadinIcon.TRASH.create(), event -> {
            if (account.user().getId() == null) {
                userCrud.closeDetail();
                return;
            }
            try {
                userSettingsService.deleteUser(account.user());
                userCrud.refresh();
                userCrud.closeDetail();
                showSuccess("User deleted");
            } catch (DataIntegrityViolationException exception) {
                showError("User is still referenced by CRM records");
            }
        });
        delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);

        Button cancel = new Button("Cancel", event -> userCrud.closeDetail());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout actions = new HorizontalLayout(save, delete, cancel);
        actions.addClassName("contact-form-actions");
        actions.setPadding(false);
        actions.setSpacing(true);
        return actions;
    }

}
