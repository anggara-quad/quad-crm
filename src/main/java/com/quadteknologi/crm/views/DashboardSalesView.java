package com.quadteknologi.crm.views;

import com.quadteknologi.crm.domain.entity.User;
import com.quadteknologi.crm.security.AppViewAccess;
import com.quadteknologi.crm.security.ViewAccessService;
import com.quadteknologi.crm.service.DashboardFilter;
import com.quadteknologi.crm.service.DashboardScope;
import com.quadteknologi.crm.service.DashboardService;
import com.quadteknologi.crm.service.DashboardService.DashboardData;
import com.quadteknologi.crm.service.DashboardService.Metric;
import com.quadteknologi.crm.service.DashboardSummaryDto;
import com.quadteknologi.crm.service.SalesLeadDetailDto;
import com.quadteknologi.crm.service.SalesOpportunityDetailDto;
import com.quadteknologi.crm.service.SalesPerformanceDto;
import com.quadteknologi.crm.ui.layout.MainLayout;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static com.quadteknologi.crm.ui.util.CurrencyFormatter.formatRupiah;

@PermitAll
@PageTitle("Dashboard Sales | Quad CRM")
@Route(value = "dashboard/sales", layout = MainLayout.class)
public class DashboardSalesView extends VerticalLayout implements BeforeEnterObserver {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private final DashboardService dashboardService;
    private final ViewAccessService viewAccessService;

    private DashboardFilter filter;
    private Long selectedSalesId;
    private DetailType selectedDetailType;
    private boolean loadScheduled;

    public DashboardSalesView(DashboardService dashboardService, ViewAccessService viewAccessService) {
        this.dashboardService = dashboardService;
        this.viewAccessService = viewAccessService;
        this.filter = dashboardService.defaultFilter();

        addClassNames("page-view", "dashboard-view", "dashboard-sales-view");
        setPadding(false);
        setSpacing(false);
        setSizeFull();

        showLoadingState();
        addAttachListener(event -> scheduleRender());
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        viewAccessService.checkBeforeEnter(event, AppViewAccess.DASHBOARD_SALES);
        if (!dashboardService.canUseTeamDashboard()) {
            event.forwardTo(ForbiddenView.class);
        }
    }

    @ClientCallable
    public void loadDashboardSales() {
        loadScheduled = false;
        render();
    }

    private void scheduleRender() {
        showLoadingState();
        if (loadScheduled) {
            return;
        }
        loadScheduled = true;
        getElement().executeJs("setTimeout(() => this.$server.loadDashboardSales(), 0)");
    }

    private void showLoadingState() {
        removeAll();
        add(createDashboardLoading("Dashboard Sales", "Loading sales performance..."));
    }

    private Component createDashboardLoading(String titleText, String message) {
        Div shell = new Div();
        shell.addClassName("dashboard-loading-state");
        H2 title = new H2(titleText);
        Paragraph text = new Paragraph(message);
        shell.add(title, text);
        return shell;
    }

    private void render() {
        removeAll();
        DashboardData data = dashboardService.getDashboardData(
                filter,
                selectedSalesId,
                selectedDetailType == DetailType.LEADS,
                selectedDetailType == DetailType.OPPORTUNITIES);
        filter = data.filter();

        add(createHeader(), createFilterBar(data), createMetricGrid(createMetrics(data)),
                createSalesPerformanceCard(data.salesPerformance()));
        createSelectedDetailCard(data.salesPerformance()).ifPresent(this::add);
    }

    private Component createHeader() {
        Header header = new Header();
        header.addClassName("dashboard-header");

        Div titleGroup = new Div();
        titleGroup.addClassName("dashboard-title-group");

        H2 title = new H2("Dashboard Sales");
        Paragraph subtitle = new Paragraph("Detailed sales performance by selected date range and sales owner.");
        titleGroup.add(title, subtitle);

        Span date = new Span(LocalDate.now().format(DATE_FORMAT));
        date.addClassName("dashboard-date");

        header.add(titleGroup, date);
        return header;
    }

