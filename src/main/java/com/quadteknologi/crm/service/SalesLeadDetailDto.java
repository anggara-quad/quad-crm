package com.quadteknologi.crm.service;

import java.time.LocalDateTime;
import java.util.UUID;

public record SalesLeadDetailDto(
        UUID leadPublicId,
        String title,
        String account,
        String status,
        String source,
        LocalDateTime createdAt,
        boolean valid) {
}
