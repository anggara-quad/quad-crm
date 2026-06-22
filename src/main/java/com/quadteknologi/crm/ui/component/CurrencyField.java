package com.quadteknologi.crm.ui.component;

import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public class CurrencyField extends CustomField<BigDecimal> {

    private static final Locale INDONESIA = Locale.forLanguageTag("id-ID");

    private final TextField input = new TextField();
    private final NumberFormat formatter = NumberFormat.getNumberInstance(INDONESIA);
    private boolean internalChange;

    public CurrencyField() {
        this(null);
    }

    public CurrencyField(String label) {
        setLabel(label);

        formatter.setGroupingUsed(true);
        formatter.setMinimumFractionDigits(0);
        formatter.setMaximumFractionDigits(0);

        input.setWidthFull();
        input.setClearButtonVisible(true);
        input.setAllowedCharPattern("[0-9]");
        input.setPrefixComponent(new Span("Rp"));
        input.setPlaceholder("0");
        input.getElement().setAttribute("inputmode", "numeric");

        input.addValueChangeListener(event -> {
            if (!internalChange) {
                updateValue();
            }
        });
        input.addFocusListener(event -> showRawValue());
        input.addBlurListener(event -> {
            if (!internalChange) {
                updateValue();
                showFormattedValue();
            }
        });

        add(input);
    }

    @Override
    protected BigDecimal generateModelValue() {
        String value = input.getValue();
        if (value == null || value.isBlank()) {
            return null;
        }

        String digits = value.replaceAll("[^0-9]", "");
        return digits.isBlank() ? null : new BigDecimal(digits);
    }

    @Override
    protected void setPresentationValue(BigDecimal value) {
        internalChange = true;
        try {
            input.setValue(value == null ? "" : formatter.format(value));
        } finally {
            internalChange = false;
        }
    }

    private void showRawValue() {
        BigDecimal value = getValue();

        internalChange = true;
        try {
            input.setValue(value == null ? "" : value.toBigInteger().toString());
        } finally {
            internalChange = false;
        }
    }

    private void showFormattedValue() {
        setPresentationValue(generateModelValue());
    }

    @Override
    public void setReadOnly(boolean readOnly) {
        super.setReadOnly(readOnly);
        input.setReadOnly(readOnly);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        input.setEnabled(enabled);
    }
}
