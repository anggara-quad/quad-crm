package com.quadteknologi.crm.service;

import java.time.LocalDateTime;
import java.util.UUID;

public record RecentActivityDto(
        String title,
        String typeCode,
        String typeName,
        String target,
        String targetType,
        UUID targetPublicId,
        String salesName,
        LocalDateTime activityDate) {
}
