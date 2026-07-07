package com.quadteknologi.crm.ui.layout;

import com.quadteknologi.crm.domain.entity.User;
import com.quadteknologi.crm.security.AppViewAccess;
import com.quadteknologi.crm.security.CurrentUserService;
import com.quadteknologi.crm.security.ViewAccessService;
import com.quadteknologi.crm.views.ContactView;
import com.quadteknologi.crm.views.DashboardSalesView;
import com.quadteknologi.crm.views.DashboardView;
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
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.RouterLink;
import jakarta.annotation.security.PermitAll;

import java.util.Optional;

import static com.quadteknologi.crm.ui.util.UiNotifications.showError;
import static com.quadteknologi.crm.ui.util.UiNotifications.showSuccess;
import static com.quadteknologi.crm.util.TextUtils.initials;
import static com.vaadin.flow.component.notification.Notification.Position.TOP_END;

@PermitAll
public class MainLayout extends AppLayout {

    private final CurrentUserService currentUserService;
    private final ViewAccessService viewAccessService;

    public MainLayout(CurrentUserService currentUserService, ViewAccessService viewAccessService) {
        this.currentUserService = currentUserService;
        this.viewAccessService = viewAccessService;

        setPrimarySection(Section.DRAWER);

        addToNavbar(createHeader());
        addToDrawer(createDrawer());
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

        Image logo = new Image("images/logo.png", "Quad Logo");
        logo.addClassName("logo");
        drawerLayout.add(logo);

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

        Div spacer = new Div();
        spacer.addClassName("drawer-spacer");

        drawerLayout.add(navigationLayout, spacer);

        return drawerLayout;
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
