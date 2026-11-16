package com.quadteknologi.crm.ui.layout;

import com.quadteknologi.crm.domain.entity.InternalCompany;
import com.quadteknologi.crm.domain.entity.User;
import com.quadteknologi.crm.security.AppViewAccess;
import com.quadteknologi.crm.security.CurrentUserService;
import com.quadteknologi.crm.security.ViewAccessService;
import com.quadteknologi.crm.service.ActiveInternalCompanySession;
import com.quadteknologi.crm.service.InternalCompanyService;
import com.quadteknologi.crm.views.ContactView;
import com.quadteknologi.crm.views.DashboardSalesView;
import com.quadteknologi.crm.views.DashboardView;
import com.quadteknologi.crm.views.InternalCompaniesView;
import com.quadteknologi.crm.views.LeadsView;
import com.quadteknologi.crm.views.OpportunitiesView;
import com.quadteknologi.crm.views.RoleAccessView;
import com.quadteknologi.crm.views.UserSettingsView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ModalityMode;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.RouterLink;
import jakarta.annotation.security.PermitAll;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.quadteknologi.crm.ui.util.InternalCompanyUi.logoTile;
import static com.quadteknologi.crm.ui.util.UiNotifications.showError;
import static com.quadteknologi.crm.ui.util.UiNotifications.showSuccess;
import static com.quadteknologi.crm.util.TextUtils.initials;
import static com.vaadin.flow.component.notification.Notification.Position.TOP_END;

@PermitAll
public class MainLayout extends AppLayout {

    private final CurrentUserService currentUserService;
    private final ViewAccessService viewAccessService;
    private final ActiveInternalCompanySession activeInternalCompanySession;
    private final InternalCompanyService internalCompanyService;

    public MainLayout(CurrentUserService currentUserService,
            ViewAccessService viewAccessService,
            ActiveInternalCompanySession activeInternalCompanySession,
            InternalCompanyService internalCompanyService) {
        this.currentUserService = currentUserService;
        this.viewAccessService = viewAccessService;
        this.activeInternalCompanySession = activeInternalCompanySession;
        this.internalCompanyService = internalCompanyService;

        setPrimarySection(Section.DRAWER);

        addToNavbar(createHeader());
        addToDrawer(createDrawer());
        addAttachListener(event -> applyCompanyAccent());
    }

    private Component createHeader() {
        Header header = new Header();
        header.addClassName("app-header");

        DrawerToggle drawerToggle = new DrawerToggle();
        drawerToggle.addClassName("app-drawer-toggle");

        Div spacer = new Div();
        spacer.addClassName("app-header-spacer");

        Div actions = new Div();
        actions.addClassName("app-header-actions");
        actions.add(createUserMenu());

        header.add(drawerToggle, spacer, actions);
        return header;
    }

    private Component createDrawer() {
        VerticalLayout drawerLayout = new VerticalLayout();
        drawerLayout.setSizeFull();

        drawerLayout.add(createCompanySwitcher());
        VerticalLayout navigationLayout = new VerticalLayout();
        navigationLayout.setSpacing(true);
        if (viewAccessService.canAccess(AppViewAccess.DASHBOARD)) {
            navigationLayout.add(createNavLink("Dashboard", VaadinIcon.DASHBOARD, DashboardView.class));
        }
        if (viewAccessService.canAccess(AppViewAccess.DASHBOARD_SALES)
                && (currentUserService.hasRole("Manager") || currentUserService.hasRole("Administrator"))) {
            navigationLayout.add(createNavLink("Dashboard Sales", VaadinIcon.CHART, DashboardSalesView.class));
        }
        if (viewAccessService.canAccess(AppViewAccess.LEADS)) {
            navigationLayout.add(createNavLink("Leads", VaadinIcon.BULLSEYE, LeadsView.class));
        }
        if (viewAccessService.canAccess(AppViewAccess.OPPORTUNITIES)) {
            navigationLayout.add(createNavLink("Opportunity", VaadinIcon.TRENDING_UP, OpportunitiesView.class));
        }
        if (viewAccessService.canAccess(AppViewAccess.CONTACT)) {
            navigationLayout.add(createNavLink("Contact", VaadinIcon.USER, ContactView.class));
        }
        if (viewAccessService.canAccess(AppViewAccess.USER_SETTINGS)) {
            navigationLayout.add(
                    createNavLink("User Settings", VaadinIcon.COG, UserSettingsView.class));
        }
        if (viewAccessService.canAccess(AppViewAccess.ROLE_ACCESS)) {
            navigationLayout.add(
                    createNavLink("Role Access", VaadinIcon.KEY, RoleAccessView.class));
        }
        if (viewAccessService.canAccess(AppViewAccess.INTERNAL_COMPANIES)) {
            navigationLayout.add(
                    createNavLink("Internal Companies", VaadinIcon.BUILDING, InternalCompaniesView.class));
        }

        Div spacer = new Div();
        spacer.addClassName("drawer-spacer");

        drawerLayout.add(navigationLayout, spacer);

        return drawerLayout;
    }

