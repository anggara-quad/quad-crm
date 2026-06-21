package com.quadteknologi.crm.domain.repository;

import com.quadteknologi.crm.domain.entity.LeadItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeadItemRepository extends JpaRepository<LeadItem, Long> {

    @EntityGraph(attributePaths = {"productType"})
    List<LeadItem> findByLeadIdOrderBySortOrderAsc(Long leadId);

    void deleteByLeadId(Long leadId);
}
