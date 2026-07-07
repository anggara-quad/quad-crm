package com.quadteknologi.crm.views;

import com.quadteknologi.crm.domain.entity.User;
import com.quadteknologi.crm.security.AppViewAccess;
import com.quadteknologi.crm.security.CurrentUserService;
import com.quadteknologi.crm.security.ViewAccessService;
import com.quadteknologi.crm.service.ClosingSoonDto;
import com.quadteknologi.crm.service.DashboardFilter;
import com.quadteknologi.crm.service.DashboardScope;
import com.quadteknologi.crm.service.DashboardService;
import com.quadteknologi.crm.service.DashboardService.DashboardData;
import com.quadteknologi.crm.service.DashboardService.Metric;
import com.quadteknologi.crm.service.DashboardService.StatusSlice;
import com.quadteknologi.crm.service.DashboardSummaryDto;
import com.quadteknologi.crm.service.LeadSourcePerformanceDto;
import com.quadteknologi.crm.service.RecentActivityDto;
import com.quadteknologi.crm.ui.component.ChartJsChart;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import static com.quadteknologi.crm.ui.util.CurrencyFormatter.formatNumber;
import static com.quadteknologi.crm.ui.util.CurrencyFormatter.formatRupiahOrZero;

@PermitAll
@PageTitle("Dashboard | Quad CRM")
@Route(value = "", layout = MainLayout.class)
public class DashboardView extends VerticalLayout implements BeforeEnterObserver {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
    private static final List<String> CHART_COLORS = List.of(
            "#009689", "#2563EB", "#7C3AED", "#D97706", "#16A34A", "#DC2626", "#475467", "#0F766E");

    private final DashboardService dashboardService;
    private final CurrentUserService currentUserService;
    private final ViewAccessService viewAccessService;

    private DashboardFilter filter;
    private boolean loadScheduled;

    public DashboardView(
            DashboardService dashboardService,
            CurrentUserService currentUserService,
            ViewAccessService viewAccessService) {
        this.dashboardService = dashboardService;
        this.currentUserService = currentUserService;
        this.viewAccessService = viewAccessService;
        this.filter = dashboardService.defaultFilter();

        addClassNames("page-view", "dashboard-view");
        setPadding(false);
        setSpacing(false);
        setSizeFull();

        showLoadingState();
        addAttachListener(event -> scheduleRender());
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        viewAccessService.checkBeforeEnter(event, AppViewAccess.DASHBOARD);
    }

    @ClientCallable
    public void loadDashboard() {
        loadScheduled = false;
        render();
    }

    private void scheduleRender() {
        showLoadingState();
        if (loadScheduled) {
            return;
        }
        loadScheduled = true;
        getElement().executeJs("setTimeout(() => this.$server.loadDashboard(), 0)");
    }

    private void showLoadingState() {
        removeAll();
        add(createDashboardLoading("Dashboard", "Loading dashboard data..."));
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
        DashboardData data = dashboardService.getDashboardData(filter);
        filter = data.filter();

        add(createHeader(data));
        add(createFilterBar(data));
        if (hasInteractiveFilters(filter)) {
            add(createActiveFilterBar(data));
        }
        add(createMetricGrid(createMetrics(data)));
        add(createDashboardCharts(data));
        if (data.managerMode()) {
            add(createManagerGrid(data));
        } else {
            add(createSalesGrid(data));
        }
    }

