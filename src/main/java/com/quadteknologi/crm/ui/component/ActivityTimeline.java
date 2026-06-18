package com.quadteknologi.crm.ui.component;

import com.quadteknologi.crm.domain.entity.Activity;
import com.quadteknologi.crm.domain.entity.OptionValue;
import com.quadteknologi.crm.domain.entity.User;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class ActivityTimeline extends Div {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    public ActivityTimeline(List<Activity> activities) {
        addClassName("activity-timeline");

        Div header = new Div();
        header.addClassName("activity-timeline-header");

        Span title = new Span("Activities");
        title.addClassName("activity-timeline-title");

        Span count = new Span(String.valueOf(activities == null ? 0 : activities.size()));
        count.addClassName("activity-timeline-count");
        header.add(title, count);

        add(header);

        if (activities == null || activities.isEmpty()) {
            Span empty = new Span("No activities yet");
            empty.addClassName("activity-timeline-empty");
            add(empty);
            return;
        }

        Div list = new Div();
        list.addClassName("activity-timeline-list");
        activities.forEach(activity -> list.add(createItem(activity)));
        add(list);
    }

    private Div createItem(Activity activity) {
        Div item = new Div();
        item.addClassName("activity-timeline-item");

        Div marker = new Div();
        marker.addClassNames("activity-timeline-marker", "kanban-color-" + activityColor(activity));

        Div content = new Div();
        content.addClassName("activity-timeline-content");

        Div topRow = new Div();
        topRow.addClassName("activity-timeline-row");

        Span subject = new Span(valueOrFallback(activity.getSubject(), "Activity"));
        subject.addClassName("activity-timeline-subject");

        Span badge = new Span(activityType(activity));
        badge.addClassNames("activity-timeline-badge", "kanban-color-" + activityColor(activity));
        topRow.add(subject, badge);

        Span meta = new Span(activityMeta(activity));
        meta.addClassName("activity-timeline-meta");
        content.add(topRow, meta);

        if (hasText(activity.getDescription())) {
            Paragraph description = new Paragraph(activity.getDescription());
            description.addClassName("activity-timeline-description");
            content.add(description);
        }

        item.add(marker, content);
        return item;
    }

    private String activityType(Activity activity) {
        return Optional.ofNullable(activity.getType())
                .map(OptionValue::getName)
                .filter(this::hasText)
                .orElseGet(() -> valueOrFallback(activity.getTypeCode(), "Note"));
    }

    private String activityColor(Activity activity) {
        return Optional.ofNullable(activity.getType())
                .map(OptionValue::getColor)
                .filter(this::hasText)
                .map(value -> value.trim().toLowerCase().replace('_', '-'))
                .orElse("default");
    }

    private String activityMeta(Activity activity) {
        String date = Optional.ofNullable(activity.getActivityDate())
                .map(this::formatDate)
                .orElse("-");
        String creator = Optional.ofNullable(activity.getCreatedBy())
                .map(User::getFullName)
                .filter(this::hasText)
                .orElse(null);
        return creator == null ? date : date + " by " + creator;
    }

    private String formatDate(LocalDateTime dateTime) {
        return dateTime.format(DATE_TIME_FORMAT);
    }

    private String valueOrFallback(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
