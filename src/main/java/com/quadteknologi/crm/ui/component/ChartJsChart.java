package com.quadteknologi.crm.ui.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.dom.Element;

import java.util.List;
import java.util.function.Consumer;

public class ChartJsChart extends Div {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CHART_JS_URL = "https://cdn.jsdelivr.net/npm/chart.js@4.5.0/dist/chart.umd.min.js";

    public ChartJsChart(
            String chartType,
            List<String> labels,
            List<Number> values,
            List<String> colors,
            boolean currencyMode,
            String datasetLabel) {
        this(chartType, labels, values, colors, currencyMode, datasetLabel, false, null);
    }

    public ChartJsChart(
            String chartType,
            List<String> labels,
            List<Number> values,
            List<String> colors,
            boolean currencyMode,
            String datasetLabel,
            boolean horizontal) {
        this(chartType, labels, values, colors, currencyMode, datasetLabel, horizontal, null);
    }

    public ChartJsChart(
            String chartType,
            List<String> labels,
            List<Number> values,
            List<String> colors,
            boolean currencyMode,
            String datasetLabel,
            Consumer<ChartSelection> selectionHandler) {
        this(chartType, labels, values, colors, currencyMode, datasetLabel, false, selectionHandler);
    }

    public ChartJsChart(
            String chartType,
            List<String> labels,
            List<Number> values,
            List<String> colors,
            boolean currencyMode,
            String datasetLabel,
            boolean horizontal,
            Consumer<ChartSelection> selectionHandler) {
        this(
                chartType,
                labels,
                List.of(new ChartDataset(datasetLabel, values, colors)),
                currencyMode,
                horizontal,
                selectionHandler);
    }

    public ChartJsChart(
            String chartType,
            List<String> labels,
            List<ChartDataset> datasets,
            boolean currencyMode,
            boolean horizontal,
            Consumer<ChartSelection> selectionHandler) {
        addClassName("dashboard-chart-canvas-wrap");

        Element canvas = new Element("canvas");
        getElement().appendChild(canvas);

        if (selectionHandler != null) {
            addClassName("dashboard-chart-clickable");
            getElement().addEventListener("chart-click", event -> selectionHandler.accept(new ChartSelection(
                            event.getEventData().get("event.detail.index").asInt(),
                            event.getEventData().get("event.detail.label").asText())))
                    .addEventData("event.detail.index")
                    .addEventData("event.detail.label");
        }

        addAttachListener(event -> event.getUI().beforeClientResponse(this, context ->
                getElement().executeJs("""
                        const host = this;
                        const canvas = host.querySelector('canvas');
                        const labels = JSON.parse($0);
                        const datasets = JSON.parse($1);
                        const chartType = $2;
                        const currencyMode = $3;
                        const sourceUrl = $4;
                        const horizontal = $5;
                        const clickable = $6;

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
                                    display: chartType === 'doughnut' || datasets.length > 1,
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
                                            const label = chartType === 'doughnut'
                                                ? (context.label || context.dataset.label)
                                                : context.dataset.label;
                                            return `${label}: ${value}`;
                                        }
                                    }
                                }
                            };

                            const chartDatasets = datasets.map((dataset) => ({
                                label: dataset.label,
                                data: dataset.values,
                                backgroundColor: dataset.colors && dataset.colors.length === 1
                                    ? dataset.colors[0]
                                    : dataset.colors,
                                borderRadius: chartType === 'bar' ? 8 : undefined,
                                borderSkipped: chartType === 'bar' ? false : undefined,
                                barThickness: chartType === 'bar' ? (horizontal ? 14 : 22) : undefined,
                                maxBarThickness: chartType === 'bar' ? (horizontal ? 22 : 28) : undefined,
                                borderColor: chartType === 'doughnut' ? '#FFFFFF' : undefined,
                                borderWidth: chartType === 'doughnut' ? 3 : undefined,
                                hoverOffset: chartType === 'doughnut' ? 4 : undefined
                            }));

                            const config = chartType === 'bar'
                                ? {
                                    type: 'bar',
                                    data: {
                                        labels,
                                        datasets: chartDatasets
                                    },
                                    options: {
                                        animation: { duration: 280 },
                                        indexAxis: horizontal ? 'y' : 'x',
                                        maintainAspectRatio: false,
                                        responsive: true,
                                        onHover: (event, elements) => {
                                            event.native.target.style.cursor = clickable && elements.length ? 'pointer' : 'default';
                                        },
                                        onClick: (event, elements) => {
                                            if (!clickable || !elements.length) {
                                                return;
                                            }
                                            const index = elements[0].index;
                                            host.dispatchEvent(new CustomEvent('chart-click', {
                                                bubbles: true,
                                                composed: true,
                                                detail: {
                                                    index,
                                                    label: labels[index],
                                                    value: datasets[0]?.values?.[index]
                                                }
                                            }));
                                        },
                                        plugins: { ...commonPlugins, legend: { ...commonPlugins.legend, display: datasets.length > 1 } },
                                        scales: {
                                            x: horizontal
                                                ? {
                                                    beginAtZero: true,
                                                    border: { display: false },
                                                    grid: { color: '#EEF2F6' },
                                                    ticks: {
                                                        color: '#667085',
                                                        font: { size: 11, weight: 600 },
                                                        callback: (value) => currencyMode ? formatCompact(value) : formatNumber(value)
                                                    }
                                                }
                                                : {
                                                    grid: { display: false },
                                                    ticks: { color: '#667085', font: { size: 11, weight: 600 } }
                                                },
                                            y: horizontal
                                                ? {
                                                    grid: { display: false },
                                                    ticks: { color: '#667085', font: { size: 11, weight: 600 } }
                                                }
                                                : {
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
                                        datasets: chartDatasets
                                    },
                                    options: {
                                        animation: { duration: 280 },
                                        cutout: '68%',
                                        maintainAspectRatio: false,
                                        responsive: true,
                                        onHover: (event, elements) => {
                                            event.native.target.style.cursor = clickable && elements.length ? 'pointer' : 'default';
                                        },
                                        onClick: (event, elements) => {
                                            if (!clickable || !elements.length) {
                                                return;
                                            }
                                            const index = elements[0].index;
                                            host.dispatchEvent(new CustomEvent('chart-click', {
                                                bubbles: true,
                                                composed: true,
                                                detail: {
                                                    index,
                                                    label: labels[index],
                                                    value: datasets[0]?.values?.[index]
                                                }
                                            }));
                                        },
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
                        toJson(datasets),
                        chartType,
                        currencyMode,
                        CHART_JS_URL,
                        horizontal,
                        selectionHandler != null)));
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize dashboard chart data", exception);
        }
    }

    public record ChartSelection(int index, String label) {
    }

    public record ChartDataset(String label, List<Number> values, List<String> colors) {

        public ChartDataset(String label, List<Number> values, String color) {
            this(label, values, List.of(color));
        }
    }
}