    private Component createFilterBar(DashboardData data) {
        HorizontalLayout filters = new HorizontalLayout();
        filters.addClassName("dashboard-filter-bar");
        filters.setPadding(false);
        filters.setSpacing(true);

        DatePicker startDate = new DatePicker("From");
        startDate.addClassName("dashboard-filter-field");
        startDate.setValue(filter.startDate());

        DatePicker endDate = new DatePicker("To");
        endDate.addClassName("dashboard-filter-field");
        endDate.setValue(filter.endDate());

        ComboBox<User> sales = new ComboBox<>("Sales");
        sales.addClassName("dashboard-sales-filter");
        sales.setItems(data.salesUsers());
        sales.setItemLabelGenerator(User::getFullName);
        sales.setPlaceholder("All Sales");
        sales.setClearButtonVisible(true);
        sales.setValue(data.salesUsers().stream()
                .filter(user -> Objects.equals(user.getId(), filter.salesId()))
                .findFirst()
                .orElse(null));

        startDate.addValueChangeListener(event -> applyFilter(
                event.getValue(), endDate.getValue(), sales.getValue()));
        endDate.addValueChangeListener(event -> applyFilter(
                startDate.getValue(), event.getValue(), sales.getValue()));
        sales.addValueChangeListener(event -> applyFilter(
                startDate.getValue(), endDate.getValue(), event.getValue()));

        filters.add(startDate, endDate, sales);
        return filters;
    }

    private void applyFilter(LocalDate startDate, LocalDate endDate, User sales) {
        DashboardScope scope = sales == null ? DashboardScope.TEAM_DATA : DashboardScope.SELECTED_SALES;
        filter = filter
                .withScope(scope, sales == null ? null : sales.getId())
                .withDateRange(startDate, endDate);
        selectedSalesId = null;
        selectedDetailType = null;
        scheduleRender();
    }

    private List<Metric> createMetrics(DashboardData data) {
        DashboardSummaryDto summary = data.summary();
        List<Metric> metrics = new ArrayList<>();
        metrics.add(new Metric("Active Sales", summary.activeSales(), "Visible sales users", "contact"));
        metrics.add(new Metric("Leads", summary.leads(), "Created in selected period", "lead"));
        metrics.add(new Metric("Valid Leads", summary.validLeads(), "Qualified lead count", "lead"));
        metrics.add(new Metric("Contacts", summary.contacts(), "People created in selected period", "contact"));
        metrics.add(new Metric("Organizations", summary.organizations(), "Companies created in selected period", "contact"));
        metrics.add(new Metric("Open Pipeline", summary.openPipeline(),
                summary.openOpportunities() + " active opportunities | Margin " + formatCurrency(summary.openMargin()),
                "pipeline"));
        metrics.add(new Metric("Total Margin", summary.wonMargin(), "Won opportunities", "opportunity"));
        metrics.add(new Metric("Forecasted Margin", summary.forecastedMargin(), "Opportunities not won", "pipeline"));
        metrics.add(new Metric("Closed Win Rate", summary.closedWinRate(), "Won / (Won + Lost)", "lead", true));
        return metrics;
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

        Span value = new Span(formatMetricValue(metric));
        value.addClassName("dashboard-metric-value");

        Span caption = new Span(metric.caption());
        caption.addClassName("dashboard-metric-caption");

        body.add(label, value, caption);
        card.add(iconWrap, body);
        return card;
    }

    private Component createSalesPerformanceCard(List<SalesPerformanceDto> performance) {
        Div card = new Div();
        card.addClassNames("dashboard-card", "dashboard-list-card", "manager-sales-card");
        card.add(createCardTitle("Sales Detail"));

        if (performance.isEmpty()) {
            card.add(emptyState("No sales users for this filter"));
            return card;
        }

        Div table = new Div();
        table.addClassName("manager-sales-table");
        table.add(createSalesHeaderRow());
        performance.forEach(row -> table.add(createSalesRow(row)));

        card.add(table);
        return card;
    }