    private Component createCompanySwitcher() {
        Div switcher = new Div();
        switcher.addClassName("company-switcher");

        List<InternalCompany> companies = internalCompanyService.findActiveCompanies();
        if (companies.isEmpty()) {
            Image logoComponent = new Image("images/logo.png", "Quad Logo");
            logoComponent.addClassName("logo");
            switcher.add(logoComponent);
            return switcher;
        }

        Optional<InternalCompany> activeCompany = activeInternalCompanySession.getCompany()
                .flatMap(active -> companies.stream()
                        .filter(company -> Objects.equals(company.getId(), active.getId()))
                        .findFirst());

        Button trigger = new Button();
        trigger.addClassName("company-trigger");
        trigger.getElement().setAttribute("aria-haspopup", "dialog");
        activeCompany.ifPresent(company -> trigger.getElement()
                .setAttribute("aria-label", "Switch company: " + company.getName()));

        Div identity = new Div();
        identity.addClassNames("company-trigger-identity", "flex", "flex-col", "min-w-0");

        Span name = new Span(activeCompany.map(InternalCompany::getName).orElse("Select company"));
        name.addClassNames("company-trigger-name", "truncate");
        name.getElement().setAttribute("title", name.getText());

        Span meta = new Span("Internal company");
        meta.addClassName("company-trigger-meta");

        identity.add(name, meta);

        Icon chevron = VaadinIcon.CHEVRON_DOWN.create();
        chevron.addClassName("company-trigger-chevron");

        Component tile = activeCompany
                .<Component>map(company -> logoTile(company, internalCompanyService))
                .orElseGet(() -> logoTile(companies.get(0), internalCompanyService));

        Div content = new Div();
        content.addClassName("company-trigger-content");
        content.add(tile, identity, chevron);
        trigger.setIcon(content);
        trigger.getElement().removeAttribute("theme");

        Popover popover = new Popover();
        popover.setTarget(trigger);
        popover.setPosition(PopoverPosition.BOTTOM_START);
        popover.setWidth("min(280px, calc(100vw - 32px))");
        popover.addClassName("company-popover");
        popover.getElement().setAttribute("aria-label", "Switch company");

        RadioButtonGroup<InternalCompany> options = new RadioButtonGroup<>();
        options.addClassName("company-options");
        options.setLabel("Switch company");
        options.setItems(companies);
        options.setRenderer(new ComponentRenderer<>(company -> {
            Div row = new Div();
            row.addClassNames("company-option", "flex", "items-center", "gap-2.5", "min-w-0");
            Span rowName = new Span(company.getName());
            rowName.addClassName("truncate");
            row.add(logoTile(company, internalCompanyService), rowName);
            return row;
        }));
        activeCompany.ifPresent(options::setValue);
        options.addValueChangeListener(event -> {
            if (!event.isFromClient() || event.getValue() == null) {
                return;
            }
            activeInternalCompanySession.select(event.getValue());
            popover.close();
            UI.getCurrent().getPage().reload();
        });

        popover.add(options);
        switcher.add(trigger, popover);
        return switcher;
    }

