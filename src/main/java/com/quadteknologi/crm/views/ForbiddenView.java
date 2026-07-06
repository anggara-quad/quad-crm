package com.quadteknologi.crm.views;

import com.quadteknologi.crm.ui.layout.MainLayout;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@PermitAll
@PageTitle("403 | Quad CRM")
@Route(value = "403", layout = MainLayout.class)
public class ForbiddenView extends VerticalLayout {

    public ForbiddenView() {
        addClassNames("page-view", "forbidden-view");
        setPadding(false);
        setSpacing(false);
        setSizeFull();

        add(createContent());
    }

    private Component createContent() {
        Div panel = new Div();
        panel.addClassName("forbidden-panel");

        Div icon = new Div(VaadinIcon.LOCK.create());
        icon.addClassName("forbidden-icon");

        Span code = new Span("403");
        code.addClassName("forbidden-code");

        H2 title = new H2("Access Denied");
        Paragraph message = new Paragraph("You do not have permission to open this page. Contact an administrator if you need access.");

        Button back = new Button("Back to Dashboard", VaadinIcon.ARROW_LEFT.create(),
                event -> getUI().ifPresent(ui -> ui.navigate(DashboardView.class)));
        back.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        back.addClassName("forbidden-action");

        panel.add(icon, code, title, message, back);
        return panel;
    }
}
