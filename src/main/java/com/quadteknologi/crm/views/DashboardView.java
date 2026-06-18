package com.quadteknologi.crm.views;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quadteknologi.crm.security.CurrentUserService;
import com.quadteknologi.crm.service.DashboardService;
import com.quadteknologi.crm.service.DashboardService.ActivityItem;
import com.quadteknologi.crm.service.DashboardService.DashboardData;
import com.quadteknologi.crm.service.DashboardService.Funnel;
import com.quadteknologi.crm.service.DashboardService.Metric;
import com.quadteknologi.crm.service.DashboardService.OpportunityItem;
import com.quadteknologi.crm.service.DashboardService.StatusSlice;
import com.quadteknologi.crm.ui.layout.MainLayout;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@RolesAllowed({"Administrator", "Manager", "Sales"})
@PageTitle("Dashboard | Quad CRM")
@Route(value = "", layout = MainLayout.class)
public class DashboardView extends VerticalLayout {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CHART_JS_URL = "https://cdn.jsdelivr.net/npm/chart.js@4.5.0/dist/chart.umd.min.js";
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private final DashboardService dashboardService;
    private final CurrentUserService currentUserService;

    public DashboardView(DashboardService dashboardService, CurrentUserService currentUserService) {
        this.dashboardService = dashboardService;
        this.currentUserService = currentUserService;

        addClassNames("page-view", "dashboard-view");
        setPadding(false);
        setSpacing(false);
        setSizeFull();

        DashboardData data = dashboardService.getDashboardData();
        add(createHeader(), createMetricGrid(data.metrics()), createMainGrid(data));
    }

    private Component createHeader() {
        Header header = new Header();
        header.addClassName("dashboard-header");

        Div titleGroup = new Div();
        titleGroup.addClassName("dashboard-title-group");

        H2 title = new H2("Dashboard");
        Paragraph subtitle = new Paragraph("Welcome back, " + currentUserService.getDisplayName().orElse("Team"));
        titleGroup.add(title, subtitle);

        Span date = new Span(LocalDate.now().format(DATE_FORMAT));
        date.addClassName("dashboard-date");

        header.add(titleGroup, date);
        return header;
    }

    private Component createMetricGrid(List<Metric> metrics) {
        Div grid = new Div();
        grid.addClassName("dashboard-metrics");
        metrics.forEach(metric -> grid.add(createMetricCard(metric)));
        return grid;
    }

    private Component createMetricCard(Metric metric) {
        Div card = new Div();
        card.addClassNames("dashboard-card", "dashboard-metric-card", "dashboard-tone-" + metric.tone());

        Div iconWrap = new Div(resolveMetricIcon(metric.tone()));
        iconWrap.addClassName("dashboard-metric-icon");

        Div body = new Div();
        body.addClassName("dashboard-metric-body");

        Span label = new Span(metric.label());
        label.addClassName("dashboard-metric-label");

        Span value = new Span(metric.amount() == null ? formatNumber(metric.value()) : formatCurrency(metric.amount()));
        value.addClassName("dashboard-metric-value");

        Span caption = new Span(metric.caption());
        caption.addClassName("dashboard-metric-caption");

        body.add(label, value, caption);
        card.add(iconWrap, body);
        return card;
    }

    private Component createMainGrid(DashboardData data) {
        Div grid = new Div();
        grid.addClassName("dashboard-main-grid");

        Div left = new Div();
        left.addClassName("dashboard-stack");
        left.add(
                createStatusChart("Lead Status", data.leadStatus(), false),
                createFunnelCard(data.funnel()),
                createActivityCard(data.recentActivities()));

        Div right = new Div();
        right.addClassName("dashboard-stack");
        right.add(
                createStatusChart("Opportunity Pipeline", data.opportunityStatus(), true),
                createClosingSoonCard(data.closingSoon()));

        grid.add(left, right);
        return grid;
    }

    private Component createStatusChart(String title, List<StatusSlice> slices, boolean amountMode) {
        Div card = new Div();
        card.addClassNames("dashboard-card", "dashboard-chart-card");

        card.add(createCardTitle(title));
        if (slices.isEmpty()) {
            card.add(emptyState("No status data"));
            return card;
        }

        boolean hasAmount = amountMode && slices.stream().anyMatch(slice -> safeAmount(slice.amount()).signum() > 0);
        List<String> labels = slices.stream().map(StatusSlice::label).toList();
        List<Number> values = slices.stream()
                .map(slice -> hasAmount ? safeAmount(slice.amount()) : slice.count())
                .map(Number.class::cast)
                .toList();
        List<String> colors = slices.stream().map(slice -> statusColor(slice.color())).toList();

        card.add(createChartJsCanvas(
                hasAmount ? "bar" : "doughnut",
                labels,
                values,
                colors,
                hasAmount,
                hasAmount ? "Estimated Amount" : "Records"));
        return card;
    }