    private Component createHeader(DashboardData data) {
        Header header = new Header();
        header.addClassName("dashboard-header");

        Div titleGroup = new Div();
        titleGroup.addClassName("dashboard-title-group");

        H2 title = new H2("Dashboard");
        Paragraph subtitle = new Paragraph(data.managerMode()
                ? "Team sales performance based on assigned leads and opportunities"
                : "Your assigned CRM activity and pipeline");
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

        if (!data.managerMode()) {
            startDate.addValueChangeListener(event -> applyDateFilter(event.getValue(), endDate.getValue()));
            endDate.addValueChangeListener(event -> applyDateFilter(startDate.getValue(), event.getValue()));
            filters.add(startDate, endDate);
            return filters;
        }

        ComboBox<User> sales = new ComboBox<>("Sales");
        sales.addClassName("dashboard-sales-filter");
        sales.setItems(data.salesUsers());
        sales.setItemLabelGenerator(User::getFullName);
        sales.setPlaceholder("All Sales");
        sales.setClearButtonVisible(true);
        sales.setValue(data.salesUsers().stream()
                .filter(user -> user.getId().equals(filter.salesId()))
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

    private void applyDateFilter(LocalDate startDate, LocalDate endDate) {
        filter = filter.withDateRange(startDate, endDate);
        scheduleRender();
    }

    private void applyFilter(LocalDate startDate, LocalDate endDate, User sales) {
        DashboardScope scope = sales == null ? DashboardScope.TEAM_DATA : DashboardScope.SELECTED_SALES;
        filter = filter
                .withScope(scope, sales == null ? null : sales.getId())
                .withDateRange(startDate, endDate);
        scheduleRender();
    }

    private List<Metric> createMetrics(DashboardData data) {
        DashboardSummaryDto summary = data.summary();
        List<Metric> metrics = new ArrayList<>();
        metrics.add(new Metric("Leads", summary.leads(), "Created in selected period", "lead"));
        metrics.add(new Metric("Valid Leads", summary.validLeads(), "Qualified lead count", "lead"));
        metrics.add(new Metric("Contacts", summary.contacts(), "People created in selected period", "contact"));
        metrics.add(new Metric("Organizations", summary.organizations(), "Companies created in selected period", "contact"));
        metrics.add(new Metric("Open Pipeline", summary.openPipeline(),
                summary.openOpportunities() + " active opportunities | Margin " + formatRupiahOrZero(summary.openMargin()),
                "pipeline"));
        metrics.add(new Metric("Won Revenue", summary.wonRevenue(),
                "Margin " + formatRupiahOrZero(summary.wonMargin()) + " | Won ratio " + summary.wonRatio() + "%",
                "opportunity"));
        metrics.add(new Metric("Total Margin", summary.wonMargin(), "Won opportunities", "opportunity"));
        metrics.add(new Metric("Forecasted Margin", summary.forecastedMargin(), "Opportunities not won", "pipeline"));
        metrics.add(new Metric("Closing Soon", summary.closingSoon(), "Expected in next 30 days", "contact"));
        if (data.managerMode()) {
            metrics.add(new Metric("Closed Win Rate", summary.closedWinRate(), "Won / (Won + Lost)", "lead", true));
            metrics.add(new Metric("Active Sales", summary.activeSales(), "Visible sales users", "contact"));
        }
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

    private Component createActiveFilterBar(DashboardData data) {
        Div bar = new Div();
        bar.addClassName("dashboard-active-filters");
        Span label = new Span("Filtered by");
        label.addClassName("dashboard-active-filters-label");
        bar.add(label);

        if (filter.leadStatusCode() != null) {
            bar.add(createFilterChip("Lead Status", statusLabel(data.leadQualification(), filter.leadStatusCode()),
                    () -> {
                        filter = filter.withLeadStatusCode(null);
                        scheduleRender();
                    }));
        }
        if (filter.opportunityStatusCode() != null) {
            bar.add(createFilterChip("Opportunity Status",
                    statusLabel(data.opportunityPipeline(), filter.opportunityStatusCode()), () -> {
                        filter = filter.withOpportunityStatusCode(null);
                        scheduleRender();
                    }));
        }
        if (filter.leadSource() != null) {
            bar.add(createFilterChip("Lead Source", filter.leadSource(), () -> {
                filter = filter.withLeadSource(null);
                scheduleRender();
            }));
        }

        Button clear = new Button("Clear all", VaadinIcon.CLOSE_SMALL.create(), event -> {
            filter = filter.clearInteractiveFilters();
            scheduleRender();
        });
        clear.addClassName("dashboard-clear-filter");
        clear.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        bar.add(clear);
        return bar;
    }

    private Component createFilterChip(String label, String value, Runnable clearHandler) {
        Button chip = new Button(label + ": " + value, VaadinIcon.CLOSE_SMALL.create(), event -> clearHandler.run());
        chip.addClassName("dashboard-filter-chip");
        chip.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        return chip;
    }

    private Component createDashboardCharts(DashboardData data) {
        Div grid = new Div();
        grid.addClassName("dashboard-chart-grid");
        grid.add(
                createStatusChart("Lead Qualification Status", data.leadQualification(), false),
                createStatusChart("Opportunity Pipeline by Value", data.opportunityPipeline(), true),
                createLeadSourceCard(data.managerMode() ? "Lead Source Performance" : "My Lead Sources",
                        data.leadSources()));
        return grid;
    }

    private Component createSalesGrid(DashboardData data) {
        Div grid = new Div();
        grid.addClassName("dashboard-main-grid");

        Div left = new Div();
        left.addClassName("dashboard-stack");
        left.add(createActivityCard("My Recent Activities", data.recentActivities(), false));

        Div right = new Div();
        right.addClassName("dashboard-stack");
        right.add(createClosingSoonCard("My Closing Soon", data.closingSoon(), false));

        grid.add(left, right);
        return grid;
    }

    private Component createManagerGrid(DashboardData data) {
        Div grid = new Div();
        grid.addClassName("manager-dashboard-grid");
        String selectedSalesName = selectedSalesName(data).orElse(null);
        boolean teamScope = selectedSalesName == null;

        Div left = new Div();
        left.addClassName("dashboard-stack");
        left.add(createActivityCard(teamScope ? "Recent Team Activities" : selectedSalesName + "'s Recent Activities",
                data.recentActivities(), teamScope));

        Div right = new Div();
        right.addClassName("dashboard-stack");
        right.add(createClosingSoonCard(teamScope ? "Team Closing Soon" : selectedSalesName + "'s Closing Soon",
                data.closingSoon(), teamScope));

        grid.add(left, right);
        return grid;
    }

    private Component createStatusChart(String title, List<StatusSlice> slices, boolean amountMode) {
        Div card = new Div();
        card.addClassNames("dashboard-card", "dashboard-chart-card");

        card.add(createCardTitle(title));
        if (slices.isEmpty() || slices.stream().noneMatch(slice -> slice.count() > 0)) {
            card.add(emptyState("No status data"));
            return card;
        }

        boolean hasAmount = amountMode;
        List<String> labels = slices.stream().map(StatusSlice::label).toList();
        List<Number> values = slices.stream()
                .map(slice -> hasAmount ? safeAmount(slice.amount()) : slice.count())
                .map(Number.class::cast)
                .toList();
        List<String> colors = slices.stream().map(slice -> statusColor(slice.color())).toList();

        card.add(new ChartJsChart(
                "bar",
                labels,
                values,
                colors,
                hasAmount,
                hasAmount ? "Estimated Amount" : "Records",
                true,
                selection -> {
                    if (selection.index() < 0 || selection.index() >= slices.size()) {
                        return;
                    }
                    StatusSlice slice = slices.get(selection.index());
                    if (title.startsWith("Lead")) {
                        toggleLeadStatus(slice.code());
                    } else {
                        toggleOpportunityStatus(slice.code());
                    }
                }));
        return card;
    }

    private Component createLeadSourceCard(String title, List<LeadSourcePerformanceDto> sources) {
        Div card = new Div();
        card.addClassNames("dashboard-card", "dashboard-list-card", "lead-source-card");
        card.add(createCardTitle(title));

        if (sources.isEmpty()) {
            card.add(emptyState("No lead source data"));
            return card;
        }

        List<String> labels = sources.stream().map(LeadSourcePerformanceDto::source).toList();
        List<Number> values = sources.stream()
                .map(LeadSourcePerformanceDto::totalLeads)
                .map(Number.class::cast)
                .toList();
        card.add(new ChartJsChart("bar", labels, values, colors(sources.size()), false, "Leads", true,
                selection -> {
                    if (selection.index() < 0 || selection.index() >= sources.size()) {
                        return;
                    }
                    toggleLeadSource(sources.get(selection.index()).source());
                }));

        Div table = new Div();
        table.addClassName("lead-source-table");
        table.add(createLeadSourceHeaderRow());
        sources.forEach(source -> table.add(createLeadSourceRow(source)));
        card.add(table);
        return card;
    }

    private Component createLeadSourceHeaderRow() {
        Div row = new Div();
        row.addClassNames("lead-source-row", "lead-source-header-row");
        row.add(
                cell("Source", "lead-source-cell", "lead-source-name-cell"),
                cell("Leads", "lead-source-cell"),
                cell("Valid", "lead-source-cell"),
                cell("Valid Rate", "lead-source-cell"),
                cell("Est. Value", "lead-source-cell", "lead-source-money-cell"),
                cell("Converted Opps", "lead-source-cell"),
                cell("Conversion Rate", "lead-source-cell"));
        return row;
    }

    private Component createLeadSourceRow(LeadSourcePerformanceDto source) {
        Div row = new Div();
        row.addClassName("lead-source-row");
        row.add(
                strongCell(source.source(), "lead-source-cell", "lead-source-name-cell"),
                strongCell(formatNumber(source.totalLeads()), "lead-source-cell"),
                strongCell(formatNumber(source.validLeads()), "lead-source-cell"),
                strongCell(source.validRate() + "%", "lead-source-cell"),
                strongCell(formatRupiahOrZero(source.estimatedLeadValue()), "lead-source-cell", "lead-source-money-cell"),
                strongCell(formatNumber(source.convertedOpportunities()), "lead-source-cell"),
                strongCell(source.conversionRate() + "%", "lead-source-cell"));
        return row;
    }

    private Component createActivityCard(String title, List<RecentActivityDto> activities, boolean showSales) {
        Div card = new Div();
        card.addClassNames("dashboard-card", "dashboard-list-card");
        card.add(createCardTitle(title));

        Div list = new Div();
        list.addClassName("dashboard-list");
        if (activities.isEmpty()) {
            list.add(emptyState("No recent activities"));
        } else {
            activities.forEach(activity -> list.add(createActivityRow(activity, showSales)));
        }

        card.add(list);
        return card;
    }

    private Component createActivityRow(RecentActivityDto activity, boolean showSales) {
        Div row = new Div();
        row.addClassName("dashboard-list-row");
        if (canOpenActivityTarget(activity)) {
            makeDashboardRowClickable(row, () -> openActivityTarget(activity));
        }

        Span type = new Span(activity.typeName());
        type.addClassNames("dashboard-activity-type", "dashboard-activity-" + activity.typeCode().toLowerCase(Locale.ROOT));

        Div body = new Div();
        body.addClassName("dashboard-list-body");

        Span title = new Span(activity.title());
        title.addClassName("dashboard-list-title");

        String metaText = showSales
                ? activity.target() + " - " + activity.salesName()
                : activity.target();
        Span meta = new Span(metaText);
        meta.addClassName("dashboard-list-meta");

        body.add(title, meta);

        Span date = new Span(formatDateTime(activity.activityDate()));
        date.addClassName("dashboard-list-date");

        row.add(type, body, date);
        if (canOpenActivityTarget(activity)) {
            Icon open = VaadinIcon.EXTERNAL_LINK.create();
            open.addClassName("dashboard-open-icon");
            row.add(open);
        }
        return row;
    }

    private Component createClosingSoonCard(String title, List<ClosingSoonDto> opportunities, boolean showSales) {
        Div card = new Div();
        card.addClassNames("dashboard-card", "dashboard-list-card");
        card.add(createCardTitle(title));

        Div list = new Div();
        list.addClassName("dashboard-list");
        if (opportunities.isEmpty()) {
            list.add(emptyState("No open opportunities closing in 30 days"));
        } else {
            opportunities.forEach(opportunity -> list.add(createOpportunityRow(opportunity, showSales)));
        }

        card.add(list);
        return card;
    }

    private Component createOpportunityRow(ClosingSoonDto opportunity, boolean showSales) {
        Div row = new Div();
        row.addClassName("dashboard-opportunity-row");
        makeDashboardRowClickable(row, () -> openOpportunityDetail(opportunity.opportunityPublicId()));

        Div body = new Div();
        body.addClassName("dashboard-list-body");

        Span title = new Span(opportunity.title());
        title.addClassName("dashboard-list-title");

        String metaText = showSales
                ? opportunity.account() + " - " + opportunity.salesName() + " - " + formatRupiahOrZero(opportunity.amount())
                : opportunity.account() + " - " + formatRupiahOrZero(opportunity.amount());
        Span meta = new Span(metaText);
        meta.addClassName("dashboard-list-meta");
        body.add(title, meta);

        Div side = new Div();
        side.addClassName("dashboard-opportunity-side");

        Span stage = new Span(opportunity.stage());
        stage.addClassNames("dashboard-stage-badge", "kanban-color-" + colorClass(opportunity.stageColor()));

        Span date = new Span(formatDate(opportunity.expectedCloseDate()));
        date.addClassName("dashboard-list-date");

        Icon open = VaadinIcon.EXTERNAL_LINK.create();
        open.addClassName("dashboard-open-icon");

        side.add(stage, date);
        row.add(body, side);
        row.add(open);
        return row;
    }

    private boolean canOpenActivityTarget(RecentActivityDto activity) {
        return activity.targetPublicId() != null
                && ("LEAD".equals(activity.targetType()) || "OPPORTUNITY".equals(activity.targetType()));
    }

    private void makeDashboardRowClickable(Div row, Runnable handler) {
        row.addClassName("dashboard-clickable-row");
        row.getElement().setAttribute("role", "button");
        row.getElement().setAttribute("tabindex", "0");
        row.addClickListener(event -> handler.run());
        row.getElement().addEventListener("keydown", event -> handler.run())
                .setFilter("event.key === 'Enter' || event.key === ' '");
    }

    private void openActivityTarget(RecentActivityDto activity) {
        if ("LEAD".equals(activity.targetType())) {
            UI.getCurrent().navigate(LeadsView.class,
                    QueryParameters.of("lead", String.valueOf(activity.targetPublicId())));
            return;
        }
        if ("OPPORTUNITY".equals(activity.targetType())) {
            openOpportunityDetail(activity.targetPublicId());
        }
    }

    private void openOpportunityDetail(java.util.UUID opportunityPublicId) {
        UI.getCurrent().navigate(OpportunitiesView.class,
                QueryParameters.of("opportunity", String.valueOf(opportunityPublicId)));
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

    private Div strongCell(String value, String... classNames) {
        Div cell = new Div();
        cell.addClassNames(classNames);
        Span text = new Span(value);
        text.addClassName("manager-sales-strong");
        cell.add(text);
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

    private List<String> colors(int size) {
        return java.util.stream.IntStream.range(0, size)
                .mapToObj(index -> CHART_COLORS.get(index % CHART_COLORS.size()))
                .toList();
    }

    private Optional<String> selectedSalesName(DashboardData data) {
        if (data.filter().scope() != DashboardScope.SELECTED_SALES || data.filter().salesId() == null) {
            return Optional.empty();
        }
        return data.salesUsers().stream()
                .filter(user -> user.getId().equals(data.filter().salesId()))
                .map(User::getFullName)
                .findFirst();
    }

    private void toggleLeadStatus(String statusCode) {
        filter = filter.withLeadStatusCode(Objects.equals(filter.leadStatusCode(), statusCode) ? null : statusCode);
        scheduleRender();
    }

    private void toggleOpportunityStatus(String statusCode) {
        filter = filter.withOpportunityStatusCode(
                Objects.equals(filter.opportunityStatusCode(), statusCode) ? null : statusCode);
        scheduleRender();
    }

    private void toggleLeadSource(String source) {
        filter = filter.withLeadSource(Objects.equals(filter.leadSource(), source) ? null : source);
        scheduleRender();
    }

    private boolean hasInteractiveFilters(DashboardFilter filter) {
        return filter.leadStatusCode() != null
                || filter.opportunityStatusCode() != null
                || filter.leadSource() != null;
    }

    private String statusLabel(List<StatusSlice> slices, String code) {
        return slices.stream()
                .filter(slice -> Objects.equals(slice.code(), code))
                .map(StatusSlice::label)
                .findFirst()
                .orElse(code);
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

    private String formatDate(LocalDate date) {
        return date == null ? "No date" : date.format(DATE_FORMAT);
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? "-" : dateTime.format(DATE_TIME_FORMAT);
    }
}