    private Component createSalesHeaderRow() {
        Div row = new Div();
        row.addClassNames("manager-sales-row", "manager-sales-header-row");
        row.add(
                cell("Sales", "manager-sales-cell", "manager-sales-person-cell"),
                cell("Leads / Valid", "manager-sales-cell"),
                cell("Open Pipeline", "manager-sales-cell"),
                cell("Won Revenue", "manager-sales-cell"),
                cell("Margin", "manager-sales-cell"),
                cell("Activity", "manager-sales-cell"));
        return row;
    }

    private Component createSalesRow(SalesPerformanceDto performance) {
        Div row = new Div();
        row.addClassName("manager-sales-row");

        Div person = new Div();
        person.addClassNames("manager-sales-cell", "manager-sales-person-cell");
        Span avatar = new Span(initials(performance.salesName()));
        avatar.addClassName("manager-sales-avatar");
        Div identity = new Div();
        identity.addClassName("manager-sales-identity");
        Span name = new Span(performance.salesName());
        name.addClassName("manager-sales-name");
        Span meta = new Span("Assigned owner");
        meta.addClassName("manager-sales-meta");
        identity.add(name, meta);
        person.add(avatar, identity);

        row.add(
                person,
                clickableMetricCell(
                        formatNumber(performance.leads()) + " / " + formatNumber(performance.validLeads()),
                        "Leads / valid",
                        performance,
                        DetailType.LEADS),
                clickableMetricCell(
                        formatCurrency(performance.openPipeline()),
                        formatNumber(performance.openOpportunities()) + " open opps | Margin "
                                + formatCurrency(performance.openMargin()),
                        performance,
                        DetailType.OPPORTUNITIES),
                metricCell(
                        formatCurrency(performance.wonRevenue()),
                        "Closed win " + performance.closedWinRate() + "%"),
                metricCell(
                        formatCurrency(performance.wonMargin()),
                        "Forecast " + formatCurrency(performance.forecastedMargin())),
                activityCell(performance));
        return row;
    }

    private java.util.Optional<Component> createSelectedDetailCard(List<SalesPerformanceDto> performance) {
        if (selectedSalesId == null || selectedDetailType == null) {
            return java.util.Optional.empty();
        }
        return performance.stream()
                .filter(row -> Objects.equals(row.salesId(), selectedSalesId))
                .findFirst()
                .map(row -> selectedDetailType == DetailType.LEADS
                        ? createLeadDetailCard(row)
                        : createOpportunityDetailCard(row));
    }

    private Component createLeadDetailCard(SalesPerformanceDto performance) {
        Div card = createDetailCardShell("Leads / Valid - " + performance.salesName());

        Div list = new Div();
        list.addClassName("sales-breakdown-list");
        if (performance.leadDetails().isEmpty()) {
            list.add(emptyState("No leads for this sales user in selected period"));
        } else {
            performance.leadDetails().forEach(lead -> list.add(createLeadDetailRow(lead)));
        }

        card.add(list);
        return card;
    }

    private Component createOpportunityDetailCard(SalesPerformanceDto performance) {
        Div card = createDetailCardShell("Open Pipeline - " + performance.salesName());

        Div list = new Div();
        list.addClassName("sales-breakdown-list");
        if (performance.opportunityDetails().isEmpty()) {
            list.add(emptyState("No open opportunities for this sales user in selected period"));
        } else {
            performance.opportunityDetails().forEach(opportunity -> list.add(createOpportunityDetailRow(opportunity)));
        }

        card.add(list);
        return card;
    }

