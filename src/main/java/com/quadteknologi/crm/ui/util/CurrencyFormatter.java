package com.quadteknologi.crm.ui.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public final class CurrencyFormatter {

    private static final Locale INDONESIA = Locale.forLanguageTag("id-ID");

    private CurrencyFormatter() {
    }

    public static String formatRupiah(BigDecimal value) {
        if (value == null) {
            return "-";
        }

        NumberFormat formatter = NumberFormat.getNumberInstance(INDONESIA);
        formatter.setMinimumFractionDigits(0);
        formatter.setMaximumFractionDigits(0);
        return formatter.format(value);
    }
}
