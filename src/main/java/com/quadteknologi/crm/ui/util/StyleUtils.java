package com.quadteknologi.crm.ui.util;

import java.math.BigDecimal;

public final class StyleUtils {

    private StyleUtils() {
    }

    public static BigDecimal amountOrZero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    public static String normalizeColor(String color) {
        return color == null || color.isBlank() ? "default" : color.toLowerCase().replace('_', '-');
    }

    public static int probabilityOrZero(Integer probability) {
        return probability == null ? 0 : probability;
    }
}
