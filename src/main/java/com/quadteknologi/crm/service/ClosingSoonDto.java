package com.quadteknologi.crm.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ClosingSoonDto(
        UUID opportunityPublicId,
        String title,
        String account,
        String salesName,
        String stage,
        String stageColor,
        BigDecimal amount,
        LocalDate expectedCloseDate) {
}
