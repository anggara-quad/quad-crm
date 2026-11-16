package com.quadteknologi.crm.service;

import com.quadteknologi.crm.domain.entity.InternalCompany;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import java.util.Optional;

@Component
@SessionScope
public class ActiveInternalCompanySession {

    private final InternalCompanyService internalCompanyService;
    private Long internalCompanyId;

    public ActiveInternalCompanySession(InternalCompanyService internalCompanyService) {
        this.internalCompanyService = internalCompanyService;
    }

    public Optional<InternalCompany> getCompany() {
        if (internalCompanyId != null) {
            Optional<InternalCompany> selected = internalCompanyService.findById(internalCompanyId)
                    .filter(company -> !Boolean.FALSE.equals(company.getActive()));
            if (selected.isPresent()) {
                return selected;
            }
        }
        Optional<InternalCompany> fallback = internalCompanyService.findDefaultCompany();
        fallback.ifPresent(company -> internalCompanyId = company.getId());
        return fallback;
    }

    public Optional<Long> getCompanyId() {
        return getCompany().map(InternalCompany::getId);
    }

    public InternalCompany requireCompany() {
        return getCompany()
                .orElseThrow(() -> new IllegalStateException("No active internal company is configured"));
    }

    public Long requireCompanyId() {
        return requireCompany().getId();
    }

    public void select(InternalCompany company) {
        internalCompanyId = company == null ? null : company.getId();
    }
}