    private void applyCompanyAccent() {
        activeInternalCompanySession.getCompany().ifPresent(company -> UI.getCurrent()
                .getPage()
                .executeJs("document.documentElement.style.setProperty('--aura-accent-color-light', $0)",
                        InternalCompanyService.normalizeColor(company.getPrimaryColor())));
    }

    private RouterLink createNavLink(String label, VaadinIcon icon, Class<? extends Component> viewClass) {
        RouterLink link = new RouterLink();
        link.addClassName("app-nav-link");
        link.setRoute(viewClass);
        link.add(icon.create(), new Span(label));
        return link;
    }

    private Component createUserMenu() {
        Optional<User> user = currentUserService.getUser();

        Button trigger = new Button(createAvatar(user));
        trigger.addClassName("app-user-trigger");
        trigger.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        trigger.getElement().setAttribute("aria-label", "Account menu");

        ContextMenu menu = new ContextMenu(trigger);
        menu.setOpenOnClick(true);
        menu.addComponent(createUserMenuContent(user, menu));

        return trigger;
    }

    private Component createAvatar(Optional<User> user) {
        Div avatar = new Div();
        avatar.addClassName("app-user-avatar");
        avatar.setText(user.map(User::getFullName).map(name -> initials(name, "U", 1)).orElse("U"));
        return avatar;
    }

    private Component createUserMenuContent(Optional<User> user, ContextMenu menu) {
        Div content = new Div();
        content.addClassName("app-user-menu-panel");

        Div summary = new Div();
        summary.addClassName("app-user-menu-summary");

        Span name = new Span(user.map(User::getFullName).orElse("Signed in"));
        name.addClassName("app-user-name");

        Span email = new Span(user.map(User::getEmail).orElse(""));
        email.addClassName("app-user-email");

        summary.add(name, email);

        Button changePassword = new Button("Change Password", VaadinIcon.LOCK.create(), event -> {
            menu.close();
            openChangePasswordDialog();
        });
        changePassword.addClassName("app-user-menu-action");
        changePassword.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button logout = new Button("Sign Out", VaadinIcon.SIGN_OUT.create(), event -> {
            menu.close();
            currentUserService.logout();
        });
        logout.addClassName("app-user-menu-action");
        logout.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        content.add(summary, changePassword, logout);
        return content;
    }

    private void openChangePasswordDialog() {
        Dialog dialog = new Dialog();
        dialog.addClassName("password-dialog");
        dialog.setModality(ModalityMode.STRICT);
        dialog.setDraggable(false);
        dialog.setResizable(false);

        Div content = new Div();
        content.addClassName("password-dialog-content");

        Div header = new Div();
        header.addClassName("password-dialog-header");
        H3 title = new H3("Change Password");
        Paragraph subtitle = new Paragraph("Update your sign-in password.");
        header.add(title, subtitle);

        PasswordField currentPassword = new PasswordField("Current Password");
        PasswordField newPassword = new PasswordField("New Password");
        PasswordField confirmPassword = new PasswordField("Confirm Password");
        currentPassword.setRevealButtonVisible(true);
        newPassword.setRevealButtonVisible(true);
        confirmPassword.setRevealButtonVisible(true);

        Div actions = new Div();
        actions.addClassName("password-dialog-actions");

        Button cancel = new Button("Cancel", event -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button save = new Button("Save Password", event -> {
            if (!newPassword.getValue().equals(confirmPassword.getValue())) {
                showError("Password confirmation does not match.", TOP_END);
                return;
            }
            try {
                currentUserService.changePassword(currentPassword.getValue(), newPassword.getValue());
                showSuccess("Password updated.", TOP_END);
                dialog.close();
            } catch (IllegalArgumentException exception) {
                showError(exception.getMessage(), TOP_END);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        actions.add(cancel, save);
        content.add(header, currentPassword, newPassword, confirmPassword, actions);
        dialog.add(content);
        dialog.open();
    }

}
