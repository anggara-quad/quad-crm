package com.quadteknologi.crm.ui.component;

import com.quadteknologi.crm.service.DashboardService.Metric;
import com.quadteknologi.crm.service.DashboardService.MetricDetailRow;
import com.quadteknologi.crm.service.DashboardService.MetricDetails;
import com.quadteknologi.crm.views.LeadsView;
import com.quadteknologi.crm.views.OpportunitiesView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.QueryParameters;

import java.time.format.DateTimeFormatter;

import static com.quadteknologi.crm.ui.util.CurrencyFormatter.formatNumber;
import static com.quadteknologi.crm.ui.util.CurrencyFormatter.formatRupiahOrZero;

public class DashboardMetricDialog extends Dialog {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public DashboardMetricDialog(Metric metric, MetricDetails details) {
        addClassName("dashboard-metric-dialog");
        setCloseOnEsc(true);
        setCloseOnOutsideClick(true);

        Div shell = new Div();
        shell.addClassName("dashboard-metric-dialog-shell");
        shell.add(createHeader(metric, details), createContent(details));
        add(shell);
    }

    private Component createHeader(Metric metric, MetricDetails details) {
        Div header = new Div();
        header.addClassName("dashboard-metric-dialog-header");

        Div titleGroup = new Div();
        titleGroup.addClassName("dashboard-metric-dialog-title-group");

        H3 title = new H3(metric.label() + " Details");
        Span summary = new Span(formatMetricValue(metric) + " | "
                + formatNumber(details.rows().size()) + " records | "
                + details.filter().startDate().format(DATE_FORMAT) + " - "
                + details.filter().endDate().format(DATE_FORMAT));
        titleGroup.add(title, summary);

        Button close = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> close());
        close.addClassName("dashboard-metric-dialog-close");
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        close.getElement().setAttribute("aria-label", "Close " + metric.label() + " details");

        header.add(titleGroup, close);
        return header;
    }

    private Component createContent(MetricDetails details) {
        Div content = new Div();
        content.addClassName("dashboard-metric-dialog-content");
        if (details.rows().isEmpty()) {
            Span empty = new Span("No detail data for the selected filters");
            empty.addClassName("dashboard-metric-dialog-empty");
            content.add(empty);
            return content;
        }

        details.rows().forEach(row -> content.add(createDetailRow(row)));
        return content;
    }

    private Component createDetailRow(MetricDetailRow detail) {
        Div row = new Div();
        row.addClassName("dashboard-metric-detail-row");

        boolean clickable = detail.publicId() != null
                && ("LEAD".equals(detail.targetType()) || "OPPORTUNITY".equals(detail.targetType()));
        if (clickable) {
            row.addClassName("dashboard-metric-detail-row-clickable");
            row.getElement().setAttribute("role", "button");
            row.getElement().setAttribute("tabindex", "0");
            row.addClickListener(event -> openTarget(detail));
            row.getElement().addEventListener("keydown", event -> openTarget(detail))
                    .setFilter("event.key === 'Enter' || event.key === ' '");
        }

        Div body = new Div();
        body.addClassName("dashboard-metric-detail-body");

        Span title = new Span(detail.title());
        title.addClassName("dashboard-metric-detail-title");
        Span meta = new Span(detail.meta());
        meta.addClassName("dashboard-metric-detail-meta");
        Span secondary = new Span(detail.secondary());
        secondary.addClassName("dashboard-metric-detail-meta");
        body.add(title, meta, secondary);

        Div aside = new Div();
        aside.addClassName("dashboard-metric-detail-aside");
        if (detail.amount() != null) {
            Span amount = new Span(formatRupiahOrZero(detail.amount()));
            amount.addClassName("dashboard-metric-detail-amount");
            aside.add(amount);
        }
        if (detail.margin() != null) {
            Span margin = new Span("Margin " + formatRupiahOrZero(detail.margin()));
            margin.addClassName("dashboard-metric-detail-meta");
            aside.add(margin);
        }

        Span badge = new Span(detail.badge());
        badge.addClassNames("dashboard-metric-detail-badge",
                "dashboard-metric-detail-badge-" + detail.badgeTone());

        row.add(body, aside, badge);
        if (clickable) {
            Icon open = VaadinIcon.EXTERNAL_LINK.create();
            open.addClassName("dashboard-metric-detail-open");
            row.add(open);
        }
        return row;
    }

    private void openTarget(MetricDetailRow detail) {
        close();
        if ("LEAD".equals(detail.targetType())) {
            UI.getCurrent().navigate(LeadsView.class,
                    QueryParameters.of("lead", String.valueOf(detail.publicId())));
            return;
        }
        if ("OPPORTUNITY".equals(detail.targetType())) {
            UI.getCurrent().navigate(OpportunitiesView.class,
                    QueryParameters.of("opportunity", String.valueOf(detail.publicId())));
        }
    }

    private String formatMetricValue(Metric metric) {
        if (metric.amount() != null) {
            return formatRupiahOrZero(metric.amount());
        }
        if (metric.percent()) {
            return metric.value() + "%";
        }
        return formatNumber(metric.value());
    }
}
