package com.quadteknologi.crm.ui.component;

import com.quadteknologi.crm.domain.entity.OptionValue;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.masterdetaillayout.MasterDetailLayout;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class KanbanBoard<T> extends MasterDetailLayout {

    private final String itemLabel;
    private final String createButtonLabel;
    private final String emptyTitle;
    private final String emptyText;
    private final Function<T, CardData> cardDataProvider;
    private final Function<T, Component> detailProvider;
    private final Consumer<OptionValue> createHandler;

    public KanbanBoard(
            String itemLabel,
            String createButtonLabel,
            String emptyTitle,
            String emptyText,
            List<OptionValue> statuses,
            Map<String, List<T>> itemsByStatus,
            Function<T, CardData> cardDataProvider,
            Function<T, Component> detailProvider) {
        this(itemLabel, createButtonLabel, emptyTitle, emptyText, statuses, itemsByStatus, cardDataProvider,
                detailProvider, null);
    }

    public KanbanBoard(
            String itemLabel,
            String createButtonLabel,
            String emptyTitle,
            String emptyText,
            List<OptionValue> statuses,
            Map<String, List<T>> itemsByStatus,
            Function<T, CardData> cardDataProvider,
            Function<T, Component> detailProvider,
            Consumer<OptionValue> createHandler) {
        this.itemLabel = itemLabel;
        this.createButtonLabel = createButtonLabel;
        this.emptyTitle = emptyTitle;
        this.emptyText = emptyText;
        this.cardDataProvider = cardDataProvider;
        this.detailProvider = detailProvider;
        this.createHandler = createHandler;

        addClassName("kanban-master-detail");
        setSizeFull();
        setMasterMinSize("720px");
        setDetailSize("360px");
        setOverlayMode(OverlayMode.DRAWER);
        setForceOverlay(true);
        addBackdropClickListener(event -> setDetail(null));
        addDetailEscapePressListener(event -> setDetail(null));
        setMaster(createBoard(statuses, itemsByStatus));
    }

    private Component createBoard(List<OptionValue> statuses, Map<String, List<T>> itemsByStatus) {
        Div board = new Div();
        board.addClassName("kanban-board");

        statuses.forEach(status -> board.add(createColumn(
                status,
                itemsByStatus.getOrDefault(status.getCode(), List.of()))));

        return board;
    }

    private Component createColumn(OptionValue status, List<T> items) {
        Div column = new Div();
        column.addClassNames("kanban-column", "kanban-color-" + normalizeColor(status.getColor()));

        Div columnHeader = new Div();
        columnHeader.addClassName("kanban-column-header");

        Div titleGroup = new Div();
        titleGroup.addClassName("kanban-column-title-group");
        Span title = new Span(status.getName() + " (" + items.size() + ")");
        title.addClassName("kanban-column-title");
        Span summary = new Span(items.size() + " " + itemLabel + (items.size() == 1 ? "" : "s"));
        summary.addClassName("kanban-column-summary");
        titleGroup.add(title, summary);

        Button addButton = new Button(VaadinIcon.PLUS.create());
        addButton.addClassName("kanban-column-add");
        addButton.getElement().setAttribute("aria-label", createButtonLabel + " in " + status.getName());
        if (createHandler != null) {
            addButton.addClickListener(event -> createHandler.accept(status));
        }

        columnHeader.add(titleGroup, addButton);

        Div progress = new Div();
        progress.addClassName("kanban-column-progress");

        Div cards = new Div();
        cards.addClassName("kanban-cards");
        if (items.isEmpty()) {
            cards.add(createEmptyState(status));
        } else {
            items.forEach(item -> cards.add(createCard(item)));
        }

        column.add(columnHeader, progress, cards);
        return column;
    }

    private Component createCard(T item) {
        CardData cardData = cardDataProvider.apply(item);

        Div card = new Div();
        card.addClassName("kanban-card");
        card.getElement().setAttribute("tabindex", "0");
        card.getElement().setAttribute("role", "button");
        card.addClickListener(event -> setDetail(detailProvider.apply(item)));

        Div cardHeader = new Div();
        cardHeader.addClassName("kanban-card-header");

        Span avatar = new Span(cardData.avatarText());
        avatar.addClassName("kanban-avatar");

        Div identity = new Div();
        identity.addClassName("kanban-identity");
        Span primary = new Span(cardData.primaryText());
        primary.addClassName("kanban-card-primary");
        Span secondary = new Span(cardData.secondaryText());
        secondary.addClassName("kanban-card-secondary");
        identity.add(primary, secondary);
        cardHeader.add(avatar, identity);

        Span title = new Span(cardData.title());
        title.addClassName("kanban-card-title");

        Div tags = new Div();
        tags.addClassName("kanban-tags");
        cardData.tags().forEach(tag -> {
            if (tag.label() == null || tag.label().isBlank()) {
                return;
            }
            Span tagElement = new Span(tag.label());
            tagElement.addClassNames("kanban-tag", "kanban-tag-" + tag.type());
            tags.add(tagElement);
        });

        card.add(cardHeader, title, tags);
        return card;
    }

    private Component createEmptyState(OptionValue status) {
        Div empty = new Div();
        empty.addClassName("kanban-empty-state");

        Div illustration = new Div();
        illustration.addClassName("kanban-empty-illustration");
        illustration.add(VaadinIcon.BULLSEYE.create());

        Span title = new Span(emptyTitle);
        title.addClassName("kanban-empty-title");
        Span text = new Span(emptyText);
        text.addClassName("kanban-empty-text");

        Button createButton = new Button(createButtonLabel, VaadinIcon.PLUS.create());
        createButton.addClassName("kanban-empty-button");
        if (createHandler != null) {
            createButton.addClickListener(event -> createHandler.accept(status));
        }

        empty.add(illustration, title, text, createButton);
        return empty;
    }

    private String normalizeColor(String color) {
        return color == null || color.isBlank() ? "default" : color.toLowerCase().replace('_', '-');
    }

    public record CardData(
            String avatarText,
            String primaryText,
            String secondaryText,
            String title,
            List<TagData> tags) {
    }

    public record TagData(String label, String type) {
    }
}
