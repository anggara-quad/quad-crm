package com.quadteknologi.crm.ui.component;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.masterdetaillayout.MasterDetailLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class MasterDetailCrud<T> extends MasterDetailLayout {

    private final Grid<T> grid;
    private final Supplier<List<T>> itemsSupplier;
    private final Supplier<T> newItemSupplier;
    private final Function<T, Component> detailProvider;

    public MasterDetailCrud(
            String title,
            String actionLabel,
            Class<T> itemType,
            Supplier<T> newItemSupplier,
            Supplier<List<T>> itemsSupplier,
            Function<T, Component> detailProvider) {
        this.grid = new Grid<>(itemType, false);
        this.newItemSupplier = newItemSupplier;
        this.itemsSupplier = itemsSupplier;
        this.detailProvider = detailProvider;

        addClassName("contact-master-detail");
        setSizeFull();
        setMasterMinSize("640px");
        setDetailSize("420px");
        setOverlayMode(OverlayMode.DRAWER);
        setForceOverlay(true);
        addBackdropClickListener(event -> closeDetail());
        addDetailEscapePressListener(event -> closeDetail());
        setMaster(createMaster(title, actionLabel));
    }

    public Grid<T> getGrid() {
        return grid;
    }

    public void refresh() {
        grid.setItems(itemsSupplier.get());
    }

    public void openNew() {
        openItem(newItemSupplier.get());
    }

    public void openItem(T item) {
        setDetail(detailProvider.apply(item));
    }

    public void closeDetail() {
        grid.deselectAll();
        setDetail(null);
    }

    private Component createMaster(String title, String actionLabel) {
        VerticalLayout master = new VerticalLayout();
        master.addClassName("contact-master");
        master.setPadding(false);
        master.setSpacing(false);
        master.setSizeFull();

        master.add(createToolbar(title, actionLabel));

        grid.addClassName("contact-grid");
        grid.setSizeFull();
        grid.asSingleSelect().addValueChangeListener(event -> {
            if (event.getValue() != null) {
                openItem(event.getValue());
            }
        });

        master.add(grid);
        return master;
    }

    private Component createToolbar(String title, String actionLabel) {
        Div toolbar = new Div();
        toolbar.addClassName("contact-master-toolbar");

        Span titleSpan = new Span(title);
        titleSpan.addClassName("contact-master-title");

        Button action = new Button(actionLabel, VaadinIcon.PLUS.create(), event -> openNew());
        action.addClassName("pipeline-create-button");

        toolbar.add(titleSpan, action);
        return toolbar;
    }
}
