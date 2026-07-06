package com.quadteknologi.crm.security;

import com.quadteknologi.crm.views.CompaniesView;
import com.quadteknologi.crm.views.ContactView;
import com.quadteknologi.crm.views.DashboardSalesView;
import com.quadteknologi.crm.views.DashboardView;
import com.quadteknologi.crm.views.LeadsView;
import com.quadteknologi.crm.views.OpportunitiesView;
import com.quadteknologi.crm.views.PersonsView;
import com.quadteknologi.crm.views.RoleAccessView;
import com.quadteknologi.crm.views.UserSettingsView;
import com.vaadin.flow.component.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public enum AppViewAccess {

    DASHBOARD("Dashboard", DashboardView.class, true),
    DASHBOARD_SALES("Dashboard Sales", DashboardSalesView.class, true),
    LEADS("Leads", LeadsView.class, true),
    OPPORTUNITIES("Opportunities", OpportunitiesView.class, true),
    CONTACT("Contact", ContactView.class, true),
    COMPANIES("Companies", CompaniesView.class, false),
    PERSONS("Persons", PersonsView.class, false),
    USER_SETTINGS("User Settings", UserSettingsView.class, true),
    ROLE_ACCESS("Role Access", RoleAccessView.class, true);

    private final String label;
    private final Class<? extends Component> viewClass;
    private final boolean menuItem;

    AppViewAccess(String label, Class<? extends Component> viewClass, boolean menuItem) {
        this.label = label;
        this.viewClass = viewClass;
        this.menuItem = menuItem;
    }

    public String code() {
        return name();
    }

    public String label() {
        return label;
    }

    public Class<? extends Component> viewClass() {
        return viewClass;
    }

    public boolean menuItem() {
        return menuItem;
    }

    public static List<AppViewAccess> configurableViews() {
        return Arrays.asList(values());
    }

    public static Optional<AppViewAccess> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(view -> view.code().equals(code))
                .findFirst();
    }
}