    private Div createDetailCardShell(String titleText) {
        Div card = new Div();
        card.addClassNames("dashboard-card", "dashboard-list-card", "sales-breakdown-card");

        Div header = new Div();
        header.addClassName("sales-breakdown-header");
        H3 title = new H3(titleText);
        title.addClassName("dashboard-card-title");

        Button close = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> {
            selectedSalesId = null;
            selectedDetailType = null;
            scheduleRender();
        });
        close.addClassName("sales-breakdown-close");
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        close.getElement().setAttribute("aria-label", "Close detail");

        header.add(title, close);
        card.add(header);
        return card;
    }

    private Component createLeadDetailRow(SalesLeadDetailDto lead) {
        Div row = new Div();
        row.addClassNames("sales-breakdown-row", "sales-breakdown-clickable-row");
        row.getElement().setAttribute("role", "button");
        row.getElement().setAttribute("tabindex", "0");
        row.addClickListener(event -> openLeadDetail(lead));
        row.getElement().addEventListener("keydown", event -> openLeadDetail(lead))
                .setFilter("event.key === 'Enter' || event.key === ' '");

        Div body = new Div();
        body.addClassName("sales-breakdown-body");

        Span title = new Span(lead.title());
        title.addClassName("sales-breakdown-title");
        Span meta = new Span(lead.account() + " | " + lead.status() + " | " + lead.source());
        meta.addClassName("sales-breakdown-meta");
        Span created = new Span("Created " + formatDateTime(lead.createdAt()));
        created.addClassName("sales-breakdown-meta");
        body.add(title, meta, created);

        Span badge = new Span(lead.valid() ? "Valid" : "Lead");
        badge.addClassNames("sales-breakdown-badge", lead.valid() ? "sales-breakdown-badge-valid" : "sales-breakdown-badge-muted");

        Icon open = VaadinIcon.EXTERNAL_LINK.create();
        open.addClassName("sales-breakdown-open-icon");

        row.add(body, badge, open);
        return row;
    }

    private Component createOpportunityDetailRow(SalesOpportunityDetailDto opportunity) {
        Div row = new Div();
        row.addClassNames("sales-breakdown-row", "sales-breakdown-clickable-row");
        row.getElement().setAttribute("role", "button");
        row.getElement().setAttribute("tabindex", "0");
        row.addClickListener(event -> openOpportunityDetail(opportunity));
        row.getElement().addEventListener("keydown", event -> openOpportunityDetail(opportunity))
                .setFilter("event.key === 'Enter' || event.key === ' '");

        Div body = new Div();
        body.addClassName("sales-breakdown-body");

        Span title = new Span(opportunity.title());
        title.addClassName("sales-breakdown-title");
        Span meta = new Span(opportunity.account() + " | " + opportunity.stage()
                + " | " + valueOrFallback(opportunity.leadTitle(), "Direct opportunity"));
        meta.addClassName("sales-breakdown-meta");
        Span forecast = new Span("Expected close " + formatDate(opportunity.expectedCloseDate())
                + " | Probability " + formatProbability(opportunity.probability()));
        forecast.addClassName("sales-breakdown-meta");
        body.add(title, meta, forecast);

        Div money = new Div();
        money.addClassName("sales-breakdown-money");
        Span amount = new Span(formatCurrency(opportunity.amount()));
        amount.addClassName("sales-breakdown-amount");
        Span margin = new Span("Margin " + formatCurrency(opportunity.margin()));
        margin.addClassName("sales-breakdown-meta");
        money.add(amount, margin);

        Icon open = VaadinIcon.EXTERNAL_LINK.create();
        open.addClassName("sales-breakdown-open-icon");

        row.add(body, money, open);
        return row;
    }

    private void openLeadDetail(SalesLeadDetailDto lead) {
        UI.getCurrent().navigate(LeadsView.class,
                QueryParameters.of("lead", String.valueOf(lead.leadPublicId())));
    }

    private void openOpportunityDetail(SalesOpportunityDetailDto opportunity) {
        UI.getCurrent().navigate(OpportunitiesView.class,
                QueryParameters.of("opportunity", String.valueOf(opportunity.opportunityPublicId())));
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

    private Div cell(String text, String... classNames) {
        Div cell = new Div();
        cell.addClassNames(classNames);
        cell.setText(text);
        return cell;
    }

    private Div metricCell(String value, String caption) {
        Div cell = new Div();
        cell.addClassNames("manager-sales-cell", "manager-sales-metric-cell");
        Span primary = new Span(value);
        primary.addClassName("manager-sales-strong");
        Span secondary = new Span(caption);
        secondary.addClassName("manager-sales-meta");
        cell.add(primary, secondary);
        return cell;
    }

    private Div clickableMetricCell(
            String value,
            String caption,
            SalesPerformanceDto performance,
            DetailType detailType) {
        Div cell = metricCell(value, caption);
        cell.addClassName("manager-sales-clickable-cell");
        if (Objects.equals(selectedSalesId, performance.salesId()) && selectedDetailType == detailType) {
            cell.addClassName("manager-sales-clickable-cell-active");
        }
        cell.getElement().setAttribute("role", "button");
        cell.getElement().setAttribute("tabindex", "0");
        cell.addClickListener(event -> selectDetail(performance.salesId(), detailType));
        cell.getElement().addEventListener("keydown", event -> selectDetail(performance.salesId(), detailType))
                .setFilter("event.key === 'Enter' || event.key === ' '");
        return cell;
    }

    private void selectDetail(Long salesId, DetailType detailType) {
        selectedSalesId = salesId;
        selectedDetailType = detailType;
        scheduleRender();
    }

    private Div activityCell(SalesPerformanceDto performance) {
        Div cell = new Div();
        cell.addClassNames("manager-sales-cell", "manager-sales-activity-cell");

        Span date = new Span(formatDateTime(performance.lastActivity()));
        date.addClassName("manager-sales-strong");

        Span badge = new Span(performance.health());
        badge.addClassNames("manager-sales-badge", healthClass(performance.health()));

        Span closingSoon = new Span(formatNumber(performance.closingSoon()) + " closing soon");
        closingSoon.addClassName("manager-sales-meta");

        cell.add(date, badge, closingSoon);
        return cell;
    }

    private Icon resolveMetricIcon(String tone) {
        return switch (tone) {
            case "lead" -> VaadinIcon.BULLSEYE.create();
            case "opportunity", "pipeline" -> VaadinIcon.TRENDING_UP.create();
            case "contact" -> VaadinIcon.USERS.create();
            default -> VaadinIcon.CHART.create();
        };
    }

    private String formatMetricValue(Metric metric) {
        if (metric.amount() != null) {
            return formatCurrency(metric.amount());
        }
        if (metric.percent()) {
            return metric.value() + "%";
        }
        return formatNumber(metric.value());
    }

    private String healthClass(String health) {
        return switch (health) {
            case "Closing Soon" -> "manager-sales-badge-alert";
            case "Need Follow Up" -> "manager-sales-badge-warning";
            case "No Recent Activity" -> "manager-sales-badge-muted";
            default -> "manager-sales-badge-healthy";
        };
    }

    private String initials(String value) {
        if (value == null || value.isBlank()) {
            return "S";
        }
        String[] parts = value.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.ROOT);
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase(Locale.ROOT);
    }

    private String formatNumber(long value) {
        return NumberFormat.getIntegerInstance(new Locale("id", "ID")).format(value);
    }

    private String formatCurrency(BigDecimal amount) {
        return formatRupiah(amount == null ? BigDecimal.ZERO : amount);
    }

    private String formatDate(LocalDate date) {
        return date == null ? "-" : date.format(DATE_FORMAT);
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? "-" : dateTime.format(DATE_TIME_FORMAT);
    }

    private String formatProbability(Integer probability) {
        return probability == null ? "-" : probability + "%";
    }

    private String valueOrFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private enum DetailType {
        LEADS,
        OPPORTUNITIES
    }
}
