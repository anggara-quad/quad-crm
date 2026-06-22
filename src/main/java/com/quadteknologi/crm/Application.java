package com.quadteknologi.crm;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.Inline;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Meta;
import com.vaadin.flow.component.page.TargetElement;
import com.vaadin.flow.component.page.Viewport;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.theme.aura.Aura;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Meta(name = "theme-color", content = "#0d9668")
@Viewport("width=device-width, initial-scale=1")
@StyleSheet(Aura.STYLESHEET)
@StyleSheet("styles.css")
@PWA(name = "Quad CRM", shortName = "Q-CRM", iconPath = "images/icon.ico")
public class Application implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void configurePage(AppShellSettings settings) {
        AppShellConfigurator.super.configurePage(settings);
        settings.addFavIcon("icon", "images/icon.ico", "512x512");
        settings.addInlineWithContents(
                TargetElement.HEAD,
                Inline.Position.PREPEND,
                """
                        if ('serviceWorker' in navigator) {
                            navigator.serviceWorker.getRegistrations()
                                .then(registrations => registrations.forEach(registration => registration.unregister()));
                        }
                        """,
                Inline.Wrapping.JAVASCRIPT);
    }
}
