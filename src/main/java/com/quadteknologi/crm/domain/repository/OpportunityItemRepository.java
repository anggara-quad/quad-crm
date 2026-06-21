package com.quadteknologi.crm.domain.repository;

import com.quadteknologi.crm.domain.entity.OpportunityItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OpportunityItemRepository extends JpaRepository<OpportunityItem, Long> {

    @EntityGraph(attributePaths = {"productType"})
    List<OpportunityItem> findByOpportunityIdOrderBySortOrderAsc(Long opportunityId);

    void deleteByOpportunityId(Long opportunityId);
}