    private Component createChartJsCanvas(
            String chartType,
            List<String> labels,
            List<Number> values,
            List<String> colors,
            boolean currencyMode,
            String datasetLabel) {
        Div wrapper = new Div();
        wrapper.addClassName("dashboard-chart-canvas-wrap");

        Element canvas = new Element("canvas");
        wrapper.getElement().appendChild(canvas);

        wrapper.addAttachListener(event -> event.getUI().beforeClientResponse(wrapper, context ->
                wrapper.getElement().executeJs("""
                        const host = this;
                        const canvas = host.querySelector('canvas');
                        const labels = JSON.parse($0);
                        const values = JSON.parse($1);
                        const colors = JSON.parse($2);
                        const chartType = $3;
                        const currencyMode = $4;
                        const datasetLabel = $5;
                        const sourceUrl = $6;

                        const formatNumber = (value) => new Intl.NumberFormat('id-ID', {
                            maximumFractionDigits: 0
                        }).format(value || 0);
                        const formatCurrency = (value) => new Intl.NumberFormat('id-ID', {
                            style: 'currency',
                            currency: 'IDR',
                            maximumFractionDigits: 0
                        }).format(value || 0);
                        const formatCompact = (value) => new Intl.NumberFormat('id-ID', {
                            notation: 'compact',
                            maximumFractionDigits: 1
                        }).format(value || 0);

                        const drawChart = () => {
                            if (!canvas || !window.Chart) {
                                return;
                            }
                            if (host.__chartInstance) {
                                host.__chartInstance.destroy();
                            }

                            const commonPlugins = {
                                legend: {
                                    display: chartType === 'doughnut',
                                    position: 'bottom',
                                    labels: {
                                        boxWidth: 8,
                                        boxHeight: 8,
                                        color: '#475467',
                                        font: { size: 11, weight: 600 },
                                        usePointStyle: true
                                    }
                                },
                                tooltip: {
                                    callbacks: {
                                        label: (context) => {
                                            const raw = Number(context.raw || 0);
                                            const value = currencyMode ? formatCurrency(raw) : formatNumber(raw);
                                            return `${context.label}: ${value}`;
                                        }
                                    }
                                }
                            };

                            const config = chartType === 'bar'
                                ? {
                                    type: 'bar',
                                    data: {
                                        labels,
                                        datasets: [{
                                            label: datasetLabel,
                                            data: values,
                                            backgroundColor: colors,
                                            borderRadius: 8,
                                            borderSkipped: false,
                                            barThickness: 24,
                                            maxBarThickness: 30
                                        }]
                                    },
                                    options: {
                                        animation: { duration: 280 },
                                        maintainAspectRatio: false,
                                        responsive: true,
                                        plugins: { ...commonPlugins, legend: { display: false } },
                                        scales: {
                                            x: {
                                                grid: { display: false },
                                                ticks: { color: '#667085', font: { size: 11, weight: 600 } }
                                            },
                                            y: {
                                                beginAtZero: true,
                                                border: { display: false },
                                                grid: { color: '#EEF2F6' },
                                                ticks: {
                                                    color: '#667085',
                                                    font: { size: 11, weight: 600 },
                                                    callback: (value) => currencyMode ? formatCompact(value) : formatNumber(value)
                                                }
                                            }
                                        }
                                    }
                                }
                                : {
                                    type: 'doughnut',
                                    data: {
                                        labels,
                                        datasets: [{
                                            label: datasetLabel,
                                            data: values,
                                            backgroundColor: colors,
                                            borderColor: '#FFFFFF',
                                            borderWidth: 3,
                                            hoverOffset: 4
                                        }]
                                    },
                                    options: {
                                        animation: { duration: 280 },
                                        cutout: '68%',
                                        maintainAspectRatio: false,
                                        responsive: true,
                                        plugins: commonPlugins
                                    }
                                };

                            host.__chartInstance = new Chart(canvas, config);
                        };

                        if (window.Chart) {
                            drawChart();
                            return;
                        }

                        if (!window.__quadChartJsLoading) {
                            window.__quadChartJsLoading = new Promise((resolve, reject) => {
                                const script = document.createElement('script');
                                script.src = sourceUrl;
                                script.async = true;
                                script.onload = resolve;
                                script.onerror = reject;
                                document.head.appendChild(script);
                            });
                        }

                        window.__quadChartJsLoading
                            .then(drawChart)
                            .catch(() => host.classList.add('dashboard-chart-error'));
                        """,
                        toJson(labels),
                        toJson(values),
                        toJson(colors),
                        chartType,
                        currencyMode,
                        datasetLabel,
                        CHART_JS_URL)));

        return wrapper;
    }

    private Component createFunnelCard(Funnel funnel) {
        Div card = new Div();
        card.addClassNames("dashboard-card", "dashboard-funnel-card");
        card.add(createCardTitle("Lead Funnel"));

        Div rows = new Div();
        rows.addClassName("dashboard-funnel");
        rows.add(
                createFunnelRow("Valid Leads", funnel.validLeads(), funnel.validRate(), "#009689"),
                createFunnelRow("Opportunities", funnel.opportunities(), funnel.opportunityRate(), "#2563EB"),
                createFunnelRow("Won", funnel.wonOpportunities(), funnel.wonRate(), "#16A34A"));

        card.add(rows);
        return card;
    }

