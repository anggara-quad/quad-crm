package com.quadteknologi.crm.ui.util;

import com.vaadin.componentfactory.addons.inputmask.InputMask;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;

public final class UiComponents {

    private UiComponents() {
    }

    public static Component dialogActions(Button save, Dialog dialog) {
        Button cancel = new Button("Cancel", event -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout actions = new HorizontalLayout(save, cancel);
        actions.addClassNames("pipeline-form-actions", "quick-create-footer");
        actions.setPadding(false);
        actions.setSpacing(true);
        return actions;
    }

    public static HorizontalLayout fieldActionRow(Component field, Button action) {
        HorizontalLayout row = new HorizontalLayout(field, action);
        row.addClassName("pipeline-combo-action-row");
        row.setPadding(false);
        row.setSpacing(true);
        row.setWidthFull();
        row.setFlexGrow(1, field);
        return row;
    }

    public static TextField phoneField(String label) {
        TextField field = new TextField(label);
        field.setClearButtonVisible(true);
        field.setPlaceholder("+6281234567890");
        new InputMask("+000000000000000").extend(field);
        field.getElement().setAttribute("inputmode", "tel");
        field.getElement().setAttribute("autocomplete", "tel");
        return field;
    }

    public static Component quickCreateBody(Component... fields) {
        Div body = new Div();
        body.addClassName("quick-create-body");
        body.add(fields);
        return body;
    }

    public static Dialog quickCreateDialog(String titleText) {
        Dialog dialog = new Dialog();
        dialog.addClassName("quick-create-dialog");
        dialog.setCloseOnEsc(true);
        dialog.setCloseOnOutsideClick(false);

        Div header = new Div();
        header.addClassName("quick-create-header");
        H3 title = new H3(titleText);
        Button close = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> dialog.close());
        close.addClassName("pipeline-detail-close");
        close.getElement().setAttribute("aria-label", "Close " + titleText);
        header.add(title, close);

        dialog.add(header);
        return dialog;
    }
}
