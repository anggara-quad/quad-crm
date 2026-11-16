package com.quadteknologi.crm.ui.util;

import com.quadteknologi.crm.domain.entity.InternalCompany;
import com.quadteknologi.crm.service.InternalCompanyService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;

import static com.quadteknologi.crm.util.TextUtils.initials;
import static com.quadteknologi.crm.util.TextUtils.trimToNull;

public final class InternalCompanyUi {

    private InternalCompanyUi() {
    }

    public static Component logo(InternalCompany company, InternalCompanyService service) {
        String alt = company.getName() == null ? "Internal company" : company.getName();
        String path = trimToNull(company.getLogoPath());
        if (path == null) {
            return logoFallback(company);
        }
        return service.readLogo(company)
                .<Component>map(data -> {
                    Image image = new Image(data.bytes(), alt, data.contentType());
                    image.addClassName("internal-company-logo");
                    return image;
                })
                .orElseGet(() -> {
                    Image image = new Image(path, alt);
                    image.addClassName("internal-company-logo");
                    return image;
                });
    }

    public static Div logoTile(InternalCompany company, InternalCompanyService service) {
        Div tile = new Div();
        tile.addClassName("company-logo-tile");
        tile.getElement().setAttribute("aria-hidden", "true");
        tile.add(logo(company, service));
        return tile;
    }

    public static Div swatch(String color) {
        Div swatch = new Div();
        swatch.addClassName("internal-company-swatch");
        swatch.getElement().getStyle().set("background-color", InternalCompanyService.normalizeColor(color));
        return swatch;
    }

    private static Div logoFallback(InternalCompany company) {
        Div fallback = new Div();
        fallback.addClassName("internal-company-logo-fallback");
        fallback.add(new Span(initials(company.getName(), "C", 2)));
        return fallback;
    }
}
