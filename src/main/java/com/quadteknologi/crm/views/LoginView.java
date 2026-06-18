package com.quadteknologi.crm.views;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.spring.security.AuthenticationContext;

@AnonymousAllowed
@PageTitle("Login | Quad CRM")
@Route("login")
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private final AuthenticationContext authenticationContext;
    private final LoginForm loginForm = new LoginForm(createLoginI18n());

    public LoginView(AuthenticationContext authenticationContext) {
        this.authenticationContext = authenticationContext;

        addClassName("login-view");
        setPadding(false);
        setSpacing(false);
        setSizeFull();

        loginForm.setAction("login");
        loginForm.setForgotPasswordButtonVisible(false);

        Div brand = new Div();
        brand.addClassName("login-brand");

        Image logo = new Image("images/logo.png", "Quad Logo");
        logo.setWidth("75px");

        brand.add(logo);

        Div card = new Div(brand, loginForm);
        card.addClassName("login-card");

        add(card);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (authenticationContext.isAuthenticated()) {
            event.forwardTo(DashboardView.class);
            return;
        }

        boolean hasError = event.getLocation()
                .getQueryParameters()
                .getParameters()
                .containsKey("error");
        loginForm.setError(hasError);
    }

    private static LoginI18n createLoginI18n() {
        LoginI18n i18n = LoginI18n.createDefault();
        LoginI18n.Form form = i18n.getForm();
        form.setTitle("Sign in");
        form.setUsername("Email");
        form.setPassword("Password");
        form.setSubmit("Sign in");
        i18n.setForm(form);

        LoginI18n.ErrorMessage errorMessage = i18n.getErrorMessage();
        errorMessage.setTitle("Login failed");
        errorMessage.setMessage("Check your email and password, then try again.");
        i18n.setErrorMessage(errorMessage);
        return i18n;
    }
}
