package com.quadteknologi.crm.util;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class TextUtils {

    private TextUtils() {
    }

    public static boolean containsSearch(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    public static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    public static String initials(String value) {
        return initials(value, "?");
    }

    public static String initials(String value, String fallback) {
        return initials(value, fallback, 2);
    }

    public static String initials(String value, String fallback, int singlePartLength) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String[] parts = value.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(singlePartLength, parts[0].length())).toUpperCase(Locale.ROOT);
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase(Locale.ROOT);
    }

    public static String normalizeSearch(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public static Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public static String sortText(String value) {
        return value == null ? "" : value.trim();
    }

    public static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public static String valueOrDash(String value) {
        return valueOrFallback(value, "-");
    }

    public static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    public static String valueOrFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
