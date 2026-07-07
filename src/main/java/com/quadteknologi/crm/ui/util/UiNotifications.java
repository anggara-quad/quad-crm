package com.quadteknologi.crm.ui.util;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;

import java.util.Objects;

public final class UiNotifications {

    private static final int SUCCESS_DURATION_MS = 2500;
    private static final int ERROR_DURATION_MS = 3500;

    private UiNotifications() {
    }

    public static void showError(String message) {
        showError(message, Notification.Position.BOTTOM_END);
    }

    public static void showError(String message, Notification.Position position) {
        Notification notification = Notification.show(Objects.toString(message, "Action failed"), ERROR_DURATION_MS,
                position);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    public static void showSuccess(String message) {
        showSuccess(message, Notification.Position.BOTTOM_END);
    }

    public static void showSuccess(String message, Notification.Position position) {
        Notification notification = Notification.show(message, SUCCESS_DURATION_MS, position);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }
}
