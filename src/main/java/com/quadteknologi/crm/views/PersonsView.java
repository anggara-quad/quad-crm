package com.quadteknologi.crm.views;

import com.quadteknologi.crm.ui.layout.MainLayout;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

@RolesAllowed({"Administrator", "Manager", "Sales"})
@PageTitle("Persons | Quad CRM")
@Route(value = "persons", layout = MainLayout.class)
public class PersonsView extends VerticalLayout {

    public PersonsView() {
        addClassName("page-view");
        setPadding(false);
        setSpacing(false);

        add(new H1("Persons"));
    }
}