    private Component createFunnelRow(String labelText, long value, int percent, String color) {
        Div row = new Div();
        row.addClassName("dashboard-funnel-row");

        Div top = new Div();
        top.addClassName("dashboard-funnel-meta");
        top.add(new Span(labelText), new Span(formatNumber(value) + " / " + percent + "%"));

        Div track = new Div();
        track.addClassName("dashboard-funnel-track");
        Div fill = new Div();
        fill.addClassName("dashboard-funnel-fill");
        fill.getStyle().set("width", Math.max(4, percent) + "%");
        fill.getStyle().set("background", color);
        track.add(fill);

        row.add(top, track);
        return row;
    }

    private Component createActivityCard(List<ActivityItem> activities) {
        Div card = new Div();
        card.addClassNames("dashboard-card", "dashboard-list-card");
        card.add(createCardTitle("Recent Activities"));

        Div list = new Div();
        list.addClassName("dashboard-list");
        if (activities.isEmpty()) {
            list.add(emptyState("No recent activities"));
        } else {
            activities.forEach(activity -> list.add(createActivityRow(activity)));
        }

        card.add(list);
        return card;
    }

    private Component createActivityRow(ActivityItem activity) {
        Div row = new Div();
        row.addClassName("dashboard-list-row");

        Div marker = new Div();
        marker.addClassName("dashboard-list-marker");

        Div body = new Div();
        body.addClassName("dashboard-list-body");

        Span title = new Span(activity.title());
        title.addClassName("dashboard-list-title");

        Span meta = new Span(activity.type() + " - " + activity.target());
        meta.addClassName("dashboard-list-meta");

        body.add(title, meta);

        Span date = new Span(formatDateTime(activity.activityDate()));
        date.addClassName("dashboard-list-date");

        row.add(marker, body, date);
        return row;
    }

    private Component createClosingSoonCard(List<OpportunityItem> opportunities) {
        Div card = new Div();
        card.addClassNames("dashboard-card", "dashboard-list-card");
        card.add(createCardTitle("Closing Soon"));

        Div list = new Div();
        list.addClassName("dashboard-list");
        if (opportunities.isEmpty()) {
            list.add(emptyState("No open opportunities"));
        } else {
            opportunities.forEach(opportunity -> list.add(createOpportunityRow(opportunity)));
        }

        card.add(list);
        return card;
    }

    private Component createOpportunityRow(OpportunityItem opportunity) {
        Div row = new Div();
        row.addClassName("dashboard-opportunity-row");

        Div body = new Div();
        body.addClassName("dashboard-list-body");

        Span title = new Span(opportunity.title());
        title.addClassName("dashboard-list-title");

        Span meta = new Span(opportunity.account() + " - " + formatCurrency(opportunity.amount()));
        meta.addClassName("dashboard-list-meta");
        body.add(title, meta);

        Div side = new Div();
        side.addClassName("dashboard-opportunity-side");

        Span stage = new Span(opportunity.stage());
        stage.addClassNames("dashboard-stage-badge", "kanban-color-" + colorClass(opportunity.stageColor()));

        Span date = new Span(formatDate(opportunity.expectedCloseDate()));
        date.addClassName("dashboard-list-date");

        side.add(stage, date);
        row.add(body, side);
        return row;
    }

    private Component createCardTitle(String titleText) {
        H3 title = new H3(titleText);
        title.addClassName("dashboard-card-title");
        return title;
    }

    private Component emptyState(String text) {
        Span empty = new Span(text);
        empty.addClassName("dashboard-empty");
        return empty;
    }

    private Icon resolveMetricIcon(String tone) {
        return switch (tone) {
            case "lead" -> VaadinIcon.BULLSEYE.create();
            case "opportunity", "pipeline" -> VaadinIcon.TRENDING_UP.create();
            case "contact" -> VaadinIcon.USERS.create();
            default -> VaadinIcon.CHART.create();
        };
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private String statusColor(String color) {
        return switch (colorClass(color)) {
            case "success" -> "#16A34A";
            case "warning" -> "#F59E0B";
            case "error" -> "#DC2626";
            case "contrast" -> "#64748B";
            case "primary" -> "#009689";
            default -> "#2563EB";
        };
    }

    private String colorClass(String color) {
        return Optional.ofNullable(color)
                .filter(value -> !value.isBlank())
                .orElse("default");
    }

    private String formatNumber(long value) {
        return NumberFormat.getIntegerInstance(new Locale("id", "ID")).format(value);
    }

    private String formatCurrency(BigDecimal amount) {
        return CURRENCY_FORMAT.format(safeAmount(amount));
    }

    private String formatDate(LocalDate date) {
        return date == null ? "No date" : date.format(DATE_FORMAT);
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? "No date" : dateTime.format(DATE_TIME_FORMAT);
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize dashboard chart data", exception);
        }
    }
}
